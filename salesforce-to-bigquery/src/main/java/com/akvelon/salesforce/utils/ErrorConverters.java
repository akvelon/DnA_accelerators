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

import com.google.api.services.bigquery.model.TableRow;
import com.google.auto.value.AutoValue;
import java.nio.charset.StandardCharsets;
import org.apache.beam.sdk.io.gcp.bigquery.BigQueryIO;
import org.apache.beam.sdk.io.gcp.bigquery.WriteResult;
import org.apache.beam.sdk.transforms.DoFn;
import org.apache.beam.sdk.transforms.PTransform;
import org.apache.beam.sdk.transforms.ParDo;
import org.apache.beam.sdk.values.PCollection;
import org.joda.time.DateTimeZone;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;

/**
 * Transforms & DoFns & Options for Error logging.
 */
public class ErrorConverters {

    /** Writes strings error messages. */
    @AutoValue
    public abstract static class WriteStringMessageErrors
            extends PTransform<PCollection<FailsafeElement<String, String>>, WriteResult> {

        public static Builder newBuilder() {
            return new AutoValue_ErrorConverters_WriteStringMessageErrors.Builder();
        }

        public abstract String getErrorRecordsTable();

        public abstract String getErrorRecordsTableSchema();

        @Override
        public WriteResult expand(PCollection<FailsafeElement<String, String>> failedRecords) {

            return failedRecords
                    .apply("FailedRecordToTableRow", ParDo.of(new FailedStringToTableRowFn()))
                    .apply(
                            "WriteFailedRecordsToBigQuery",
                            BigQueryIO.writeTableRows()
                                    .to(getErrorRecordsTable())
                                    .withJsonSchema(getErrorRecordsTableSchema())
                                    .withCreateDisposition(BigQueryIO.Write.CreateDisposition.CREATE_IF_NEEDED)
                                    .withWriteDisposition(BigQueryIO.Write.WriteDisposition.WRITE_APPEND));
        }

        /** Builder for {@link WriteStringMessageErrors}. */
        @AutoValue.Builder
        public abstract static class Builder {
            public abstract Builder setErrorRecordsTable(String errorRecordsTable);

            public abstract Builder setErrorRecordsTableSchema(String errorRecordsTableSchema);

            public abstract WriteStringMessageErrors build();
        }
    }

    /**
     * The {@link FailedStringToTableRowFn} converts string objects which have failed processing into
     * {@link TableRow} objects which can be output to a dead-letter table.
     */
    public static class FailedStringToTableRowFn
            extends DoFn<FailsafeElement<String, String>, TableRow> {

        /**
         * The formatter used to convert timestamps into a BigQuery compatible <a
         * href="https://cloud.google.com/bigquery/docs/reference/standard-sql/data-types#timestamp-type">format</a>.
         */
        private static final DateTimeFormatter TIMESTAMP_FORMATTER =
                DateTimeFormat.forPattern("yyyy-MM-dd HH:mm:ss.SSSSSS");

        @ProcessElement
        public void processElement(ProcessContext context) {
            FailsafeElement<String, String> failsafeElement = context.element();
            final String message = failsafeElement.getOriginalPayload();

            // Format the timestamp for insertion
            String timestamp =
                    TIMESTAMP_FORMATTER.print(context.timestamp().toDateTime(DateTimeZone.UTC));

            // Build the table row
            final TableRow failedRow =
                    new TableRow()
                            .set("timestamp", timestamp)
                            .set("errorMessage", failsafeElement.getErrorMessage())
                            .set("stacktrace", failsafeElement.getStacktrace());

            // Only set the payload if it's populated on the message.
            if (message != null) {
                failedRow
                        .set("payloadString", message)
                        .set("payloadBytes", message.getBytes(StandardCharsets.UTF_8));
            }

            context.output(failedRow);
        }
    }

    /**
     * The {@link WriteSalesforceMessageErrors} class is a transform which can be used to write messages
     * which failed processing to an error records table. Each record is saved to the error table is
     * enriched with the timestamp of that record and the details of the error including an error
     * message and stacktrace for debugging.
     */
    @AutoValue
    public abstract static class WriteSalesforceMessageErrors
            extends PTransform<PCollection<FailsafeElement<String, String>>, WriteResult> {

        public static Builder newBuilder() {
            return new AutoValue_ErrorConverters_WriteSalesforceMessageErrors.Builder();
        }

        public abstract String getErrorRecordsTable();

        public abstract String getErrorRecordsTableSchema();

        @Override
        public WriteResult expand(
                PCollection<FailsafeElement<String, String>> failedRecords) {

            return failedRecords
                    .apply("FailedRecordToTableRow", ParDo.of(new FailedMessageToTableRowFn()))
                    .apply(
                            "WriteFailedRecordsToBigQuery",
                            BigQueryIO.writeTableRows()
                                    .to(getErrorRecordsTable())
                                    .withJsonSchema(getErrorRecordsTableSchema())
                                    .withCreateDisposition(BigQueryIO.Write.CreateDisposition.CREATE_IF_NEEDED)
                                    .withWriteDisposition(BigQueryIO.Write.WriteDisposition.WRITE_APPEND));
        }

        /** Builder for {@link WriteSalesforceMessageErrors}. */
        @AutoValue.Builder
        public abstract static class Builder {
            public abstract Builder setErrorRecordsTable(String errorRecordsTable);

            public abstract Builder setErrorRecordsTableSchema(String errorRecordsTableSchema);

            public abstract WriteSalesforceMessageErrors build();
        }
    }

    /**
     * The {@link FailedMessageToTableRowFn} converts Salesforce message which have failed processing into
     * {@link TableRow} objects which can be output to a dead-letter table.
     */
    public static class FailedMessageToTableRowFn
            extends DoFn<FailsafeElement<String, String>, TableRow> {

        /**
         * The formatter used to convert timestamps into a BigQuery compatible <a
         * href="https://cloud.google.com/bigquery/docs/reference/standard-sql/data-types#timestamp-type">format</a>.
         */
        private static final DateTimeFormatter TIMESTAMP_FORMATTER =
                DateTimeFormat.forPattern("yyyy-MM-dd HH:mm:ss.SSSSSS");

        @ProcessElement
        public void processElement(ProcessContext context) {
            FailsafeElement<String, String> failsafeElement = context.element();
            String message = failsafeElement.getOriginalPayload();

            // Format the timestamp for insertion
            String timestamp =
                    TIMESTAMP_FORMATTER.print(context.timestamp().toDateTime(DateTimeZone.UTC));

            String payloadString =
                    "message: " + (message == null ? "" : message);

            byte[] payloadBytes =
                    (message == null
                            ? "".getBytes(StandardCharsets.UTF_8)
                            : message.getBytes(StandardCharsets.UTF_8));

            // Build the table row
            TableRow failedRow =
                    new TableRow()
                            .set("timestamp", timestamp)
                            .set("errorMessage", failsafeElement.getErrorMessage())
                            .set("stacktrace", failsafeElement.getStacktrace())
                            .set("payloadString", payloadString)
                            .set("payloadBytes", payloadBytes);

            context.output(failedRow);
        }
    }
}
