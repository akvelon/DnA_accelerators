# DnA_accelerators

These Dataflow templates are an effort to solve simple, but large, in-Cloud data
tasks, including data import/export/backup/restore and bulk API operations,
without a development environment. The technology under the hood which makes
these operations possible is the
[Google Cloud Dataflow](https://cloud.google.com/dataflow/) service combined
with a set of [Apache Beam](https://beam.apache.org/) SDK templated pipelines.

Akvelon is providing this collection of pre-implemented Dataflow templates as a
reference and to provide easy customization for developers wanting to extend
their functionality.

[![Open in Cloud Shell](http://gstatic.com/cloudssh/images/open-btn.svg)](https://console.cloud.google.com/cloudshell/editor?cloudshell_git_repo=https%3A%2F%2Fgithub.com%2Fakvelon%2FDnA_accelerators.git)

## Getting Started

### Requirements

* Java 8
* Maven 3

### Building the Project

Build the entire project using the maven compile command.

```sh
mvn clean compile
```

## Template Pipelines

* [Salesforce to Txt](salesforce-to-txt)
* [Salesforce to BigQuery](salesforce-to-bigquery)

## Contact Us

* [Get in touch with us](https://akvelon.com/contact-us/).
* [Report an issue](https://github.com/akvelon/DnA_accelerators/issues).