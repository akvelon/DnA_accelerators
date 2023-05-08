### Info

Run `train.ipynb` for training and `pipeline_test.ipynb` for testing.

### Data

For the demo we use mock salesforce data with client opportunities.

The task is to detect anomaly within the data based on the input values. Some of them are categorical ones, the other are numerical data.
To demonstrate the pipeline, we encode the values with PyTorch then apply a clustering model to detect the anomalies.

Prepared models and parameters tailored for our data are located in `./pretrained`

### Expansion service

[Beam docs](https://beam.apache.org/documentation/glossary/#expansion-service)

In order to make the inference on python available in multilang pipeline, all model inference and data preprocessing is 
packed into a custom PTransform that is hosted on expansion service.

Expansion service image must be supplied with all the dependencies that are used in Python part of the pipeline. If these imports are available in `pip`,
we can include them directly in `Dockerfile`, otherwise we can copy the package source to the image and install it directly with `setup.py`.

To start an expansion service for loading custom PTransform with RunInference, you need to:

### Pipeline

All the stuff needed to run the service locally or on cloud is located in `./pipeline`:

`setup.py` and `./anomaly_detection` directory are used to install the custom transform in docker image and also hold the main pipeline code.

Original implementation of expansion service used by runner can't be used on a remote machine due to `localhost` set as
listening address. Instead, we already modified the original implementation of the service to make it available for remote connection.

1. Start a cloud compute instance with at least 25 GB of boot disk. Port 8088 must be open for incoming connections.
2. (Optional) Build and test the image locally: `docker build ./pipeline -t exp_service_test` then `docker run -p 8088:8088 exp_service_test`
3. Cloud build the image and upload it to the registry: `gcloud builds submit ./pipeline --tag gs://{gcp_proj}/{project_name}/{tag}:latest`
4. Use your image as compute image base and run the expansion service

### Multilang

Finally, this custom PTransform can be directly included in Java pipeline as described here:

[Java multi-language pipelines quickstart](https://beam.apache.org/documentation/sdks/java-multi-language-pipelines/)

The parameters you'll also need to pass are the URIs to saved model weights from `./pretrained`. You also need to specify the hosted expansion service public address in Java pipeline.