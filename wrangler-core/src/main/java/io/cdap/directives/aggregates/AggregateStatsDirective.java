/*
 *  Copyright © 2017-2019 Cask Data, Inc.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License"); you may not
 *  use this file except in compliance with the License. You may obtain a copy of
 *  the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 *  WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 *  License for the specific language governing permissions and limitations under
 *  the License.
 */
package io.cdap.directives.aggregates;

import io.cdap.wrangler.api.Arguments;
import io.cdap.wrangler.api.Directive;
import io.cdap.wrangler.api.DirectiveExecutionException;
import io.cdap.wrangler.api.DirectiveParseException;
import io.cdap.wrangler.api.ExecutorContext;
import io.cdap.wrangler.api.Row;
import io.cdap.wrangler.api.parser.ColumnName;
import io.cdap.wrangler.api.parser.TokenType;
import io.cdap.wrangler.api.parser.UsageDefinition;

import java.util.ArrayList;
import java.util.List;

/**
 * A directive that computes aggregate statistics (sum, avg, min, max, count) on a numeric column.
 */
public class AggregateStatsDirective implements Directive {
    public static final String NAME = "aggregate-stats";
    private String column;
    private Double sum;
    private Double min;
    private Double max;
    private long count;
    private List<Row> results;

    @Override
    public UsageDefinition define() {
        UsageDefinition.Builder builder = UsageDefinition.builder(NAME);
        builder.define("column", TokenType.COLUMN_NAME);
        return builder.build();
    }

    @Override
    public void initialize(Arguments args) throws DirectiveParseException {
        this.column = ((ColumnName) args.value("column")).value();
        this.sum = 0.0;
        this.min = null;
        this.max = null;
        this.count = 0;
        this.results = new ArrayList<>();
    }

    @Override
    public List<Row> execute(List<Row> rows, ExecutorContext context) throws DirectiveExecutionException {
        // Process all rows to compute aggregates
        for (Row row : rows) {
            Object value = row.getValue(column);
            if (value instanceof Number) {
                double num = ((Number) value).doubleValue();
                sum += num;
                count++;
                if (min == null || num < min) {
                    min = num;
                }
                if (max == null || num > max) {
                    max = num;
                }
            }
        }

        // Create result rows
        if (count > 0) {
            results.add(new Row("sum_" + column, sum));
            results.add(new Row("avg_" + column, sum / count));
            results.add(new Row("min_" + column, min));
            results.add(new Row("max_" + column, max));
            results.add(new Row("count_" + column, count));
        }
        return results;
    }

    @Override
    public void destroy() {
        // No-op
    }
}
