/*
 * Copyright (C) 2025 The Android Open Source Project
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

package com.android.tools.metalava.model.testsuite.sourcefile

import com.android.tools.metalava.model.JavaImport
import com.android.tools.metalava.model.provider.Capability
import com.android.tools.metalava.model.testing.RequiresCapabilities
import com.android.tools.metalava.model.testsuite.BaseModelTest
import com.android.tools.metalava.model.testsuite.value.ValueExample
import com.android.tools.metalava.testing.EntryPoint
import com.android.tools.metalava.testing.EntryPointCallerRule
import com.android.tools.metalava.testing.EntryPointCallerTracker
import com.android.tools.metalava.testing.java
import kotlin.test.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runners.Parameterized

class CommonParameterizedImportTest : BaseModelTest() {

    @Parameterized.Parameter(0) lateinit var params: TestParams

    /**
     * Will try and rewrite the stack trace of any test failures to refer to the location where the
     * [ValueExample] that is currently being tested was created.
     */
    @get:Rule val entryPointCallerRule = EntryPointCallerRule { params.entryPointCallerTracker }

    data class TestParams
    @EntryPoint
    constructor(
        val name: String,
        val imports: String = "import $name;\n",
        val expectedJavaImports: List<JavaImport>,
    ) {
        /**
         * Record the stack trace of the creation of this which can be used to provide a stack trace
         * to the creator of this instance in the event of a test failure.
         */
        val entryPointCallerTracker = EntryPointCallerTracker()

        override fun toString(): String {
            return name
        }
    }

    companion object {
        private val params =
            listOf(
                TestParams(
                    name = "implicit String",
                    imports = "",
                    expectedJavaImports = emptyList()
                ),
                TestParams(
                    name = "java.util.Map.Entry",
                    expectedJavaImports =
                        listOf(
                            JavaImport(
                                qualifiedName = "java.util.Map.Entry",
                                onDemand = false,
                                static = false,
                            ),
                        )
                ),
                TestParams(
                    name = "static java.util.Map.Entry",
                    expectedJavaImports =
                        listOf(
                            JavaImport(
                                qualifiedName = "java.util.Map.Entry",
                                onDemand = false,
                                static = true,
                            ),
                        )
                ),
                TestParams(
                    name = "java.util.Map.*",
                    expectedJavaImports =
                        listOf(
                            JavaImport(
                                qualifiedName = "java.util.Map",
                                onDemand = true,
                                static = false,
                            ),
                        )
                ),
                TestParams(
                    name = "static java.util.Map.*",
                    expectedJavaImports =
                        listOf(
                            JavaImport(
                                qualifiedName = "java.util.Map",
                                onDemand = true,
                                static = true,
                            ),
                        )
                ),
                TestParams(
                    name = "java.io.*",
                    expectedJavaImports =
                        listOf(
                            JavaImport(
                                qualifiedName = "java.io",
                                onDemand = true,
                                static = false,
                            ),
                        )
                ),
                TestParams(
                    name = "static java.lang.Character.*",
                    expectedJavaImports =
                        listOf(
                            JavaImport(
                                qualifiedName = "java.lang.Character",
                                onDemand = true,
                                static = true,
                            ),
                        )
                ),
                TestParams(
                    name = "static java.lang.System.err",
                    expectedJavaImports =
                        listOf(
                            JavaImport(
                                qualifiedName = "java.lang.System.err",
                                onDemand = false,
                                static = true,
                            ),
                        )
                ),
            )

        @JvmStatic @Parameterized.Parameters fun params() = params
    }

    @RequiresCapabilities(Capability.JAVA)
    @Test
    fun `Test imports`() {
        runSourceCodebaseTest(
            java(
                """
                    package test.pkg;
                    ${params.imports.trimIndent()}
                    public class Test {}
                """
            ),
        ) {
            val classItem = codebase.assertClass("test.pkg.Test")
            val sourceFile = classItem.sourceFile()!!

            var allJavaImports = sourceFile.allJavaImports()
            assertEquals(params.expectedJavaImports, allJavaImports, message = "allJavaImports")
        }
    }
}
