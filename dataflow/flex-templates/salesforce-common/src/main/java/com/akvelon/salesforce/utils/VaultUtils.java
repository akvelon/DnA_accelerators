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
package com.akvelon.salesforce.utils;

import io.cdap.plugin.salesforce.SalesforceConstants;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import org.apache.beam.vendor.grpc.v1p48p1.com.google.gson.JsonObject;
import org.apache.beam.vendor.grpc.v1p48p1.com.google.gson.JsonParser;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.util.EntityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Class for working with HashiCorp Vault.
 */
public class VaultUtils {

    private static final Logger LOG = LoggerFactory.getLogger(VaultUtils.class);

    /**
     * Retrieves all credentials from HashiCorp Vault secret storage.
     *
     * @param secretStoreUrl url to the secret storage that contains a credentials for Salesforce
     * @param token          Vault token to access the secret storage
     * @return credentials for Salesforce config
     */
    public static Map<String, String> getSalesforceCredentialsFromVault(
            String secretStoreUrl, String token) {
        Map<String, String> credentialMap = new HashMap<>();

        JsonObject credentials = null;
        try {
            HttpClient client = HttpClientBuilder.create().build();
            HttpGet request = new HttpGet(secretStoreUrl);
            request.addHeader("X-Vault-Token", token);
            request.addHeader("X-Vault-Namespace", "admin");
            HttpResponse response = client.execute(request);
            String json = EntityUtils.toString(response.getEntity(), "UTF-8");

      /*
       Vault's response JSON has a specific schema, where the actual data is placed under
       {data: {data: <actual data>}}.
       Example:
         {
           "request_id": "6a0bb14b-ef24-256c-3edf-cfd52ad1d60d",
           "lease_id": "",
           "renewable": false,
           "lease_duration": 0,
           "data": {
             "data": {
               "username": "username",
               "password": "password",
               "securityToken": "security-token",
               "consumerKey": "consumer-key",
               "consumerSecret": "consumer-secret"
             },
             "metadata": {
               "created_time": "2020-10-20T11:43:11.109186969Z",
               "deletion_time": "",
               "destroyed": false,
               "version": 8
             }
           },
           "wrap_info": null,
           "warnings": null,
           "auth": null
         }
      */
            // Parse security properties from the response JSON
            credentials =
                    JsonParser.parseString(json)
                            .getAsJsonObject()
                            .get("data")
                            .getAsJsonObject()
                            .getAsJsonObject("data");
        } catch (IOException e) {
            LOG.error("Failed to retrieve credentials from Vault.", e);
        }

        if (credentials != null) {
            credentialMap.put(SalesforceConstants.PROPERTY_CONSUMER_KEY, credentials
                    .get(SalesforceConstants.PROPERTY_CONSUMER_KEY).getAsString());
            credentialMap.put(SalesforceConstants.PROPERTY_CONSUMER_SECRET, credentials
                    .get(SalesforceConstants.PROPERTY_CONSUMER_SECRET).getAsString());
            credentialMap.put(SalesforceConstants.PROPERTY_SECURITY_TOKEN, credentials
                    .get(SalesforceConstants.PROPERTY_SECURITY_TOKEN).getAsString());
            credentialMap.put(SalesforceConstants.PROPERTY_USERNAME, credentials
                    .get(SalesforceConstants.PROPERTY_USERNAME).getAsString());
            credentialMap.put(SalesforceConstants.PROPERTY_PASSWORD, credentials
                    .get(SalesforceConstants.PROPERTY_PASSWORD).getAsString());
        }

        return credentialMap;
    }
}
