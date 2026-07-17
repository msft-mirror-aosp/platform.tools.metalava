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

package com.android.tools.metalava.model.testsuite.surface

import com.android.tools.lint.checks.infrastructure.TestFile
import com.android.tools.metalava.model.AnnotationManager
import com.android.tools.metalava.model.Codebase
import com.android.tools.metalava.model.PackageFilter
import com.android.tools.metalava.model.annotation.DefaultAnnotationManager
import com.android.tools.metalava.model.api.ApiSurfaceRules
import com.android.tools.metalava.model.api.ApiSurfaceSelector
import com.android.tools.metalava.model.api.flags.ApiFlag
import com.android.tools.metalava.model.api.flags.ApiFlagAction.*
import com.android.tools.metalava.model.api.flags.ApiFlags
import com.android.tools.metalava.model.provider.InputFormat
import com.android.tools.metalava.model.testing.SupportedInputFormats
import com.android.tools.metalava.model.testing.surfaces.TestableApiSurfaces.HIDE
import com.android.tools.metalava.model.testing.surfaces.TestableApiSurfaces.UNANNOTATED_API
import com.android.tools.metalava.model.testing.surfaces.TestableApiSurfaces.UNANNOTATED_NON_RECURSIVE_API
import com.android.tools.metalava.model.testing.surfaces.TestableApiSurfaces.annotatedOnlyRules
import com.android.tools.metalava.model.testing.surfaces.TestableApiSurfaces.publicSystemModuleRules
import com.android.tools.metalava.model.testsuite.BaseModelTest
import com.android.tools.metalava.testing.EntryPoint
import com.android.tools.metalava.testing.EntryPointCallerRule
import com.android.tools.metalava.testing.EntryPointCallerTracker
import com.android.tools.metalava.testing.ExitPoint
import com.android.tools.metalava.testing.KnownSourceFiles
import com.android.tools.metalava.testing.java
import kotlin.collections.plus
import org.junit.Rule
import org.junit.Test
import org.junit.runners.Parameterized

@SupportedInputFormats(InputFormat.JAVA)
class CommonParameterizedSelectedApiTest : BaseModelTest() {

    @Parameterized.Parameter(0) internal lateinit var params: TestParams

    /**
     * Will try and rewrite the stack trace of any test failures to refer to the location where the
     * [TestParams] that is currently being tested was created.
     */
    @get:Rule val entryPointCallerRule = EntryPointCallerRule { params.entryPointCallerTracker }

    data class TestParams
    @EntryPoint
    constructor(
        val name: String,
        val surfaceRules: ApiSurfaceRules,
        val sources: List<TestFile>,
        val surface: String,
        val expected: String,
        /** Optional configured [ApiFlags] to use when resolving flagged APIs. */
        val apiFlags: ApiFlags? = null,
        /** Optional previously released codebase sources, used to test API reverting/stability. */
        val previouslyReleasedSources: List<TestFile>? = null,
    ) {
        /**
         * Record the stack trace of the creation of this which can be used to provide a stack trace
         * to the creator of this instance in the event of a test failure.
         */
        val entryPointCallerTracker = EntryPointCallerTracker()

        override fun toString() = name
    }

