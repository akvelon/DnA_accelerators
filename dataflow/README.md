# Google Cloud Dataflow Accelerators
[Apache Beam](https://beam.apache.org/) provides unified streaming and batch processing to power ML and streaming analytics use cases. [Google Cloud Dataflow](https://cloud.google.com/dataflow) is managed to run Apache Beam in the cloud with minimal latency and costs, and integrates with other Google Cloud products like [Vertex AI](https://cloud.google.com/vertex-ai) and [Tensorflow TFX](https://www.tensorflow.org/tfx).
Akvelon, a [Google Cloud Service Partner](https://cloud.google.com/find-a-partner/partner/akvelon), and an active Apache Beam contributor and [Beam Summit](https://beamsummit.org/) partner, presents several of our favorite accelerators for Dataflow.

[Akvelon, a Google Cloud Partner](https://cloud.google.com/find-a-partner/partner/akvelon), is providing this open-source collection of Dataflow Flex templates as a reference and easy customizations for developers looking to build streaming, batch, and multi-language data pipelines with ML processing in Google Cloud Dataflow.

## Flex Templates for Google Cloud Dataflow

Google Cloud Dataflow [Flex Templates](https://cloud.google.com/dataflow/docs/concepts/dataflow-templates) are a powerful way to build and run data pipelines on Google Cloud Platform. With [Flex Templates](https://cloud.google.com/dataflow/docs/guides/templates/using-flex-templates), you can package your pipeline code and dependencies as a Docker image, and then run it on Dataflow with just a few clicks. This makes it easy to build and deploy complex pipelines quickly and reliably.

* [Salesforce to Txt](flex-templates/salesforce-to-txt) - Flex templates for batch and streaming Salesforce data processing with Google Cloud Dataflow, using Apache Beam [CDAP IO](https://beam.apache.org/documentation/io/built-in/cdap/).
* [Salesforce to BigQuery](flex-templates/salesforce-to-bigquery) - Flex templates for batch and streaming Salesforce data processing with Google Cloud Dataflow and BigQuery, using Apache Beam [CDAP IO](https://beam.apache.org/documentation/io/built-in/cdap/). Flex templates provide a comprehensive example of using Machine Learning (ML) to process streaming data in Dataflow, using Java multi-language pipeline with Python transforms to run custom TFX and PyTorch [ML models](https://github.com/akvelon/DnA_accelerators/tree/dev/dataflow/ml/salesforce). This complete Flex template example also demonstrates creating and setting up [Expansion Service](https://beam.apache.org/documentation/programming-guide/#multi-language-pipelines) in Dataflow to enable running custom Python transforms within a Java pipeline.

## Machine Learning with Google Cloud Dataflow

* [Tensorflow TFX model training with Apache Beam](ml/salesforce/tfx/regression) - a Python notebook and Python Beam pipeline that demonstrate both a Jupyter notebook to train a Tensorflow TFX ML model and the converted ready-to-use Python pipeline
* [PyTorch ML model training and Expansion Service for multi-language pipelines with Apache Beam](ml/salesforce/pytorch/anomaly_detection) - a complete example to train a PyTorch ML model using Apache Beam, convert the notebook to the Python pipeline, create custom Python Transforms and deploy as Apache Beam Expansion Service for Google Cloud Dataflow.

## Getting Started

### Requirements

* Java 8
* Maven 3

[![Open in Cloud Shell](http://gstatic.com/cloudssh/images/open-btn.svg)](https://console.cloud.google.com/cloudshell/editor?cloudshell_git_repo=https%3A%2F%2Fgithub.com%2Fakvelon%2FDnA_accelerators.git)


## Contact Us
[Akvelon](https://akvelon.com/data-analitycs/) is a digital product and software engineering company that empowers strategic advantage and accelerates your path to value in Data and Analytics, AI/ML, MLOps, Application development, and more with innovation and predictable delivery.

* [Get in touch about Data and Analytics and Data Migrations projects](https://akvelon.com/contact-us/).
* [Get in touch about ML projects](https://akvelon.com/contact-us/).
* [Get in touch about Google Cloud projects](https://akvelon.com/contact-us/).
* [Request a feature](https://akvelon.com/contact-us/).
* [Report an issue](https://github.com/akvelon/DnA_accelerators/issues). 

