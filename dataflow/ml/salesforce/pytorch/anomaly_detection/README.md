## Introduction

This experiment uses [PyTorch](https://pytorch.org/)-based and [scikit-learn](https://scikit-learn.org/) clustering model for multi-stage machine learning pipeline using
Python and Apache Beam.

The task is to detect anomaly within the data based on the input values.
In our example we use [autoembedder](https://github.com/chrislemke/autoembedder) to train the encoder and [hdbscan](https://hdbscan.readthedocs.io/)
clustering model for detecting the anomalies on processed data.

All the steps are performed on Apache Beam using Python API. 
You can check out train and test by running the Jupyter Notebooks:

* [train.ipynb](train.ipynb)
* [pipeline_test.ipynb](pipeline_test.ipynb)

Prepared models and parameters tailored for our data are located in `./pretrained`. You can use them for testing the pipeline
instead of training the model from the ground.

## Data

For the demo we use [mock salesforce data](../../../../data/salesforce) with client opportunities.
To demonstrate the pipeline, we encode the values with PyTorch then apply a clustering model to detect the anomalies.

## Expansion service

### [Beam documentation on expansion service](https://beam.apache.org/documentation/glossary/#expansion-service)

In order to make the inference on python available in multilang pipeline, all model inference and data preprocessing is 
packed into a custom PTransform that is hosted on expansion service.

Expansion service image must be supplied with all the dependencies that are used in Python part of the pipeline. If these imports are available in `pip`,
we can include them directly in `Dockerfile`, otherwise we can copy the package source to the image and install it directly with `setup.py`.

In our example we pack everything we need in expansion service in a `Dockefile` and then deploy in on a remote host.

### Pipeline

All the files that are needed to run the service locally or on cloud are located in `./pipeline`:

`setup.py` and `./anomaly_detection` directory are used to install the custom transform in docker image and also hold the main pipeline code.

Original implementation of expansion service used by runner can't be used on a remote machine due to `localhost` set as
listening address. Instead, we already modified the original implementation of the service to make it available for remote connection.

To start an expansion service for loading custom PTransform with RunInference, you need to:

### 1. Build and test the image locally:
```bash
docker build ./pipeline -t exp_service
```
```bash
docker run -p 8088:8088 exp_service
```
### 2. Cloud build the image and upload it to the registry:
```bash
gcloud builds submit ./pipeline --tag gs://{gc_project_name}/{project_name}/{tag}:latest
```
We use [Google Cloud CLI](https://cloud.google.com/sdk/docs/install) for building the image. After that the image
will be available by the following path, and we can use it for launching Cloud Compute instance.

### 3. Start a cloud compute instance:
```
To run the expansion service with all the required modules, the Compute Engine instance needs at least
25 GB of boot disk.
```

Create a Cloud Compute instance and use the image that we built using Google CLI. You can also check out our
prepared Docker image for anomaly detection (`expansion-service`) and include it instead:

[Akvelon Dockerhub](https://hub.docker.com/r/akvelon/dna-accelerator/tags)

* You also need to make sure that port **8088** is open for incoming connections in [GCP Firewall](https://console.cloud.google.com/networking/firewalls/).

### 4. Include the custom PTransform:

* Upload the model weights to bucket:

```bash
gsutil -m cp -r /pretrained gs://{bucket-path}
```
* Include links to the uploaded files as parameters `encoder_uri`, `model_uri` and `params_uri` when importing PTransform.
### 5. Start using the service

Launch the prepared machine.
You can now call for custom Beam transform by accessing the external ip with port `8088`

### Multilang

This custom PTransform can be directly included in Java pipeline as described here:

[Java multi-language pipelines quickstart](https://beam.apache.org/documentation/sdks/java-multi-language-pipelines/)

The parameters you'll also need to pass are the URIs to saved model weights from `./pretrained`. You also need to specify the hosted expansion service public address in Java pipeline.