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
package com.akvelon.salesforce.templates;

import com.google.gson.Gson;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import org.apache.beam.vendor.guava.v26_0_jre.com.google.common.collect.Lists;
import org.apache.beam.vendor.guava.v26_0_jre.com.google.common.reflect.TypeToken;
import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Salesforce producer.
 */
public class SalesforceProducer {

    private static final Logger LOG = LoggerFactory.getLogger(SalesforceProducer.class);

    private static final Gson GSON = new Gson();
    private static final List<String> RESTRICTED_COLUMNS = Lists.newArrayList(
            "LastModifiedDate", "HasOpportunityLineItem", "FiscalYear",
            "ForecastCategory", "IsClosed", "HasOpenActivity", "ExpectedRevenue",
            "IsWon", "PushCount", "IsDeleted", "FiscalQuarter", "Fiscal",
            "SystemModstamp", "CreatedDate", "LastActivityDate", "HasOverdueTask",
            "LastStageChangeDate", "LastReferencedDate", "LastViewedDate"
    );
    public static final int DEFAULT_TIMEOUT = 10;
    public static final int DEFAULT_NUM_OF_RECORDS = 5;
    public static final String SALESFORCE_SOBJECTS_URI = "/services/data/v57.0/sobjects/";

    public static void main(String[] args) {

        String jsonFile, url, token, objectType;
        int amount;
        if (args.length == 5) {
            jsonFile = args[0];
            url = args[1];
            token = args[2];
            objectType = args[3];
            amount = Integer.parseInt(args[4]);
        } else {
            throw new IllegalArgumentException("Please provide args: " +
                    "[jsonFilePathPrefix, Salesforce base URL, Salesforce Access Token, " +
                    "Salesforce ObjectType, Amount of files]");
        }

        int recordsWritten = 0;
        for (int fileNumber = 1; fileNumber <= amount; fileNumber++) {
            try {
                List<String> lines = new ArrayList<>();
                try (BufferedReader br = new BufferedReader(new FileReader(jsonFile + fileNumber))) {
                    String line;
                    while ((line = br.readLine()) != null) {
                        lines.add(line);
                    }
                } catch (IOException e) {
                    LOG.error("Can't read file", e);
                }
                for (String jsonRecord: lines) {

                    HashMap<String, String> map =
                            GSON.fromJson(jsonRecord, new TypeToken<HashMap<String, String>>() {
                            }.getType());

                    List<String> keysToRemove =
                            map.keySet().stream().filter(key -> key.contains("Id") || key.contains("__c")
                                    || RESTRICTED_COLUMNS.contains(key)).collect(Collectors.toList());
                    for (String key : keysToRemove) {
                        map.remove(key);
                    }
                    jsonRecord = GSON.toJson(map);

                    LOG.info(jsonRecord);

                    HttpURLConnection con = (HttpURLConnection) new URL(url + SALESFORCE_SOBJECTS_URI + objectType).openConnection();
                    con.setRequestMethod("POST");
                    con.setDoOutput(true);
                    con.setRequestProperty("Authorization", "Bearer " + token);
                    con.setRequestProperty("Content-Type", "application/json");
                    con.getOutputStream().write(jsonRecord.getBytes(StandardCharsets.UTF_8));
                    InputStream inputStream = con.getInputStream();

                    String result = IOUtils.toString(inputStream, StandardCharsets.UTF_8);
                    LOG.info(result);
                    con.disconnect();

                    recordsWritten++;
                    if (recordsWritten % DEFAULT_NUM_OF_RECORDS == 0) {
                        TimeUnit.SECONDS.sleep(DEFAULT_TIMEOUT);
                    }
                }
            } catch (Exception e) {
                LOG.error("Exception", e);
            }
        }
    }
}
