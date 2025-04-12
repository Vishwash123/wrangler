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
package io.cdap.wrangler.api.parser;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.cdap.wrangler.api.annotations.PublicEvolving;

import java.io.Serializable;

/**
 * A token representing a byte size (e.g., "10KB", "1.5MB") parsed from a recipe.
 */
@PublicEvolving
public class ByteSize implements Token, Serializable {
    private final String value;
    private final long bytes;

    public ByteSize(String value) {
        this.value = value;
        this.bytes = parseBytes(value);
    }

    @Override
    public Object value() {
        return value;
    }

    @Override
    public TokenType type() {
        return TokenType.BYTE_SIZE;
    }

    @Override
    public JsonElement toJson() {
        JsonObject json = new JsonObject();
        json.addProperty("type", type().name());
        json.addProperty("value", value);
        json.addProperty("bytes", bytes);
        return json;
    }

    public long getBytes() {
        return bytes;
    }

    private long parseBytes(String input) {
        if (input == null || input.trim().isEmpty()) {
            throw new IllegalArgumentException("Byte size cannot be empty");
        }
        String num = input.replaceAll("[^0-9.]", "");
        String unit = input.replaceAll("[0-9.]", "").toLowerCase();
        if (num.isEmpty()) {
            throw new IllegalArgumentException("Invalid byte size format: " + input);
        }
        try {
            double value = Double.parseDouble(num);
            if (value < 0) {
                throw new IllegalArgumentException("Byte size cannot be negative: " + input);
            }
            switch (unit) {
                case "b": return (long) value;
                case "kb": return (long) (value * 1024);
                case "mb": return (long) (value * 1024 * 1024);
                case "gb": return (long) (value * 1024 * 1024 * 1024);
                case "tb": return (long) (value * 1024L * 1024 * 1024 * 1024);
                case "pb": return (long) (value * 1024L * 1024 * 1024 * 1024 * 1024);
                default: throw new IllegalArgumentException("Invalid byte unit: " + unit);
            }
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid number format in byte size: " + input, e);
        }
    }
}
