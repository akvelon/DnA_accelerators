# Dataflow Flex templates to ingest data from Cdap Salesforce to BigQuery

This directory contains a set of Dataflow Flex templates that create a streaming / batch pipeline
to read data from a [Cdap Salesforce Streaming plugin](https://github.com/data-integrations/salesforce) and write data into BigQuery table.

## Requirements

- Java 8
- Maven 3.8
- Salesforce account with data
- BigQuery table for output
- Hashicorp Vault (Optional)

## Getting Started

This section describes what is needed to run precompiled template that already built.
- Prepare Flex Template artifacts
- Template parameters to set
- How to create and run Dataflow job from Flex Template

#### Artifacts
[Expansion service](https://hub.docker.com/layers/akvelon/dna-accelerator/expansion-service/images/sha256-045986791106f035993819d3ff3b66ac182489a45c14eba78c6f5077ff11910f?context=explore) image
```
docker pull akvelon/dna-accelerator:expansion-service
```

[Template launcher](https://hub.docker.com/layers/akvelon/dna-accelerator/template-launcher/images/sha256-ec1ba066d40a9b2dc6b7fe82dccbbfdc2d8cfed4664a501332bc35e48a5045f2?context=explore) image
```
docker pull akvelon/dna-accelerator:template-launcher
```

#### Run Dataflow job
Upload Dataflow template json to GCP storage and follow [Running templates documentation](https://cloud.google.com/dataflow/docs/guides/templates/running-templates) setting all [template parameters](#template-parameters)

## How to build templates

This section describes how to build and run one of the available templates in this repository.
You should follow:
1. First steps paragraph.
2. Building particular template paragraph.

### First steps

#### Setting up pipeline variables:

```
PROJECT=<my-gcp-project>
BUCKET_NAME=<my-cloud-storage-bucket>
REGION=<my-region>
```

#### Template Metadata Storage Bucket Creation

The Dataflow Flex template has to store its metadata in a bucket in
[Google Cloud Storage](https://cloud.google.com/storage), so it can be executed from the Google Cloud Platform.
Create the bucket in Google Cloud Storage if it doesn't exist yet:

```
gsutil mb gs://${BUCKET_NAME}
```

#### Setting up containerization variables:

```
IMAGE_NAME=<my-image-name>
TARGET_GCR_IMAGE=gcr.io/${PROJECT}/${IMAGE_NAME}
BASE_CONTAINER_IMAGE=<my-base-container-image>
BASE_CONTAINER_IMAGE_VERSION=<my-base-container-image-version>
TEMPLATE_PATH="gs://${BUCKET_NAME}/templates/your-template-name.json"
```

#### Assembling the Uber-JAR

The Dataflow Flex Templates require your Java project to be built into
an Uber JAR file.

Build the Uber JAR:

```
cd .. && mvn package -am -pl salesforce-to-bigquery && cd salesforce-to-bigquery
```

An **Uber JAR** - also known as **fat JAR** - is a single JAR file that contains
both target package *and* all its dependencies.

The result of the `package` task execution is a `salesforce-to-bigquery-1.0-SNAPSHOT.jar`
file that is generated under the `target` folder in salesforce-to-bigquery directory.

Then follow one of instructions for building particular template:
#### Available template pipelines

- [Salesforce to BigQuery Batch template](src/main/java/com/akvelon/salesforce/templates/CdapSalesforceBatchToBigQuery.java)
- [Salesforce to BigQuery Streaming template](src/main/java/com/akvelon/salesforce/templates/CdapSalesforceStreamingToBigQuery.java)
- [Salesforce to BigQuery Streaming multi-language (Java + Python ML) template](src/main/java/com/akvelon/salesforce/templates/CdapRunInference.java)


### Build Salesforce to BigQuery Batch template

Dataflow Flex Templates package the pipeline as a Docker image and stage these images
on your project's [Container Registry](https://cloud.google.com/container-registry).

#### Creating the Dataflow Flex Template

To execute the template you need to create the template spec file containing all
the necessary information to run the job. This template already has the following
[metadata file](src/main/resources/salesforce_to_bigquery_batch_metadata.json) in resources.

Navigate to the template folder:

```
cd salesforce-to-bigquery
```

Build the Dataflow Flex Template:

```
gcloud dataflow flex-template build ${TEMPLATE_PATH} \
       --image-gcr-path "${TARGET_GCR_IMAGE}" \
       --sdk-language "JAVA" \
       --flex-template-base-image ${BASE_CONTAINER_IMAGE} \
       --metadata-file "src/main/resources/salesforce_to_bigquery_batch_metadata.json" \
       --jar "target/salesforce-to-bigquery-1.0-SNAPSHOT.jar" \
       --env FLEX_TEMPLATE_JAVA_MAIN_CLASS="com.akvelon.salesforce.templates.CdapSalesforceBatchToBigQuery"
```

#### Template Parameters

To deploy the pipeline, you should refer to the template file and pass the
[parameters](https://cloud.google.com/dataflow/docs/guides/specifying-exec-params#setting-other-cloud-dataflow-pipeline-options)
required by the pipeline.

The template requires the following parameters:
- `referenceName` - This will be used to uniquely identify this source.
- `loginUrl` - Salesforce endpoint to authenticate to. Example: *'https://MyDomainName.my.salesforce.com/services/oauth2/token'*.
- `SObjectName` - Salesforce object to pull supported by CDAP Salesforce Streaming Source.
- `pushTopicName` - name of the push topic that was created from query for some sObject. This push topic should have enabled *pushTopicNotifyCreate* property.
  If push topic with such name doesn't exist, then new push topic for provided **'SObjectName'** will be created automatically.
- `outputTableSpec` - Big Query table spec to write the output to.

The template allows for the user to supply the following optional parameters:
- `offset` - Salesforce SObject query offset. Example: *1 days, 2 hours, 30 minutes*.
- `duration` - Salesforce SObject query duration. Example: *1 days, 2 hours, 30 minutes*.
- `query` - The SOQL query to retrieve results from. Example: *select Id, Name from Opportunity*.
- `datetimeBefore` - Salesforce SObject query datetime filter. Example: *2019-03-12T11:29:52Z*.
- `datetimeAfter` - Salesforce SObject query datetime filter. Example: *2019-03-12T11:29:52Z*.

You can provide the next secured parameters directly instead of providing HashiCorp Vault parameters:
- `username` - Salesforce username.
- `password` - Salesforce user password.
- `securityToken` - Salesforce security token.
- `consumerKey` - Salesforce connected app's consumer key.
- `consumerSecret` - Salesforce connected app's consumer secret.

To run the template follow [How to run template instruction](#how-to-run-template)

### Build Salesforce to BigQuery Streaming template

Dataflow Flex Templates package the pipeline as a Docker image and stage these images
on your project's [Container Registry](https://cloud.google.com/container-registry).

#### Creating the Dataflow Flex Template

To execute the template you need to create the template spec file containing all
the necessary information to run the job. This template already has the following
[metadata file](src/main/resources/salesforce_to_bigquery_streaming_metadata.json) in resources.

Navigate to the template folder:

```
cd salesforce-to-bigquery
```

Build the Dataflow Flex Template:

```
gcloud dataflow flex-template build ${TEMPLATE_PATH} \
       --image-gcr-path "${TARGET_GCR_IMAGE}" \
       --sdk-language "JAVA" \
       --flex-template-base-image ${BASE_CONTAINER_IMAGE} \
       --metadata-file "src/main/resources/salesforce_to_bigquery_streaming_metadata.json" \
       --jar "target/salesforce-to-bigquery-1.0-SNAPSHOT.jar" \
       --env FLEX_TEMPLATE_JAVA_MAIN_CLASS="com.akvelon.salesforce.templates.CdapSalesforceStreamingToBigQuery"
```

#### Template Parameters

To deploy the pipeline, you should refer to the template file and pass the
[parameters](https://cloud.google.com/dataflow/docs/guides/specifying-exec-params#setting-other-cloud-dataflow-pipeline-options)
required by the pipeline.

The template requires the following parameters:
- `referenceName` - This will be used to uniquely identify this source.
- `loginUrl` - Salesforce endpoint to authenticate to. Example: *'https://MyDomainName.my.salesforce.com/services/oauth2/token'*.
- `SObjectName` - Salesforce object to pull supported by CDAP Salesforce Streaming Source.
- `pushTopicName` - name of the push topic that was created from query for some sObject. This push topic should have enabled *pushTopicNotifyCreate* property.
  If push topic with such name doesn't exist, then new push topic for provided **'sObjectName'** will be created automatically.
- `outputTableSpec` - Big Query table spec to write the output to.

The template allows for the user to supply the following optional parameters:
- `pullFrequencySec` - delay in seconds between polling for new records updates.
- `startOffset` - inclusive start offset from which the reading should be started.
- `secretStoreUrl` - URL to Salesforce credentials in HashiCorp Vault secret storage in the format
  'http(s)://vaultip:vaultport/path/to/credentials'.
- `vaultToken` - Token to access HashiCorp Vault secret storage.
- `outputDeadLetterTable` - The dead-letter table to output to within BigQuery in <project-id>:<dataset>.<table> format.

You can provide the next secured parameters directly instead of providing HashiCorp Vault parameters:
- `username` - Salesforce username.
- `password` - Salesforce user password.
- `securityToken` - Salesforce security token.
- `consumerKey` - Salesforce connected app's consumer key.
- `consumerSecret` - Salesforce connected app's consumer secret. 

To run the template follow [How to run template instruction](#how-to-run-template)

### Build Salesforce to BigQuery Streaming multi-language (Java + Python ML) template

Dataflow Flex Templates package the pipeline as a Docker image and stage these images
on your project's [Container Registry](https://cloud.google.com/container-registry).

#### Prerequisites

Multi-language Dataflow templates requires Python Expansion service.
Additional information you can find [here](../Dataflow/ML/runinference/anomaly_detection/README.md).

#### Creating the Dataflow Flex Template

To execute the template you need to create the template spec file containing all
the necessary information to run the job. This template already has the following
[metadata file](src/main/resources/salesforce_to_bigquery_runinference_metadata.json) in resources.

Navigate to the template folder:

```
cd salesforce-to-bigquery
```

Build the Dataflow Flex Template:

```
gcloud dataflow flex-template build ${TEMPLATE_PATH} \
       --image-gcr-path "${TARGET_GCR_IMAGE}" \
       --sdk-language "JAVA" \
       --flex-template-base-image ${BASE_CONTAINER_IMAGE} \
       --metadata-file "src/main/resources/salesforce_to_bigquery_runinference_metadata.json" \
       --jar "target/salesforce-to-bigquery-1.0-SNAPSHOT.jar" \
       --env FLEX_TEMPLATE_JAVA_MAIN_CLASS="com.akvelon.salesforce.templates.CdapRunInference"
```

#### Additional steps for multi-language templates

1. Navigate to the `flex-templates` folder:

```
cd DnA_accelerators/dataflow/flex-templates
```
3. Rebuild your project using this command:
```
mvn clean install
```
4. Navigate to `resources` folder:
```
cd salesforce-to-bigquery/src/main/resources
``` 
4. Copy `salesforce-to-bigquery-1.0-SNAPSHOT.jar` file from the target folder to the `resources` folder from step 3.
```
cp ../../../target/salesforce-to-bigquery-1.0-SNAPSHOT.jar .
``` 
6. Execute the following command:
```
gcloud builds submit . --tag ${TARGET_GCR_IMAGE}:latest
```

*Note: this command will replace the default image to the image with Java and Python.
It is needed to run multi-language templates.*

#### Template Parameters

To deploy the pipeline, you should refer to the template file and pass the
[parameters](https://cloud.google.com/dataflow/docs/guides/specifying-exec-params#setting-other-cloud-dataflow-pipeline-options)
required by the pipeline.

The template requires the following parameters:
- `referenceName` - This will be used to uniquely identify this source.
- `loginUrl` - Salesforce endpoint to authenticate to. Example: *'https://MyDomainName.my.salesforce.com/services/oauth2/token'*.
- `SObjectName` - Salesforce object to pull supported by CDAP Salesforce Streaming Source.
- `pushTopicName` - name of the push topic that was created from query for some sObject. This push topic should have enabled *pushTopicNotifyCreate* property.
  If push topic with such name doesn't exist, then new push topic for provided **'sObjectName'** will be created automatically.
- `outputTableSpec` - Big Query table spec to write the output to.
- `outputDeadLetterTable` - The dead-letter table to output to within BigQuery in <project-id>:<dataset>.<table> format. 
- `expansionService` - Python expansion service in format host:port, needed for RunInference transforms. You can use pre-build [expansion service image](https://hub.docker.com/layers/akvelon/dna-accelerator/expansion-service/images/sha256-045986791106f035993819d3ff3b66ac182489a45c14eba78c6f5077ff11910f?context=explore), create a Compute Engine VM using the [expansion service image](https://hub.docker.com/layers/akvelon/dna-accelerator/expansion-service/images/sha256-045986791106f035993819d3ff3b66ac182489a45c14eba78c6f5077ff11910f?context=explore), and configure port 8088 open for incoming connections in GCP Firewall. Please see [Expansion Service section](https://github.com/akvelon/DnA_accelerators/blob/main/dataflow/ml/salesforce/pytorch/anomaly_detection/README.md#expansion-service) for full steps to build your expansion service.
- `modelUri` - Model URI for Python ML RunInference.
- `paramsUri` - Params URI for Python ML RunInference.
- `encoderUri` - Encoder URI for Python ML RunInference.

The template allows for the user to supply the following optional parameters:
- `pullFrequencySec` - delay in seconds between polling for new records updates.
- `startOffset` - inclusive start offset from which the reading should be started.
- `secretStoreUrl` - URL to Salesforce credentials in HashiCorp Vault secret storage in the format
  'http(s)://vaultip:vaultport/path/to/credentials'.
- `vaultToken` - Token to access HashiCorp Vault secret storage.

You can provide the next secured parameters directly instead of providing HashiCorp Vault parameters:
- `username` - Salesforce username.
- `password` - Salesforce user password.
- `securityToken` - Salesforce security token.
- `consumerKey` - Salesforce connected app's consumer key.
- `consumerSecret` - Salesforce connected app's consumer secret.

## How to run template

You can execute template in 3 different ways:
1. Using [Dataflow Google Cloud Console](https://console.cloud.google.com/dataflow/jobs)

2. Using `gcloud` CLI tool
    ```bash
    gcloud dataflow flex-template run "salesforce-to-bigquery-`date +%Y%m%d-%H%M%S`" \
        --template-file-gcs-location "${TEMPLATE_PATH}" \
        --parameters username="your-username" \
        --parameters password="your-password" \
        --parameters securityToken="your-token" \
        --parameters consumerKey="your-key" \
        --parameters consumerSecret="your-secret" \
        --parameters loginUrl="https://MyDomainName.my.salesforce.com/services/oauth2/token" \
        --parameters referenceName="job referenceName" \
        --parameters SObjectName="Accounts" \
        #...other parameters
        --parameters outputTableSpec="BigQuery output table" \
        --region "${REGION}"
    ```
3. With a REST API request
    ```
    API_ROOT_URL="https://dataflow.googleapis.com"
    TEMPLATES_LAUNCH_API="${API_ROOT_URL}/v1b3/projects/${PROJECT}/locations/${REGION}/flexTemplates:launch"
    JOB_NAME="salesforce-to-bigquery-`date +%Y%m%d-%H%M%S-%N`"

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
                     "SObjectName"="Accounts"
                      //...other parameters
                 }
             }
         }
        '
        "${TEMPLATES_LAUNCH_API}"
    ```
