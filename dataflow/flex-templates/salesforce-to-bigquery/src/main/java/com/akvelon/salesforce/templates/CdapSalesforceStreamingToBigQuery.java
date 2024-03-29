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

import static com.akvelon.salesforce.utils.BigQueryConstants.DEADLETTER_SCHEMA;
import static com.akvelon.salesforce.utils.BigQueryConstants.DEFAULT_DEADLETTER_TABLE_SUFFIX;
import static com.akvelon.salesforce.utils.VaultUtils.getSalesforceCredentialsFromVault;

import com.akvelon.salesforce.options.SalesforceToBigQueryStreamingSourceOptions;
import com.akvelon.salesforce.transforms.BigQueryErrorTransform;
import com.akvelon.salesforce.transforms.FormatInputTransform;
import com.akvelon.salesforce.utils.FailsafeRecord;
import com.akvelon.salesforce.utils.FailsafeRecordCoder;
import com.akvelon.salesforce.utils.PluginConfigOptionsConverter;
import com.google.api.services.bigquery.model.TableRow;
import com.google.auto.value.AutoValue;
import io.cdap.plugin.salesforce.SalesforceConstants;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.apache.beam.sdk.Pipeline;
import org.apache.beam.sdk.coders.Coder;
import org.apache.beam.sdk.coders.CoderRegistry;
import org.apache.beam.sdk.coders.KvCoder;
import org.apache.beam.sdk.coders.NullableCoder;
import org.apache.beam.sdk.coders.StringUtf8Coder;
import org.apache.beam.sdk.io.gcp.bigquery.BigQueryIO;
import org.apache.beam.sdk.io.gcp.bigquery.BigQueryInsertError;
import org.apache.beam.sdk.io.gcp.bigquery.InsertRetryPolicy;
import org.apache.beam.sdk.io.gcp.bigquery.TableRowJsonCoder;
import org.apache.beam.sdk.io.gcp.bigquery.WriteResult;
import org.apache.beam.sdk.io.hadoop.WritableCoder;
import org.apache.beam.sdk.options.PipelineOptionsFactory;
import org.apache.beam.sdk.transforms.DoFn;
import org.apache.beam.sdk.transforms.Flatten;
import org.apache.beam.sdk.transforms.MapElements;
import org.apache.beam.sdk.transforms.PTransform;
import org.apache.beam.sdk.transforms.ParDo;
import org.apache.beam.sdk.transforms.Values;
import org.apache.beam.sdk.transforms.windowing.AfterProcessingTime;
import org.apache.beam.sdk.transforms.windowing.GlobalWindows;
import org.apache.beam.sdk.transforms.windowing.Repeatedly;
import org.apache.beam.sdk.transforms.windowing.Window;
import org.apache.beam.sdk.values.KV;
import org.apache.beam.sdk.values.PCollection;
import org.apache.beam.sdk.values.PCollectionList;
import org.apache.beam.sdk.values.PCollectionTuple;
import org.apache.beam.sdk.values.TupleTag;
import org.apache.beam.sdk.values.TupleTagList;
import org.apache.beam.vendor.guava.v26_0_jre.com.google.common.base.Throwables;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.hadoop.io.NullWritable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The {@link CdapSalesforceStreamingToBigQuery} pipeline is a streaming pipeline which ingests data in
 * JSON format from CDAP Salesforce, and outputs the resulting records to BigQuery table. Salesforce
 * parameters and output BigQuery table file path are specified by the user as template parameters. <br>
 *
 * <p><b>Example Usage</b>
 * <p>
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
 * <p>
 * By default this will run the pipeline locally with the DirectRunner. To change the runner, specify:
 * {@code
 * --runner=YOUR_SELECTED_RUNNER
 * }
 */
public class CdapSalesforceStreamingToBigQuery {

    /* Logger for class.*/
    private static final Logger LOG = LoggerFactory.getLogger(CdapSalesforceStreamingToBigQuery.class);

    /**
     * The tag for the main output of the json transformation.
     */
    static final TupleTag<TableRow> TRANSFORM_OUT = new TupleTag<TableRow>() {
    };

    /**
     * The tag for the dead-letter output of the json to table row transform.
     */
    static final TupleTag<FailsafeRecord<String, String>> TRANSFORM_DEADLETTER_OUT =
            new TupleTag<FailsafeRecord<String, String>>() {
            };

