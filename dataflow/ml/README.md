## Machine Learning with Google Cloud Dataflow

This part of repo contains the machine learning components written in Python to be used in Beam multi-language pipelines.

* [Tensorflow TFX model training with Apache Beam](salesforce/tfx/regression) - a Python notebook and Python Beam pipeline that demonstrates both Jupyter notebook to train a Tensorflow TFX ML model and the converted Python pipeline ready for Expansion Service use
* [PyTorch ML model training and Expansion Service for multilanguage pipelines with Apache Beam](salesforce/pytorch/anomaly_detection) - a complete example to train a PyTorch ML model using Apache Beam, convert the notebook to the Python pipeline, create custom Python Transforms and deploy as Apache Beam Expansion Service for Google Cloud Dataflow.

### Requirements

* Python 3.9+
* [Google Cloud CLI](https://cloud.google.com/sdk/docs/install)

Each experiment contains separate `requirements.txt`. It's advised to use virtual environment in order to train or test the models.

