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
package com.akvelon.salesforce.transforms;

import static org.apache.beam.sdk.util.Preconditions.checkStateNotNull;

import com.akvelon.salesforce.utils.GetOffsetUtils;
import io.cdap.cdap.api.data.schema.Schema;
import io.cdap.plugin.salesforce.plugin.source.batch.SalesforceBatchSource;
import io.cdap.plugin.salesforce.plugin.source.batch.SalesforceSourceConfig;
import io.cdap.plugin.salesforce.plugin.source.streaming.SalesforceReceiver;
import io.cdap.plugin.salesforce.plugin.source.streaming.SalesforceStreamingSource;
import io.cdap.plugin.salesforce.plugin.source.streaming.SalesforceStreamingSourceConfig;
import java.util.HashMap;
import java.util.Map;
import org.apache.beam.sdk.io.cdap.CdapIO;
import org.apache.beam.sdk.io.cdap.ConfigWrapper;
import org.apache.beam.sdk.io.cdap.Plugin;
import org.apache.hadoop.io.NullWritable;

/** Different input transformations over the processed data in the pipeline. */
public class FormatInputTransform {

    /**
     * Configures Cdap Salesforce Read transform.
     *
     * @param pluginConfigParams Cdap Salesforce plugin config parameters
     * @return configured Read transform
     */
    @SuppressWarnings("rawtypes")
    public static CdapIO.Read<Schema, HashMap> readFromCdapSalesforceBatch(
            Map<String, Object> pluginConfigParams) {

        final SalesforceSourceConfig pluginConfig =
                new ConfigWrapper<>(SalesforceSourceConfig.class).withParams(pluginConfigParams).build();

        checkStateNotNull(pluginConfig, "Plugin config can't be null.");

        return CdapIO.<Schema, HashMap>read()
                .withCdapPluginClass(SalesforceBatchSource.class)
                .withPluginConfig(pluginConfig)
                .withKeyClass(Schema.class)
                .withValueClass(HashMap.class);
    }

    /**
     * Configures Cdap Salesforce Streaming Read transform.
     *
     * @param pluginConfigParams Cdap Salesforce plugin config parameters
     * @param pullFrequencySec Delay in seconds between polling for new records updates
     * @param startOffset Inclusive start offset from which the reading should be started
     * @return configured Read transform
     */
    public static CdapIO.Read<NullWritable, String> readFromCdapSalesforceStreaming(
            Map<String, Object> pluginConfigParams, Long pullFrequencySec, Long startOffset) {

        final SalesforceStreamingSourceConfig pluginConfig =
                new ConfigWrapper<>(SalesforceStreamingSourceConfig.class)
                        .withParams(pluginConfigParams)
                        .build();
        checkStateNotNull(pluginConfig, "Plugin config can't be null.");

        pluginConfig.ensurePushTopicExistAndWithCorrectFields();

        CdapIO.Read<NullWritable, String> read =
                CdapIO.<NullWritable, String>read()
                        .withCdapPlugin(
                                Plugin.createStreaming(
                                        SalesforceStreamingSource.class,
                                        GetOffsetUtils.getOffsetFnForSalesforce(),
                                        SalesforceReceiver.class,
                                        config -> {
                                            SalesforceStreamingSourceConfig salesforceConfig =
                                                    (SalesforceStreamingSourceConfig) config;
                                            return new Object[] {
                                                    salesforceConfig.getAuthenticatorCredentials(),
                                                    salesforceConfig.getPushTopicName()
                                            };
                                        }))
                        .withPluginConfig(pluginConfig)
                        .withKeyClass(NullWritable.class)
                        .withValueClass(String.class);
        if (pullFrequencySec != null) {
            read = read.withPullFrequencySec(pullFrequencySec);
        }
        if (startOffset != null) {
            read = read.withStartOffset(startOffset);
        }
        return read;
    }
}
