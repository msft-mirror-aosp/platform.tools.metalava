/*
 * Copyright (C) 2019 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.tools.metalava.model.text

import org.junit.Assert.*
import org.junit.Test

class SignatureFileParseHeaderTest {
    @Test
    fun `Check format parsing (v1)`() {
        assertSame(
            FileFormat.V1,
            ApiFile.parseHeader(
                "api.txt",
                """
                package test.pkg {
                  public class MyTest {
                    ctor public MyTest();
                  }
                }
                """
                    .trimIndent()
            )
        )
    }

    @Test
    fun `Check format parsing (v2)`() {
        assertSame(
            FileFormat.V2,
            ApiFile.parseHeader(
                "api.txt",
                """
            // Signature format: 2.0
            package libcore.util {
              @java.lang.annotation.Documented @java.lang.annotation.Retention(java.lang.annotation.RetentionPolicy.SOURCE) public @interface NonNull {
                method public abstract int from() default java.lang.Integer.MIN_VALUE;
                method public abstract int to() default java.lang.Integer.MAX_VALUE;
              }
            }
                """
                    .trimIndent()
            )
        )
    }

    @Test
    fun `Check format parsing (v3)`() {
        assertSame(
            FileFormat.V3,
            ApiFile.parseHeader(
                "api.txt",
                """
            // Signature format: 3.0
            package androidx.collection {
              public final class LruCacheKt {
                ctor public LruCacheKt();
              }
            }
                """
                    .trimIndent()
            )
        )
    }

    @Test
    fun `Check format parsing (v2 non-unix newlines)`() {
        assertSame(
            FileFormat.V2,
            ApiFile.parseHeader(
                "api.txt",
                "// Signature format: 2.0\r\n" +
                    "package libcore.util {\\r\n" +
                    "  @java.lang.annotation.Documented @java.lang.annotation.Retention(java.lang.annotation.RetentionPolicy.SOURCE) public @interface NonNull {\r\n" +
                    "    method public abstract int from() default java.lang.Integer.MIN_VALUE;\r\n" +
                    "    method public abstract int to() default java.lang.Integer.MAX_VALUE;\r\n" +
                    "  }\r\n" +
                    "}\r\n"
            )
        )
    }

    @Test
    fun `Check format parsing (invalid)`() {
        assertThrows("Unknown file format of api.txt", ApiParseException::class.java) {
            ApiFile.parseHeader(
                "api.txt",
                """
            blah blah
                """
                    .trimIndent()
            )
        }
    }

    @Test
    fun `Check format parsing (blank)`() {

        assertSame(null, ApiFile.parseHeader("api.txt", ""))
    }
}