    companion object {
        private val extraSources =
            listOf(
                KnownSourceFiles.hideAnnotation,
                KnownSourceFiles.flaggedApiSource,
            )

        /**
         * Build [TestParams] and add them to this list.
         *
         * @param name the [TestParams.name].
         * @param surfaceRules the [TestParams.surfaceRules].
         * @param sources the [TestParams.sources].
         * @param apiFlags the [TestParams.apiFlags].
         * @param previouslyReleasedSources the [TestParams.previouslyReleasedSources].
         * @param body lambda that will add tests for specific surfaces using [Builder.surfaceTest]
         *   which creates a [TestParams] using the above plus some surface specific information.
         */
        @EntryPoint
        fun MutableList<TestParams>.buildTests(
            name: String,
            surfaceRules: ApiSurfaceRules,
            sources: List<TestFile>,
            apiFlags: ApiFlags? = null,
            previouslyReleasedSources: List<TestFile>? = null,
            body: Builder.() -> Unit,
        ) {
            val builder =
                Builder(
                    this,
                    name,
                    surfaceRules,
                    sources,
                    apiFlags,
                    previouslyReleasedSources,
                )
            buildSurfaceTests(builder, body)
        }

        /**
         * Invokes [body] on [builder].
         *
         * Separated out as per instructions in [ExitPoint].
         */
        @ExitPoint
        fun buildSurfaceTests(builder: Builder, body: Builder.() -> Unit) {
            builder.body()
        }

        /** Builder of [TestParams]. */
        class Builder(
            private val params: MutableList<TestParams>,
            private val name: String,
            private val surfaceRules: ApiSurfaceRules,
            private val sources: List<TestFile>,
            private val apiFlags: ApiFlags? = null,
            private val previouslyReleasedSources: List<TestFile>? = null,
        ) {
            /**
             * Create a test for [surface] that expects [expected] to be the result of calling
             * [Codebase.assertSelectedApiVariants].
             */
            @EntryPoint
            fun surfaceTest(surface: String, expected: String) {
                params.add(
                    TestParams(
                        "$name/$surface",
                        surfaceRules,
                        sources + extraSources,
                        surface,
                        expected,
                        apiFlags,
                        previouslyReleasedSources,
                    )
                )
            }
        }

        @JvmStatic
        @Parameterized.Parameters
        fun params() = buildList {
            buildTests(
                name = "basic class",
                surfaceRules = publicSystemModuleRules,
                sources =
                    listOf(
                        java(
                            """
                                package test.pkg;
                                public interface Test {
                                }
                            """
                        ),
                    ),
            ) {
                surfaceTest(
                    surface = "public",
                    expected =
                        """
                            package test.pkg
                                   self - ApiVariantSet[public(C)]
                              class test.pkg.Test
                                     self - ApiVariantSet[public(C)]
                        """,
                )
            }

            buildTests(
                name = "@hide doctag class",
                surfaceRules = publicSystemModuleRules,
                sources =
                    listOf(
                        java(
                            """
                                package test;
                                /** @hide */
                                public interface Hidden {
                                }
                            """
                        ),
                    ),
            ) {
                surfaceTest(
                    surface = "public",
                    expected =
                        """
                            package test
                                   self - ApiVariantSet[]
                              class test.Hidden
                                     self - ApiVariantSet[]
                        """,
                )
            }

            buildTests(
                name = "@hide annotation class",
                surfaceRules = publicSystemModuleRules,
                sources =
                    listOf(
                        java(
                            """
                                package test;
                                $HIDE
                                public interface Hidden {
                                }
                            """
                        ),
                    ),
            ) {
                surfaceTest(
                    surface = "public",
                    expected =
                        """
                            package test
                                   self - ApiVariantSet[]
                              class test.Hidden
                                     self - ApiVariantSet[]
                        """,
                )
            }

            buildTests(
                name = "annotated only",
                surfaceRules = annotatedOnlyRules,
                sources =
                    listOf(
                        java(
                            """
                                package test.pkg;
                            """
                        ),
                        java(
                            """
                                package test.pkg;
                                $UNANNOTATED_API
                                public class Test {
                                    public Test(int a) {}

                                    public void method() {}
                                }
                            """
                        ),
                        java(
                            """
                                package test.pkg;
                                $UNANNOTATED_NON_RECURSIVE_API
                                public class ClassOnly {
                                    public ClassOnly() {}

                                    public void notIncluded() {}
                                }
                            """
                        ),
                        java(
                            """
                                package test.pkg;
                                public interface Unannotated {
                                }
                            """
                        ),
                    ),
            ) {
                surfaceTest(
                    surface = "public",
                    expected =
                        """
                            package test.pkg
                                   self - ApiVariantSet[public(C)]
                              class test.pkg.Test
                                     self - ApiVariantSet[public(C)]
                                constructor test.pkg.Test(int)
                                       self - ApiVariantSet[public(C)]
                                method test.pkg.Test.method()
                                       self - ApiVariantSet[public(C)]
                              class test.pkg.ClassOnly
                                     self - ApiVariantSet[public(C)]
                                constructor test.pkg.ClassOnly()
                                       self - ApiVariantSet[]
                                method test.pkg.ClassOnly.notIncluded()
                                       self - ApiVariantSet[]
                              class test.pkg.Unannotated
                                     self - ApiVariantSet[]
                        """,
                )
            }

            buildTests(
                name = "public class in package private class",
                surfaceRules = publicSystemModuleRules,
                sources =
                    listOf(
                        java(
                            """
                                package test.pkg;
                                class Outer {
                                    public class Inner {
                                        public Inner() {}
                                        public void method() {}
                                    }
                                }
                            """
                        ),
                    ),
            ) {
                surfaceTest(
                    surface = "public",
                    expected =
                        """
                            package test.pkg
                                   self - ApiVariantSet[]
                              class test.pkg.Outer
                                     self - ApiVariantSet[]
                                constructor test.pkg.Outer()
                                       self - ApiVariantSet[]
                                class test.pkg.Outer.Inner
                                       self - ApiVariantSet[]
                                  constructor test.pkg.Outer.Inner()
                                         self - ApiVariantSet[]
                                  method test.pkg.Outer.Inner.method()
                                         self - ApiVariantSet[]
                        """,
                )
            }

            buildTests(
                name = "package private class in public class",
                surfaceRules = publicSystemModuleRules,
                sources =
                    listOf(
                        java(
                            """
                                package test.pkg;
                                public class Outer {
                                    class Inner {
                                        public Inner() {}
                                        public void method() {}
                                    }
                                }
                            """
                        ),
                    ),
            ) {
                surfaceTest(
                    surface = "public",
                    expected =
                        """
                            package test.pkg
                                   self - ApiVariantSet[public(C)]
                              class test.pkg.Outer
                                     self - ApiVariantSet[public(C)]
                                constructor test.pkg.Outer()
                                       self - ApiVariantSet[public(C)]
                                class test.pkg.Outer.Inner
                                       self - ApiVariantSet[]
                                  constructor test.pkg.Outer.Inner()
                                         self - ApiVariantSet[]
                                  method test.pkg.Outer.Inner.method()
                                         self - ApiVariantSet[]
                        """,
                )
            }
            buildTests(
                name = "flagged APIs",
                surfaceRules = publicSystemModuleRules,
                sources =
                    listOf(
                        java(
                            """
                                package test.pkg;
                                import android.annotation.FlaggedApi;
                                public class Outer {
                                    @FlaggedApi("reverted_flag")
                                    public void revertedMethod() {}

                                    @FlaggedApi("removed_flag")
                                    public void removedMethod() {}
                                }
                            """
                        ),
                    ),
                apiFlags =
                    ApiFlags(
                        listOf(
                            ApiFlag("reverted_flag", REVERT),
                            ApiFlag("removed_flag", REVERT),
                        )
                    ),
                previouslyReleasedSources =
                    listOf(
                        java(
                            """
                                package test.pkg;
                                public class Outer {
                                    public void revertedMethod() {}
                                }
                            """
                        )
                    ),
            ) {
                surfaceTest(
                    surface = "public",
                    expected =
                        """
                            package test.pkg
                                   self - ApiVariantSet[public(C)]
                              class test.pkg.Outer
                                     self - ApiVariantSet[public(C)]
                                constructor test.pkg.Outer()
                                       self - ApiVariantSet[public(C)]
                                method test.pkg.Outer.revertedMethod()
                                       self - ApiVariantSet[public(C)]
                                method test.pkg.Outer.removedMethod()
                                       self - ApiVariantSet[]
                        """,
                )
            }

            buildTests(
                name = "record component",
                surfaceRules = annotatedOnlyRules,
                sources =
                    listOf(
                        java(
                            """
                                package test.pkg;
                                $UNANNOTATED_NON_RECURSIVE_API
                                public record MyRecord(int x) {}
                            """
                        ),
                    ),
            ) {
                surfaceTest(
                    surface = "public",
                    // TODO: The record constructor and accessors should be in the same API as the
                    // class.
                    expected =
                        """
                            package test.pkg
                                   self - ApiVariantSet[public(C)]
                              class test.pkg.MyRecord
                                     self - ApiVariantSet[public(C)]
                                constructor test.pkg.MyRecord(int)
                                       self - ApiVariantSet[]
                                method test.pkg.MyRecord.x()
                                       self - ApiVariantSet[]
                        """,
                )
            }
        }
    }

