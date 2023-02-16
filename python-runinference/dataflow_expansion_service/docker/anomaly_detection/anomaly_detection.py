import apache_beam as beam
import category_encoders as ce
import numpy as np
import torch
import hdbscan
from autoembedder import Autoembedder
from apache_beam.ml.inference.base import RunInference, PredictionResult, KeyedModelHandler


from apache_beam.ml.inference.sklearn_inference import SklearnModelHandlerNumpy
from apache_beam.ml.inference.sklearn_inference import ModelFileType
from apache_beam.ml.inference.pytorch_inference import PytorchModelHandlerTensor, _convert_to_result


class CustomSklearnModelHandlerNumpy(SklearnModelHandlerNumpy):
    def run_inference(self, batch, model, inference_args=None):
        predictions = hdbscan.approximate_predict(model, batch)
        return [PredictionResult(x, y) for x, y in zip(batch, predictions[0])]


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
        return sum((el[key].element_size() for el in batch for key in el.keys()))


class DecodePrediction(beam.DoFn):
    def process(self, element):
        uid, prediction = element
        cluster = prediction.inference.tolist()
        bq_dict = {"id": uid, "cluster": cluster}
        yield bq_dict


class AnomalyDetection(beam.PTransform):
    def __init__(self):
        super().__init__()

        self.parameters = {
            "hidden_layers": [[25, 20], [20, 10]],
            "epochs": 10,
            "lr": 0.0001,
            "verbose": 1,
            "batch_size": 16,
        }
        self.list_cat = [(64, 32)]
        self.num_cont_features = 1

        self.hasher = ce.HashingEncoder(n_components=64, max_process=1, max_sample=16)
        self.anomaly_detection_model_handler = CustomSklearnModelHandlerNumpy(model_uri='gs://salesforce-example/anomaly-detection/anomaly_detection.model',
                                                                              model_file_type=ModelFileType.JOBLIB)

        self.encoder_handler = CustomPytorchModelHandlerTensor(state_dict_path='gs://salesforce-example/anomaly-detection/encoder.pth',
                                                               model_class=Autoembedder,
                                                               model_params={'config': self.parameters,
                                                                             'num_cont_features': self.num_cont_features,
                                                                             'embedding_sizes': self.list_cat})

    def encode_and_normalize(self, bq_row, num_fields=None, id_field='Id'):
        if num_fields is None:
            num_fields = ['Amount']

        amount_mean = 1137889.913561848
        amount_std = 1197302.0975264315

        target_list = [getattr(bq_row, feature_name) for feature_name in set(bq_row._fields)-set(num_fields)-set(id_field)]
        hashed_list = self.hasher.fit_transform(np.asarray([target_list]))
        result = (getattr(bq_row, id_field),
                  {'cont_cols': torch.Tensor([(getattr(bq_row, x)-amount_mean)/amount_std for x in num_fields]).reshape(-1, 1),
                   'cat_cols': torch.Tensor(hashed_list.to_numpy().reshape(-1, 1))})
        return result

    def expand(self, pcoll):
        return (
                pcoll
                | "Normalize and encode" >> beam.Map(self.encode_and_normalize)
                | "Encode" >> RunInference(model_handler=KeyedModelHandler(self.encoder_handler))
                | "Concat features" >> beam.Map(lambda x: (x[0], x[1].inference.detach().numpy()))
                | "Detect anomaly" >> RunInference(model_handler=KeyedModelHandler(self.anomaly_detection_model_handler))
                | "Get predictions" >> beam.ParDo(DecodePrediction())
        )
