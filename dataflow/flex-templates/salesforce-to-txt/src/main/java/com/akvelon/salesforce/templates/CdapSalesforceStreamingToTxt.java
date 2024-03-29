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
package com.akvelon.salesforce.templates;

import static com.akvelon.salesforce.utils.VaultUtils.getSalesforceCredentialsFromVault;

import com.akvelon.salesforce.options.SalesforceToTxtStreamingSourceOptions;
import com.akvelon.salesforce.transforms.FormatInputTransform;
import com.akvelon.salesforce.utils.PluginConfigOptionsConverter;
import io.cdap.plugin.salesforce.SalesforceConstants;
import java.util.Map;
import org.apache.beam.sdk.Pipeline;
import org.apache.beam.sdk.PipelineResult;
import org.apache.beam.sdk.coders.KvCoder;
import org.apache.beam.sdk.coders.NullableCoder;
import org.apache.beam.sdk.coders.StringUtf8Coder;
import org.apache.beam.sdk.io.TextIO;
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
 * The {@link CdapSalesforceStreamingToTxt} pipeline is a streaming pipeline which ingests data in
 * JSON format from CDAP Salesforce, and outputs the resulting records to .txt file. Salesforce
 * parameters and output .txt file path are specified by the user as template parameters. <br>
 *
 * <p><b>Example Usage</b>
 *
 * <pre>
 * # Gradle preparation
 *
 * To run this example your {@code build.gradle} file should contain the following task
 * to execute the pipeline:
 * {@code
 * task executeCdap (type:JavaExec) {
 *     mainClass = System.getProperty("mainClass")
 *     classpath = sourceSets.main.runtimeClasspath
 *     systemProperties System.getProperties()
 *     args System.getProperty("exec.args", "").split()
 * }
 * }
 *
 * This task allows to run the pipeline via the following command:
 * {@code
 * gradle clean executeCdap -DmainClass=com.akvelon.salesforce.CdapSalesforceStreamingToTxt \
 *      -Dexec.args="--<argument>=<value> --<argument>=<value>"
 * }
 *
 * # Running the pipeline
 * To execute this pipeline, specify the parameters in the following format:
 * {@code
 * --username=your-user-name\
 * --password=your-password \
 * --securityToken=your-token \
 * --consumerKey=your-key \
 * --consumerSecret=your-secret \
 * --loginUrl=your-login-url \
 * --sObjectName=object-name \
 * --pushTopicName=your-push-topic-name \
 * --referenceName=your-reference-name \
 * --outputTxtFilePathPrefix=your-path-to-output-folder-with-filename-prefix \
 * --pullFrequencySec=1 \
 * --secretStoreUrl=your-url \
 * --vaultToken=your-token \
 * --startOffset=0
 * }
 *
 * By default this will run the pipeline locally with the DirectRunner. To change the runner, specify:
 * {@code
 * --runner=YOUR_SELECTED_RUNNER
 * }
 * </pre>
 */
public class CdapSalesforceStreamingToTxt {

    /* Logger for class.*/
    private static final Logger LOG = LoggerFactory.getLogger(CdapSalesforceStreamingToTxt.class);

    /**
     * Main entry point for pipeline execution.
     *
     * @param args Command line arguments to the pipeline.
     */
    public static void main(String[] args) {
        SalesforceToTxtStreamingSourceOptions options =
                PipelineOptionsFactory.fromArgs(args)
                        .withValidation()
                        .as(SalesforceToTxtStreamingSourceOptions.class);

        // Create the pipeline
        Pipeline pipeline = Pipeline.create(options);
        run(pipeline, options);
    }

    /**
     * Runs a pipeline which reads records from CDAP Salesforce and writes them to .txt file.
     *
     * @param options arguments to the pipeline
     */
    public static PipelineResult run(
            Pipeline pipeline, SalesforceToTxtStreamingSourceOptions options) {
        if (options.getSecretStoreUrl() != null && options.getVaultToken() != null) {
            Map<String, String> credentials =
                    getSalesforceCredentialsFromVault(options.getSecretStoreUrl(), options.getVaultToken());
            options.setConsumerKey(credentials.get(SalesforceConstants.PROPERTY_CONSUMER_KEY));
            options.setConsumerSecret(credentials.get(SalesforceConstants.PROPERTY_CONSUMER_SECRET));
            options.setSecurityToken(credentials.get(SalesforceConstants.PROPERTY_SECURITY_TOKEN));
            options.setUsername(credentials.get(SalesforceConstants.PROPERTY_USERNAME));
            options.setPassword(credentials.get(SalesforceConstants.PROPERTY_PASSWORD));
        } else {
            LOG.warn(
                    "No information to retrieve Salesforce credentials from store was provided. "
                            + "Trying to retrieve them from pipeline options.");
        }
        Map<String, Object> paramsMap =
                PluginConfigOptionsConverter.salesforceStreamingSourceOptionsToParamsMap(options);
        LOG.info("Starting Cdap-Salesforce-streaming-to-txt pipeline with parameters: {}", paramsMap);

        /*
         * Steps:
         *  1) Read messages in from Cdap Salesforce
         *  2) Extract values only
         *  3) Write successful records to .txt file
         */

        pipeline
                .apply(
                        "readFromCdapSalesforceStreaming",
                        FormatInputTransform.readFromCdapSalesforceStreaming(
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
                        "writeToTxt",
                        TextIO.write()
                                .withWindowedWrites()
                                .withNumShards(1)
                                .to(options.getOutputTxtFilePathPrefix()));

        return pipeline.run();
    }
}
