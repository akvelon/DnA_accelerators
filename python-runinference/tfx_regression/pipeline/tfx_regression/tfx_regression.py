# Copyright 2023 Akvelon Inc.
#
# Licensed to the Apache Software Foundation (ASF) under one
# or more contributor license agreements.  See the NOTICE file
# distributed with this work for additional information
# regarding copyright ownership.  The ASF licenses this file
# to you under the Apache License, Version 2.0 (the
# "License"); you may not use this file except in compliance
# with the License.  You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

import tensorflow as tf
import apache_beam as beam
import numpy as np
import datetime
import category_encoders as ce
from apache_beam.ml.inference.base import RunInference
from tfx_bsl.public.beam.run_inference import CreateModelHandler
from tfx_bsl.public.proto import model_spec_pb2


class ExampleProcessor:
    def create_example_with_label(self, feature: np.float32,
                                  label: np.float32) -> tf.train.Example:
        return tf.train.Example(
            features=tf.train.Features(
                feature={'x': self.create_feature(feature),
                         'y': self.create_y(label)
                         }))

    def create_example(self, feature: np.float32):
        return tf.train.Example(
            features=tf.train.Features(
                feature={'x': self.create_feature(feature)}
            ))

    def create_feature(self, element):
        return tf.train.Feature(float_list=tf.train.FloatList(value=element))

    def create_id(self, element):
        return tf.train.Feature(bytes_list=tf.train.BytesList(value=[element]))

    def create_y(self, element):
        return tf.train.Feature(float_list=tf.train.FloatList(value=[element]))


class FormatOutput(beam.DoFn):
    def process(self, element):
        predict_log = element.predict_log
        input_value = tf.train.Example.FromString(predict_log.request.inputs['data'].string_val[0])
        input_float_value = input_value.features.feature['x'].float_list
        output_value = predict_log.response.outputs
        output_float_value = output_value['output_0'].float_val[0]
        yield f"example is {str(input_float_value)} prediction is {str(output_float_value)}"


class TFXRegressor(beam.PTransform):
    def __init__(self):
        super().__init__()
        self.hasher = ce.HashingEncoder(n_components=64, max_process=1, max_sample=1)

    def hash_encode(self, element, id='Id'):
        result = [getattr(element, k) for k in set(element._fields) - {'CreatedDate', 'CloseDate', id}]
        hashed_list = self.hasher.fit_transform(np.asarray([result]))
        return hashed_list.to_numpy()

    def add_target_date_and_encode(self, element, id='Id'):
        # Taking rstrip of trailing Z timezone
        created_date = datetime.datetime.strptime(getattr(element, 'CreatedDate').rstrip('Z'), '%Y-%m-%dT%H:%M:%S.%f')
        closed_date = datetime.datetime.strptime(getattr(element, 'CloseDate'), '%m/%d/%y')
        hashed_list = self.hash_encode(element, id)
        return (closed_date - created_date).days, hashed_list

    def expand(self, pcoll):
        saved_model_spec = model_spec_pb2.SavedModelSpec(model_path='pretrained/tf_regressor')
        inference_spec_type = model_spec_pb2.InferenceSpecType(saved_model_spec=saved_model_spec)
        model_handler = CreateModelHandler(inference_spec_type)
        return (
                pcoll
                | beam.Map(lambda df: self.hash_encode(df))
                | "Convert to tf.Examples" >> beam.Map(lambda x: ExampleProcessor().create_example(feature=x[0]).SerializeToString())
                | RunInference(model_handler)
                | beam.ParDo(FormatOutput())
        )