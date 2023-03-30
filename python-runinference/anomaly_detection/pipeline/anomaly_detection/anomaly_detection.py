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

import apache_beam as beam
import category_encoders as ce
import numpy as np
import torch
import sys
import joblib
import hdbscan
from autoembedder import Autoembedder
from apache_beam.io.filesystems import FileSystems
from apache_beam.ml.inference.base import RunInference, PredictionResult, KeyedModelHandler
from apache_beam.ml.inference.sklearn_inference import SklearnModelHandlerNumpy, ModelFileType
from apache_beam.ml.inference.pytorch_inference import PytorchModelHandlerTensor, _convert_to_result


class CustomSklearnModelHandlerNumpy(SklearnModelHandlerNumpy):
    def run_inference(self, batch, model, inference_args=None):
        predictions = hdbscan.approximate_predict(model, batch)
        return [PredictionResult(x, y) for x, y in zip(batch[0], predictions[0])]


class CustomPytorchModelHandlerTensor(PytorchModelHandlerTensor):

    def run_inference(self, batch, model, inference_args=None):
        with torch.no_grad():
            list_of_cont_tensors = []
            list_of_cat_tensors = []
            for item in batch:
                list_of_cont_tensors.append(item['cont_cols'])
                list_of_cat_tensors.append(item['cat_cols'])

            batched_tensors_cont = torch.hstack(list_of_cont_tensors)
            batched_tensors_cat = torch.hstack(list_of_cat_tensors)

            model(x_cont=batched_tensors_cont, x_cat=batched_tensors_cat)
            predictions = model.code_value
            return _convert_to_result(batch, predictions)

    def get_num_bytes(self, batch) -> int:
        return sum(sys.getsizeof(element) for element in batch)


class AnomalyDetection(beam.PTransform):
    def __init__(self, encoder_uri, model_uri, params_uri):
        super().__init__()
        self._model_uri = model_uri
        self._encoder_uri = encoder_uri
        self._params_uri = params_uri

        self.params = joblib.load(FileSystems.open(self._params_uri, 'rb'))

        self.hasher = ce.HashingEncoder(n_components=64, max_process=1, max_sample=16)
        self.anomaly_detection_model_handler = CustomSklearnModelHandlerNumpy(model_uri=self._model_uri,
                                                                              model_file_type=ModelFileType.JOBLIB)
        self.encoder_handler = CustomPytorchModelHandlerTensor(state_dict_path=self._encoder_uri,
                                                               model_class=Autoembedder,
                                                               model_params={'config': self.params['params'],
                                                                             'num_cont_features': self.params['num_cont_features'],
                                                                             'embedding_sizes': self.params['list_cat']})
        print(self.encoder_handler)

    def encode_and_normalize(self, bq_row, num_fields=None, id_field='Id'):
        if num_fields is None:
            num_fields = ['Amount']

        # This is mean and deviation calculated on a training data set
        # These parameters are unique for each set of data
        amount_mean = 1137889.913561848
        amount_std = 1197302.0975264315

        target_list = [getattr(bq_row, feature_name) for feature_name in set(bq_row._fields)-set(num_fields)-set([id_field])]
        hashed_list = self.hasher.fit_transform(np.asarray([target_list]))

        cont_cols = torch.Tensor([(getattr(bq_row, x)-amount_mean)/amount_std for x in num_fields]).reshape(-1, 1)
        cat_cols = torch.Tensor(hashed_list.to_numpy().reshape(-1, 1))

        result = (getattr(bq_row, id_field),
                  {'cont_cols': cont_cols,
                   'cat_cols': cat_cols})

        return result

    def expand(self, pcoll):
        return (
                pcoll
                | "Normalize and encode" >> beam.Map(self.encode_and_normalize)
                | "Encode" >> RunInference(model_handler=KeyedModelHandler(self.encoder_handler))
                | "Concat features" >> beam.Map(lambda x: (x[0], x[1].inference.detach().numpy()))
                | "Detect anomaly" >> RunInference(model_handler=KeyedModelHandler(self.anomaly_detection_model_handler))
        )
