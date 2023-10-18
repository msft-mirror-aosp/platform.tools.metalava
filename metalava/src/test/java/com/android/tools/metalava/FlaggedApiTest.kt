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
import java.util.Locale
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

private val annotationsList = listOf(systemApiSource, flaggedApiSource)

@RunWith(Parameterized::class)
class FlaggedApiTest(private val config: Configuration) : DriverTest() {

    /** The configuration of the test. */
    data class Configuration(
        val surface: Surface,
        val flagged: Flagged,
    ) {
        val extraArguments = surface.args + flagged.args

        override fun toString(): String {
            val surfaceText = surface.name.lowercase(Locale.US)
            val prepositionText = flagged.name.lowercase(Locale.US)
            return "$surfaceText $prepositionText flagged api"
        }
    }

    /** The surfaces that this test will check. */
    enum class Surface(val args: List<String>) {
        PUBLIC(emptyList()),
        SYSTEM(listOf(ARG_SHOW_ANNOTATION, ANDROID_SYSTEM_API)),
    }

    /** The different configurations of the flagged API that this test will check. */
    enum class Flagged(val args: List<String>) {
        WITH(emptyList()),
        WITHOUT(listOf(ARG_HIDE_ANNOTATION, ANDROID_FLAGGED_API))
    }

    companion object {
        /** Compute the cross product of [Surface] and [Flagged]. */
        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun configurations(): Iterable<Configuration> =
            Surface.values().flatMap { surface ->
                Flagged.values().map { flagged ->
                    Configuration(
                        surface = surface,
                        flagged = flagged,
                    )
                }
            }
    }

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
        val expectedApi =
            when (config.surface) {
                Surface.PUBLIC ->
                    when (config.flagged) {
                        Flagged.WITH -> expectedPublicApi
                        Flagged.WITHOUT -> expectedPublicApiMinusFlaggedApi
                    }
                Surface.SYSTEM ->
                    when (config.flagged) {
                        Flagged.WITH -> expectedSystemApi
                        Flagged.WITHOUT -> expectedSystemApiMinusFlaggedApi
                    }
            }

        check(
            format = FileFormat.V2,
            sourceFiles =
                buildList {
                        addAll(sourceFiles)
                        addAll(annotationsList)
                    }
                    .toTypedArray(),
            api = expectedApi,
            extraArguments =
                arrayOf(ARG_HIDE_PACKAGE, "android.annotation") + config.extraArguments,
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
