package com.akvelon.salesforce.transforms;

import com.akvelon.salesforce.utils.FailsafeRecord;
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

/** {@link PTransform}s and {@link DoFn}s for handling BigQuery errors. */
public class BigQueryErrorTransform {

    /** Writes strings error messages. */
    @AutoValue
    public abstract static class WriteStringMessageErrors
            extends PTransform<PCollection<FailsafeRecord<String, String>>, WriteResult> {

        public static WriteStringMessageErrors.Builder newBuilder() {
            return new AutoValue_BigQueryErrorTransform_WriteStringMessageErrors.Builder();
        }

        public abstract String getErrorRecordsTable();

        public abstract String getErrorRecordsTableSchema();

        @Override
        public WriteResult expand(PCollection<FailsafeRecord<String, String>> failedRecords) {

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
            public abstract WriteStringMessageErrors.Builder setErrorRecordsTable(String errorRecordsTable);

            public abstract WriteStringMessageErrors.Builder setErrorRecordsTableSchema(String errorRecordsTableSchema);

            public abstract WriteStringMessageErrors build();
        }
    }

    /**
     * The {@link FailedStringToTableRowFn} converts string objects which have failed processing into
     * {@link TableRow} objects which can be output to a BigQuery dead-letter table.
     */
    public static class FailedStringToTableRowFn
            extends DoFn<FailsafeRecord<String, String>, TableRow> {

        /**
         * The formatter used to convert timestamps into a BigQuery compatible <a
         * href="https://cloud.google.com/bigquery/docs/reference/standard-sql/data-types#timestamp-type">format</a>.
         */
        private static final DateTimeFormatter TIMESTAMP_FORMATTER =
                DateTimeFormat.forPattern("yyyy-MM-dd HH:mm:ss.SSSSSS");

        @ProcessElement
        public void processElement(ProcessContext context) {
            FailsafeRecord<String, String> failsafeRecord = context.element();
            final String message = failsafeRecord.getOriginalPayload();

            // Format the timestamp for insertion
            String timestamp =
                    TIMESTAMP_FORMATTER.print(context.timestamp().toDateTime(DateTimeZone.UTC));

            // Build the table row
            final TableRow failedRow =
                    new TableRow()
                            .set("timestamp", timestamp)
                            .set("errorMessage", failsafeRecord.getErrorMessage())
                            .set("stacktrace", failsafeRecord.getStacktrace());

            // Only set the payload if it's populated on the message.
            if (message != null) {
                failedRow
                        .set("payloadString", message)
                        .set("payloadBytes", message.getBytes(StandardCharsets.UTF_8));
            }

            context.output(failedRow);
        }
    }
}
