FROM gcr.io/dataflow-templates-base/java8-template-launcher-base:latest

RUN apt-get update && apt-get install -y python3.9 python3.9-venv python3-pip python3-setuptools

ADD anomaly_detection /template/anomaly_detection

RUN python3.9 -m venv /template
RUN . /template/bin/activate

RUN python3.9 /template/anomaly_detection/setup.py install
RUN python3.9 -m pip install apache-beam[gcp] hdbscan torch autoencoder category-encoders

ENV FLEX_TEMPLATE_JAVA_CLASSPATH=/template/*
ENV FLEX_TEMPLATE_JAVA_MAIN_CLASS=com.akvelon.salesforce.templates.CdapRunInference
COPY salesforce-to-bigquery-1.0-SNAPSHOT.jar /template/