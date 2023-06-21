# Flex Templates for Google Cloud Dataflow

Google Cloud Dataflow [Flex Templates](https://cloud.google.com/dataflow/docs/concepts/dataflow-templates) are a powerful way to build and run data pipelines on Google Cloud Platform. With [Flex Templates](https://cloud.google.com/dataflow/docs/guides/templates/using-flex-templates), you can package your pipeline code and dependencies as a Docker image, and then run it on Dataflow with just a few clicks. This makes it easy to build and deploy complex pipelines quickly and reliably.

[Akvelon, a Google Cloud Partner](https://cloud.google.com/find-a-partner/partner/akvelon), is providing this open-source collection of Dataflow Flex templates as a reference and easy customizations for developers looking to build streaming, batch, multilanguage data pipelines with ML processing in Google Cloud Dataflow.

[![Open in Cloud Shell](http://gstatic.com/cloudssh/images/open-btn.svg)](https://console.cloud.google.com/cloudshell/editor?cloudshell_git_repo=https%3A%2F%2Fgithub.com%2Fakvelon%2FDnA_accelerators.git)

## Getting Started

### Requirements

* Java 8
* Maven 3

### Building the Project

1. Run [dependencies/install-dependencies.sh](../dependencies/install-dependencies.sh) script to install Maven dependecies.

2. Build the entire project using the Maven package command:
```sh
mvn clean package
```

## Template Pipelines
* [Salesforce to Txt](salesforce-to-txt) - Flex templates for batch and streaming Salesforce data processing with Google Cloud Dataflow, using Apache Beam [CDAP IO](https://beam.apache.org/documentation/io/built-in/cdap/).
* [Salesforce to BigQuery](salesforce-to-bigquery) - Flex templates for batch and streaming Salesforce data processing with Google Cloud Dataflow and BigQuery, using Apache Beam [CDAP IO](https://beam.apache.org/documentation/io/built-in/cdap/). Flex templates provide a comprehensive example of using Machine Learning (ML) to process streaming data in Dataflow, using Java multilanguage pipeline with Python transforms to run custom TFX and PyTorch [ML models](https://github.com/akvelon/DnA_accelerators/tree/dev/dataflow/ml/salesforce). This complete Flex template example also demonstrates an option to create and set up a custom [Expansion Service](https://beam.apache.org/documentation/programming-guide/#multi-language-pipelines) in Dataflow to enable running custom Python transforms within a Java pipeline.

## Contact Us
[Akvelon](https://akvelon.com/data-analitycs/) is a digital product and software engineering company that empowers strategic advantage and accelerates your path to value in Data and Analytics, AI/ML, MLOps, Application development, and more with innovation and predictable delivery.

* [Get in touch about Data and Analytics and Data Migrations projects](https://akvelon.com/contact-us/).
* [Get in touch about ML projects](https://akvelon.com/contact-us/). 
* [Get in touch about Google Cloud projects](https://akvelon.com/contact-us/).
* [Request a feature](https://akvelon.com/contact-us/).
* [Report an issue](https://github.com/akvelon/DnA_accelerators/issues). 

