### Info

Run `train.ipynb` for training and `pipeline_test.ipynb` for testing

### Expansion service

To start an expansion service for loading custom PTransform with RunInference, you need to:

###### GCP:

Original implementation of expansion service used by runner can't be used on a remote machine due to `localhost` set as
listening address. Instead we modified the original implementation of the service to make it available for remote connections.

All the stuff needed to run the service locally or on cloud is located in `/pipeline`.

1. Start a cloud compute instance with at least 25 GB of boot disk. Port 8088 must be open for incoming connections.
2. (Optional) Build and test the image locally: `docker build ./pipeline -t exp_service_test` then `docker run -p 8088:8088 exp_service_test`
3. Cloud build the image and upload it to the registry: `gcloud builds submit ./pipeline --tag gs://{gcp_proj}/{project_name}/{tag}:latest`
4. Use your image as compute image base and run the expansion service