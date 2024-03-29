package com.akvelon.salesforce.options;

import io.cdap.plugin.common.Constants;
import io.cdap.plugin.salesforce.SalesforceConstants;
import io.cdap.plugin.salesforce.plugin.source.batch.util.SalesforceSourceConstants;
import org.apache.beam.runners.dataflow.options.DataflowPipelineOptions;
import org.apache.beam.sdk.options.Description;
import org.apache.beam.sdk.options.Validation;

/**
 * The {@link SalesforceBaseSourceOptions} interface provides the custom execution options passed by the
 * executor at the command-line for example with Cdap Salesforce source plugins.
 */
public interface SalesforceBaseSourceOptions extends DataflowPipelineOptions {

    @Validation.Required
    @Description(SalesforceSourceConstants.PROPERTY_SOBJECT_NAME)
    String getSObjectName();

    void setSObjectName(String sObjectName);

    @Description(SalesforceConstants.PROPERTY_USERNAME)
    String getUsername();

    void setUsername(String username);

    @Description(SalesforceConstants.PROPERTY_PASSWORD)
    String getPassword();

    void setPassword(String password);

    @Description(SalesforceConstants.PROPERTY_SECURITY_TOKEN)
    String getSecurityToken();

    void setSecurityToken(String securityToken);

    @Description(SalesforceConstants.PROPERTY_CONSUMER_KEY)
    String getConsumerKey();

    void setConsumerKey(String consumerKey);

    @Description(SalesforceConstants.PROPERTY_CONSUMER_SECRET)
    String getConsumerSecret();

    void setConsumerSecret(String consumerSecret);

    @Validation.Required
    @Description(SalesforceConstants.PROPERTY_LOGIN_URL)
    String getLoginUrl();

    void setLoginUrl(String loginUrl);

    @Validation.Required
    @Description(Constants.Reference.REFERENCE_NAME_DESCRIPTION)
    String getReferenceName();

    void setReferenceName(String referenceName);
}
