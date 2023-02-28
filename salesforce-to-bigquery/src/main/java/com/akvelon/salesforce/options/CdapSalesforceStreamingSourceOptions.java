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
import org.apache.beam.sdk.options.Default;
import org.apache.beam.sdk.options.Description;
import org.apache.beam.sdk.options.Validation;

/**
 * The {@link CdapSalesforceStreamingSourceOptions} interface provides the custom execution options passed by the
 * executor at the command-line for example with Cdap Salesforce plugins.
 */
public interface CdapSalesforceStreamingSourceOptions extends DataflowPipelineOptions {

    //Python

    @Description("Expansion Service")
    @Default.String("")
    String getExpansionService();

    void setExpansionService(String expansionService);

    @Description("Model URI")
    @Default.String("gs://apache-beam-testing-cdap/anomaly_detection_single.model")
    String getModelUri();

    void setModelUri(String modelUri);

    // Base

    @Validation.Required
    @Description(Constants.Reference.REFERENCE_NAME_DESCRIPTION)
    @Default.String("myReference")
    String getReferenceName();

    void setReferenceName(String referenceName);

    //Salesforce

    @Description(SalesforceConstants.PROPERTY_USERNAME)
    @Default.String("akarys.shorabek@akvelon.com")
    String getUsername();

    void setUsername(String username);

    @Description(SalesforceConstants.PROPERTY_PASSWORD)
    @Default.String("Akvelon2023#")
    String getPassword();

    void setPassword(String password);

    @Description(SalesforceConstants.PROPERTY_SECURITY_TOKEN)
    @Default.String("A9cyYJH5hl1VWXCNjva2YwIh")
    String getSecurityToken();

    void setSecurityToken(String securityToken);

    @Description(SalesforceConstants.PROPERTY_CONSUMER_KEY)
    @Default.String("3MVG9t0sl2P.pByr4TRAiAY43fPIry8GgeN22WuRUTiIVg7j7o9KTlSGhRDTvuIZ2ivTLew3_Bfc6MRPDcErC")
    String getConsumerKey();

    void setConsumerKey(String consumerKey);

    @Description(SalesforceConstants.PROPERTY_CONSUMER_SECRET)
    @Default.String("77B38C597867F12182E33E98C188EF966E2754676F67308EE61AFB95F84E3C6E")
    String getConsumerSecret();

    void setConsumerSecret(String consumerSecret);

    @Validation.Required
    @Description(SalesforceConstants.PROPERTY_LOGIN_URL)
    @Default.String("https://login.salesforce.com/services/oauth2/token")
    String getLoginUrl();

    void setLoginUrl(String loginUrl);

    //Source

    @Validation.Required
    @Description(SalesforceSourceConstants.PROPERTY_SOBJECT_NAME)
    @Default.String("Account")
    String getSObjectName();

    void setSObjectName(String sObjectName);

    // BigQuery

    @Description("Big Query table spec to write the output to / path to output txt file")
    @Validation.Required
    @Default.String("gs://apache-beam-testing-cdap/output/salesforce-dna.txt")
    String getOutputTableSpec();

    void setOutputTableSpec(String outputTableSpec);

    @Description(
            "The dead-letter table to output to within BigQuery in <project-id>:<dataset>.<table> "
                    + "format. If it doesn't exist, it will be created during pipeline execution.")
    @Default.String("your-dead-letter-table")
    String getOutputDeadletterTable();

    void setOutputDeadletterTable(String outputDeadletterTable);

    //Streaming

    @Validation.Required
    @Description("Salesforce push topic name. Plugin will track updates from this topic.")
    @Default.String("myAccountTopic")
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
