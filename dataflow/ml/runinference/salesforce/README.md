#### Overview

This approach uses python to run inference and data processing part of pipeline.
We make use of custom Apache Beam PTransform packed with processing and ML steps. Each pipeline
consists of expansion service that is responsible for supplying custom libraries and building python
package with the target PTransform.

Repo contains all the images for deploying the service and also a training pipeline for each of the models available.

### Install requirements:
`pip install -r requirements.txt`

### Structure

Model folders:

* `anomaly_detection` - PyTorch/Sklearn pipeline for detecting anomalies within Amount spent
* `tfx_regression` - TFX-based regression for predicting days of opportunity being open

Please refer to these directories for more info.
