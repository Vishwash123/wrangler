package io.cdap.directives.aggregates;

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
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

import io.cdap.wrangler.TestingRig;
import io.cdap.wrangler.api.Row;

import org.junit.Assert;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;

/**
 * Unit tests for AggregateStatsDirective.
 */
public class AggregateStatsDirectiveTest {

    @Test
    public void testAggregateStats() throws Exception {
        String[] recipe = {
                "aggregate-stats :data_transfer_size :response_time total_size_mb total_time_sec MB seconds"
        };
        List<Row> rows = Arrays.asList(
                new Row("data_transfer_size", "10KB").add("response_time", "5ms"),
                new Row("data_transfer_size", "1MB").add("response_time", "2s")
        );

        List<Row> results = TestingRig.execute(recipe, rows);

        Assert.assertEquals(1, results.size());

        Object sizeValue = results.get(0).getValue("total_size_mb");
        System.out.println("total_size_mb: " + sizeValue + ", type: " +
                (sizeValue != null ? sizeValue.getClass().getName() : "null"));
        Assert.assertNotNull("total_size_mb should not be null", sizeValue);
        Assert.assertTrue("total_size_mb should be Double", sizeValue instanceof Double);
        Assert.assertEquals(1.010239, (Double) sizeValue, 0.001);

        Object timeValue = results.get(0).getValue("total_time_sec");
        System.out.println("total_time_sec: " + timeValue + ", type: " +
                (timeValue != null ? timeValue.getClass().getName() : "null"));
        Assert.assertNotNull("total_time_sec should not be null", timeValue);
        Assert.assertTrue("total_time_sec should be Double", timeValue instanceof Double);
        Assert.assertEquals(2.005, (Double) timeValue, 0.001);
    }

    @Test
    public void testEmptyInput() throws Exception {
        String[] recipe = {
                "aggregate-stats :data_transfer_size :response_time total_size_mb total_time_sec MB seconds"
        };
        List<Row> rows = Arrays.asList();
        List<Row> results = TestingRig.execute(recipe, rows);

        Assert.assertEquals(1, results.size());

        Object sizeValue = results.get(0).getValue("total_size_mb");
        Assert.assertNotNull("total_size_mb should not be null", sizeValue);
        Assert.assertEquals(0.0, (Double) sizeValue, 0.001);

        Object timeValue = results.get(0).getValue("total_time_sec");
        Assert.assertNotNull("total_time_sec should not be null", timeValue);
        Assert.assertEquals(0.0, (Double) timeValue, 0.001);
    }

    @Test
    public void testInvalidInput() throws Exception {
        String[] recipe = {
                "aggregate-stats :data_transfer_size :response_time total_size_mb total_time_sec MB seconds"
        };
        List<Row> rows = Arrays.asList(
                new Row("data_transfer_size", "10XB").add("response_time", "5hr"),
                new Row("data_transfer_size", "1MB").add("response_time", "2s")
        );

        List<Row> results = TestingRig.execute(recipe, rows);

        Assert.assertEquals(1, results.size());

        Object sizeValue = results.get(0).getValue("total_size_mb");
        Assert.assertNotNull("total_size_mb should not be null", sizeValue);
        Assert.assertEquals(1.0, (Double) sizeValue, 0.001);

        Object timeValue = results.get(0).getValue("total_time_sec");
        Assert.assertNotNull("total_time_sec should not be null", timeValue);
        Assert.assertEquals(2.0, (Double) timeValue, 0.001);
    }
}
