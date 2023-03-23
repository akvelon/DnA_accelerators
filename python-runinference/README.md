### Install requirements:
`pip install -r requirements.txt`

### Structure

`data` holds the .csv with salesforce exported data

Model folders:

* `anomaly_detection` - PyTorch/Sklearn pipeline for detecting anomalies within Amount spent
* `tfx_regression` - TFX-based regression for predicting days of opportunity being open

### Run

1. Train the model using `train.ipynb` in model folder
2. Upload the models to `salesforce-example` storage from `pretrained` folder in the model dir. (see notes)
3. Send the image to container storage for use in Java pipeline

### Sending images with python PTransform/RunInference to GCP:

    $ cd {model_folder}/pipeline
    $ gcloud builds submit . --tag gs://salesforce-example/{project_name}/{tag}:latest

Example:

    gcloud builds submit . --tag gs://salesforce-example/tfx_regression/tfx_regression:latest

Notes:

1. You need to provide target `.jar` in each Docker file. By default, it is set to `salesforce-to-bigquery-1.0-SNAPSHOT.jar`.
2. In case you uploaded the model to a specific different folder in google cloud storage, you may need to specify the location of model file in `{model_folder}/pipeline/{model_name}/{model_name}.py`
