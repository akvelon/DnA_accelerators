FROM google/cloud-sdk:latest

RUN apt-get update && apt-get install -y default-jdk
RUN apt-get update && apt-get install -y python3.9

RUN pip3 install apache-beam[gcp] hdbscan
