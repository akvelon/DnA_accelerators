package com.akvelon.salesforce.templates;

import com.akvelon.salesforce.options.CdapSalesforceSourceOptions;
import com.akvelon.salesforce.transforms.FormatInputTransform;
import com.akvelon.salesforce.utils.PluginConfigOptionsConverter;
import com.google.gson.Gson;
import io.cdap.cdap.api.data.schema.Schema;
import java.util.HashMap;
import java.util.Map;
import org.apache.beam.sdk.Pipeline;
import org.apache.beam.sdk.PipelineResult;
import org.apache.beam.sdk.coders.KvCoder;
import org.apache.beam.sdk.coders.SerializableCoder;
import org.apache.beam.sdk.coders.StringUtf8Coder;
import org.apache.beam.sdk.io.TextIO;
import org.apache.beam.sdk.options.PipelineOptionsFactory;
import org.apache.beam.sdk.transforms.MapValues;
import org.apache.beam.sdk.transforms.Values;
import org.apache.beam.sdk.values.TypeDescriptors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Batch Salesforce Pipeline.
 */
public class CdapSalesforceBatchToTxt {

    private static final Gson GSON = new Gson();

    /* Logger for class.*/
    private static final Logger LOG = LoggerFactory.getLogger(CdapSalesforceBatchToTxt.class);

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
     * Runs a pipeline which reads records from CDAP Salesforce plugin.
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
         *  3) Write successful records to .txt file
         */

        pipeline
                .apply("readFromCdapSalesforce", FormatInputTransform.readFromCdapSalesforce(paramsMap))
                .setCoder(
                        KvCoder.of(
                                SerializableCoder.of(Schema.class), SerializableCoder.of(HashMap.class)))
                .apply(MapValues.into(TypeDescriptors.strings()).via(map -> GSON.toJson(map, Map.class)))
                .setCoder(KvCoder.of(SerializableCoder.of(Schema.class), StringUtf8Coder.of()))
                .apply(Values.create())
                .apply("writeToTxt", TextIO.write().to(options.getOutputTxtFilePathPrefix()));

        return pipeline.run();
    }
}
