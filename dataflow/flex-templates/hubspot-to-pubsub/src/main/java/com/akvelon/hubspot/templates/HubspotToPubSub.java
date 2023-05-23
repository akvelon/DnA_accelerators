/*
 * Copyright 2023 Akvelon Inc.
 *
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

import com.akvelon.hubspot.options.CdapHubspotOptions;
import com.akvelon.hubspot.transforms.FormatInputTransform;
import com.akvelon.hubspot.transforms.FormatOutputTransform;
import com.akvelon.hubspot.utils.JsonElementCoder;
import com.akvelon.hubspot.utils.PluginConfigOptionsConverter;
import com.google.gson.JsonElement;
import java.util.Map;
import org.apache.beam.sdk.Pipeline;
import org.apache.beam.sdk.PipelineResult;
import org.apache.beam.sdk.coders.KvCoder;
import org.apache.beam.sdk.coders.NullableCoder;
import org.apache.beam.sdk.coders.StringUtf8Coder;
import org.apache.beam.sdk.io.hadoop.WritableCoder;
import org.apache.beam.sdk.options.PipelineOptionsFactory;
import org.apache.beam.sdk.transforms.MapValues;
import org.apache.beam.sdk.transforms.Values;
import org.apache.beam.sdk.values.TypeDescriptors;
import org.apache.hadoop.io.NullWritable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * HubspotToPubsub Pipeline.
 */
public class HubspotToPubSub {
    /* Logger for class.*/
    private static final Logger LOG = LoggerFactory.getLogger(HubspotToPubSub.class);

    /**
     * Main entry point for pipeline execution.
     *
     * @param args Command line arguments to the pipeline.
     */
    public static void main(String[] args) {
        CdapHubspotOptions options =
                PipelineOptionsFactory.fromArgs(args).withValidation().as(CdapHubspotOptions.class);

        // Create the pipeline
        Pipeline pipeline = Pipeline.create(options);
        run(pipeline, options);
    }

    public static PipelineResult run(Pipeline pipeline, CdapHubspotOptions options) {
        Map<String, Object> paramsMap = PluginConfigOptionsConverter.hubspotOptionsToParamsMap(options);
        LOG.info("Starting Cdap-Hubspot-to-pubsub pipeline with parameters: {}", paramsMap);

        /*
         * Steps:
         *  1) Read messages in from Cdap Hubspot
         *  2) Extract values only
         *  3) Write successful records to PubSub
         */
        pipeline.getCoderRegistry().registerCoderForClass(JsonElement.class, JsonElementCoder.of());

        pipeline
                .apply("readFromCdapHubspot", FormatInputTransform.readFromCdapHubspot(paramsMap))
                .setCoder(
                        KvCoder.of(
                                NullableCoder.of(WritableCoder.of(NullWritable.class)), JsonElementCoder.of()))
                .apply(
                        MapValues.into(TypeDescriptors.strings())
                                .via(
                                        jsonElement -> {
                                            if (jsonElement == null) {
                                                return "{}";
                                            }
                                            return jsonElement.toString();
                                        }))
                .setCoder(
                        KvCoder.of(
                                NullableCoder.of(WritableCoder.of(NullWritable.class)), StringUtf8Coder.of()))
                .apply(Values.create())
                .apply("writeToPubSub", new FormatOutputTransform.FormatOutput(options));

        return pipeline.run();
    }
}
