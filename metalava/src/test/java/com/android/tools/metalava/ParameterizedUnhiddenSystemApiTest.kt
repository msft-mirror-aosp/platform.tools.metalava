/*
 * Copyright (C) 2018 The Android Open Source Project
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
import com.android.tools.metalava.reporter.Issues
import com.android.tools.metalava.testing.EntryPoint
import com.android.tools.metalava.testing.EntryPointCallerRule
import com.android.tools.metalava.testing.EntryPointCallerTracker
import com.android.tools.metalava.testing.java
import org.junit.AssumptionViolatedException
import org.junit.Rule
import org.junit.Test
import org.junit.runners.Parameterized

/** Tests for the [Issues.UNHIDDEN_SYSTEM_API] check. */
class ParameterizedUnhiddenSystemApiTest : DriverTest() {

    @Parameterized.Parameter(0) internal lateinit var params: TestParams

    /**
     * Will try and rewrite the stack trace of any test failures to refer to the location where the
     * [TestParams] that is currently being tested was created.
     */
    @get:Rule val entryPointCallerRule = EntryPointCallerRule { params.entryPointCallerTracker }

    internal data class TestParams
    @EntryPoint
    constructor(
        val apiSurface: KnownApiSurface,
        val expectedIssuesForConfig: String = "",
        val expectedIssuesForOptions: String = "",
        val expectedApiForConfig: String,
        val expectedApiForOptions: String = expectedApiForConfig,
    ) {
        /**
         * Record the stack trace of the creation of this which can be used to provide a stack trace
         * to the creator of this instance in the event of a test failure.
         */
        val entryPointCallerTracker = EntryPointCallerTracker()

        override fun toString(): String {
            return apiSurface.surface
        }
    }

    companion object {
        private val params =
            listOf(
                TestParams(
                    apiSurface = KnownApiSurface.PUBLIC,
                    expectedApiForConfig =
                        """
                            package test.pkg {
                              public class Foo {
                                ctor public Foo();
                                method public void method1();
                              }
                            }
                        """,
                    expectedApiForOptions =
                        """
                            package test.pkg {
                              public class Foo {
                                ctor public Foo();
                                method public void method1();
                                method public void method4();
                              }
                            }
                        """,
                ),
                TestParams(
                    apiSurface = KnownApiSurface.SYSTEM,
                    expectedIssuesForConfig =
                        "src/test/pkg/Foo.java:8: warning: @android.annotation.SystemApi APIs must not be marked @hide: method test.pkg.Foo.method2() (ErrorWhenNew) [HiddenShowAnnotation]",
                    expectedIssuesForOptions =
                        "src/test/pkg/Foo.java:14: error: @android.annotation.SystemApi APIs must also be marked @hide: method test.pkg.Foo.method4() [UnhiddenSystemApi]",
                    expectedApiForConfig =
                        """
                            package test.pkg {
                              public class Foo {
                                method public void method2();
                                method public void method4();
                              }
                            }
                        """,
                ),
                TestParams(
                    apiSurface = KnownApiSurface.MODULE_LIB,
                    expectedIssuesForConfig =
                        "src/test/pkg/Foo.java:8: warning: @android.annotation.SystemApi APIs must not be marked @hide: method test.pkg.Foo.method2() (ErrorWhenNew) [HiddenShowAnnotation]",
                    expectedIssuesForOptions =
                        "src/test/pkg/Foo.java:14: error: @android.annotation.SystemApi APIs must also be marked @hide: method test.pkg.Foo.method4() [UnhiddenSystemApi]",
                    expectedApiForConfig = "",
                ),
            )

        @JvmStatic @Parameterized.Parameters(name = "{0}") internal fun params() = params
    }

    private fun checkUnhiddenSystemApi(
        apiSurface: KnownApiSurface? = null,
        extraSourceFiles: Array<TestFile> = emptyArray(),
        extraArguments: Array<String> = emptyArray(),
        expectedIssues: String = "",
        expectedApi: String,
    ) {
        check(
            apiSurface = apiSurface,
            extraArguments = extraArguments,
            expectedIssues = expectedIssues,
            sourceFiles =
                arrayOf(
                    *extraSourceFiles,
                    java(
                        """
                            package test.pkg;
                            import android.annotation.SystemApi;
                            public class Foo {
                                public void method1() { }

                                /** @hide */
                                @SystemApi
                                public void method2() { }

                                /** @hide Always hidden */
                                public void method3() { }

                                @SystemApi
                                public void method4() { }

                            }
                        """
                    ),
                ),
            expectedApiSignature = expectedApi,
        )
    }

    @Test
    fun `Test api surface configuration`() {
        checkUnhiddenSystemApi(
            apiSurface = params.apiSurface,
            expectedIssues = params.expectedIssuesForConfig,
            expectedApi = params.expectedApiForConfig,
        )
    }

    @Test
    fun `Test api surface options`() {
        val knownApiSurface = params.apiSurface
        val commandLineOptions =
            knownApiSurface.optionalCommandLineOptions
                // Do not run the test if the surface for not provide the necessary options.
                ?: throw AssumptionViolatedException(
                    "${knownApiSurface.surface} does not provide command line options"
                )
        checkUnhiddenSystemApi(
            extraSourceFiles = knownApiSurface.additionalSourceFiles.toTypedArray(),
            extraArguments = commandLineOptions.toTypedArray(),
            expectedIssues = params.expectedIssuesForOptions,
            expectedApi = params.expectedApiForOptions,
        )
    }
}
