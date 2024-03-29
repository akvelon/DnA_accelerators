# Dataflow Flex Template to ingest data from Cdap Salesforce Streaming Plugin to Txt

This directory contains a Dataflow Flex Template that creates a streaming pipeline
to read data from a [Cdap Salesforce Streaming plugin](https://github.com/data-integrations/salesforce) and write data into a .txt file.

## Requirements

- Java 8
- Salesforce account with data

## Getting Started

This section describes what is needed to get the template up and running.
- Set up the environment
- Build Cdap Salesforce Streaming to Txt Flex Template
- Create a Dataflow job to ingest data using the template

#### Pipeline variables:

```
PROJECT=<my-project>
BUCKET_NAME=<my-bucket>
REGION=<my-region>
```

#### Template Metadata Storage Bucket Creation

The Dataflow Flex template has to store its metadata in a bucket in
[Google Cloud Storage](https://cloud.google.com/storage), so it can be executed from the Google Cloud Platform.
Create the bucket in Google Cloud Storage if it doesn't exist yet:

```
gsutil mb gs://${BUCKET_NAME}
```

#### Containerization variables:

```
IMAGE_NAME=<my-image-name>
TARGET_GCR_IMAGE=gcr.io/${PROJECT}/${IMAGE_NAME}
BASE_CONTAINER_IMAGE=<my-base-container-image>
BASE_CONTAINER_IMAGE_VERSION=<my-base-container-image-version>
TEMPLATE_PATH="gs://${BUCKET_NAME}/templates/salesforce-to-txt.json"
```
OPTIONAL
```
JS_PATH=gs://path/to/udf
JS_FUNC_NAME=my-js-function
```

## Build Cdap Salesforce Streaming to Txt Flex Dataflow Template

Dataflow Flex Templates package the pipeline as a Docker image and stage these images
on your project's [Container Registry](https://cloud.google.com/container-registry).

### Assembling the Uber-JAR

The Dataflow Flex Templates require your Java project to be built into
an Uber JAR file.

Navigate to the v2 folder:

```
cd /path/to/DataflowTemplates/v2
```

Build the Uber JAR:

```
mvn package -am -pl salesforce-to-txt
```

ℹ️ An **Uber JAR** - also known as **fat JAR** - is a single JAR file that contains
both target package *and* all its dependencies.

The result of the `package` task execution is a `salesforce-to-txt-1.0-SNAPSHOT.jar`
file that is generated under the `target` folder in salesforce-to-txt directory.

### Creating the Dataflow Flex Template

To execute the template you need to create the template spec file containing all
the necessary information to run the job. This template already has the following
[metadata file](src/main/resources/salesforce_to_txt_metadata.json) in resources.

Navigate to the template folder:

```
cd /path/to/DataflowTemplates/v2/salesforce-to-txt
```

Build the Dataflow Flex Template:

```
gcloud dataflow flex-template build ${TEMPLATE_PATH} \
       --image-gcr-path "${TARGET_GCR_IMAGE}" \
       --sdk-language "JAVA" \
       --flex-template-base-image ${BASE_CONTAINER_IMAGE} \
       --metadata-file "src/main/resources/salesforce_to_txt_metadata.json" \
       --jar "target/salesforce-to-txt-1.0-SNAPSHOT.jar" \
       --env FLEX_TEMPLATE_JAVA_MAIN_CLASS="com.akvelon.salesforce.templates.CdapSalesforceStreamingToTxt"
```

### Executing Template

To deploy the pipeline, you should refer to the template file and pass the
[parameters](https://cloud.google.com/dataflow/docs/guides/specifying-exec-params#setting-other-cloud-dataflow-pipeline-options)
required by the pipeline.

The template requires the following parameters:
- `referenceName` - This will be used to uniquely identify this source.
- `loginUrl` - Salesforce endpoint to authenticate to. Example: *'https://MyDomainName.my.salesforce.com/services/oauth2/token'*.
- `SObjectName` - Salesforce object to pull supported by CDAP Salesforce Streaming Source.
- `pushTopicName` - name of the push topic that was created from query for some sObject. This push topic should have enabled *pushTopicNotifyCreate* property.
  If push topic with such name doesn't exist, then new push topic for provided **'sObjectName'** will be created automatically.
- `outputTxtFilePathPrefix` - Path to output folder with filename prefix. It will write a set of .txt files with names like {prefix}-###.

The template allows for the user to supply the following optional parameters:
- `pullFrequencySec` - delay in seconds between polling for new records updates.
- `startOffset` - inclusive start offset from which the reading should be started.
- `secretStoreUrl`: URL to Salesforce credentials in HashiCorp Vault secret storage in the format
  'http(s)://vaultip:vaultport/path/to/credentials'
- `vaultToken`: Token to access HashiCorp Vault secret storage

You can provide the next secured parameters directly instead of providing HashiCorp Vault parameters:
- `username` - Salesforce username.
- `password` - Salesforce user password.
- `securityToken` - Salesforce security token.
- `consumerKey` - Salesforce connected app's consumer key.
- `consumerSecret` - Salesforce connected app's consumer secret.

You can do this in 3 different ways:
1. Using [Dataflow Google Cloud Console](https://console.cloud.google.com/dataflow/jobs)

2. Using `gcloud` CLI tool
    ```bash
    gcloud dataflow flex-template run "salesforce-to-txt-`date +%Y%m%d-%H%M%S`" \
        --template-file-gcs-location "${TEMPLATE_PATH}" \
        --parameters username="your-username" \
        --parameters password="your-password" \
        --parameters securityToken="your-token" \
        --parameters consumerKey="your-key" \
        --parameters consumerSecret="your-secret" \
        --parameters loginUrl="https://MyDomainName.my.salesforce.com/services/oauth2/token" \
        --parameters SObjectName="Accounts" \
        --parameters pushTopicName="your-topic" \
        --parameters secretStoreUrl="http(s)://host:port/path/to/credentials" \
        --parameters vaultToken="your-token" \
        --parameters referenceName="your-reference-name" \
        --parameters outputTxtFilePathPrefix="your-file-path-prefix" \
        --region "${REGION}"
    ```
3. With a REST API request
    ```
    API_ROOT_URL="https://dataflow.googleapis.com"
    TEMPLATES_LAUNCH_API="${API_ROOT_URL}/v1b3/projects/${PROJECT}/locations/${REGION}/flexTemplates:launch"
    JOB_NAME="salesforce-to-txt-`date +%Y%m%d-%H%M%S-%N`"

    time curl -X POST -H "Content-Type: application/json" \
        -H "Authorization: Bearer $(gcloud auth print-access-token)" \
        -d '
         {
             "launch_parameter": {
                 "jobName": "'$JOB_NAME'",
                 "containerSpecGcsPath": "'$TEMPLATE_PATH'",
                 "parameters": {
                     "username"="your-username",
                     "password"="your-password",
                     "securityToken"="your-token",
                     "consumerKey"="your-key",
                     "consumerSecret"="your-secret",
                     "loginUrl"="https://MyDomainName.my.salesforce.com/services/oauth2/token",
                     "SObjectName"="Accounts",
                     "pushTopicName": "your-topic",
                     "secretStoreUrl": "http(s)://host:port/path/to/credentials",
                     "vaultToken": "your-token",
                     "referenceName": "your-reference-name",
                     "outputTxtFilePathPrefix": "your-file-path-prefix"
                 }
             }
         }
        '
        "${TEMPLATES_LAUNCH_API}"
    ```