    /**
     * String/String Coder for {@link FailsafeRecord}.
     */
    private static final FailsafeRecordCoder<String, String> FAILSAFE_RECORD_CODER =
            FailsafeRecordCoder.of(
                    NullableCoder.of(StringUtf8Coder.of()), NullableCoder.of(StringUtf8Coder.of()));
    private static final String SOBJECT_PARAMETER_PREFIX = "\"sobject\":";

    /**
     * Main entry point for pipeline execution.
     *
     * @param args Command line arguments to the pipeline.
     */
    public static void main(String[] args) {
        SalesforceToBigQueryStreamingSourceOptions options =
                PipelineOptionsFactory.fromArgs(args)
                        .withValidation()
                        .as(SalesforceToBigQueryStreamingSourceOptions.class);

        // Create the pipeline
        Pipeline pipeline = Pipeline.create(options);

        // Register the coder for pipeline
        FailsafeRecordCoder<String, String> coder =
                FailsafeRecordCoder.of(NullableCoder.of(StringUtf8Coder.of()),
                        NullableCoder.of(StringUtf8Coder.of()));

        CoderRegistry coderRegistry = pipeline.getCoderRegistry();
        coderRegistry.registerCoderForType(coder.getEncodedTypeDescriptor(), coder);

        run(pipeline, options);
    }

