/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.tools.metalava

import com.android.tools.lint.checks.infrastructure.TestFile
import com.android.tools.metalava.model.text.FileFormat
import com.android.tools.metalava.testing.java
import org.junit.Test

private val annotationsList = listOf(systemApiSource, flaggedApiSource)

class FlaggedApiTest : DriverTest() {
    /**
     * Check the result of generating APIs with and without flagged apis for both public and system
     * API surfaces.
     */
    private fun checkFlaggedApis(
        vararg sourceFiles: TestFile,
        expectedPublicApi: String,
        expectedPublicApiMinusFlaggedApi: String,
        expectedSystemApi: String,
        expectedSystemApiMinusFlaggedApi: String,
    ) {
        fun checkFlaggedApi(api: String, extraArguments: Array<String>) {
            check(
                format = FileFormat.V2,
                sourceFiles =
                    buildList {
                            addAll(sourceFiles)
                            addAll(annotationsList)
                        }
                        .toTypedArray(),
                api = api,
                extraArguments = arrayOf(ARG_HIDE_PACKAGE, "android.annotation") + extraArguments
            )
        }

        // public api scope, including flagged APIs
        checkFlaggedApi(api = expectedPublicApi, extraArguments = arrayOf())

        // public api scope, excluding flagged APIs
        checkFlaggedApi(
            api = expectedPublicApiMinusFlaggedApi,
            extraArguments = arrayOf(ARG_HIDE_ANNOTATION, "android.annotation.FlaggedApi")
        )

        // system api scope, including flagged APIs
        checkFlaggedApi(
            api = expectedSystemApi,
            extraArguments = arrayOf(ARG_SHOW_ANNOTATION, "android.annotation.SystemApi")
        )

        // system api scope, excluding flagged APIs
        checkFlaggedApi(
            api = expectedSystemApiMinusFlaggedApi,
            extraArguments =
                arrayOf(
                    ARG_SHOW_ANNOTATION,
                    "android.annotation.SystemApi",
                    ARG_HIDE_ANNOTATION,
                    "android.annotation.FlaggedApi"
                )
        )
    }

    @Test
    fun `Basic test that FlaggedApi annotated items can be hidden`() {
        checkFlaggedApis(
            java(
                """
                    package test.pkg;

                    import android.annotation.FlaggedApi;
                    import android.annotation.SystemApi;

                    public class Foo {
                        @FlaggedApi("foo/bar")
                        public void flaggedPublicApi() {}

                        /** @hide */
                        @SystemApi
                        @FlaggedApi("foo/bar")
                        public void flaggedSystemApi() {}
                    }
                """
            ),
            expectedPublicApi =
                """
                    // Signature format: 2.0
                    package test.pkg {
                      public class Foo {
                        ctor public Foo();
                        method @FlaggedApi("foo/bar") public void flaggedPublicApi();
                      }
                    }
                """,
            expectedPublicApiMinusFlaggedApi =
                """
                    // Signature format: 2.0
                    package test.pkg {
                      public class Foo {
                        ctor public Foo();
                      }
                    }
                """,
            expectedSystemApi =
                """
                    // Signature format: 2.0
                    package test.pkg {
                      public class Foo {
                        method @FlaggedApi("foo/bar") public void flaggedSystemApi();
                      }
                    }
                """,
            expectedSystemApiMinusFlaggedApi =
                """
                    // Signature format: 2.0
                """,
        )
    }
}
