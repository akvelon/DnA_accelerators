## Machine Learning with Google Cloud Dataflow

This part of the repo contains the machine learning components written in Python to be used in Beam multi-language pipelines.

* [Tensorflow TFX model training with Apache Beam](salesforce/tfx/regression) - a Python notebook and Python Beam pipeline that demonstrates both Jupyter notebook to train a Tensorflow TFX ML model and the converted ready-to-use Python pipeline
* [PyTorch ML model training and Expansion Service for multi-language pipelines with Apache Beam](salesforce/pytorch/anomaly_detection) - a complete example of training a PyTorch ML model using Apache Beam, converting the notebook to the Python pipeline, creating custom Python Transforms and using these transforms in a RunInference model handler or deploying as Apache Beam Expansion Service for Google Cloud Dataflow.

### Requirements

* Python 3.9+
* [Google Cloud CLI](https://cloud.google.com/sdk/docs/install)

Each experiment contains a separate `requirements.txt`. It's advised to use a virtual environment in order to train or test the models.

