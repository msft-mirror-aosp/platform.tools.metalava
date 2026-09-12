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
import com.android.tools.metalava.model.junit4.ParameterFilter
import com.android.tools.metalava.model.provider.Capability
import com.android.tools.metalava.model.provider.InputFormat
import com.android.tools.metalava.model.testing.CodebaseCreatorConfig
import com.android.tools.metalava.model.testing.SupportedInputFormats
import com.android.tools.metalava.model.testing.surfaces.TestableApiSurfaces.DOC_ONLY
import com.android.tools.metalava.model.testing.surfaces.TestableApiSurfaces.HIDE
import com.android.tools.metalava.model.testing.surfaces.TestableApiSurfaces.MODULE_API
import com.android.tools.metalava.model.testing.surfaces.TestableApiSurfaces.PUBLIC_API
import com.android.tools.metalava.model.testing.surfaces.TestableApiSurfaces.REMOVED_FROM_API
import com.android.tools.metalava.model.testing.surfaces.TestableApiSurfaces.STANDALONE_API
import com.android.tools.metalava.model.testing.surfaces.TestableApiSurfaces.SYSTEM_API
import com.android.tools.metalava.model.testing.surfaces.TestableApiSurfaces.UNANNOTATED_API
import com.android.tools.metalava.model.testing.surfaces.TestableApiSurfaces.UNANNOTATED_NON_RECURSIVE_API
import com.android.tools.metalava.model.testing.surfaces.TestableApiSurfaces.annotatedOnlyPublicSystemModuleRules
import com.android.tools.metalava.model.testing.surfaces.TestableApiSurfaces.annotatedOnlyRules
import com.android.tools.metalava.model.testing.surfaces.TestableApiSurfaces.publicStandaloneRules
import com.android.tools.metalava.model.testing.surfaces.TestableApiSurfaces.publicSystemModuleRules
import com.android.tools.metalava.model.testsuite.BaseModelTest
import com.android.tools.metalava.model.testsuite.ModelSuiteRunner
import com.android.tools.metalava.testing.EntryPoint
import com.android.tools.metalava.testing.EntryPointCallerRule
import com.android.tools.metalava.testing.EntryPointCallerTracker
import com.android.tools.metalava.testing.ExitPoint
import com.android.tools.metalava.testing.KnownSourceFiles
import com.android.tools.metalava.testing.java
import kotlin.test.assertEquals
import org.junit.Assume.assumeFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runners.Parameterized

