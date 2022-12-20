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
package com.akvelon.salesforce.utils;

import io.cdap.plugin.common.Constants;
import io.cdap.plugin.salesforce.SalesforceConstants;
import io.cdap.plugin.salesforce.plugin.source.batch.util.SalesforceSourceConstants;
import java.util.Map;
import com.akvelon.salesforce.options.CdapSalesforceStreamingSourceOptions;
import org.apache.beam.vendor.guava.v26_0_jre.com.google.common.collect.ImmutableMap;

/**
 * Class for converting CDAP {@link org.apache.beam.sdk.options.PipelineOptions} to map for {@link
 * org.apache.beam.sdk.io.cdap.ConfigWrapper}.
 */
public class PluginConfigOptionsConverter {

    private static final String SALESFORCE_STREAMING_PUSH_TOPIC_NAME = "pushTopicName";
    private static final String SALESFORCE_PUSH_TOPIC_NOTIFY_CREATE = "pushTopicNotifyCreate";
    private static final String SALESFORCE_PUSH_TOPIC_NOTIFY_UPDATE = "pushTopicNotifyUpdate";
    private static final String SALESFORCE_PUSH_TOPIC_NOTIFY_DELETE = "pushTopicNotifyDelete";
    private static final String SALESFORCE_PUSH_TOPIC_NOTIFY_FOR_FIELDS = "pushTopicNotifyForFields";
    private static final String SALESFORCE_REFERENCED_NOTIFY_FOR_FIELDS = "Referenced";
    private static final String SALESFORCE_ENABLED_NOTIFY = "Enabled";

    /** Returns map of parameters for Cdap Salesforce streaming source plugin. */
    public static Map<String, Object> salesforceStreamingSourceOptionsToParamsMap(
            CdapSalesforceStreamingSourceOptions options) {
        //TODO: validate secured parameters
        return ImmutableMap.<String, Object>builder()
                .put(Constants.Reference.REFERENCE_NAME, options.getReferenceName())
                .put(SALESFORCE_STREAMING_PUSH_TOPIC_NAME, options.getPushTopicName())
                .put(SalesforceConstants.PROPERTY_USERNAME, options.getUsername())
                .put(SalesforceConstants.PROPERTY_PASSWORD, options.getPassword())
                .put(SalesforceConstants.PROPERTY_SECURITY_TOKEN, options.getSecurityToken())
                .put(SalesforceConstants.PROPERTY_CONSUMER_KEY, options.getConsumerKey())
                .put(SalesforceConstants.PROPERTY_CONSUMER_SECRET, options.getConsumerSecret())
                .put(SalesforceConstants.PROPERTY_LOGIN_URL, options.getLoginUrl())
                .put(SalesforceSourceConstants.PROPERTY_SOBJECT_NAME, options.getSObjectName())
                .put(SALESFORCE_PUSH_TOPIC_NOTIFY_CREATE, SALESFORCE_ENABLED_NOTIFY)
                .put(SALESFORCE_PUSH_TOPIC_NOTIFY_UPDATE, SALESFORCE_ENABLED_NOTIFY)
                .put(SALESFORCE_PUSH_TOPIC_NOTIFY_DELETE, SALESFORCE_ENABLED_NOTIFY)
                .put(SALESFORCE_PUSH_TOPIC_NOTIFY_FOR_FIELDS, SALESFORCE_REFERENCED_NOTIFY_FOR_FIELDS)
                .build();
    }
}
