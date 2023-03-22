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
package com.akvelon.salesforce.options;

import io.cdap.plugin.common.Constants;
import io.cdap.plugin.salesforce.SalesforceConstants;
import io.cdap.plugin.salesforce.plugin.source.batch.util.SalesforceSourceConstants;
import org.apache.beam.runners.dataflow.options.DataflowPipelineOptions;
import org.apache.beam.sdk.options.Description;
import org.apache.beam.sdk.options.Validation;

/**
 * The {@link CdapSalesforceSourceOptions+} interface provides the custom execution options passed by the
 * executor at the command-line for example with Cdap Salesforce plugins.
 */
public interface CdapSalesforceSourceOptions extends DataflowPipelineOptions {

    @Validation.Required
    @Description(SalesforceSourceConstants.PROPERTY_SOBJECT_NAME)
    String getSObjectName();

    void setSObjectName(String sObjectName);

    @Validation.Required
    @Description("Big Query table spec to write the output to / path to output txt file")
    String getOutputTableSpec();

    void setOutputTableSpec(String outputTableSpec);

    @Validation.Required
    @Description(SalesforceConstants.PROPERTY_USERNAME)
    String getUsername();

    void setUsername(String username);

    @Validation.Required
    @Description(SalesforceConstants.PROPERTY_PASSWORD)
    String getPassword();

    void setPassword(String password);

    @Validation.Required
    @Description(SalesforceConstants.PROPERTY_SECURITY_TOKEN)
    String getSecurityToken();

    void setSecurityToken(String securityToken);

    @Validation.Required
    @Description(SalesforceConstants.PROPERTY_CONSUMER_KEY)
    String getConsumerKey();

    void setConsumerKey(String consumerKey);

    @Validation.Required
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

    //Optional

    @Description("Salesforce SObject query offset.")
    String getOffset();

    void setOffset(String offset);

    @Description("Salesforce SObject query duration.")
    String getDuration();

    void setDuration();

    @Description("Salesforce SObject query datetime filter. Example: 2019-03-12T11:29:52Z")
    String getDatetimeBefore();

    void setDatetimeBefore();

    @Description("Salesforce SObject query datetime filter. Example: 2019-03-12T11:29:52Z")
    String getDatetimeAfter();

    void setDatetimeAfter();

    @Description("The SOQL query to retrieve results from. Example: select Id, Name from Opportunity")
    String getQuery();

    void setQuery();
}
