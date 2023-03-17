###############################################################################
#  Copyright 2023 Akvelon Inc.
#
#  Licensed to the Apache Software Foundation (ASF) under one
#  or more contributor license agreements.  See the NOTICE file
#  distributed with this work for additional information
#  regarding copyright ownership.  The ASF licenses this file
#  to you under the Apache License, Version 2.0 (the
#  "License"); you may not use this file except in compliance
#  with the License.  You may obtain a copy of the License at
#
#      http://www.apache.org/licenses/LICENSE-2.0
#
#  Unless required by applicable law or agreed to in writing, software
#  distributed under the License is distributed on an "AS IS" BASIS,
#  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
#  See the License for the specific language governing permissions and
#  limitations under the License.
###############################################################################
FROM gcr.io/dataflow-templates-base/java8-template-launcher-base:latest

RUN apt-get update && apt-get install -y python3.9 python3.9-venv python3-pip python3-setuptools

#ADD anomaly_detection /template/anomaly_detection
#ADD setup.py /template/setupanomaly.py

RUN python3.9 -m venv /template
RUN . /template/bin/activate

#RUN python3.9 /template/setupanomaly.py install
RUN python3.9 -m pip install apache-beam[gcp] hdbscan torch category-encoders
RUN python3.9 -m pip install -U akvelon-test-anomaly-detection
RUN python3.9 -m pip install autoembedder

ENV FLEX_TEMPLATE_JAVA_CLASSPATH=/template/*
ENV FLEX_TEMPLATE_JAVA_MAIN_CLASS=com.akvelon.salesforce.templates.CdapRunInference
COPY salesforce-to-bigquery-1.0-SNAPSHOT.jar /template/

