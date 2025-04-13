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
package io.cdap.wrangler.api;

import io.cdap.wrangler.api.parser.ByteSize;

import org.junit.Assert;
import org.junit.Test;



/**
 * Unit tests for ByteSize parsing.
 */
public class ByteSizeTest {
    @Test
    public void testParseByteSize() {
        ByteSize size = new ByteSize("10KB");
        Assert.assertEquals(10240L, size.getBytes());

        size = new ByteSize("1.5MB");
        Assert.assertEquals(1572864L, size.getBytes());

        size = new ByteSize("2GB");
        Assert.assertEquals(2147483648L, size.getBytes());

        size = new ByteSize("100B");
        Assert.assertEquals(100L, size.getBytes());

        size = new ByteSize("1TB");
        Assert.assertEquals(1099511627776L, size.getBytes());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testInvalidByteSize() {
        new ByteSize("10XB");
    }

    @Test(expected = IllegalArgumentException.class)
    public void testEmptyByteSize() {
        new ByteSize("");
    }

    @Test(expected = IllegalArgumentException.class)
    public void testNegativeByteSize() {
        new ByteSize("-5KB");
    }
}
