FROM apache/beam_python3.9_sdk

RUN apt-get update && apt-get install -y python3-setuptools

ADD . .

RUN python3 -m pip install hdbscan torch autoembedder category-encoders
RUN python3 setup.py install

RUN python3 -m pip install -U apache-beam[gcp]==2.45.0

EXPOSE 8088
ENTRYPOINT python3 -m expansion_service -p 8088 --fully_qualified_name_glob "*"
