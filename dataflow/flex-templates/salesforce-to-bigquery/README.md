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

This section describes what is needed to run precompiled Salesforce to BigQuery Streaming multi-language (Java + Python ML) template.
- Prepare Flex Template artifacts
- Template parameters to set
- How to create and run a Dataflow job from Flex Template

#### Artifacts

In order to run precompiled template you need 2 artifacts:
1. Dataflow template JSON
2. Template launcher image

You can find the Dataflow template [JSON file](src/main/resources/salesforce_to_bigquery_runinference_flex_template.json) in the resources folder.
Upload it to your GCP bucket.
By default, it will use the next template launcher image on the Docker hub:

[Template launcher](https://hub.docker.com/layers/akvelon/dna-accelerator/template-launcher/images/sha256-ec1ba066d40a9b2dc6b7fe82dccbbfdc2d8cfed4664a501332bc35e48a5045f2?context=explore) image
```
docker pull akvelon/dna-accelerator:template-launcher
```

> **_NOTE:_** We recommend using the `withExtraPackages` method of the `RunInference` class to pass pip dependencies for your ML models.

Though if your use cases require customization of the environment or using transforms not available in the default Beam SDK, you might need to run your own expansion service before running your pipeline. We provide an example of creating your custom expansion service for such cases.

<details>
  <summary>Click here to see the details</summary>
       
---

You can download the [Custom Expansion service image](https://hub.docker.com/layers/akvelon/dna-accelerator/expansion-service-2.47/images/sha256-a7a0869a6a9ee9ef7036a37c408234aa923fb14acd07f95ed180bbe757141138?context=explore) from the Docker hub:

```
docker pull akvelon/dna-accelerator:expansion-service
```

Start an instance on Google Compute Engine using this expansion service image.
Store the host and port of the expansion service instance, you will be able to provide it as the template parameter later.
Please see [Expansion Service setup](https://github.com/akvelon/DnA_accelerators/blob/main/dataflow/ml/salesforce/pytorch/anomaly_detection/README.md#expansion-service) section for more details.

---

</details>

#### Template parameters

To deploy the pipeline, you should refer to the template file and pass the
[parameters](https://cloud.google.com/dataflow/docs/guides/specifying-exec-params#setting-other-cloud-dataflow-pipeline-options)
required by the pipeline.

The template parameters you can find in [Build Salesforce to BigQuery Streaming multi-language (Java + Python ML) template parameters section](#multi-language-template-params).

#### Run Dataflow job
Upload Dataflow template JSON to GCP storage and follow [Running templates documentation](https://cloud.google.com/dataflow/docs/guides/templates/running-templates) setting all [template parameters](#multi-language-template-params)

## How to build templates

This section describes how to build and run one of the available templates in this repository.
You should follow:
1. First steps paragraph.
2. Building a particular template paragraph.

### First steps
#### GCP active project set up
```
gcloud auth login
```
```
gcloud config set project <my-gcp-project-id>
```
#### Setting up pipeline variables:

```
PROJECT=<my-gcp-project>
BUCKET_NAME=<my-cloud-storage-bucket>
REGION=<my-region>
```

#### Template Metadata Storage Bucket Creation

The Dataflow Flex Template has to store its metadata in a bucket in
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

Then follow one of the instructions for building a particular template:
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

Make sure that you are in the template folder:

```
/salesforce-to-bigquery
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
- `outputTableSpec` - Big Query table spec to write the output to. The table scheme you can find in [salesforce_opportunity_batch_scheme.json](/src/main/resources/bigquery-tables/salesforce_opportunity_batch_scheme.json)

It is also necessary to provide Salesforce security parameters
<details>
  <summary>directly:</summary>
  
- `username` - Salesforce username.
- `password` - Salesforce user password.
- `securityToken` - Salesforce security token.
- `consumerKey` - Salesforce connected app's consumer key.
- `consumerSecret` - Salesforce connected app's consumer secret.

</details>

<details>
  <summary>or as HashiCorp Vault parameters:</summary>
  
- `secretStoreUrl` - URL to Salesforce credentials in HashiCorp Vault secret storage in the format
  'http(s)://vaultip:vaultport/path/to/credentials'.
- `vaultToken` - Token to access HashiCorp Vault secret storage.

</details>

The template allows for the user to supply the following optional parameters:
- `offset` - Salesforce SObject query offset. Example: *1 days, 2 hours, 30 minutes*.
- `duration` - Salesforce SObject query duration. Example: *1 days, 2 hours, 30 minutes*.
- `query` - The SOQL query to retrieve results from. Example: *select Id, Name from Opportunity*.
- `datetimeBefore` - Salesforce SObject query datetime filter. Example: *2019-03-12T11:29:52Z*.
- `datetimeAfter` - Salesforce SObject query datetime filter. Example: *2019-03-12T11:29:52Z*.

To run the template follow [How to run template instruction](#how-to-run-template)

### Build Salesforce to BigQuery Streaming template

Dataflow Flex Templates package the pipeline as a Docker image and stage these images
on your project's [Container Registry](https://cloud.google.com/container-registry).

#### Creating the Dataflow Flex Template

To execute the template you need to create the template spec file containing all
the necessary information to run the job. This template already has the following
[metadata file](src/main/resources/salesforce_to_bigquery_streaming_metadata.json) in resources.

Make sure that you are in the template folder:

```
/salesforce-to-bigquery
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
- `outputTableSpec` - Big Query table spec to write the output to. The table scheme you can find in [salesforce_opportunity_streaming_scheme.json](/src/main/resources/bigquery-tables/salesforce_opportunity_streaming_scheme.json)
- `outputDeadLetterTable` - The dead-letter table to output to within BigQuery in \<project-id\>:\<dataset\>.\<table\> format. The table scheme you can find in [deadletter_table_scheme.json](/src/main/resources/bigquery-tables/deadletter_table_scheme.json)

It is also necessary to provide Salesforce security parameters
<details>
  <summary>directly:</summary>
  
- `username` - Salesforce username.
- `password` - Salesforce user password.
- `securityToken` - Salesforce security token.
- `consumerKey` - Salesforce connected app's consumer key.
- `consumerSecret` - Salesforce connected app's consumer secret.

</details>

<details>
  <summary>or as HashiCorp Vault parameters:</summary>
  
- `secretStoreUrl` - URL to Salesforce credentials in HashiCorp Vault secret storage in the format
  'http(s)://vaultip:vaultport/path/to/credentials'.
- `vaultToken` - Token to access HashiCorp Vault secret storage.

</details>

The template allows for the user to supply the following optional parameters:
- `pullFrequencySec` - delay in seconds between polling for new records updates.
- `startOffset` - inclusive start offset from which the reading should be started.

To run the template follow [How to run template instruction](#how-to-run-template)

### Build Salesforce to BigQuery Streaming multi-language (Java + Python ML) template

Dataflow Flex Templates package the pipeline as a Docker image and stage these images
on your project's [Container Registry](https://cloud.google.com/container-registry).

#### Prerequisites

[Dataflow Runner v2](https://cloud.google.com/dataflow/docs/runner-v2) supports multi-language pipelines out of the box by using a default expansion service with transforms from Beam SDKs. If your use case requires environment customizations or transforms not available in the default Beam SDKs you can create custom expansion service. 
You can find additional information on creating a custom expansion service [here](../../ml/salesforce/pytorch/anomaly_detection/README.md).

#### Creating the Dataflow Flex Template

To execute the template you need to create the template spec file containing all
the necessary information to run the job. This template already has the following
[metadata file](src/main/resources/salesforce_to_bigquery_runinference_metadata.json) in resources.

Make sure that you are in the template folder:

```
/salesforce-to-bigquery
```

Build the Dataflow Flex Template from salesforce-to-bigquery folder:

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

> **_NOTE:_** *Following commands will replace the default launcher image to the image with Java and Python. It is needed to run multi-language templates.*

<br />

1. Navigate to the `flex-templates` folder:

```
cd DnA_accelerators/dataflow/flex-templates
```

2. Rebuild your project using this command:

```
mvn clean install
```

3. Navigate to `resources` folder:
```
cd salesforce-to-bigquery/src/main/resources
```

4. Copy `salesforce-to-bigquery-1.0-SNAPSHOT.jar` file from the target folder to the `resources` folder from step 3.
```
cp ../../../target/salesforce-to-bigquery-1.0-SNAPSHOT.jar .
```
5. Execute the next command:

```
gcloud builds submit . --tag ${TARGET_GCR_IMAGE}:latest
```

#### <a id="multi-language-template-params"></a>Template Parameters
To deploy the pipeline, you should refer to the template file and pass the
[parameters](https://cloud.google.com/dataflow/docs/guides/specifying-exec-params#setting-other-cloud-dataflow-pipeline-options)
required by the pipeline.

The template requires the following parameters:
- `referenceName` - This will be used to uniquely identify this source.
- `loginUrl` - Salesforce endpoint to authenticate to. Example: *'https://MyDomainName.my.salesforce.com/services/oauth2/token'*.
- `SObjectName` - Salesforce object to pull supported by CDAP Salesforce Streaming Source.
- `pushTopicName` - name of the push topic that was created from query for some sObject. This push topic should have enabled *pushTopicNotifyCreate* property.
  If push topic with such name doesn't exist, then a new push topic for provided **'sObjectName'** will be created automatically.
- `outputTableSpec` - Big Query table spec to write the output to. The table scheme you can find in [salesforce_opportunity_anomaly_detection_scheme.json](/src/main/resources/bigquery-tables/salesforce_opportunity_anomaly_detecton_scheme.json)
- `outputDeadLetterTable` - The dead-letter table to output to within BigQuery in \<project-id\>:\<dataset\>.\<table\> format. The table scheme you can find in [deadletter_table_scheme.json](/src/main/resources/bigquery-tables/deadletter_table_scheme.json)
- `modelUri` - Model URI for Python ML RunInference.
- `paramsUri` - Params URI for Python ML RunInference.
- `encoderUri` - Encoder URI for Python ML RunInference.

It is also necessary to provide Salesforce security parameters
<details>
  <summary>directly:</summary>
  
- `username` - Salesforce username.
- `password` - Salesforce user password.
- `securityToken` - Salesforce security token.
- `consumerKey` - Salesforce connected app's consumer key.
- `consumerSecret` - Salesforce connected app's consumer secret.

</details>

<details>
  <summary>or as HashiCorp Vault parameters:</summary>
  
- `secretStoreUrl` - URL to Salesforce credentials in HashiCorp Vault secret storage in the format
  'http(s)://vaultip:vaultport/path/to/credentials'.
- `vaultToken` - Token to access HashiCorp Vault secret storage.

</details>

The template allows the user to supply the following optional parameters:
- `pullFrequencySec` - delay in seconds between polling for new records updates.
- `startOffset` - inclusive start offset from which the reading should be started.
- `expansionService` - Python expansion service in format host:port.
  > **_NOTE:_** You can leave it empty if all of your extra packages are available in pip and you're passing them in `withExtraPackages` method.
  
  You can create Compute Engine VM using pre-build [Expansion Service image](https://hub.docker.com/layers/akvelon/dna-accelerator/expansion-service-2.47/images/sha256-a7a0869a6a9ee9ef7036a37c408234aa923fb14acd07f95ed180bbe757141138?context=explore),
  and configure port 8088 open for incoming connections in GCP Firewall. Please see [Expansion Service setup documentation](https://github.com/akvelon/DnA_accelerators/blob/main/dataflow/ml/salesforce/pytorch/anomaly_detection/README.md#expansion-service) for more details on how to build your expansion service.

## <a id="how-to-run-template"></a>How to run Dataflow Flex Template

You can execute Dataflow Flex Template in 3 different ways:
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
