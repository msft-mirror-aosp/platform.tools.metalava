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

import com.android.tools.metalava.model.text.FileFormat
import com.android.tools.metalava.testing.java
import org.junit.Test

class FlaggedApiTest : DriverTest() {
    @Test
    fun `FlaggedApi annotated items can be hidden if requested via command line`() {
        fun checkFlaggedApi(api: String, extraArguments: Array<String>) {
            check(
                format = FileFormat.V2,
                sourceFiles =
                    arrayOf(
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
                        systemApiSource,
                        flaggedApiSource
                    ),
                api = api,
                extraArguments = arrayOf(ARG_HIDE_PACKAGE, "android.annotation") + extraArguments
            )
        }

        // public api scope, including flagged APIs
        checkFlaggedApi(
            api =
                """
                // Signature format: 2.0
                package test.pkg {
                  public class Foo {
                    ctor public Foo();
                    method @FlaggedApi("foo/bar") public void flaggedPublicApi();
                  }
                }
                """,
            extraArguments = arrayOf()
        )

        // public api scope, excluding flagged APIs
        checkFlaggedApi(
            api =
                """
                // Signature format: 2.0
                package test.pkg {
                  public class Foo {
                    ctor public Foo();
                  }
                }
                """,
            extraArguments = arrayOf(ARG_HIDE_ANNOTATION, "android.annotation.FlaggedApi")
        )

        // system api scope, including flagged APIs
        checkFlaggedApi(
            api =
                """
                // Signature format: 2.0
                package test.pkg {
                  public class Foo {
                    method @FlaggedApi("foo/bar") public void flaggedSystemApi();
                  }
                }
                """,
            extraArguments = arrayOf(ARG_SHOW_ANNOTATION, "android.annotation.SystemApi")
        )

        // system api scope, excluding flagged APIs
        checkFlaggedApi(
            api = """
                // Signature format: 2.0
                """,
            extraArguments =
                arrayOf(
                    ARG_SHOW_ANNOTATION,
                    "android.annotation.SystemApi",
                    ARG_HIDE_ANNOTATION,
                    "android.annotation.FlaggedApi"
                )
        )
    }
}
