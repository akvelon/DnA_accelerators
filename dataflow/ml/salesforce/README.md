#### Salesforce ML overview

This approach uses Python to run the inference and data processing part of the pipeline.
We make use of custom Apache Beam PTransform packed with processing and ML steps. 
Each pipeline uses a Python package with the target PTransform passed to the `withExtraPackages` RunInference class method or supplied by an expansion service.

Repo contains all the images for deploying the service and also a training pipeline for each of the models available.
These particular models and pipelines use data from **Salesforce** and are prepared especially to work with this data.

### Install requirements:

* Python 3.9+
* [Google Cloud CLI](https://cloud.google.com/sdk/docs/install)

`pip install -r requirements.txt`

### Structure

Model folders:

* `pytorch/anomaly_detection` - PyTorch/Sklearn pipeline for detecting anomalies within the Amount spent
* `tfx/regression` - TFX-based regression for predicting days of opportunity being open

Please refer to these directories for more info.
