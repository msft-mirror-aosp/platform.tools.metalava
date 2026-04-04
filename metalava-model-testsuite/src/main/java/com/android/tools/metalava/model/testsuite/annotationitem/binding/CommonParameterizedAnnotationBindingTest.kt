/*
 * Copyright (C) 2026 The Android Open Source Project
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

package com.android.tools.metalava.model.testsuite.annotationitem.binding

import com.android.tools.lint.checks.infrastructure.TestFile
import com.android.tools.metalava.model.AnnotationItem
import com.android.tools.metalava.model.Codebase
import com.android.tools.metalava.model.annotation.binding.bindTo
import com.android.tools.metalava.model.testsuite.BaseModelTest
import com.android.tools.metalava.testing.EntryPoint
import com.android.tools.metalava.testing.EntryPointCallerRule
import com.android.tools.metalava.testing.EntryPointCallerTracker
import com.android.tools.metalava.testing.java
import kotlin.reflect.KClass
import kotlin.test.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runners.Parameterized

class CommonParameterizedAnnotationBindingTest : BaseModelTest() {

    @Parameterized.Parameter(0) internal lateinit var params: TestParams<*>

    /**
     * Will try and rewrite the stack trace of any test failures to refer to the location where the
     * [TestParams] that is currently being tested was created.
     */
    @get:Rule val entryPointCallerRule = EntryPointCallerRule { params.entryPointCallerTracker }

    data class BoundProviderContext(
        val codebase: Codebase,
    )

    data class TestParams<T : Any>
    @EntryPoint
    constructor(
        val name: String,
        val sourceFiles: List<TestFile>,
        val annotation: String,
        val kClass: KClass<T>,
        val expectedIssues: String = "",
        val expectedBoundProvider: BoundProviderContext.() -> T?,
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

    /**
     * Binding for any annotation that shows that a binding does not have to process all attributes.
     *
     * Only implements [equals] and [hashCode] for testing purposes, this not required for use with
     * [AnnotationItem.bindTo].
     */
    class Empty {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false
            return true
        }

        override fun hashCode() = javaClass.hashCode()
    }

    /** Class that cannot be instantiated, used for testing error handling. */
    class InvalidClass(val invalid: Runnable)

    /** Binding for [requiredBooleanAnnotation]. */
    data class BooleanAnno(
        val value: Boolean,
    )

    /** Binding for [requiredIntAnnotation]. */
    data class IntAnno(
        val value: Int,
    )

    /** Binding for [requiredIntAnnotation]. */
    data class IntAnnoWithDefault(
        val value: Int = 9,
    )

    /** Binding for [requiredStringAnnotation]. */
    data class StringAnno(
        val value: String,
    )

    companion object {
        /** See [BooleanAnno]. */
        private val requiredBooleanAnnotation =
            java(
                """
                    package test.pkg;
                    public @interface BooleanAnno {
                        boolean value();
                    }
                """
            )

        /** See [IntAnno]. */
        private val requiredIntAnnotation =
            java(
                """
                    package test.pkg;
                    public @interface IntAnno {
                        int value();
                    }
                """
            )

        /** See [IntAnno]. */
        private val optionalIntAnnotation =
            java(
                """
                    package test.pkg;
                    public @interface IntAnno {
                        int value() default 17;
                    }
                """
            )

        /** See [StringAnno]. */
        private val requiredStringAnnotation =
            java(
                """
                    package test.pkg;
                    public @interface StringAnno {
                        String value();
                    }
                """
            )

        @EntryPoint
        inline fun <reified T : Any> testParams(
            name: String,
            sourceFiles: List<TestFile>,
            annotation: String,
            expectedBound: T?,
            expectedIssues: String = "",
        ) =
            TestParams(
                name,
                sourceFiles,
                annotation,
                kClass = T::class,
                expectedIssues,
            ) {
                expectedBound
            }

        private val params =
            listOf(
                // No parameters test
                testParams(
                    name = "no parameters class",
                    sourceFiles = listOf(requiredBooleanAnnotation),
                    annotation = "@BooleanAnno(value = true)",
                    expectedBound = Empty(),
                ),

                // Invalid parameters test
                testParams<InvalidClass>(
                    name = "invalid parameters",
                    sourceFiles = listOf(requiredBooleanAnnotation),
                    annotation = "@BooleanAnno(value = true)",
                    expectedBound = null,
                    expectedIssues =
                        "MAIN_SRC/src/test/pkg/Test.java:2: error: internal error: could not bind com.android.tools.metalava.model.testsuite.annotationitem.binding.CommonParameterizedAnnotationBindingTest.InvalidClass to test.pkg.BooleanAnno: No argument provided for a required parameter: parameter #0 invalid of fun `<init>`(java.lang.Runnable): com.android.tools.metalava.model.testsuite.annotationitem.binding.CommonParameterizedAnnotationBindingTest.InvalidClass [InvalidAnnotationBinding]",
                ),

                // Invalid value test.
                testParams<StringAnno>(
                    name = "invalid value",
                    sourceFiles = listOf(requiredBooleanAnnotation),
                    annotation = "@BooleanAnno(value = false)",
                    expectedBound = null,
                    expectedIssues =
                        "MAIN_SRC/src/test/pkg/Test.java:2: error: Attribute 'value' is invalid: `false` cannot be converted to kotlin.String [InvalidAnnotationBinding]",
                ),

                // Single value types tests.
                testParams(
                    name = "boolean",
                    sourceFiles = listOf(requiredBooleanAnnotation),
                    annotation = "@BooleanAnno(value = false)",
                    expectedBound = BooleanAnno(value = false),
                ),
                testParams(
                    name = "int",
                    sourceFiles = listOf(requiredIntAnnotation),
                    annotation = "@IntAnno(value = 12)",
                    expectedBound = IntAnno(value = 12),
                ),
                testParams(
                    name = "String",
                    sourceFiles = listOf(requiredStringAnnotation),
                    annotation = """@StringAnno(value = "a")""",
                    expectedBound = StringAnno(value = "a"),
                ),

                // Annotations/classes with defaults tests
                testParams(
                    name = "int annotation with default",
                    sourceFiles = listOf(optionalIntAnnotation),
                    annotation = "@IntAnno()",
                    // Uses the default from the annotation definition.
                    expectedBound = IntAnno(value = 17),
                ),
                testParams(
                    name = "int annotation and class with defaults",
                    sourceFiles = listOf(optionalIntAnnotation),
                    annotation = "@IntAnno()",
                    // Uses the default from the annotation definition.
                    expectedBound = IntAnnoWithDefault(value = 17),
                ),
            )

        @JvmStatic @Parameterized.Parameters fun params() = params
    }

    @Test
    fun testBinding() {
        val sources = buildList {
            addAll(params.sourceFiles)
            add(
                java(
                    """
                        package test.pkg;
                        public class Test {
                        }
                    """
                )
            )
        }
        runCodebaseTest(
            inputSet(sources),
        ) {
            val testClass = codebase.assertClass("test.pkg.Test")
            val annotationSource = params.annotation.replace("@", "@test.pkg.")

            // Metalava generally assumes that the sources that it processes can be compiled.
            // However, that does not prevent Metalava from processing invalid files. e.g. Metalava
            // is used to break dependency cycles  which mean that it can be given files before they
            // are compiled so they could contain invalid content. In those cases Metalava must not
            // abort processing and instead should report as many issues as it can.
            //
            // This creates annotations from sources directly rather than add them into the source
            // so that it can test some of those invalid but common mistakes, e.g. missing required
            // attributes.
            val annotation =
                AnnotationItem.createFromSource(codebase, annotationSource)
                    ?: error("could not create annotation from $annotationSource")
            val bound = annotation.bindTo(params.kClass, testClass)

            // Check expected issues first.
            assertAndRemoveReportedIssues(params.expectedIssues)

            val expectedBoundProvider = params.expectedBoundProvider
            val context = BoundProviderContext(codebase)
            val expectedBound = context.expectedBoundProvider()
            assertEquals(expectedBound, bound)
        }
    }
}
