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
import com.akvelon.salesforce.utils.ErrorConverters;
import com.akvelon.salesforce.utils.FailsafeElement;
import com.akvelon.salesforce.utils.FailsafeElementCoder;
import com.akvelon.salesforce.utils.PluginConfigOptionsConverter;
import com.google.api.services.bigquery.model.TableRow;
import com.google.gson.Gson;
import io.cdap.plugin.salesforce.SalesforceConstants;
import java.util.Map;
import org.apache.beam.sdk.Pipeline;
import org.apache.beam.sdk.coders.Coder;
import org.apache.beam.sdk.coders.CoderRegistry;
import org.apache.beam.sdk.coders.KvCoder;
import org.apache.beam.sdk.coders.NullableCoder;
import org.apache.beam.sdk.coders.RowCoder;
import org.apache.beam.sdk.coders.StringUtf8Coder;
import org.apache.beam.sdk.extensions.python.PythonExternalTransform;
import org.apache.beam.sdk.io.gcp.bigquery.BigQueryIO;
import org.apache.beam.sdk.io.gcp.bigquery.InsertRetryPolicy;
import org.apache.beam.sdk.io.gcp.bigquery.WriteResult;
import org.apache.beam.sdk.options.PipelineOptionsFactory;
import org.apache.beam.sdk.schemas.Schema;
import org.apache.beam.sdk.transforms.Flatten;
import org.apache.beam.sdk.transforms.MapElements;
import org.apache.beam.sdk.transforms.SimpleFunction;
import org.apache.beam.sdk.transforms.windowing.AfterProcessingTime;
import org.apache.beam.sdk.transforms.windowing.GlobalWindows;
import org.apache.beam.sdk.transforms.windowing.Repeatedly;
import org.apache.beam.sdk.transforms.windowing.Window;
import org.apache.beam.sdk.values.KV;
import org.apache.beam.sdk.values.PCollection;
import org.apache.beam.sdk.values.PCollectionList;
import org.apache.beam.sdk.values.PCollectionTuple;
import org.apache.beam.sdk.values.Row;
import org.apache.beam.sdk.values.TupleTag;
import org.apache.beam.sdk.values.TypeDescriptor;
import org.apache.commons.lang3.ObjectUtils;
import org.joda.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The {@link CdapRunInference} pipeline is a streaming pipeline which ingests data in
 * JSON format from CDAP Salesforce, runs Python RunInference and outputs the resulting records to BigQuery table.
 * Salesforce parameters and output BigQuery table file path are specified by the user as template parameters. <br>
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
public class CdapRunInference {

    /* Logger for class.*/
    private static final Logger LOG = LoggerFactory.getLogger(CdapRunInference.class);
    private static final Gson GSON = new Gson();
    private static final String SALESFORCE_SOBJECT = "sobject";
    private static final String SALESFORCE_SOBJECT_ID = "Id";

    /**
     * The tag for the main output of the json transformation.
     */
    static final TupleTag<TableRow> TRANSFORM_OUT = new TupleTag<TableRow>() {
    };

    /**
     * The tag for the dead-letter output of the json to table row transform.
     */
    static final TupleTag<FailsafeElement<String, String>> TRANSFORM_DEADLETTER_OUT =
            new TupleTag<FailsafeElement<String, String>>() {
            };

    /**
     * String/String Coder for FailsafeElement.
     */
    private static final FailsafeElementCoder<String, String> FAILSAFE_ELEMENT_CODER =
            FailsafeElementCoder.of(
                    NullableCoder.of(StringUtf8Coder.of()), NullableCoder.of(StringUtf8Coder.of()));

    public static final String DEADLETTER_SCHEMA =
            "{\n"
                    + "  \"fields\": [\n"
                    + "    {\n"
                    + "      \"name\": \"timestamp\",\n"
                    + "      \"type\": \"TIMESTAMP\",\n"
                    + "      \"mode\": \"REQUIRED\"\n"
                    + "    },\n"
                    + "    {\n"
                    + "      \"name\": \"payloadString\",\n"
                    + "      \"type\": \"STRING\",\n"
                    + "      \"mode\": \"REQUIRED\"\n"
                    + "    },\n"
                    + "    {\n"
                    + "      \"name\": \"payloadBytes\",\n"
                    + "      \"type\": \"BYTES\",\n"
                    + "      \"mode\": \"REQUIRED\"\n"
                    + "    },\n"
                    + "    {\n"
                    + "      \"name\": \"attributes\",\n"
                    + "      \"type\": \"RECORD\",\n"
                    + "      \"mode\": \"REPEATED\",\n"
                    + "      \"fields\": [\n"
                    + "        {\n"
                    + "          \"name\": \"key\",\n"
                    + "          \"type\": \"STRING\",\n"
                    + "          \"mode\": \"NULLABLE\"\n"
                    + "        },\n"
                    + "        {\n"
                    + "          \"name\": \"value\",\n"
                    + "          \"type\": \"STRING\",\n"
                    + "          \"mode\": \"NULLABLE\"\n"
                    + "        }\n"
                    + "      ]\n"
                    + "    },\n"
                    + "    {\n"
                    + "      \"name\": \"errorMessage\",\n"
                    + "      \"type\": \"STRING\",\n"
                    + "      \"mode\": \"NULLABLE\"\n"
                    + "    },\n"
                    + "    {\n"
                    + "      \"name\": \"stacktrace\",\n"
                    + "      \"type\": \"STRING\",\n"
                    + "      \"mode\": \"NULLABLE\"\n"
                    + "    }\n"
                    + "  ]\n"
                    + "}";

