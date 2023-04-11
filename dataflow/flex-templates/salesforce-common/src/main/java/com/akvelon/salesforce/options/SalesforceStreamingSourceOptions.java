package com.akvelon.salesforce.options;

import org.apache.beam.sdk.options.Description;
import org.apache.beam.sdk.options.Validation;

/**
 * The {@link SalesforceStreamingSourceOptions} interface provides the custom execution options passed by the
 * executor at the command-line for example with Cdap Salesforce streaming source plugins.
 */
public interface SalesforceStreamingSourceOptions extends SalesforceBaseSourceOptions {

    @Validation.Required
    @Description("Salesforce push topic name. Plugin will track updates from this topic.")
    String getPushTopicName();

    void setPushTopicName(String pushTopicName);

    @Description("Delay in seconds between polling for new records updates.")
    Long getPullFrequencySec();

    void setPullFrequencySec(Long pullFrequencySec);

    @Description("Inclusive start offset from which the reading should be started.")
    Long getStartOffset();

    void setStartOffset(Long startOffset);

    @Description("URL to credentials in Vault")
    String getSecretStoreUrl();

    void setSecretStoreUrl(String secretStoreUrl);

    @Description("Vault token")
    String getVaultToken();

    void setVaultToken(String vaultToken);
}