    @Test
    fun `Test selected api variants`() {
        val rules = params.surfaceRules.retargetAt(params.surface)

        fun runSelectedApiTest(annotationManagerFactory: (TestFixture.() -> AnnotationManager)?) {
            runCodebaseTest(
                inputSet(params.sources),
                testFixture =
                    TestFixture(
                        apiPackages = PackageFilter.parse("test.*"),
                        apiSurfaceRules = rules,
                        apiFlags = params.apiFlags,
                        annotationManagerFactory = annotationManagerFactory,
                        javaLanguageLevel = "17",
                    ),
            ) {
                codebase.assertSelectedApiVariants(params.expected)
            }
        }

        val previouslyReleasedSources = params.previouslyReleasedSources

        if (previouslyReleasedSources != null) {
            runCodebaseTest(
                inputSet(previouslyReleasedSources),
            ) {
                val releasedCodebase = codebase
                val annotationManagerFactory: TestFixture.() -> AnnotationManager = {
                    DefaultAnnotationManager(
                        DefaultAnnotationManager.Config(
                            reporter = recordingReporter,
                            apiSurfaceSelector = ApiSurfaceSelector(rules),
                            apiFlags = params.apiFlags,
                            previouslyReleasedCodebaseProvider = { releasedCodebase }
                        )
                    )
                }

                runSelectedApiTest(annotationManagerFactory)
            }
        } else {
            runSelectedApiTest(annotationManagerFactory = null)
        }
    }
}
