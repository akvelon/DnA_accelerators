## Introduction

This experiment uses [PyTorch](https://pytorch.org/)-based and [scikit-learn](https://scikit-learn.org/) clustering model for multi-stage machine learning pipeline using
Python and Apache Beam.

The task is to detect anomalies within the data based on the input values.
In our example we use [Autoembedder](https://github.com/chrislemke/autoembedder) to train the encoder and [HDBSCAN](https://hdbscan.readthedocs.io/)
clustering model for detecting the anomalies in processed data. We use [RunInference](https://beam.apache.org/documentation/transforms/python/elementwise/runinference/) to wrap the models and add them to the custom Beam PTransform.

All the steps are performed on Apache Beam using Python API. 
You can check out train and test by running the Jupyter Notebooks:

* [train.ipynb](train.ipynb)
* [pipeline_test.ipynb](pipeline_test.ipynb)

Prepared models and parameters tailored for our data are located in `./pretrained`. You can use them for testing the pipeline
instead of training the model from the ground.

## Data

For the demo, we use [mock Salesforce data](../../../../data/salesforce) with client opportunities.
To demonstrate the pipeline, we encode the values with PyTorch and then apply a clustering model to detect the anomalies.

## How to use the Anomaly Detection transform in Java multi-language pipeline
You have two options to include a custom Python transform in the multi-language pipeline:
<details>
  <summary>Use PythonExternalTransform to stage PyPI package dependencies</summary>
  </br>
  
  If your custom Python transform is available in PyPI, you can use the `withExtraPackages` method of the 
  `PythonExternalTransform` class and specify the dependencies required by the RunInference model handler in the arguments.
  
  More details in Apache Beam documentation: [Creating cross-language Python transforms](https://beam.apache.org/documentation/programming-guide/#1312-creating-cross-language-python-transforms)
    
  We've published the anomaly detection package and can use it along with other necessary Python packages in our pipeline leaving the expansion service parameter empty:

  ```java
   PythonExternalTransform.<PCollection<?>, PCollection<KV<String, Row>>>from(
                                  ANOMALY_DETECTION_TRANSFORM, options.getExpansionService())
                          .withExtraPackages(Lists.newArrayList("akvelon-test-anomaly-detection",
                      "category_encoders", "torch", "hdbscan", "autoembedder"));
  ```
</details>

> **NOTE:** Only use the Expansion Service when you need a custom environment that includes packages not available in the Beam SDK or not published in PyPI. In other cases, use the `withExtraPackages` method to pass Python PTransforms dependencies

<details>
  <summary>Use Custom Expansion Service</summary>

### Expansion service

#### [Beam documentation on expansion service](https://beam.apache.org/documentation/glossary/#expansion-service)

In order to make the inference on Python available in a multi-language pipeline, all model inference and data preprocessing is 
packed into a custom PTransform that is installed in an expansion service.

The expansion service image must be supplied with all the dependencies that are used in the Python part of the pipeline. If these imports are available in PyPI,
we can include them directly in `Dockerfile`, otherwise we can copy the package source to the image and install it directly with `setup.py`.

In our example, we use both `pip` to collect the required packages and our module `setup.py` in [Dockerfile](./pipeline/Dockerfile) to build an expansion service and then deploy it on a remote host.

By default, the implementation of the expansion service used by the runner doesn't support remote connections thus working only for a pipeline on the same machine.
Our packed pipeline image includes a modified version of the service with allowed outside connections from any address (`0.0.0.0`) to the container.

You can find the original implementation of the expansion service here:

https://github.com/apache/beam/blob/master/sdks/python/apache_beam/runners/portability/expansion_service_main.py

#### Pipeline

All the files that are needed to run the service locally or on the cloud are located in `./pipeline`:

`setup.py` file and `./anomaly_detection` directory are used to install the custom transform in the Docker image and also hold the main pipeline code.

To start an expansion service for loading custom PTransform with RunInference, you need to:

#### 1. Build and test the image locally:
```bash
docker build ./pipeline -t exp_service
```
```bash
docker run -p 8088:8088 exp_service
```
#### 2. Cloud build the image and upload it to the registry:
```bash
gcloud builds submit ./pipeline --tag gs://{gc_project_name}/{project_name}/{tag}:latest
```
We use [Google Cloud CLI](https://cloud.google.com/sdk/docs/install) for building the image. After that the image
will be available by the following path, and we can use it for launching the Cloud Compute instance.

#### 3. Start a Cloud Compute instance:
```
To run the expansion service with all the required modules, the Compute Engine instance needs at least
25 GB of a boot disk.
```

Create a Cloud Compute instance and use the image that we built using Google CLI. You can also check out our
prepared Docker image for anomaly detection (`expansion-service`) and include it instead:

[Akvelon Dockerhub](https://hub.docker.com/r/akvelon/dna-accelerator/tags)

* You also need to make sure that port **8088** is open for incoming connections in [GCP Firewall](https://console.cloud.google.com/networking/firewalls/).

#### 4. Include the custom PTransform:

* Upload the model weights to the bucket:

```bash
gsutil -m cp -r /pretrained gs://{bucket-path}
```
* Include links to the uploaded files as parameters `encoder_uri`, `model_uri` and `params_uri` when importing PTransform.
  
The parameters you'll also need to pass are the URIs to the saved model weights from `./pretrained`. You also need to specify the hosted expansion service public address in the Java pipeline.
#### 5. Start using the service

Launch the prepared machine.
You can now call for custom Beam transform by accessing the external IP with port `8088`

---

More details in Apache Beam documentation: [Java multi-language pipelines quickstart](https://beam.apache.org/documentation/sdks/java-multi-language-pipelines/)

</details>
