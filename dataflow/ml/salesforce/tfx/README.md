### Info

Run `train.ipynb` for training

[TFX Beam docs](https://cloud.google.com/dataflow/docs/notebooks/run_inference_tensorflow_with_tfx)

### Data

For the demo we use mock salesforce data with client opportunities.

In this pipeline we use Tensorflow and TFX to build a deep learning model for predicting the amount in the sample data.

Prepared models and parameters tailored for our data are located in `./pretrained`

### Expansion service

[Beam docs](https://beam.apache.org/documentation/glossary/#expansion-service)

In order to make the inference on python available in multilang pipeline, all model inference and data preprocessing is
packed into a custom PTransform that is hosted on expansion service.

Expansion service image must be supplied with all the dependencies that are used in Python part of the pipeline. If these imports are available in `pip`,
we can include them directly in `Dockerfile`, otherwise we can copy the package source to the image and install it directly with `setup.py`.

To start an expansion service for loading custom PTransform with RunInference, you need to:

### Pipeline

### Multilang

[Java multi-language pipelines quickstart](https://beam.apache.org/documentation/sdks/java-multi-language-pipelines/)

The parameters you'll also need to pass are the URIs to saved model weights from `./pretrained`. You also need to specify the hosted expansion service public address in Java pipeline.