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
import com.akvelon.salesforce.utils.FailsafeElementCoder;
import com.akvelon.salesforce.utils.PluginConfigOptionsConverter;
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
import org.apache.beam.sdk.io.TextIO;
import org.apache.beam.sdk.options.PipelineOptionsFactory;
import org.apache.beam.sdk.schemas.Schema;
import org.apache.beam.sdk.transforms.Create;
import org.apache.beam.sdk.transforms.MapElements;
import org.apache.beam.sdk.transforms.SimpleFunction;
import org.apache.beam.sdk.transforms.windowing.AfterProcessingTime;
import org.apache.beam.sdk.transforms.windowing.FixedWindows;
import org.apache.beam.sdk.transforms.windowing.GlobalWindows;
import org.apache.beam.sdk.transforms.windowing.Repeatedly;
import org.apache.beam.sdk.transforms.windowing.Window;
import org.apache.beam.sdk.values.KV;
import org.apache.beam.sdk.values.PCollection;
import org.apache.beam.sdk.values.Row;
import org.apache.beam.sdk.values.TypeDescriptor;
import org.joda.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The {@link CdapRunInference} pipeline is a streaming pipeline which ingests data in
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
public class CdapRunInference {

    /* Logger for class.*/
    private static final Logger LOG = LoggerFactory.getLogger(CdapRunInference.class);
    private static final Gson GSON = new Gson();
    private static final String SALESFORCE_SOBJECT = "sobject";
    private static final String SALESFORCE_SOBJECT_ID = "Id";
    private static final String MODEL_URI_PARAM = "model_uri";

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
//                PluginConfigOptionsConverter.hubspotOptionsToParamsMap(options);
                PluginConfigOptionsConverter.salesforceStreamingSourceOptionsToParamsMap(options);
        LOG.info("Starting Cdap-Salesforce-streaming-to-txt pipeline with parameters: {}", paramsMap);

        /*
         * Steps:
         *  1) Read messages in from Cdap Salesforce
         */

