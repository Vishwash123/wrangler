/*
 * Copyright © 2017-2019 Cask Data, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package io.cdap.directives.aggregates;

import io.cdap.wrangler.api.Arguments;
import io.cdap.wrangler.api.Directive;
import io.cdap.wrangler.api.DirectiveExecutionException;
import io.cdap.wrangler.api.DirectiveParseException;
import io.cdap.wrangler.api.ExecutorContext;
import io.cdap.wrangler.api.Row;
import io.cdap.wrangler.api.TransientStore;
import io.cdap.wrangler.api.TransientVariableScope;
import io.cdap.wrangler.api.parser.ColumnName;
import io.cdap.wrangler.api.parser.Text;
import io.cdap.wrangler.api.parser.TokenType;
import io.cdap.wrangler.api.parser.UsageDefinition;

import java.util.ArrayList;
import java.util.List;

/**
 * A directive that aggregates byte sizes and time durations into total size and time,
 * with optional unit conversions (e.g., MB, seconds).
 *
 * Usage: aggregate-stats :source_size :source_time :target_size :target_time [size_unit] [time_unit]
 * Example: aggregate-stats :data_transfer_size :response_time total_size_mb total_time_sec MB seconds
 */
public class AggregateStatsDirective implements Directive {
    public static final String NAME = "aggregate-stats";
    private String sourceSizeColumn;
    private String sourceTimeColumn;
    private String targetSizeColumn;
    private String targetTimeColumn;
    private String sizeUnit = "MB"; // Default to MB
    private String timeUnit = "seconds"; // Default to seconds
    private static final String SIZE_KEY = "total_bytes";
    private static final String TIME_KEY = "total_nanos";
    private static final String COUNT_KEY = "row_count";

    @Override
    public UsageDefinition define() {
        UsageDefinition.Builder builder = UsageDefinition.builder(NAME);
        builder.define("source-size", TokenType.COLUMN_NAME);
        builder.define("source-time", TokenType.COLUMN_NAME);
        builder.define("target-size", TokenType.COLUMN_NAME);
        builder.define("target-time", TokenType.COLUMN_NAME);
        builder.define("size-unit", TokenType.TEXT, true); // Optional
        builder.define("time-unit", TokenType.TEXT, true); // Optional
        return builder.build();
    }

    @Override
    public void initialize(Arguments args) throws DirectiveParseException {
        this.sourceSizeColumn = ((ColumnName) args.value("source-size")).value();
        this.sourceTimeColumn = ((ColumnName) args.value("source-time")).value();
        this.targetSizeColumn = ((ColumnName) args.value("target-size")).value();
        this.targetTimeColumn = ((ColumnName) args.value("target-time")).value();
        if (args.contains("size-unit")) {
            this.sizeUnit = ((Text) args.value("size-unit")).value().toUpperCase();
        }
        if (args.contains("time-unit")) {
            this.timeUnit = ((Text) args.value("time-unit")).value().toLowerCase();
        }
    }

    @Override
    public List<Row> execute(List<Row> rows, ExecutorContext context) throws DirectiveExecutionException {
        TransientStore store = context.getTransientStore();
        Long totalBytes = (Long) store.get(SIZE_KEY);
        Long totalNanos = (Long) store.get(TIME_KEY);
        Long rowCount = (Long) store.get(COUNT_KEY);
        totalBytes = totalBytes != null ? totalBytes : 0L;
        totalNanos = totalNanos != null ? totalNanos : 0L;
        rowCount = rowCount != null ? rowCount : 0L;

        // Process each row
        for (Row row : rows) {
            // Aggregate byte sizes
            Object sizeValue = row.getValue(sourceSizeColumn);
            if (sizeValue instanceof Number) {
                totalBytes += ((Number) sizeValue).longValue();
            } else if (sizeValue instanceof String) {
                try {
                    totalBytes += parseByteSize((String) sizeValue);
                } catch (NumberFormatException e) {
                    // Skip invalid sizes
                }
            }

            // Aggregate time durations
            Object timeValue = row.getValue(sourceTimeColumn);
            if (timeValue instanceof Number) {
                totalNanos += ((Number) timeValue).longValue();
            } else if (timeValue instanceof String) {
                try {
                    totalNanos += parseTimeDuration((String) timeValue);
                } catch (NumberFormatException e) {
                    // Skip invalid times
                }
            }

            rowCount++;
        }

        // Update store
        store.set(TransientVariableScope.GLOBAL, SIZE_KEY, totalBytes);
        store.set(TransientVariableScope.GLOBAL, TIME_KEY, totalNanos);
        store.set(TransientVariableScope.GLOBAL, COUNT_KEY, rowCount);

        // Return empty list during processing; final output in finalize
        return new ArrayList<>();
    }

