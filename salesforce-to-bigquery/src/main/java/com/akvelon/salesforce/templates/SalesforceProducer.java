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

    private static final String DEFAULT_JSON_FILE = "/home/vitaly/Documents/trail/salesforce-opportunity-";
    private static final String DEFAULT_URL = "https://akvekoninc-dev-ed.my.salesforce.com";
    private static final String DEFAULT_TOKEN = "00D7Q00000BTuPu!AQoAQJE2zeXixkryhtAs2HXHbNuPjuV3Js0AJ1_InK_LHczF4PC_qS8SJmLjuYTuWmB_9OmRbFrBSfKiTXcQB10BkZlmgBnY";
    private static final Gson GSON = new Gson();
    private static final List<String> RESTRICTED_COLUMNS = Lists.newArrayList(
            "LastModifiedDate", "HasOpportunityLineItem", "FiscalYear",
            "ForecastCategory", "IsClosed", "HasOpenActivity", "ExpectedRevenue",
            "IsWon", "PushCount", "IsDeleted", "FiscalQuarter", "Fiscal",
            "SystemModstamp", "CreatedDate", "LastActivityDate", "HasOverdueTask",
            "LastStageChangeDate", "LastReferencedDate", "LastViewedDate"
    );

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
            jsonFile = DEFAULT_JSON_FILE;
            url = DEFAULT_URL;
            token = DEFAULT_TOKEN;
            amount = 4;
            objectType = "Opportunity";
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

                    HttpURLConnection con = (HttpURLConnection) new URL(url + "/services/data/v57.0/sobjects/" + objectType).openConnection();
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
                    if (recordsWritten % 5 == 0) {
                        TimeUnit.SECONDS.sleep(10);
//                        break;
                    }
                }
            } catch (Exception e) {
                LOG.error("Exception", e);
            }
        }
    }
}
