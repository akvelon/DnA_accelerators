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

import com.akvelon.salesforce.options.CdapSalesforceSourceOptions;
import com.akvelon.salesforce.transforms.FormatInputTransform;
import com.akvelon.salesforce.utils.PluginConfigOptionsConverter;
import com.google.api.services.bigquery.model.TableRow;
import com.google.gson.Gson;
import io.cdap.cdap.api.data.schema.Schema;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import org.apache.beam.sdk.Pipeline;
import org.apache.beam.sdk.PipelineResult;
import org.apache.beam.sdk.coders.Coder;
import org.apache.beam.sdk.coders.KvCoder;
import org.apache.beam.sdk.coders.SerializableCoder;
import org.apache.beam.sdk.coders.StringUtf8Coder;
import org.apache.beam.sdk.io.gcp.bigquery.BigQueryIO;
import org.apache.beam.sdk.io.gcp.bigquery.InsertRetryPolicy;
import org.apache.beam.sdk.io.gcp.bigquery.TableRowJsonCoder;
import org.apache.beam.sdk.options.PipelineOptionsFactory;
import org.apache.beam.sdk.transforms.MapElements;
import org.apache.beam.sdk.transforms.MapValues;
import org.apache.beam.sdk.transforms.SimpleFunction;
import org.apache.beam.sdk.transforms.Values;
import org.apache.beam.sdk.values.TypeDescriptors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Batch Salesforce Pipeline.
 */
public class CdapSalesforceBatchToBigQuery {

    private static final Gson GSON = new Gson();

    /* Logger for class.*/
    private static final Logger LOG = LoggerFactory.getLogger(CdapSalesforceBatchToBigQuery.class);

    /**
     * Main entry point for pipeline execution.
     *
     * @param args Command line arguments to the pipeline.
     */
    public static void main(String[] args) {
        CdapSalesforceSourceOptions options =
                PipelineOptionsFactory.fromArgs(args)
                        .withValidation()
                        .as(CdapSalesforceSourceOptions.class);

        // Create the pipeline
        Pipeline pipeline = Pipeline.create(options);
        run(pipeline, options);
    }

    /**
     * Runs a pipeline that reads records from Salesforce via CDAP plugin.
     *
     * @param options arguments to the pipeline
     */
    public static PipelineResult run(Pipeline pipeline, CdapSalesforceSourceOptions options) {
        Map<String, Object> paramsMap =
                PluginConfigOptionsConverter.salesforceBatchSourceOptionsToParamsMap(options);
        LOG.info("Starting Cdap-Salesforce pipeline with parameters: {}", paramsMap);

        /*
         * Steps:
         *  1) Read messages from Cdap Salesforce
         *  2) Extract values only
         *  3) Write successful records out to BigQuery
         */

        pipeline
                .apply("readFromCdapSalesforce", FormatInputTransform.readFromCdapSalesforce(paramsMap))
                .setCoder(
                        KvCoder.of(
                                SerializableCoder.of(Schema.class), SerializableCoder.of(HashMap.class)))
                .apply(MapValues.into(TypeDescriptors.strings()).via(map -> GSON.toJson(map, Map.class)))
                .setCoder(KvCoder.of(SerializableCoder.of(Schema.class), StringUtf8Coder.of()))
                .apply(Values.create())
                .apply("FormatOutput", MapElements.via(new FormatOutput()))
                .apply(
                        "WriteToBigQuery",
                        BigQueryIO.writeTableRows()
                                .withoutValidation()
                                .withCreateDisposition(BigQueryIO.Write.CreateDisposition.CREATE_NEVER)
                                .withWriteDisposition(BigQueryIO.Write.WriteDisposition.WRITE_APPEND)
                                .withExtendedErrorInfo()
                                .withMethod(BigQueryIO.Write.Method.STORAGE_WRITE_API)
                                .withFailedInsertRetryPolicy(InsertRetryPolicy.retryTransientErrors())
                                .to(options.getOutputTableSpec()));

        return pipeline.run();
    }

    /** Formats the output. */
    static class FormatOutput extends SimpleFunction<String, TableRow> {

        @Override
        public TableRow apply(String input) {
            if (input != null) {
                TableRow row;
                // Parse the JSON into a {@link TableRow} object.
                try (InputStream inputStream =
                             new ByteArrayInputStream(input.getBytes(StandardCharsets.UTF_8))) {
                    row = TableRowJsonCoder.of().decode(inputStream, Coder.Context.OUTER);
                    return row;
                } catch (IOException e) {
                    throw new RuntimeException("Failed to serialize json to table row: " + input, e);
                }
            }
            return null;
        }
    }
}