        /*
         * Step #1: Read messages in from Cdap Salesforce
         */
        PCollection<String> jsonMessages;
        boolean isSimple = options.getOutputDeadletterTable().contains("simple");
        boolean noOutput = options.getOutputDeadletterTable().contains("without");
        if (!isSimple) {
            Window<String> window;
            if (options.getOutputDeadletterTable().contains("global")) {
                window = Window.<String>into(new GlobalWindows())
                        .triggering(Repeatedly.forever(AfterProcessingTime.pastFirstElementInPane()
                                .plusDelayOf(Duration.ZERO)))
                        .discardingFiredPanes()
                        .withAllowedLateness(Duration.ZERO);
            } else {
                window = Window.<String>into(FixedWindows.of(Duration.standardSeconds(60)))
                        .triggering(Repeatedly.forever(AfterProcessingTime.pastFirstElementInPane()
                                .plusDelayOf(Duration.ZERO)))
                        .discardingFiredPanes()
                        .withAllowedLateness(Duration.ZERO);
            }
            jsonMessages = pipeline
                    .apply(
                            "readFromSalesforceSparkReceiver",
                            FormatInputTransform.readFromSalesforceSparkReceiver(
                                    paramsMap, options.getPullFrequencySec(), options.getStartOffset()))
                    .setCoder(StringUtf8Coder.of())
                    .apply("window", window);

        } else {
            jsonMessages = pipeline.apply(Create.of("{\"Id\":\"0068d00000Ch2LAAAZ\",\"IsDeleted\":\"false\",\"AccountId\":\"0018d00000Rv6YhAAJ\",\"RecordTypeId\":\"0128d000001OmbhAAC\",\"IsPrivate\":\"false\",\"Name\":\"Opportunity for McBride299\",\"Description\":\"\",\"StageName\":\"Value Proposition\",\"Amount\":\"567000.0\",\"Probability\":\"50.0\",\"ExpectedRevenue\":\"283500.0\",\"TotalOpportunityQuantity\":\"2009.0\",\"CloseDate\":\"2023-03-03\",\"Type\":\"New Business\",\"NextStep\":\"\",\"LeadSource\":\"Employee Referral\",\"IsClosed\":\"false\",\"IsWon\":\"false\",\"ForecastCategory\":\"Pipeline\",\"ForecastCategoryName\":\"Pipeline\",\"CampaignId\":\"\",\"HasOpportunityLineItem\":\"true\",\"Pricebook2Id\":\"01s8d0000078p2HAAQ\",\"OwnerId\":\"0058d000005lFP4AAM\",\"CreatedDate\":\"2022-08-15T00:00:00.000Z\",\"CreatedById\":\"0058d000005lFNaAAM\",\"LastModifiedDate\":\"2023-02-03T05:23:17.000Z\",\"LastModifiedById\":\"0058d000005lFNaAAM\",\"SystemModstamp\":\"2023-02-03T05:23:34.000Z\",\"LastActivityDate\":\"2023-01-19\",\"PushCount\":\"0\",\"LastStageChangeDate\":\"\",\"FiscalQuarter\":\"1\",\"FiscalYear\":\"2023\",\"Fiscal\":\"2023 1\",\"ContactId\":\"\",\"LastViewedDate\":\"\",\"LastReferencedDate\":\"\",\"HasOpenActivity\":\"true\",\"HasOverdueTask\":\"false\",\"LastAmountChangedHistoryId\":\"\",\"LastCloseDateChangedHistoryId\":\"\",\"DeliveryInstallationStatus__c\":\"\",\"TrackingNumber__c\":\"\",\"OrderNumber__c\":\"\",\"CurrentGenerators__c\":\"\",\"MainCompetitors__c\":\"\",\"Opportunity_Source__c\":\"AE\"}", "{\"Id\":\"0068d00000Ch2LFAAZ\",\"IsDeleted\":\"false\",\"AccountId\":\"0018d00000Rv6YaAAJ\",\"RecordTypeId\":\"0128d000001OmbhAAC\",\"IsPrivate\":\"false\",\"Name\":\"Opportunity for Collins378\",\"Description\":\"\",\"StageName\":\"Closed Won\",\"Amount\":\"446850.0\",\"Probability\":\"100.0\",\"ExpectedRevenue\":\"446850.0\",\"TotalOpportunityQuantity\":\"1961.0\",\"CloseDate\":\"2022-04-08\",\"Type\":\"New Business\",\"NextStep\":\"\",\"LeadSource\":\"Employee Referral\",\"IsClosed\":\"true\",\"IsWon\":\"true\",\"ForecastCategory\":\"Closed\",\"ForecastCategoryName\":\"Closed\",\"CampaignId\":\"\",\"HasOpportunityLineItem\":\"true\",\"Pricebook2Id\":\"01s8d0000078p2HAAQ\",\"OwnerId\":\"0058d000005lFOwAAM\",\"CreatedDate\":\"2022-03-07T00:00:00.000Z\",\"CreatedById\":\"0058d000005lFNaAAM\",\"LastModifiedDate\":\"2023-02-03T05:23:23.000Z\",\"LastModifiedById\":\"0058d000005lFNaAAM\",\"SystemModstamp\":\"2023-02-03T05:23:23.000Z\",\"LastActivityDate\":\"\",\"PushCount\":\"0\",\"LastStageChangeDate\":\"\",\"FiscalQuarter\":\"2\",\"FiscalYear\":\"2022\",\"Fiscal\":\"2022 2\",\"ContactId\":\"\",\"LastViewedDate\":\"\",\"LastReferencedDate\":\"\",\"HasOpenActivity\":\"false\",\"HasOverdueTask\":\"false\",\"LastAmountChangedHistoryId\":\"0088d00000Z2zt7AAB\",\"LastCloseDateChangedHistoryId\":\"\",\"DeliveryInstallationStatus__c\":\"\",\"TrackingNumber__c\":\"\",\"OrderNumber__c\":\"\",\"CurrentGenerators__c\":\"\",\"MainCompetitors__c\":\"\",\"Opportunity_Source__c\":\"AE\"}"));
        }

//        PCollection<KV<String, Iterable<Double>>> input = jsonMessages
//                .apply(
//                        MapElements.into(new TypeDescriptor<KV<String, Iterable<Double>>>() {
//                                })
//                                .via(
//                                        json -> {
//                                            String id;
//                                            double amount;
//                                            LOG.info("Start processing EVENT");
//
//                                            if (!isSimple) {
//                                                Map<Object, Object> eventMap = GSON.fromJson(json, Map.class);
//                                                Map<Object, Object> map = (Map<Object, Object>) eventMap.get(SALESFORCE_SOBJECT);
//                                                id = (String) map.get(SALESFORCE_SOBJECT_ID);
////                                                id = eventMap.get("vid").toString();
//                                                amount = 123d;
//                                            } else {
//                                                Map<Object, Object> map = GSON.fromJson(json, Map.class);
//                                                id = (String) map.get("Id");
//                                                amount = Double.parseDouble((String) map.get("Amount"));
//                                            }
//                                            LOG.info("PROCESSING RECORD WITH ID {}", id);
//                                            List<Double> list = new ArrayList<>();
//                                            list.add(amount);
//                                            return KV.of(id, list);
//                                        }))
//                .setCoder(KvCoder.of(StringUtf8Coder.of(), IterableCoder.of(DoubleCoder.of())));

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
                                            String id = "", accountType = "no", billingCountry = "no", forecastCategory = "no", industry = "no", opportunitySource = "no",
                                            opportunityType = "no", ownerRole = "no", productFamily = "no", segment = "no", stage = "no";
                                            boolean isClosed = false, isWon = false;
                                            long amount = 123;
                                            if (!isSimple) {
                                                Map<Object, Object> eventMap = GSON.fromJson(json, Map.class);
                                                Map<Object, Object> map = (Map<Object, Object>) eventMap.get(SALESFORCE_SOBJECT);
                                                id = (String) map.get(SALESFORCE_SOBJECT_ID);
                                            } else {
                                                Map<Object, Object> map = GSON.fromJson(json, Map.class);
                                                id = (String) map.get("Id");
                                                opportunityType = (String) map.get("Type");
                                                stage = (String) map.get("StageName");
                                                forecastCategory = (String) map.get("ForecastCategory");
                                                amount = (long) Double.parseDouble((String) map.get("Amount"));
                                                isWon = Boolean.parseBoolean((String) map.get("IsWon"));
                                                isClosed = Boolean.parseBoolean((String) map.get("IsClosed"));
                                            }
//                                            List<String> list = new ArrayList<>();
//                                            list.add(id);
//                                            list.add(accountType);
//                                            list.add(String.valueOf(amount));
//                                            list.add(billingCountry);
//                                            list.add(Boolean.toString(isClosed));
//                                            list.add(forecastCategory);
//                                            list.add(industry);
//                                            list.add(opportunitySource);
//                                            list.add(opportunityType);
//                                            list.add(ownerRole);
//                                            list.add(productFamily);
//                                            list.add(segment);
//                                            list.add(stage);
//                                            list.add(Boolean.toString(isWon));
//                                            return list;
                                            return Row.withSchema(rowSchema)
                                                    .attachValues(id, accountType, amount, billingCountry, isClosed, forecastCategory,
                                                            industry, opportunitySource, opportunityType, ownerRole, productFamily,
                                                            segment, stage, isWon);
                                        }
                                )
                ).setCoder(RowCoder.of(rowSchema));

        Schema outSchema =
                Schema.of(
                        Schema.Field.of("example", Schema.FieldType.DOUBLE),
                        Schema.Field.of("inference", Schema.FieldType.INT64));
        PCollection<String> outputLines = null;
        if (!options.getOutputDeadletterTable().contains("only")) {
            Coder<KV<String, Row>> outputCoder =
                    KvCoder.of(StringUtf8Coder.of(), RowCoder.of(outSchema));

            outputLines =
                    input.apply(
                    PythonExternalTransform.<PCollection<?>, PCollection<KV<String, Row>>>from(
                                    "anomaly_detection.AnomalyDetection", options.getExpansionService())
                            .withOutputCoder(outputCoder))
//                            .withExtraPackages(Lists.newArrayList("hdbscan", "torch", "autoencoder", "category-encoders", "autoembedder", "akvelon-test-anomaly-detection")))
                    .apply("FormatOutput", MapElements.via(new FormatOutput()));
        }  else if (!noOutput) {
            outputLines = input.apply("FormatToString", MapElements.via(new SimpleFunction<Row, String>() {
                @Override
                public String apply(Row input) {
                    return input.getValue("Id");
                }
            }));
        }

        if (!isSimple) {
            if (!noOutput) {
                outputLines
                        .apply("WriteResults", TextIO.write().withWindowedWrites().withNumShards(1)
                                .to(options.getOutputTableSpec()));
            }
        } else {
            outputLines.apply(TextIO.write().to(options.getOutputTableSpec()));
        }

        pipeline.run();
    }

    private static String getModelLoaderScript() {
        String s = "import hdbscan\n";
        s = s + "from apache_beam.ml.inference.sklearn_inference import SklearnModelHandlerNumpy, ModelFileType\n";
        s = s + "from apache_beam.ml.inference.base import RunInference, PredictionResult, KeyedModelHandler\n";
        s = s + "def get_model_handler(model_uri):\n\n";
        s = s + "  class CustomSklearnModelHandlerNumpy(SklearnModelHandlerNumpy):\n";
        s = s + "    def run_inference(self, batch, model, inference_args=None):\n";
        s = s + "      predictions = hdbscan.approximate_predict(model, batch)\n";
        s = s + "      return [PredictionResult(x, y) for x, y in zip(batch, predictions[0])]\n\n";
        s = s + "  anomaly_detection_model_handler = CustomSklearnModelHandlerNumpy(model_uri=model_uri, model_file_type=ModelFileType.JOBLIB)\n\n";
        s = s + "  return KeyedModelHandler(anomaly_detection_model_handler)";
        return s;
    }

    /** Formats the output. */
    static class FormatOutput extends SimpleFunction<KV<String, Row>, String> {

        public static final String INFERENCE = "inference";

        @Override
        public String apply(KV<String, Row> input) {
            if (input != null && input.getValue() != null) {
                LOG.info(input.getValue().toString());
                return input.getKey() + ", " + input.getValue().getValue(INFERENCE);
            }
            return "";
        }
    }

}