    /**
     * Runs a pipeline that reads records from CDAP Salesforce and writes them to BigQuery.
     *
     * @param options arguments to the pipeline
     */
    public static void run(
            Pipeline pipeline, SalesforceToBigQueryStreamingSourceOptions options) {
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
         *  2) Transform the Json Messages into TableRows
         *  3) Write the successful records out to BigQuery
         *  4) Write failed records out to BigQuery
         *  5) Insert records that failed BigQuery inserts into a deadletter table.
         */

        /*
         * Step #1: Read messages in from Cdap Salesforce
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

        /*
         * Step #2: Transform the Json Messages into TableRows
         */
        PCollectionTuple tableRows = jsonMessages.apply("ConvertMessageToTableRow", new JsonToTableRow());

        /*
         * Step #3: Write the successful records out to BigQuery
         */
        WriteResult writeResult = tableRows
                .get(TRANSFORM_OUT)
                .apply(
                        "WriteSuccessfulRecords",
                        BigQueryIO.writeTableRows()
                                .withoutValidation()
                                .withCreateDisposition(BigQueryIO.Write.CreateDisposition.CREATE_NEVER)
                                .withWriteDisposition(BigQueryIO.Write.WriteDisposition.WRITE_APPEND)
                                .withExtendedErrorInfo()
                                .withMethod(BigQueryIO.Write.Method.STREAMING_INSERTS)
                                .ignoreUnknownValues()
                                .withFailedInsertRetryPolicy(InsertRetryPolicy.retryTransientErrors())
                                .to(options.getOutputTableSpec()));

        /*
         * Step 3 Contd.
         * Elements that failed inserts into BigQuery are extracted and converted to FailsafeRecord
         */
        PCollection<FailsafeRecord<String, String>> failedInserts =
                writeResult
                        .getFailedInsertsWithErr()
                        .apply(
                                "WrapInsertionErrors",
                                MapElements.into(FAILSAFE_RECORD_CODER.getEncodedTypeDescriptor())
                                        .via(CdapSalesforceStreamingToBigQuery::wrapBigQueryInsertError))
                        .setCoder(FAILSAFE_RECORD_CODER);

        /*
         * Step #4: Write failed records out to BigQuery
         */
        PCollectionList.of(tableRows.get(TRANSFORM_DEADLETTER_OUT))
                .apply("Flatten", Flatten.pCollections())
                .apply(
                        "WriteTransformationFailedRecords",
                        BigQueryErrorTransform.WriteStringMessageErrors.newBuilder()
                                .setErrorRecordsTable(
                                        ObjectUtils.firstNonNull(
                                                options.getOutputDeadletterTable(),
                                                options.getOutputTableSpec() + DEFAULT_DEADLETTER_TABLE_SUFFIX))
                                .setErrorRecordsTableSchema(DEADLETTER_SCHEMA)
                                .build());

        /*
         * Step #5: Insert records that failed BigQuery inserts into a deadletter table.
         */
        failedInserts.apply(
                "WriteInsertionFailedRecords",
                BigQueryErrorTransform.WriteStringMessageErrors.newBuilder()
                        .setErrorRecordsTable(
                                ObjectUtils.firstNonNull(
                                        options.getOutputDeadletterTable(),
                                        options.getOutputTableSpec() + DEFAULT_DEADLETTER_TABLE_SUFFIX))
                        .setErrorRecordsTableSchema(DEADLETTER_SCHEMA)
                        .build());

        pipeline.run();
    }

    /**
     * Method to wrap a {@link BigQueryInsertError} into a {@link FailsafeRecord}.
     *
     * @param insertError BigQueryInsert error.
     * @return {@link FailsafeRecord} object.
     */
    protected static FailsafeRecord<String, String> wrapBigQueryInsertError(
            BigQueryInsertError insertError) {

        FailsafeRecord<String, String> failsafeRecord;
        try {

            failsafeRecord =
                    FailsafeRecord.of(
                            insertError.getRow().toPrettyString(), insertError.getRow().toPrettyString());
            failsafeRecord.setErrorMessage(insertError.getError().toPrettyString());

        } catch (IOException e) {
            LOG.error("Failed to wrap BigQuery insert error.");
            throw new RuntimeException(e);
        }
        return failsafeRecord;
    }

    /**
     * Extracts SObject json from the original {@param json} with additional parameters.
     *
     * @return extracted SObject json, or original json if it's impossible to extract.
     */
    public static String extractSObjectFromJson(String json) {
        if (json.contains(SOBJECT_PARAMETER_PREFIX)) {
            String sobject = json.split(SOBJECT_PARAMETER_PREFIX)[1];
            return sobject.substring(0, sobject.length() - 1);
        } else {
            return json;
        }
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

    static class JsonToTableRow
            extends PTransform<PCollection<String>, PCollectionTuple> {

        @Override
        public PCollectionTuple expand(PCollection<String> input) {

            return input
                // Map the incoming messages into FailsafeRecords so we can recover from failures
                // across multiple transforms.
                .apply("MapToRecord", ParDo.of(new MessageToFailsafeRecordFn()))
                .apply(
                        "JsonToTableRow",
                        FailsafeJsonToTableRow.<String>newBuilder()
                                .setSuccessTag(TRANSFORM_OUT)
                                .setFailureTag(TRANSFORM_DEADLETTER_OUT)
                                .build());
        }

        @AutoValue
        public abstract static class FailsafeJsonToTableRow<T>
                extends PTransform<PCollection<FailsafeRecord<T, String>>, PCollectionTuple> {

            public static <T> Builder<T> newBuilder() {
                return new AutoValue_CdapSalesforceStreamingToBigQuery_JsonToTableRow_FailsafeJsonToTableRow.Builder<>();
            }

            public abstract TupleTag<TableRow> successTag();

            public abstract TupleTag<FailsafeRecord<T, String>> failureTag();

            @Override
            public PCollectionTuple expand(PCollection<FailsafeRecord<T, String>> failsafeRecords) {
                return failsafeRecords.apply(
                        "JsonToTableRow",
                        ParDo.of(
                            new DoFn<FailsafeRecord<T, String>, TableRow>() {
                                @ProcessElement
                                public void processElement(ProcessContext context) {
                                    FailsafeRecord<T, String> element = context.element();
                                    String json = element.getCurrentPayload();
                                    try {
                                        String sobjectJson = extractSObjectFromJson(json);
                                        TableRow row = convertJsonToTableRow(sobjectJson);
                                        context.output(row);
                                    } catch (Exception e) {
                                        context.output(
                                                failureTag(),
                                                FailsafeRecord.of(element)
                                                        .setErrorMessage(e.getMessage())
                                                        .setStacktrace(Throwables.getStackTraceAsString(e)));
                                    }
                                }
                            })
                        .withOutputTags(successTag(), TupleTagList.of(failureTag())));
            }

            /**
             * Builder for {@link FailsafeJsonToTableRow}.
             */
            @AutoValue.Builder
            public abstract static class Builder<T> {

                public abstract Builder<T> setSuccessTag(TupleTag<TableRow> successTag);

                public abstract Builder<T> setFailureTag(TupleTag<FailsafeRecord<T, String>> failureTag);

                public abstract FailsafeJsonToTableRow<T> build();
            }
        }
    }

    /**
     * The {@link MessageToFailsafeRecordFn} wraps Json Message with the {@link FailsafeRecord}
     * class so errors can be recovered from and the original message can be output to the error records
     * table.
     */
    static class MessageToFailsafeRecordFn
            extends DoFn<String, FailsafeRecord<String, String>> {

        @ProcessElement
        public void processElement(ProcessContext context) {
            String message = context.element();
            context.output(FailsafeRecord.of(message, message));
        }
    }

}
