package com.akvelon.salesforce.utils;

/**
 * Constants related to BigQuery.
 */
public class BigQueryConstants {

    /**
     * The default dead letter table schema.
     */
    public static final String DEADLETTER_SCHEMA =
            "{\n"
                    + "  \"fields\": [\n"
                    + "    {\n"
                    + "      \"name\": \"timestamp\",\n"
                    + "      \"type\": \"TIMESTAMP\",\n"
                    + "      \"mode\": \"REQUIRED\"\n"
                    + "    },\n"
                    + "    {\n"
                    + "      \"name\": \"payloadString\",\n"
                    + "      \"type\": \"STRING\",\n"
                    + "      \"mode\": \"REQUIRED\"\n"
                    + "    },\n"
                    + "    {\n"
                    + "      \"name\": \"payloadBytes\",\n"
                    + "      \"type\": \"BYTES\",\n"
                    + "      \"mode\": \"REQUIRED\"\n"
                    + "    },\n"
                    + "    {\n"
                    + "      \"name\": \"attributes\",\n"
                    + "      \"type\": \"RECORD\",\n"
                    + "      \"mode\": \"REPEATED\",\n"
                    + "      \"fields\": [\n"
                    + "        {\n"
                    + "          \"name\": \"key\",\n"
                    + "          \"type\": \"STRING\",\n"
                    + "          \"mode\": \"NULLABLE\"\n"
                    + "        },\n"
                    + "        {\n"
                    + "          \"name\": \"value\",\n"
                    + "          \"type\": \"STRING\",\n"
                    + "          \"mode\": \"NULLABLE\"\n"
                    + "        }\n"
                    + "      ]\n"
                    + "    },\n"
                    + "    {\n"
                    + "      \"name\": \"errorMessage\",\n"
                    + "      \"type\": \"STRING\",\n"
                    + "      \"mode\": \"NULLABLE\"\n"
                    + "    },\n"
                    + "    {\n"
                    + "      \"name\": \"stacktrace\",\n"
                    + "      \"type\": \"STRING\",\n"
                    + "      \"mode\": \"NULLABLE\"\n"
                    + "    }\n"
                    + "  ]\n"
                    + "}";

    /**
     * The default suffix for error tables if dead letter table is not specified.
     */
    public static final String DEFAULT_DEADLETTER_TABLE_SUFFIX = "_error_records";
}