    /**
     * The default suffix for error tables if dead letter table is not specified.
     */
    private static final String DEFAULT_DEADLETTER_TABLE_SUFFIX = "_error_records";
    public static final String DEFAULT_PYTHON_SDK_OVERRIDES = "apache/beam_python3.9_sdk:2.45.0," +
            "gcr.io/dataflow-template-demo-374507/anomaly-detection-expansion-service:latest";
    public static final String ANOMALY_DETECTION_TRANFORM = "anomaly_detection.AnomalyDetection";

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

            options.setSdkHarnessContainerImageOverrides(DEFAULT_PYTHON_SDK_OVERRIDES);
        // Create the pipeline
        Pipeline pipeline = Pipeline.create(options);

        // Register the coder for pipeline
        FailsafeElementCoder<String, String> coder =
                FailsafeElementCoder.of(NullableCoder.of(StringUtf8Coder.of()),
                        NullableCoder.of(StringUtf8Coder.of()));

        CoderRegistry coderRegistry = pipeline.getCoderRegistry();
        coderRegistry.registerCoderForType(coder.getEncodedTypeDescriptor(), coder);

        run(pipeline, options);
    }

    /**
     * Runs a pipeline which reads records from CDAP Salesforce and writes them to .txt file.
     *
     * @param options arguments to the pipeline
     */
    public static void run(
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
         *  2) Transform messages from Cdap Salesforce to Rows
         *  3) Run Python RunInference Anomaly Detection transform
         *  4) Transform the RunInference result into TableRows
         *  5) Write the successful records out to BigQuery
         *  6) Write failed records out to BigQuery
         *  7) Insert records that failed BigQuery inserts into a deadletter table.
         */

        /*
         * Step #1: Read messages in from Cdap Salesforce
         */
        PCollection<String> jsonMessages;
        Window<String> window = Window.<String>into(new GlobalWindows())
                    .triggering(Repeatedly.forever(AfterProcessingTime.pastFirstElementInPane()
                            .plusDelayOf(Duration.ZERO)))
                    .discardingFiredPanes()
                    .withAllowedLateness(Duration.ZERO);
        jsonMessages = pipeline
                .apply(
                        "readFromSalesforceSparkReceiver",
                        FormatInputTransform.readFromSalesforceSparkReceiver(
                                paramsMap, options.getPullFrequencySec(), options.getStartOffset()))
                .setCoder(StringUtf8Coder.of())
                .apply("window", window);

        /*
         * Step #2: Transform messages from Cdap Salesforce to Rows
         */
        Schema rowSchema =  Schema.of(
                Schema.Field.of("Id", Schema.FieldType.STRING),
                Schema.Field.of("AccountType", Schema.FieldType.STRING),
                Schema.Field.of("Amount", Schema.FieldType.INT64),
                Schema.Field.of("BillingCountry", Schema.FieldType.STRING),
                Schema.Field.of("IsClosed", Schema.FieldType.BOOLEAN),
                Schema.Field.of("ForecastCategory", Schema.FieldType.STRING),
                Schema.Field.of("Industry", Schema.FieldType.STRING),
                Schema.Field.of("OpportunitySource", Schema.FieldType.STRING),
                Schema.Field.of("OpportunityType", Schema.FieldType.STRING),
                Schema.Field.of("OwnerRole", Schema.FieldType.STRING),
                Schema.Field.of("ProductFamily", Schema.FieldType.STRING),
                Schema.Field.of("Segment", Schema.FieldType.STRING),
                Schema.Field.of("Stage", Schema.FieldType.STRING),
                Schema.Field.of("IsWon", Schema.FieldType.BOOLEAN));
        PCollection<Row> input = jsonMessages
                .apply(
                        MapElements.into(new TypeDescriptor<Row>() {})
                                .via(
                                        json -> {
                                            String id, accountType = "no", billingCountry = "no", forecastCategory, industry = "no", opportunitySource = "no",
                                            opportunityType, ownerRole = "no", productFamily = "no", segment = "no", stage;
                                            boolean isClosed, isWon;
                                            long amount;
                                            Map<Object, Object> eventMap = GSON.fromJson(json, Map.class);
                                            Map<Object, Object> map = (Map<Object, Object>) eventMap.get(SALESFORCE_SOBJECT);
                                            id = (String) map.get(SALESFORCE_SOBJECT_ID);
                                            opportunityType = (String) map.get("Type");
                                            stage = (String) map.get("StageName");
                                            forecastCategory = (String) map.get("ForecastCategory");
                                            amount = (long) Double.parseDouble((String) map.get("Amount"));
                                            isWon = Boolean.parseBoolean((String) map.get("IsWon"));
                                            isClosed = Boolean.parseBoolean((String) map.get("IsClosed"));
                                            return Row.withSchema(rowSchema)
                                                    .attachValues(id, accountType, amount, billingCountry, isClosed, forecastCategory,
                                                            industry, opportunitySource, opportunityType, ownerRole, productFamily,
                                                            segment, stage, isWon);
                                        }
                                )
                ).setCoder(RowCoder.of(rowSchema));

        /*
         * Step #3: Run Python RunInference Anomaly Detection transform
         */
        Schema outSchema =
                Schema.of(
                        Schema.Field.of("example", Schema.FieldType.DOUBLE),
                        Schema.Field.of("inference", Schema.FieldType.INT64));
        Coder<KV<String, Row>> outputCoder =
                    KvCoder.of(StringUtf8Coder.of(), RowCoder.of(outSchema));

        PCollection<String> outputLines =
                    input.apply(
                    PythonExternalTransform.<PCollection<?>, PCollection<KV<String, Row>>>from(
                                    ANOMALY_DETECTION_TRANFORM, options.getExpansionService())
                            .withOutputCoder(outputCoder))
                    .apply("FormatOutput", MapElements.via(new FormatOutput()));

        /*
         * Step #4: Transform the RunInference result into TableRows
         */
        PCollectionTuple tableRows = outputLines.apply("ConvertMessageToTableRow", new CdapSalesforceStreamingToBigQuery.JsonToTableRow());

        /*
         * Step #5: Write the successful records out to BigQuery
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
                                .withFailedInsertRetryPolicy(InsertRetryPolicy.retryTransientErrors())
                                .to(options.getOutputTableSpec()));

        /*
         * Step 5 Contd.
         * Elements that failed inserts into BigQuery are extracted and converted to FailsafeElement
         */
        PCollection<FailsafeElement<String, String>> failedInserts =
                writeResult
                        .getFailedInsertsWithErr()
                        .apply(
                                "WrapInsertionErrors",
                                MapElements.into(FAILSAFE_ELEMENT_CODER.getEncodedTypeDescriptor())
                                        .via(CdapSalesforceStreamingToBigQuery::wrapBigQueryInsertError))
                        .setCoder(FAILSAFE_ELEMENT_CODER);

        /*
         * Step #6: Write failed records out to BigQuery
         */
        PCollectionList.of(tableRows.get(TRANSFORM_DEADLETTER_OUT))
                .apply("Flatten", Flatten.pCollections())
                .apply(
                        "WriteTransformationFailedRecords",
                        ErrorConverters.WriteSalesforceMessageErrors.newBuilder()
                                .setErrorRecordsTable(
                                        ObjectUtils.firstNonNull(
                                                options.getOutputDeadletterTable(),
                                                options.getOutputTableSpec() + DEFAULT_DEADLETTER_TABLE_SUFFIX))
                                .setErrorRecordsTableSchema(DEADLETTER_SCHEMA)
                                .build());

        /*
         * Step #7: Insert records that failed BigQuery inserts into a deadletter table.
         */
        failedInserts.apply(
                "WriteInsertionFailedRecords",
                ErrorConverters.WriteStringMessageErrors.newBuilder()
                        .setErrorRecordsTable(
                                ObjectUtils.firstNonNull(
                                        options.getOutputDeadletterTable(),
                                        options.getOutputTableSpec() + DEFAULT_DEADLETTER_TABLE_SUFFIX))
                        .setErrorRecordsTableSchema(DEADLETTER_SCHEMA)
                        .build());

        pipeline.run();
    }

    /** Formats the output. */
    static class FormatOutput extends SimpleFunction<KV<String, Row>, String> {

        public static final String INFERENCE = "inference";
        private static final Gson GSON = new Gson();

        @Override
        public String apply(KV<String, Row> input) {
            if (input != null && input.getValue() != null) {
                LOG.info(input.getValue().toString());
                Integer cluster = input.getValue().getValue(INFERENCE);
                RunInferenceResult result = new RunInferenceResult(input.getKey(), cluster);
                return GSON.toJson(result);
            }
            return "";
        }
    }

    private static final class RunInferenceResult {

        private String opportunityId;
        private Integer anomalyCluster;

        public RunInferenceResult(String opportunityId, Integer anomalyCluster) {
            this.opportunityId = opportunityId;
            this.anomalyCluster = anomalyCluster;
        }
    }
}
