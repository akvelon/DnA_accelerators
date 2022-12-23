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
package com.akvelon.salesforce.options;

import io.cdap.plugin.common.Constants;
import io.cdap.plugin.salesforce.SalesforceConstants;
import io.cdap.plugin.salesforce.plugin.source.batch.util.SalesforceSourceConstants;
import org.apache.beam.runners.dataflow.options.DataflowPipelineOptions;
import org.apache.beam.sdk.options.Description;
import org.apache.beam.sdk.options.Validation;

/**
 * The {@link CdapSalesforceStreamingSourceOptions} interface provides the custom execution options passed by the
 * executor at the command-line for example with Cdap Salesfroce plugins.
 */
public interface CdapSalesforceStreamingSourceOptions extends DataflowPipelineOptions {

    // Base

    @Validation.Required
    @Description(Constants.Reference.REFERENCE_NAME_DESCRIPTION)
    String getReferenceName();

    void setReferenceName(String referenceName);

    //Salesforce

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

    //Source

    @Validation.Required
    @Description(SalesforceSourceConstants.PROPERTY_SOBJECT_NAME)
    String getSObjectName();

    void setSObjectName(String sObjectName);

    @Validation.Required
    @Description(
            "Path to output folder with filename prefix."
                    + "It will write a set of .txt files with names like {prefix}-###.")
    String getOutputTxtFilePathPrefix();

    void setOutputTxtFilePathPrefix(String outputTxtFilePathPrefix);

    //Streaming

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
