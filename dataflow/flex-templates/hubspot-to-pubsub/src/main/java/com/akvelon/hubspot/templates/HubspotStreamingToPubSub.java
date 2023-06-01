/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.akvelon.hubspot.templates;

import com.akvelon.hubspot.options.CdapHubspotStreamingSourceOptions;
import com.akvelon.hubspot.transforms.HubspotFormatInput;
import com.akvelon.hubspot.transforms.PubSubFormatOutput;
import com.akvelon.hubspot.utils.PluginConfigOptionsConverter;
import java.util.Map;
import org.apache.beam.sdk.Pipeline;
import org.apache.beam.sdk.PipelineResult;
import org.apache.beam.sdk.coders.KvCoder;
import org.apache.beam.sdk.coders.NullableCoder;
import org.apache.beam.sdk.coders.StringUtf8Coder;
import org.apache.beam.sdk.io.hadoop.WritableCoder;
import org.apache.beam.sdk.options.PipelineOptionsFactory;
import org.apache.beam.sdk.transforms.Values;
import org.apache.beam.sdk.transforms.windowing.AfterProcessingTime;
import org.apache.beam.sdk.transforms.windowing.GlobalWindows;
import org.apache.beam.sdk.transforms.windowing.Repeatedly;
import org.apache.beam.sdk.transforms.windowing.Window;
import org.apache.beam.sdk.values.KV;
import org.apache.hadoop.io.NullWritable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The {@link HubspotToPubSubBatch} Streaming pipeline reading json encoded data from Hubspot and publishes
 * to Google Cloud PubSub. Input topics, output topic. <br>
 *
 * <p><b>Pipeline Requirements</b>
 *
 * <ul>
 *   <li>Hubspot private auth key.
 *   <li>Hubspot Object(s) exists.
 *   <li>The PubSub output topic exists.
 * </ul>
 *
 * <p><b>Example Usage</b>
 *
 * <pre>
 * # Set the pipeline vars
 * PROJECT=id-of-my-project
 * BUCKET_NAME=my-bucket
 * REGION=my-region
 *
 * # Set containerization vars
 * IMAGE_NAME=my-image-name
 * TARGET_GCR_IMAGE=gcr.io/${PROJECT}/${IMAGE_NAME}
 * BASE_CONTAINER_IMAGE=my-base-container-image
 * BASE_CONTAINER_IMAGE_VERSION=my-base-container-image-version
 * TEMPLATE_PATH="gs://${BUCKET_NAME}/templates/hubspot_streaming_to_pubsub.json"
 * TARGET_GCR_IMAGE=gcr.io/${PROJECT}/${IMAGE_NAME}
 *
 * # Create bucket in the cloud storage
 * gsutil mb gs://${BUCKET_NAME}
 *
 * # Go to the beam folder
 * cd /path/to/flex-templates/hubspot-to-pubsub
 *
 * <b>FLEX TEMPLATE</b>
 * # Assemble jar with dependencies
 * mvn package -am -pl hubspot-to-pubsub
 *
 * # Go to the template folder
 * cd /path/to/flex-templates/hubspot-to-pubsub
 *
 * # Build the flex template
 * gcloud dataflow flex-template build ${TEMPLATE_PATH} \
 *       --image-gcr-path "${TARGET_GCR_IMAGE}" \
 *       --sdk-language "JAVA" \
 *       --flex-template-base-image ${BASE_CONTAINER_IMAGE} \
 *       --metadata-file "src/main/resources/hubspot_streaming_to_pubsub.json" \
 *       --jar "target/hubspot-to-pubsub-1.0-SNAPSHOT.jar" \
 *       --env FLEX_TEMPLATE_JAVA_MAIN_CLASS="com.akvelon.hubspot.templates.HubspotToPubSub"
 *
 * # Execute template:
 * API_ROOT_URL="https://dataflow.googleapis.com"
 * TEMPLATES_LAUNCH_API="${API_ROOT_URL}/v1b3/projects/${PROJECT}/locations/${REGION}/flexTemplates:launch"
 * JOB_NAME="hubspot-to-pubsub-`date +%Y%m%d-%H%M%S-%N`"
 *
 * time curl -X POST -H "Content-Type: application/json" \
 *         -H "Authorization: Bearer $(gcloud auth print-access-token)" \
 *         -d '
 *          {
 *              "launch_parameter": {
 *                  "jobName": "'$JOB_NAME'",
 *                  "containerSpecGcsPath": "'$TEMPLATE_PATH'",
 *                  "parameters": {
 *                      "CDAP reference name": "Any String"
 *                      "hubspot private application access token": "your-private-app-access-token",
 *                      "hubspot objects to pull supported by CDAP": "Contacts",
 *                      "outputTopic": "projects/'$PROJECT'/topics/your-topic-name"
 *                  }
 *              }
 *          }
 *         '
 *         "${TEMPLATES_LAUNCH_API}"
 * </pre>
 */
public class HubspotStreamingToPubSub {

  /* Logger for class.*/
  private static final Logger LOG = LoggerFactory.getLogger(HubspotStreamingToPubSub.class);

  /**
   * Main entry point for pipeline execution.
   *
   * @param args Command line arguments to the pipeline.
   */
  public static void main(String[] args) {
    CdapHubspotStreamingSourceOptions options =
        PipelineOptionsFactory.fromArgs(args)
            .withValidation()
            .as(CdapHubspotStreamingSourceOptions.class);

    // Create the pipeline
    Pipeline pipeline = Pipeline.create(options);
    run(pipeline, options);
  }

  /**
   * Runs a pipeline which reads records from CDAP Hubspot and writes it to pubsub topic.
   *
   * @param options arguments to the pipeline
   */
  public static PipelineResult run(Pipeline pipeline, CdapHubspotStreamingSourceOptions options) {
    Map<String, Object> paramsMap = PluginConfigOptionsConverter.hubspotOptionsToParamsMap(options);
    LOG.info("Starting Cdap-Hubspot-streaming-to-pubsub pipeline with parameters: {}", paramsMap);

    /*
     * Steps:
     *  1) Read messages in from Cdap Hubspot
     *  2) Extract values only
     *  3) Write successful records to pubsub topic
     */

    pipeline
        .apply(
            "readFromCdapHubspotStreaming",
            HubspotFormatInput.readFromCdapHubspotStreaming(
                paramsMap, options.getPullFrequencySec(), options.getStartOffset()))
        .setCoder(
            KvCoder.of(
                NullableCoder.of(WritableCoder.of(NullWritable.class)), StringUtf8Coder.of()))
        .apply(
            "globalwindow",
            Window.<KV<NullWritable, String>>into(new GlobalWindows())
                .triggering(Repeatedly.forever(AfterProcessingTime.pastFirstElementInPane()))
                .discardingFiredPanes())
        .apply(Values.create())
        .apply(
            "writeToPubsub",
                new PubSubFormatOutput(options));

    return pipeline.run();
  }
}