@SupportedInputFormats(InputFormat.JAVA, InputFormat.KOTLIN)
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
        val expectedContainsRevertedItem: Boolean = false,
    ) {
        /** The [InputFormat] of [sources]. */
        val inputFormat: InputFormat by lazy {
            sources
                .asSequence()
                .map { InputFormat.fromFilename(it.targetRelativePath) }
                .reduce { if1, if2 -> if1.combineWith(if2) }
        }

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
         * Filter out any test parameter combinations that are not valid for a specific
         * [InputFormat].
         */
        @JvmStatic
        @ParameterFilter
        fun parameterFilter(
            config: CodebaseCreatorConfig<ModelSuiteRunner>,
            testParams: TestParams,
        ): Boolean {
            val inputFormat = config.inputFormat
            return testParams.inputFormat == inputFormat
        }

        /**
         * Build [TestParams] and add them to this list.
         *
         * @param name the [TestParams.name].
         * @param surfaceRules the [TestParams.surfaceRules].
         * @param sources the [TestParams.sources].
         * @param apiFlags the [TestParams.apiFlags].
         * @param previouslyReleasedSources the [TestParams.previouslyReleasedSources].
         * @param expectedContainsRevertedItem the [TestParams.expectedContainsRevertedItem].
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
            expectedContainsRevertedItem: Boolean = false,
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
                    expectedContainsRevertedItem,
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
            private val expectedContainsRevertedItem: Boolean = false,
        ) {
            /**
             * Create a test for [surface] that expects [expected] to be the result of calling
             * [Codebase.assertSelectedApiVariants].
             */
            @EntryPoint
            fun surfaceTest(
                surface: String,
                expected: String,
                expectedContainsRevertedItem: Boolean = this.expectedContainsRevertedItem,
            ) {
                params.add(
                    TestParams(
                        "$name/$surface",
                        surfaceRules,
                        sources + extraSources,
                        surface,
                        expected,
                        apiFlags,
                        previouslyReleasedSources,
                        expectedContainsRevertedItem,
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
                                content - ApiVariantSet[]
                              class test.pkg.Test
                                     self - ApiVariantSet[public(C)]
                                  content - ApiVariantSet[]
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
                                content - ApiVariantSet[]
                              class test.Hidden
                                     self - ApiVariantSet[]
                                  content - ApiVariantSet[]
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
                                    $PUBLIC_API
                                    void method();
                                }
                            """
                        ),
                    ),
            ) {
                surfaceTest(
                    surface = "public",
                    // TODO(b/512093496): A hidden class cannot contain any non-hidden members.
                    expected =
                        """
                            package test
                                   self - ApiVariantSet[]
                                content - ApiVariantSet[]
                              class test.Hidden
                                     self - ApiVariantSet[]
                                  content - ApiVariantSet[]
                                method test.Hidden.method()
                                       self - ApiVariantSet[]
                                    content - ApiVariantSet[]
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
                                content - ApiVariantSet[]
                              class test.pkg.Test
                                     self - ApiVariantSet[public(C)]
                                  content - ApiVariantSet[]
                                constructor test.pkg.Test(int)
                                       self - ApiVariantSet[public(C)]
                                    content - ApiVariantSet[]
                                method test.pkg.Test.method()
                                       self - ApiVariantSet[public(C)]
                                    content - ApiVariantSet[]
                              class test.pkg.ClassOnly
                                     self - ApiVariantSet[public(C)]
                                  content - ApiVariantSet[]
                                constructor test.pkg.ClassOnly()
                                       self - ApiVariantSet[]
                                    content - ApiVariantSet[]
                                method test.pkg.ClassOnly.notIncluded()
                                       self - ApiVariantSet[]
                                    content - ApiVariantSet[]
                              class test.pkg.Unannotated
                                     self - ApiVariantSet[]
                                  content - ApiVariantSet[]
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
                                content - ApiVariantSet[]
                              class test.pkg.Outer
                                     self - ApiVariantSet[]
                                  content - ApiVariantSet[]
                                constructor test.pkg.Outer()
                                       self - ApiVariantSet[]
                                    content - ApiVariantSet[]
                                class test.pkg.Outer.Inner
                                       self - ApiVariantSet[]
                                    content - ApiVariantSet[]
                                  constructor test.pkg.Outer.Inner()
                                         self - ApiVariantSet[]
                                      content - ApiVariantSet[]
                                  method test.pkg.Outer.Inner.method()
                                         self - ApiVariantSet[]
                                      content - ApiVariantSet[]
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
                                content - ApiVariantSet[]
                              class test.pkg.Outer
                                     self - ApiVariantSet[public(C)]
                                  content - ApiVariantSet[]
                                constructor test.pkg.Outer()
                                       self - ApiVariantSet[public(C)]
                                    content - ApiVariantSet[]
                                class test.pkg.Outer.Inner
                                       self - ApiVariantSet[]
                                    content - ApiVariantSet[]
                                  constructor test.pkg.Outer.Inner()
                                         self - ApiVariantSet[]
                                      content - ApiVariantSet[]
                                  method test.pkg.Outer.Inner.method()
                                         self - ApiVariantSet[]
                                      content - ApiVariantSet[]
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
                expectedContainsRevertedItem = true,
            ) {
                surfaceTest(
                    surface = "public",
                    expected =
                        """
                            package test.pkg
                                   self - ApiVariantSet[public(C)]
                                content - ApiVariantSet[]
                              class test.pkg.Outer
                                     self - ApiVariantSet[public(C)]
                                  content - ApiVariantSet[]
                                constructor test.pkg.Outer()
                                       self - ApiVariantSet[public(C)]
                                    content - ApiVariantSet[]
                                method test.pkg.Outer.revertedMethod()
                                       self - ApiVariantSet[public(C)]
                                    content - ApiVariantSet[]
                                method test.pkg.Outer.removedMethod()
                                       self - ApiVariantSet[]
                                    content - ApiVariantSet[]
                        """,
                )
            }

            buildTests(
                name = "revert item with different variants",
                surfaceRules = publicSystemModuleRules,
                sources =
                    listOf(
                        java(
                            """
                                package test.pkg;
                                import android.annotation.FlaggedApi;
                                public class Outer {
                                    @FlaggedApi("reverted_flag")
                                    $SYSTEM_API
                                    public void revertedMethod() {}
                                }
                            """
                        ),
                    ),
                apiFlags =
                    ApiFlags(
                        listOf(
                            ApiFlag("reverted_flag", REVERT),
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
                expectedContainsRevertedItem = true,
            ) {
                surfaceTest(
                    surface = "public",
                    expected =
                        """
                            package test.pkg
                                   self - ApiVariantSet[public(C)]
                                content - ApiVariantSet[]
                              class test.pkg.Outer
                                     self - ApiVariantSet[public(C)]
                                  content - ApiVariantSet[]
                                constructor test.pkg.Outer()
                                       self - ApiVariantSet[public(C)]
                                    content - ApiVariantSet[]
                                method test.pkg.Outer.revertedMethod()
                                       self - ApiVariantSet[]
                                    content - ApiVariantSet[]
                        """,
                )

                surfaceTest(
                    surface = "system",
                    expected =
                        """
                            package test.pkg
                                   self - ApiVariantSet[public(C)]
                                content - ApiVariantSet[]
                              class test.pkg.Outer
                                     self - ApiVariantSet[public(C)]
                                  content - ApiVariantSet[]
                                constructor test.pkg.Outer()
                                       self - ApiVariantSet[public(C)]
                                    content - ApiVariantSet[]
                                method test.pkg.Outer.revertedMethod()
                                       self - ApiVariantSet[public(C)]
                                    content - ApiVariantSet[]
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
                    expected =
                        """
                            package test.pkg
                                   self - ApiVariantSet[public(C)]
                                content - ApiVariantSet[]
                              class test.pkg.MyRecord
                                     self - ApiVariantSet[public(C)]
                                  content - ApiVariantSet[]
                                constructor test.pkg.MyRecord(int)
                                       self - ApiVariantSet[public(C)]
                                    content - ApiVariantSet[]
                                method test.pkg.MyRecord.x()
                                       self - ApiVariantSet[public(C)]
                                    content - ApiVariantSet[]
                        """,
                )
            }

            buildTests(
                name = "doconly",
                surfaceRules = publicSystemModuleRules,
                sources =
                    listOf(
                        java(
                            """
                                package test.pkg;
                                $DOC_ONLY
                                public class DocOnlyClass {
                                    public void method() {}
                                }
                                public class Test {
                                    $DOC_ONLY
                                    public void docOnlyMethod() {}
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
                                   self - ApiVariantSet[public(CD)]
                                content - ApiVariantSet[]
                              class test.pkg.DocOnlyClass
                                     self - ApiVariantSet[public(D)]
                                  content - ApiVariantSet[]
                                constructor test.pkg.DocOnlyClass()
                                       self - ApiVariantSet[public(D)]
                                    content - ApiVariantSet[]
                                method test.pkg.DocOnlyClass.method()
                                       self - ApiVariantSet[public(D)]
                                    content - ApiVariantSet[]
                              class test.pkg.Test
                                     self - ApiVariantSet[public(C)]
                                  content - ApiVariantSet[]
                                constructor test.pkg.Test()
                                       self - ApiVariantSet[public(C)]
                                    content - ApiVariantSet[]
                                method test.pkg.Test.docOnlyMethod()
                                       self - ApiVariantSet[public(C)]
                                    content - ApiVariantSet[]
                        """,
                )
            }

            buildTests(
                name = "removed",
                surfaceRules = publicSystemModuleRules,
                sources =
                    listOf(
                        java(
                            """
                                package test.pkg;
                                $REMOVED_FROM_API
                                public class RemovedClass {
                                    public void method() {}
                                }
                                public class Test {
                                    $REMOVED_FROM_API
                                    public void removedMethod() {}
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
                                   self - ApiVariantSet[public(CR)]
                                content - ApiVariantSet[]
                              class test.pkg.RemovedClass
                                     self - ApiVariantSet[public(R)]
                                  content - ApiVariantSet[]
                                constructor test.pkg.RemovedClass()
                                       self - ApiVariantSet[public(R)]
                                    content - ApiVariantSet[]
                                method test.pkg.RemovedClass.method()
                                       self - ApiVariantSet[public(R)]
                                    content - ApiVariantSet[]
                              class test.pkg.Test
                                     self - ApiVariantSet[public(C)]
                                  content - ApiVariantSet[public(R)]
                                constructor test.pkg.Test()
                                       self - ApiVariantSet[public(C)]
                                    content - ApiVariantSet[]
                                method test.pkg.Test.removedMethod()
                                       self - ApiVariantSet[public(R)]
                                    content - ApiVariantSet[]
                        """,
                )
            }

            buildTests(
                name = "doconly and removed",
                surfaceRules = publicSystemModuleRules,
                sources =
                    listOf(
                        java(
                            """
                                package test.pkg;
                                $DOC_ONLY
                                $REMOVED_FROM_API
                                public class DocOnlyAndRemovedClass {
                                    public void method() {}
                                }
                                public class Test {
                                    $DOC_ONLY
                                    $REMOVED_FROM_API
                                    public void docOnlyAndRemovedMethod() {}
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
                                   self - ApiVariantSet[public(CR)]
                                content - ApiVariantSet[]
                              class test.pkg.DocOnlyAndRemovedClass
                                     self - ApiVariantSet[public(R)]
                                  content - ApiVariantSet[]
                                constructor test.pkg.DocOnlyAndRemovedClass()
                                       self - ApiVariantSet[public(R)]
                                    content - ApiVariantSet[]
                                method test.pkg.DocOnlyAndRemovedClass.method()
                                       self - ApiVariantSet[public(R)]
                                    content - ApiVariantSet[]
                              class test.pkg.Test
                                     self - ApiVariantSet[public(C)]
                                  content - ApiVariantSet[public(R)]
                                constructor test.pkg.Test()
                                       self - ApiVariantSet[public(C)]
                                    content - ApiVariantSet[]
                                method test.pkg.Test.docOnlyAndRemovedMethod()
                                       self - ApiVariantSet[public(R)]
                                    content - ApiVariantSet[]
                        """,
                )
            }

            buildTests(
                name = "@removed doctag",
                surfaceRules = publicSystemModuleRules,
                sources =
                    listOf(
                        java(
                            """
                                package test.pkg;
                                /** @removed */
                                public class RemovedClass {
                                    public void method() {}
                                }
                                public class Test {
                                    /** @removed */
                                    public void removedMethod() {}
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
                                   self - ApiVariantSet[public(CR)]
                                content - ApiVariantSet[]
                              class test.pkg.RemovedClass
                                     self - ApiVariantSet[public(R)]
                                  content - ApiVariantSet[]
                                constructor test.pkg.RemovedClass()
                                       self - ApiVariantSet[public(R)]
                                    content - ApiVariantSet[]
                                method test.pkg.RemovedClass.method()
                                       self - ApiVariantSet[public(R)]
                                    content - ApiVariantSet[]
                              class test.pkg.Test
                                     self - ApiVariantSet[public(C)]
                                  content - ApiVariantSet[public(R)]
                                constructor test.pkg.Test()
                                       self - ApiVariantSet[public(C)]
                                    content - ApiVariantSet[]
                                method test.pkg.Test.removedMethod()
                                       self - ApiVariantSet[public(R)]
                                    content - ApiVariantSet[]
                        """,
                )
            }

            buildTests(
                name = "public class extending system class",
                surfaceRules = publicSystemModuleRules,
                sources =
                    listOf(
                        java(
                            """
                                package test.pkg;
                                $SYSTEM_API
                                public class SystemClass {
                                }
                                public class PublicClass extends SystemClass {
                                }
                            """
                        ),
                    ),
            ) {
                surfaceTest(
                    surface = "system",
                    expected =
                        """
                            package test.pkg
                                   self - ApiVariantSet[public(C),system(C)]
                                content - ApiVariantSet[]
                              class test.pkg.SystemClass
                                     self - ApiVariantSet[system(C)]
                                  content - ApiVariantSet[]
                                constructor test.pkg.SystemClass()
                                       self - ApiVariantSet[system(C)]
                                    content - ApiVariantSet[]
                              class test.pkg.PublicClass
                                     self - ApiVariantSet[public(C)]
                                  content - ApiVariantSet[system(C)]
                                constructor test.pkg.PublicClass()
                                       self - ApiVariantSet[public(C)]
                                    content - ApiVariantSet[]
                        """,
                )
            }

            buildTests(
                name = "public class extending module class",
                surfaceRules = publicSystemModuleRules,
                sources =
                    listOf(
                        java(
                            """
                                package test.pkg;
                                $MODULE_API
                                public class ModuleClass {
                                }
                                public class PublicClass extends ModuleClass {
                                }
                            """
                        ),
                    ),
            ) {
                surfaceTest(
                    surface = "module",
                    expected =
                        """
                            package test.pkg
                                   self - ApiVariantSet[public(C),module(C)]
                                content - ApiVariantSet[]
                              class test.pkg.ModuleClass
                                     self - ApiVariantSet[module(C)]
                                  content - ApiVariantSet[]
                                constructor test.pkg.ModuleClass()
                                       self - ApiVariantSet[module(C)]
                                    content - ApiVariantSet[]
                              class test.pkg.PublicClass
                                     self - ApiVariantSet[public(C)]
                                  content - ApiVariantSet[module(C)]
                                constructor test.pkg.PublicClass()
                                       self - ApiVariantSet[public(C)]
                                    content - ApiVariantSet[]
                        """,
                )
            }

            buildTests(
                name = "public class extending standalone class",
                surfaceRules = publicStandaloneRules,
                sources =
                    listOf(
                        java(
                            """
                                package test.pkg;
                                $STANDALONE_API
                                public class StandaloneClass {
                                }
                                public class PublicClass extends StandaloneClass {
                                }
                            """
                        ),
                    ),
            ) {
                surfaceTest(
                    surface = "standalone",
                    expected =
                        """
                            package test.pkg
                                   self - ApiVariantSet[standalone(C)]
                                content - ApiVariantSet[]
                              class test.pkg.StandaloneClass
                                     self - ApiVariantSet[standalone(C)]
                                  content - ApiVariantSet[]
                                constructor test.pkg.StandaloneClass()
                                       self - ApiVariantSet[standalone(C)]
                                    content - ApiVariantSet[]
                              class test.pkg.PublicClass
                                     self - ApiVariantSet[standalone(C)]
                                  content - ApiVariantSet[]
                                constructor test.pkg.PublicClass()
                                       self - ApiVariantSet[standalone(C)]
                                    content - ApiVariantSet[]
                        """,
                )
            }

            buildTests(
                name = "removed public class extending removed system class",
                surfaceRules = publicSystemModuleRules,
                sources =
                    listOf(
                        java(
                            """
                                package test.pkg;
                                $SYSTEM_API
                                $REMOVED_FROM_API
                                public class SystemClass {
                                }
                                $REMOVED_FROM_API
                                public class PublicClass extends SystemClass {
                                }
                            """
                        ),
                    ),
            ) {
                surfaceTest(
                    surface = "system",
                    expected =
                        """
                            package test.pkg
                                   self - ApiVariantSet[public(R),system(R)]
                                content - ApiVariantSet[]
                              class test.pkg.SystemClass
                                     self - ApiVariantSet[system(R)]
                                  content - ApiVariantSet[]
                                constructor test.pkg.SystemClass()
                                       self - ApiVariantSet[system(R)]
                                    content - ApiVariantSet[]
                              class test.pkg.PublicClass
                                     self - ApiVariantSet[public(R)]
                                  content - ApiVariantSet[system(R)]
                                constructor test.pkg.PublicClass()
                                       self - ApiVariantSet[public(R)]
                                    content - ApiVariantSet[]
                        """,
                )
            }

            buildTests(
                name = "public class extending removed system class",
                surfaceRules = publicSystemModuleRules,
                sources =
                    listOf(
                        java(
                            """
                                package test.pkg;
                                $SYSTEM_API
                                $REMOVED_FROM_API
                                public class SystemClass {
                                }
                                public class PublicClass extends SystemClass {
                                }
                            """
                        ),
                    ),
            ) {
                surfaceTest(
                    surface = "system",
                    expected =
                        """
                            package test.pkg
                                   self - ApiVariantSet[public(C),system(R)]
                                content - ApiVariantSet[]
                              class test.pkg.SystemClass
                                     self - ApiVariantSet[system(R)]
                                  content - ApiVariantSet[]
                                constructor test.pkg.SystemClass()
                                       self - ApiVariantSet[system(R)]
                                    content - ApiVariantSet[]
                              class test.pkg.PublicClass
                                     self - ApiVariantSet[public(C)]
                                  content - ApiVariantSet[]
                                constructor test.pkg.PublicClass()
                                       self - ApiVariantSet[public(C)]
                                    content - ApiVariantSet[]
                        """,
                )
            }

            buildTests(
                name = "nested class in public class",
                surfaceRules = publicSystemModuleRules,
                sources =
                    listOf(
                        java(
                            """
                                package test.pkg;
                                public class Outer {
                                    $SYSTEM_API
                                    public class Inner {
                                    }
                                }
                            """
                        ),
                    ),
            ) {
                surfaceTest(
                    surface = "system",
                    expected =
                        """
                            package test.pkg
                                   self - ApiVariantSet[public(C),system(C)]
                                content - ApiVariantSet[]
                              class test.pkg.Outer
                                     self - ApiVariantSet[public(C)]
                                  content - ApiVariantSet[]
                                constructor test.pkg.Outer()
                                       self - ApiVariantSet[public(C)]
                                    content - ApiVariantSet[]
                                class test.pkg.Outer.Inner
                                       self - ApiVariantSet[system(C)]
                                    content - ApiVariantSet[]
                                  constructor test.pkg.Outer.Inner()
                                         self - ApiVariantSet[system(C)]
                                      content - ApiVariantSet[]
                        """,
                )
            }

            buildTests(
                name = "system method in unannotated class",
                surfaceRules = annotatedOnlyPublicSystemModuleRules,
                sources =
                    listOf(
                        java(
                            """
                                package test.pkg;
                                public class Test {
                                    $SYSTEM_API
                                    public void systemMethod() {}
                                }
                            """
                        ),
                    ),
            ) {
                surfaceTest(
                    surface = "system",
                    expected =
                        """
                            package test.pkg
                                   self - ApiVariantSet[]
                                content - ApiVariantSet[]
                              class test.pkg.Test
                                     self - ApiVariantSet[]
                                  content - ApiVariantSet[]
                                constructor test.pkg.Test()
                                       self - ApiVariantSet[]
                                    content - ApiVariantSet[]
                                method test.pkg.Test.systemMethod()
                                       self - ApiVariantSet[]
                                    content - ApiVariantSet[]
                        """,
                )
            }

            buildTests(
                name = "system method in public inner class",
                surfaceRules = publicSystemModuleRules,
                sources =
                    listOf(
                        java(
                            """
                                package test.pkg;
                                public class Test {
                                    public class Inner {
                                        $SYSTEM_API
                                        public void systemMethod() {}
                                    }
                                }
                            """
                        ),
                    ),
            ) {
                // TODO(b/512093496): The behavior shown below is not correct as propagating
                //  variants from members to the containing package is broken and will be fixed in
                //  follow up changes.
                surfaceTest(
                    surface = "system",
                    expected =
                        """
                            package test.pkg
                                   self - ApiVariantSet[public(C),system(C)]
                                content - ApiVariantSet[]
                              class test.pkg.Test
                                     self - ApiVariantSet[public(C)]
                                  content - ApiVariantSet[]
                                constructor test.pkg.Test()
                                       self - ApiVariantSet[public(C)]
                                    content - ApiVariantSet[]
                                class test.pkg.Test.Inner
                                       self - ApiVariantSet[public(C)]
                                    content - ApiVariantSet[system(C)]
                                  constructor test.pkg.Test.Inner()
                                         self - ApiVariantSet[public(C)]
                                      content - ApiVariantSet[]
                                  method test.pkg.Test.Inner.systemMethod()
                                         self - ApiVariantSet[system(C)]
                                      content - ApiVariantSet[]
                        """,
                )
            }

            buildTests(
                name = "removed class overriding method from public interface marked as @Hide",
                surfaceRules = publicSystemModuleRules,
                sources =
                    listOf(
                        java(
                            """
                                package test.pkg;

                                public interface PublicInterface {
                                    void method();
                                }
                            """
                        ),
                        java(
                            """
                                package test.pkg;

                                $SYSTEM_API
                                $REMOVED_FROM_API
                                public class RemovedClass implements PublicInterface {
                                    $HIDE
                                    @Override
                                    public void method() {}
                                }
                            """
                        ),
                    ),
            ) {
                surfaceTest(
                    surface = "module",
                    expected =
                        """
                            package test.pkg
                                   self - ApiVariantSet[public(C),system(R)]
                                content - ApiVariantSet[]
                              class test.pkg.PublicInterface
                                     self - ApiVariantSet[public(C)]
                                  content - ApiVariantSet[]
                                method test.pkg.PublicInterface.method()
                                       self - ApiVariantSet[public(C)]
                                    content - ApiVariantSet[]
                              class test.pkg.RemovedClass
                                     self - ApiVariantSet[system(R)]
                                  content - ApiVariantSet[]
                                constructor test.pkg.RemovedClass()
                                       self - ApiVariantSet[system(R)]
                                    content - ApiVariantSet[]
                                method test.pkg.RemovedClass.method()
                                       self - ApiVariantSet[public(C)]
                                    content - ApiVariantSet[]
                        """,
                )
            }

            buildTests(
                name = "system class overriding method from removed public class marked as @Hide",
                surfaceRules = publicSystemModuleRules,
                sources =
                    listOf(
                        java(
                            """
                                package test.pkg;

                                $REMOVED_FROM_API
                                public class RemovedPublicClass {
                                    public void method() {}
                                }
                            """
                        ),
                        java(
                            """
                                package test.pkg;

                                $SYSTEM_API
                                public class SystemClass extends RemovedPublicClass {
                                    $HIDE
                                    @Override
                                    public void method() {}
                                }
                            """
                        ),
                    ),
            ) {
                // The removed status is not inherited by the overriding method.
                surfaceTest(
                    surface = "module",
                    expected =
                        """
                            package test.pkg
                                   self - ApiVariantSet[public(R),system(C)]
                                content - ApiVariantSet[]
                              class test.pkg.RemovedPublicClass
                                     self - ApiVariantSet[public(R)]
                                  content - ApiVariantSet[]
                                constructor test.pkg.RemovedPublicClass()
                                       self - ApiVariantSet[public(R)]
                                    content - ApiVariantSet[]
                                method test.pkg.RemovedPublicClass.method()
                                       self - ApiVariantSet[public(R)]
                                    content - ApiVariantSet[]
                              class test.pkg.SystemClass
                                     self - ApiVariantSet[system(C)]
                                  content - ApiVariantSet[]
                                constructor test.pkg.SystemClass()
                                       self - ApiVariantSet[system(C)]
                                    content - ApiVariantSet[]
                                method test.pkg.SystemClass.method()
                                       self - ApiVariantSet[]
                                    content - ApiVariantSet[]
                        """,
                )
            }

            buildTests(
                name = "inaccessible class extending and implementing method from public class",
                surfaceRules = publicSystemModuleRules,
                sources =
                    listOf(
                        java(
                            """
                                package test.pkg;

                                public abstract class PublicClass {
                                    public abstract void method();
                                }
                            """
                        ),
                        java(
                            """
                                package test.pkg;

                                class InaccessibleClass extends PublicClass {
                                    @Override
                                    public void method() {}
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
                                content - ApiVariantSet[]
                              class test.pkg.PublicClass
                                     self - ApiVariantSet[public(C)]
                                  content - ApiVariantSet[]
                                constructor test.pkg.PublicClass()
                                       self - ApiVariantSet[public(C)]
                                    content - ApiVariantSet[]
                                method test.pkg.PublicClass.method()
                                       self - ApiVariantSet[public(C)]
                                    content - ApiVariantSet[]
                              class test.pkg.InaccessibleClass
                                     self - ApiVariantSet[]
                                  content - ApiVariantSet[]
                                constructor test.pkg.InaccessibleClass()
                                       self - ApiVariantSet[]
                                    content - ApiVariantSet[]
                                method test.pkg.InaccessibleClass.method()
                                       self - ApiVariantSet[]
                                    content - ApiVariantSet[]
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
                        // Disable the supported InputFormat check as this test is already
                        // parameterized and filtered by InputFormat.
                        checkSupportedInputFormats = false,
                    ),
            ) {
                // Snapshot codebases do not support hidden items. The lack of the HIDDEN_ITEMS
                // capability indicates this test is running against a snapshot codebase.
                val isSnapshot = !codebaseCreatorHasCapability(Capability.HIDDEN_ITEMS)

                // Snapshot codebases cannot test reverted items. When snapshotting,
                // CodebaseSnapshotTaker replaces reverted items with their released counterparts
                // (via actualItemToSnapshot) and copies variants directly from them instead of
                // verifying SelectedApiUpdater's variant calculations on the source items.
                assumeFalse(
                    "Snapshot cannot support revert test",
                    params.expectedContainsRevertedItem && isSnapshot,
                )

                codebase.assertSelectedApiVariants(params.expected)

                // Snapshot codebases do not track whether items were reverted, so
                // codebase.containsRevertedItem is only checked on source codebases.
                if (!isSnapshot) {
                    assertEquals(
                        params.expectedContainsRevertedItem,
                        codebase.containsRevertedItem,
                        message = "codebase.containsRevertedItem",
                    )
                }
            }
        }

        // If previously released sources are provided, create a codebase from them to act as the
        // previously released codebase. This is supplied to the AnnotationManager so it can
        // determine the released status of items when testing API stability and reverting
        // flagged APIs.
        val previouslyReleasedSources = params.previouslyReleasedSources
        if (previouslyReleasedSources != null) {
            runCodebaseTest(
                inputSet(previouslyReleasedSources),
                // Disable the supported InputFormat check as this test is already
                // parameterized and filtered by InputFormat.
                testFixture = TestFixture(checkSupportedInputFormats = false),
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
