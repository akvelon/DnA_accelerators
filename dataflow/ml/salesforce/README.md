#### Salesforce ML overview

This approach uses Python to run inference and data processing part of pipeline.
We make use of custom Apache Beam PTransform packed with processing and ML steps. Each pipeline
consists of expansion service that is responsible for supplying custom libraries and building python
package with the target PTransform.

Repo contains all the images for deploying the service and also a training pipeline for each of the models available.
These particular models and pipelines use data from **salesforce** and are prepared especially to work with this data.

### Install requirements:

* Python 3.9+
* [Google Cloud CLI](https://cloud.google.com/sdk/docs/install)

`pip install -r requirements.txt`

### Structure

Model folders:

* `pytorch/anomaly_detection` - PyTorch/Sklearn pipeline for detecting anomalies within Amount spent
* `tfx/regression` - TFX-based regression for predicting days of opportunity being open

Please refer to these directories for more info.
