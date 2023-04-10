package com.akvelon.salesforce.options;

import org.apache.beam.sdk.options.Default;
import org.apache.beam.sdk.options.Description;

/**
 * The {@link CdapSalesforceStreamingSourceOptions} interface provides the custom execution options passed by the
 * executor at the command-line for example with multi-language (Java + Python ML) Cdap Salesforce plugins.
 */
public interface CdapSalesforceStreamingMLSourceOptions extends CdapSalesforceStreamingSourceOptions {

    //Python

    @Description("Python expansion service in format host:port")
    String getExpansionService();

    void setExpansionService(String expansionService);

    @Description("Model URI for Python ML RunInference")
    @Default.String("gs://salesforce-example/anomaly-detection/anomaly_detection.model")
    String getModelUri();

    void setModelUri(String modelUri);

    @Description("Encoder URI for Python ML RunInference")
    @Default.String("gs://salesforce-example/anomaly-detection/encoder.pth")
    String getEncoderUri();

    void setEncoderUri(String encoderUri);

    @Description("Model params URI for Python ML RunInference")
    @Default.String("gs://salesforce-example/anomaly-detection/model.params")
    String getParamsUri();

    void setParamsUri(String paramsUri);
}
