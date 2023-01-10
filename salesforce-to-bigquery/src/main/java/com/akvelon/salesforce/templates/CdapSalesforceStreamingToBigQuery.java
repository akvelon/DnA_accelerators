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
package com.akvelon.salesforce.templates;

import static com.akvelon.salesforce.utils.VaultUtils.getSalesforceCredentialsFromVault;

import com.akvelon.salesforce.options.CdapSalesforceStreamingSourceOptions;
import com.akvelon.salesforce.transforms.FormatInputTransform;
import com.akvelon.salesforce.utils.PluginConfigOptionsConverter;
import com.google.api.services.bigquery.model.TableRow;
import io.cdap.plugin.salesforce.SalesforceConstants;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.apache.beam.sdk.Pipeline;
import org.apache.beam.sdk.PipelineResult;
import org.apache.beam.sdk.coders.Coder;
import org.apache.beam.sdk.coders.KvCoder;
import org.apache.beam.sdk.coders.NullableCoder;
import org.apache.beam.sdk.coders.StringUtf8Coder;
import org.apache.beam.sdk.io.gcp.bigquery.BigQueryIO;
import org.apache.beam.sdk.io.gcp.bigquery.InsertRetryPolicy;
import org.apache.beam.sdk.io.gcp.bigquery.TableRowJsonCoder;
import org.apache.beam.sdk.io.hadoop.WritableCoder;
import org.apache.beam.sdk.options.PipelineOptionsFactory;
import org.apache.beam.sdk.transforms.DoFn;
import org.apache.beam.sdk.transforms.ParDo;
import org.apache.beam.sdk.transforms.Values;
import org.apache.beam.sdk.transforms.windowing.AfterProcessingTime;
import org.apache.beam.sdk.transforms.windowing.GlobalWindows;
import org.apache.beam.sdk.transforms.windowing.Repeatedly;
import org.apache.beam.sdk.transforms.windowing.Window;
import org.apache.beam.sdk.values.KV;
import org.apache.beam.sdk.values.PCollection;
import org.apache.hadoop.io.NullWritable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The {@link CdapSalesforceStreamingToBigQuery} pipeline is a streaming pipeline which ingests data in
 * JSON format from CDAP Salesforce, and outputs the resulting records to BigQuery table. Salesforce
 * parameters and output BigQuery table file path are specified by the user as template parameters. <br>
 *
 * <p><b>Example Usage</b>
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
 * --outputTableSpec=your-big-query-table \
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
 */
public class CdapSalesforceStreamingToBigQuery {

    /* Logger for class.*/
    private static final Logger LOG = LoggerFactory.getLogger(CdapSalesforceStreamingToBigQuery.class);

    /**
     * Main entry point for pipeline execution.
     *
     * @param args Command line arguments to the pipeline.
     */
    public static void main(String[] args) {
        CdapSalesforceStreamingSourceOptions options =
                PipelineOptionsFactory.fromArgs(args)
                        .withValidation()
                        .as(CdapSalesforceStreamingSourceOptions.class);

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
            Pipeline pipeline, CdapSalesforceStreamingSourceOptions options) {
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
         *  3) Convert message to TableRows
         *  4) Write successful records to BigQuery
         */

        PCollection<String> jsonMessages = pipeline
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
                .apply(Values.create());

        PCollection<TableRow> tableRows = jsonMessages.apply("ConvertMessageToTableRow", ParDo.of(
                new DoFn<String, TableRow>() {
                    @ProcessElement
                    public void processElement(ProcessContext context) {
                        String json = context.element();
                        if (json != null) {
                            try {
                                TableRow row = convertJsonToTableRow(json);
                                context.output(row);
                            } catch (Exception e) {
                                LOG.error("Can not convert JSON to TableRow");
                            }
                        }
                    }
                }));

        tableRows
            .apply(
                "WriteSuccessfulRecords",
                BigQueryIO.writeTableRows()
                    .withoutValidation()
                    .withCreateDisposition(BigQueryIO.Write.CreateDisposition.CREATE_NEVER)
                    .withWriteDisposition(BigQueryIO.Write.WriteDisposition.WRITE_APPEND)
                    .withExtendedErrorInfo()
                    .withMethod(BigQueryIO.Write.Method.STREAMING_INSERTS)
                    .withFailedInsertRetryPolicy(InsertRetryPolicy.retryTransientErrors())
                    .to(options.getOutputTableSpec()));

        return pipeline.run();
    }

    /**
     * Converts a JSON string to a {@link TableRow} object. If the data fails to convert, a {@link
     * RuntimeException} will be thrown.
     *
     * @param json The JSON string to parse.
     * @return The parsed {@link TableRow} object.
     */
    public static TableRow convertJsonToTableRow(String json) {
        TableRow row;
        // Parse the JSON into a {@link TableRow} object.
        try (InputStream inputStream =
                     new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8))) {
            row = TableRowJsonCoder.of().decode(inputStream, Coder.Context.OUTER);

        } catch (IOException e) {
            throw new RuntimeException("Failed to serialize json to table row: " + json, e);
        }

        return row;
    }
}