    @Override
    public void destroy() {
        // Handled by finalize or RecipePipeline
    }

    /**
     * Finalizes aggregation by retrieving totals and converting units.
     */
    public List<Row> finalize(ExecutorContext context) throws DirectiveExecutionException {
        TransientStore store = context.getTransientStore();
        Long totalBytes = (Long) store.get(SIZE_KEY);
        Long totalNanos = (Long) store.get(TIME_KEY);
        Long rowCount = (Long) store.get(COUNT_KEY);
        totalBytes = totalBytes != null ? totalBytes : 0L;
        totalNanos = totalNanos != null ? totalNanos : 0L;
        rowCount = rowCount != null ? rowCount : 0L;

        // Convert size to target unit
        double sizeOutput;
        switch (sizeUnit) {
            case "BYTES":
                sizeOutput = totalBytes;
                break;
            case "KB":
                sizeOutput = totalBytes / 1024.0;
                break;
            case "MB":
                sizeOutput = totalBytes / (1024.0 * 1024.0);
                break;
            case "GB":
                sizeOutput = totalBytes / (1024.0 * 1024.0 * 1024.0);
                break;
            default:
                throw new DirectiveExecutionException("Unsupported size unit: " + sizeUnit);
        }

        // Convert time to target unit
        double timeOutput;
        switch (timeUnit) {
            case "nanoseconds":
                timeOutput = totalNanos;
                break;
            case "milliseconds":
                timeOutput = totalNanos / 1_000_000.0;
                break;
            case "seconds":
                timeOutput = totalNanos / 1_000_000_000.0;
                break;
            case "minutes":
                timeOutput = totalNanos / (60.0 * 1_000_000_000.0);
                break;
            default:
                throw new DirectiveExecutionException("Unsupported time unit: " + timeUnit);
        }

        // Create output row
        List<Row> results = new ArrayList<>();
        Row result = new Row();
        result.add(targetSizeColumn, sizeOutput);
        result.add(targetTimeColumn, timeOutput);
        results.add(result);

        // Reset store
        store.reset(TransientVariableScope.GLOBAL);
        return results;
    }

    /**
     * Parses byte size strings (e.g., "10KB", "1.5MB") to bytes.
     */
    private long parseByteSize(String value) {
        String[] parts = value.split("(?i)(?<=\\d)(?=\\p{Alpha})");
        if (parts.length != 2) {
            throw new NumberFormatException("Invalid byte size: " + value);
        }
        double number = Double.parseDouble(parts[0]);
        String unit = parts[1].toUpperCase();
        switch (unit) {
            case "B":
            case "BYTES":
                return (long) number;
            case "KB":
                return (long) (number * 1024);
            case "MB":
                return (long) (number * 1024 * 1024);
            case "GB":
                return (long) (number * 1024 * 1024 * 1024);
            default:
                throw new NumberFormatException("Unknown unit: " + unit);
        }
    }

    /**
     * Parses time duration strings (e.g., "5ms", "2.1s") to nanoseconds.
     */
    private long parseTimeDuration(String value) {
        String[] parts = value.split("(?i)(?<=\\d)(?=\\p{Alpha})");
        if (parts.length != 2) {
            throw new NumberFormatException("Invalid time duration: " + value);
        }
        double number = Double.parseDouble(parts[0]);
        String unit = parts[1].toLowerCase();
        switch (unit) {
            case "ns":
            case "nanoseconds":
                return (long) number;
            case "ms":
            case "milliseconds":
                return (long) (number * 1_000_000);
            case "s":
            case "seconds":
                return (long) (number * 1_000_000_000);
            case "m":
            case "minutes":
                return (long) (number * 60 * 1_000_000_000);
            default:
                throw new NumberFormatException("Unknown unit: " + unit);
        }
    }
}
