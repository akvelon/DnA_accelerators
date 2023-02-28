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
package com.akvelon.salesforce.transforms;

import static org.apache.beam.sdk.util.Preconditions.checkStateNotNull;

import com.akvelon.salesforce.utils.GetOffsetUtils;
import io.cdap.plugin.hubspot.source.streaming.HubspotReceiver;
import io.cdap.plugin.hubspot.source.streaming.HubspotStreamingSource;
import io.cdap.plugin.hubspot.source.streaming.HubspotStreamingSourceConfig;
import io.cdap.plugin.salesforce.plugin.source.streaming.SalesforceReceiver;
import io.cdap.plugin.salesforce.plugin.source.streaming.SalesforceStreamingSource;
import io.cdap.plugin.salesforce.plugin.source.streaming.SalesforceStreamingSourceConfig;
import java.util.Map;
import org.apache.beam.sdk.io.cdap.CdapIO;
import org.apache.beam.sdk.io.cdap.ConfigWrapper;
import org.apache.beam.sdk.io.cdap.Plugin;
import org.apache.beam.sdk.io.sparkreceiver.ReceiverBuilder;
import org.apache.beam.sdk.io.sparkreceiver.SparkReceiverIO;
import org.apache.hadoop.io.NullWritable;
import org.apache.spark.streaming.receiver.Receiver;

/** Different input transformations over the processed data in the pipeline. */
public class FormatInputTransform {

    /**
     * Configures Cdap Salesforce Streaming Read transform.
     *
     * @param pluginConfigParams Cdap Hubspot plugin config parameters
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
                                        GetOffsetUtils.getOffsetFnForCdapPlugin(SalesforceStreamingSource.class),
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

    public static CdapIO.Read<String, String> readFromCdapSalesforceStreamingString(
            Map<String, Object> pluginConfigParams, Long pullFrequencySec, Long startOffset) {

        final SalesforceStreamingSourceConfig pluginConfig =
                new ConfigWrapper<>(SalesforceStreamingSourceConfig.class)
                        .withParams(pluginConfigParams)
                        .build();
        checkStateNotNull(pluginConfig, "Plugin config can't be null.");

        pluginConfig.ensurePushTopicExistAndWithCorrectFields();

        CdapIO.Read<String, String> read =
                CdapIO.<String, String>read()
                        .withCdapPlugin(
                                Plugin.createStreaming(
                                        SalesforceStreamingSource.class,
                                        GetOffsetUtils.getOffsetFnForCdapPlugin(SalesforceStreamingSource.class),
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
                        .withKeyClass(String.class)
                        .withValueClass(String.class);
        if (pullFrequencySec != null) {
            read = read.withPullFrequencySec(pullFrequencySec);
        }
        if (startOffset != null) {
            read = read.withStartOffset(startOffset);
        }
        return read;
    }

    public static SparkReceiverIO.Read<String> readFromSalesforceSparkReceiver(
            Map<String, Object> pluginConfigParams, Long pullFrequencySec, Long startOffset) {

        final SalesforceStreamingSourceConfig pluginConfig =
                new ConfigWrapper<>(SalesforceStreamingSourceConfig.class)
                        .withParams(pluginConfigParams)
                        .build();
        checkStateNotNull(pluginConfig, "Plugin config can't be null.");

        pluginConfig.ensurePushTopicExistAndWithCorrectFields();

        Plugin<String, String> cdapPlugin = Plugin.createStreaming(
                SalesforceStreamingSource.class,
                GetOffsetUtils.getOffsetFnForCdapPlugin(SalesforceStreamingSource.class),
                SalesforceReceiver.class,
                config -> {
                    SalesforceStreamingSourceConfig salesforceConfig =
                            (SalesforceStreamingSourceConfig) config;
                    return new Object[] {
                            salesforceConfig.getAuthenticatorCredentials(),
                            salesforceConfig.getPushTopicName()
                    };
                });
        cdapPlugin.withConfig(pluginConfig);
        ReceiverBuilder<String, ? extends Receiver<String>> receiverBuilder =
                cdapPlugin.getReceiverBuilder();

        SparkReceiverIO.Read<String> read =
                SparkReceiverIO.<String>read()
                        .withGetOffsetFn(GetOffsetUtils.getOffsetFnForCdapPlugin(SalesforceStreamingSource.class))
                        .withSparkReceiverBuilder(receiverBuilder);

        if (pullFrequencySec != null) {
            read = read.withPullFrequencySec(pullFrequencySec);
        }
        if (startOffset != null) {
            read = read.withStartOffset(startOffset);
        }
        return read;
    }

    /**
     * Configures Cdap Hubspot Streaming Read transform.
     *
     * @param pluginConfigParams Cdap Hubspot plugin config parameters
     * @param pullFrequencySec Delay in seconds between polling for new records updates
     * @param startOffset Inclusive start offset from which the reading should be started
     * @return configured Read transform
     */
    public static SparkReceiverIO.Read<String> readFromCdapHubspotStreaming(
            Map<String, Object> pluginConfigParams, Long pullFrequencySec, Long startOffset) {

        final HubspotStreamingSourceConfig pluginConfig =
                new ConfigWrapper<>(HubspotStreamingSourceConfig.class)
                        .withParams(pluginConfigParams)
                        .build();
        checkStateNotNull(pluginConfig, "Plugin config can't be null.");

        Plugin<NullWritable, String> cdapPlugin = Plugin.createStreaming(
                HubspotStreamingSource.class,
                GetOffsetUtils.getOffsetFnForCdapPlugin(HubspotStreamingSource.class),
                HubspotReceiver.class);

        cdapPlugin.withConfig(pluginConfig);
        ReceiverBuilder<String, ? extends Receiver<String>> receiverBuilder =
                cdapPlugin.getReceiverBuilder();

        SparkReceiverIO.Read<String> read =
                SparkReceiverIO.<String>read()
                        .withGetOffsetFn(GetOffsetUtils.getOffsetFnForCdapPlugin(HubspotStreamingSource.class))
                        .withSparkReceiverBuilder(receiverBuilder);
        if (pullFrequencySec != null) {
            read = read.withPullFrequencySec(pullFrequencySec);
        }
        if (startOffset != null) {
            read = read.withStartOffset(startOffset);
        }
        return read;
    }
}