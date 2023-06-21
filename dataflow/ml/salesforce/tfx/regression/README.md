### Info

Run `train.ipynb` for training

[TFX Beam docs](https://cloud.google.com/dataflow/docs/notebooks/run_inference_tensorflow_with_tfx)

### Data

For the demo, we use mock Salesforce data with client opportunities.

In this pipeline, we use Tensorflow and TFX to build a deep-learning model for predicting the opportunity window in days

Prepared models and parameters tailored for our data are located in `./pretrained`

### Custom Python Transforms usage
To use Custom Python Transform you have two options:
<details>
  <summary>Use PythonExternalTransform to stage PyPI package dependencies</summary>
</br>
  
  If your custom Python transform is available in PyPI, you can use the `withExtraPackages` method of the 
  `PythonExternalTransform` class and specify the dependencies required by the RunInference model handler in the arguments.

  More details in Apache Beam documentation: [Creating cross-language Python transforms](https://beam.apache.org/documentation/programming-guide/#1312-creating-cross-language-python-transforms)
</details>

<details>
  <summary>Use Custom Expansion Service</summary>
  
#### Expansion service
> **NOTE:** Only use the Expansion Service when you need a custom environment that includes packages not available in the Beam SDK or not published in pip. In other cases, use the `withExtraPackages` method to pass Python PTransforms dependencies

[Beam docs](https://beam.apache.org/documentation/glossary/#expansion-service)

In order to make the inference on Python available in a multi-language pipeline, all model inference and data preprocessing is
packed into a custom PTransform that is hosted on an expansion service.

The Expansion service image must be supplied with all the dependencies that are used in the Python part of the pipeline. If these imports are available in `pip`,
we can include them directly in `Dockerfile`, otherwise we can copy the package source to the image and install it directly with `setup.py`.

To start an expansion service for loading custom PTransform with RunInference, you need to:
</details>

### Pipeline

### Multi-language

[Java multi-language pipelines quickstart](https://beam.apache.org/documentation/sdks/java-multi-language-pipelines/)

The parameters you'll also need to pass are the URIs to the saved model weights from `./pretrained`. You also need to specify the hosted expansion service public address in the Java pipeline.
