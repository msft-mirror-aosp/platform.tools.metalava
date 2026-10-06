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

package com.android.tools.metalava.model.testsuite.visitors

import com.android.tools.lint.checks.infrastructure.TestFile
import com.android.tools.metalava.model.Assertions
import com.android.tools.metalava.model.Codebase
import com.android.tools.metalava.model.EmittedOnlyPredicate
import com.android.tools.metalava.model.FilterPredicate
import com.android.tools.metalava.model.SelectableItem
import com.android.tools.metalava.model.api.surface.ApiSurface
import com.android.tools.metalava.model.api.surface.ApiSurfacePredicate
import com.android.tools.metalava.model.provider.InputFormat
import com.android.tools.metalava.model.testing.SupportedInputFormats
import com.android.tools.metalava.model.testing.surfaces.TestableApiSurfaces.REMOVED_FROM_API
import com.android.tools.metalava.model.testing.surfaces.TestableApiSurfaces.SYSTEM_API
import com.android.tools.metalava.model.testing.surfaces.TestableApiSurfaces.publicSystemModuleRules
import com.android.tools.metalava.model.testing.surfaces.initializeSelectedApiInstances
import com.android.tools.metalava.model.testsuite.BaseModelTest
import com.android.tools.metalava.model.visitors.ApiFilters
import com.android.tools.metalava.model.visitors.ApiFiltersVisitor
import com.android.tools.metalava.model.visitors.ApiSurfaceVisitor
import com.android.tools.metalava.model.visitors.ApiType
import com.android.tools.metalava.model.visitors.ApiVisitor
import com.android.tools.metalava.testing.EntryPoint
import com.android.tools.metalava.testing.EntryPointCallerRule
import com.android.tools.metalava.testing.EntryPointCallerTracker
import com.android.tools.metalava.testing.TestFileCache
import com.android.tools.metalava.testing.TestFileCacheRule
import com.android.tools.metalava.testing.cacheIn
import com.android.tools.metalava.testing.jarFromSources
import com.android.tools.metalava.testing.java
import com.android.tools.metalava.testing.signature
import kotlin.test.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.ClassRule
import org.junit.Rule
import org.junit.Test
import org.junit.runners.Parameterized

/** Common tests for [ApiSurfaceVisitor]. */
@SupportedInputFormats(InputFormat.JAVA, InputFormat.SIGNATURE)
class CommonParameterizedApiSurfaceVisitorTest : BaseModelTest() {

    @Parameterized.Parameter(0) lateinit var testCase: TestCase

    /**
     * Will try and rewrite the stack trace of any test failures to refer to the location where the
     * [TestCase] that is currently being tested was created.
     */
    @get:Rule val entryPointCallerRule = EntryPointCallerRule { testCase.entryPointCallerTracker }

    data class TestCase
    @EntryPoint
    constructor(
        val name: String,
        val input: List<TestFile>,
        val expectedNotNested: String,
        val expectedNested: String = expectedNotNested,
        val apiVisitorFilters: (Codebase.() -> ApiFilters?)? = null,
        val apiFiltersVisitorFilters: (Codebase.() -> ApiFilters?)? = null,
        val filterEmit: (Codebase.() -> FilterPredicate?)? = null,
        val classpath: List<TestFile> = emptyList(),
    ) {
        /**
         * Record the stack trace of the creation of this which can be used to provide a stack trace
         * to the creator of this instance in the event of a test failure.
         */
        val entryPointCallerTracker = EntryPointCallerTracker()

        override fun toString(): String = name
    }

    companion object : Assertions {
        /** Create a [TestFileCache] whose lifespan encompasses all the tests in this class. */
        @ClassRule @JvmField val testFileCacheRule = TestFileCacheRule()

        private val notEmittedClassJar =
            jarFromSources(
                    "not-emitted-class.jar",
                    java(
                        """
                            package test.pkg;

                            public class NotEmittedClass {
                                public NotEmittedClass() {}
                                public void method() {}
                            }
                        """
                    ),
                )
                .cacheIn(testFileCacheRule)

        /** Create a [TestCase] for [ApiSurfaceVisitor] using [ApiSurfacePredicate.wholeCoreApi]. */
        @EntryPoint
        fun wholeCoreTestCase(
            name: String,
            input: List<TestFile>,
            expectedNotNested: String,
            expectedNested: String = expectedNotNested,
            classpath: List<TestFile> = emptyList(),
        ) =
            TestCase(
                name = "whole core/$name",
                input = input,
                expectedNotNested = expectedNotNested,
                expectedNested = expectedNested,
                filterEmit = { ApiSurfacePredicate.wholeCoreEmittableApi(apiSurfaces.main) },
                classpath = classpath,
            )

        /**
         * Create a [TestCase] for [ApiSurfaceVisitor] using
         * [ApiSurfacePredicate.wholeCoreAndRemovedApi].
         */
        @EntryPoint
        fun wholeCoreAndRemovedTestCase(
            name: String,
            input: List<TestFile>,
            expectedNotNested: String,
            expectedNested: String = expectedNotNested,
            classpath: List<TestFile> = emptyList(),
        ) =
            TestCase(
                name = "whole core and removed/$name",
                input = input,
                expectedNotNested = expectedNotNested,
                expectedNested = expectedNested,
                filterEmit = {
                    EmittedOnlyPredicate.and(
                        ApiSurfacePredicate.wholeCoreAndRemovedApi(apiSurfaces.main)
                    )
                },
                classpath = classpath,
            )

        /**
         * Create a [TestCase] that compares [ApiVisitor] with
         * [ApiSurfacePredicate.nonElidingApiFilters] and [ApiFiltersVisitor] with
         * [ApiSurfacePredicate.forSurfaceFilters].
         */
        @EntryPoint
        fun forSurfaceTestCase(
            name: String,
            input: List<TestFile>,
            expectedNotNested: String,
            expectedNested: String = expectedNotNested,
            apiType: ApiType = ApiType.CORE,
            apiSurface: Codebase.() -> ApiSurface = { apiSurfaces.byName["system"]!! },
            classpath: List<TestFile> = emptyList(),
        ) =
            TestCase(
                name = "for surface/$name",
                input = input,
                expectedNotNested = expectedNotNested,
                expectedNested = expectedNested,
                apiVisitorFilters = {
                    ApiSurfacePredicate.nonElidingApiFilters(
                        apiType,
                        apiSurface(),
                    )
                },
                apiFiltersVisitorFilters = {
                    ApiSurfacePredicate.forSurfaceFilters(
                        apiType,
                        apiSurface(),
                    )
                },
                filterEmit = null,
                classpath = classpath,
            )

        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun params() =
            listOf(
                wholeCoreTestCase(
                    name = "outer and inner class",
                    input =
                        listOf(
                            java(
                                """
                                    package test.pkg;

                                    public class Outer {
                                        public int field;
                                        public Outer() {}
                                        public void method() {}

                                        public static class Inner {
                                            public int innerField;
                                            public Inner() {}
                                            public void innerMethod() {}
                                        }
                                    }

                                    class PackagePrivateClass {
                                        public void method() {}
                                    }
                                """
                            ),
                            signature(
                                """
                                    // Signature format: 2.0
                                    package test.pkg {
                                      public class Outer {
                                        ctor public Outer();
                                        method public void method();
                                        field public int field;
                                      }
                                      public static class Outer.Inner {
                                        ctor public Outer.Inner();
                                        method public void innerMethod();
                                        field public int innerField;
                                      }
                                    }
                                """
                            ),
                        ),
                    expectedNotNested =
                        """
                            package test.pkg
                              class test.pkg.Outer
                                constructor test.pkg.Outer()
                                method test.pkg.Outer.method()
                                field test.pkg.Outer.field
                              class test.pkg.Outer.Inner
                                constructor test.pkg.Outer.Inner()
                                method test.pkg.Outer.Inner.innerMethod()
                                field test.pkg.Outer.Inner.innerField
                        """,
                    expectedNested =
                        """
                            package test.pkg
                              class test.pkg.Outer
                                constructor test.pkg.Outer()
                                method test.pkg.Outer.method()
                                field test.pkg.Outer.field
                                class test.pkg.Outer.Inner
                                  constructor test.pkg.Outer.Inner()
                                  method test.pkg.Outer.Inner.innerMethod()
                                  field test.pkg.Outer.Inner.innerField
                        """,
                ),
                wholeCoreTestCase(
                    name = "class without nested classes",
                    input =
                        listOf(
                            java(
                                """
                                    package test.pkg;

                                    public class Foo {
                                        public Foo() {}
                                        public void method() {}
                                    }
                                """
                            ),
                            signature(
                                """
                                    // Signature format: 2.0
                                    package test.pkg {
                                      public class Foo {
                                        ctor public Foo();
                                        method public void method();
                                      }
                                    }
                                """
                            ),
                        ),
                    expectedNotNested =
                        """
                            package test.pkg
                              class test.pkg.Foo
                                constructor test.pkg.Foo()
                                method test.pkg.Foo.method()
                        """,
                ),
                wholeCoreTestCase(
                    name = "hidden method and field",
                    input =
                        listOf(
                            java(
                                """
                                    package test.pkg;

                                    public class Foo {
                                        public Foo() {}
                                        public void method() {}
                                        /** @hide */
                                        public void hiddenMethod() {}
                                        public int field;
                                        /** @hide */
                                        public int hiddenField;
                                    }
                                """
                            ),
                            signature(
                                """
                                    // Signature format: 2.0
                                    package test.pkg {
                                      public class Foo {
                                        ctor public Foo();
                                        method public void method();
                                        field public int field;
                                      }
                                    }
                                """
                            ),
                        ),
                    expectedNotNested =
                        """
                            package test.pkg
                              class test.pkg.Foo
                                constructor test.pkg.Foo()
                                method test.pkg.Foo.method()
                                field test.pkg.Foo.field
                        """,
                ),
                wholeCoreTestCase(
                    name = "hidden class",
                    input =
                        listOf(
                            java(
                                """
                                    package test.pkg;

                                    public class Foo {
                                        public Foo() {}
                                        public void method() {}
                                    }

                                    /** @hide */
                                    public class HiddenClass {
                                        public HiddenClass() {}
                                        public void method() {}
                                    }
                                """
                            ),
                            signature(
                                """
                                    // Signature format: 2.0
                                    package test.pkg {
                                      public class Foo {
                                        ctor public Foo();
                                        method public void method();
                                      }
                                    }
                                """
                            ),
                        ),
                    expectedNotNested =
                        """
                            package test.pkg
                              class test.pkg.Foo
                                constructor test.pkg.Foo()
                                method test.pkg.Foo.method()
                        """,
                ),
                wholeCoreTestCase(
                    name = "removed method",
                    input =
                        listOf(
                            java(
                                """
                                    package test.pkg;

                                    public class Foo {
                                        public Foo() {}
                                        public void method() {}
                                        $REMOVED_FROM_API
                                        public void removedMethod() {}
                                    }
                                """
                            ),
                            signature(
                                """
                                    // Signature format: 2.0
                                    package test.pkg {
                                      public class Foo {
                                        ctor public Foo();
                                        method public void method();
                                      }
                                    }
                                """
                            ),
                        ),
                    expectedNotNested =
                        """
                            package test.pkg
                              class test.pkg.Foo
                                constructor test.pkg.Foo()
                                method test.pkg.Foo.method()
                        """,
                ),
                wholeCoreTestCase(
                    name = "hidden inner class",
                    input =
                        listOf(
                            java(
                                """
                                    package test.pkg;

                                    public class Outer {
                                        public int field;
                                        public Outer() {}
                                        public void method() {}

                                        /** @hide */
                                        public static class Inner {
                                            public int innerField;
                                            public Inner() {}
                                            public void innerMethod() {}
                                        }
                                    }
                                """
                            ),
                            signature(
                                """
                                    // Signature format: 2.0
                                    package test.pkg {
                                      public class Outer {
                                        ctor public Outer();
                                        method public void method();
                                        field public int field;
                                      }
                                    }
                                """
                            ),
                        ),
                    expectedNotNested =
                        """
                            package test.pkg
                              class test.pkg.Outer
                                constructor test.pkg.Outer()
                                method test.pkg.Outer.method()
                                field test.pkg.Outer.field
                        """,
                ),
                wholeCoreTestCase(
                    name = "package private and private members",
                    input =
                        listOf(
                            java(
                                """
                                    package test.pkg;

                                    public class Foo {
                                        public Foo() {}
                                        public void publicMethod() {}
                                        void packagePrivateMethod() {}
                                        private void privateMethod() {}
                                        public int publicField;
                                        int packagePrivateField;
                                        private int privateField;
                                    }
                                """
                            ),
                            signature(
                                """
                                    // Signature format: 2.0
                                    package test.pkg {
                                      public class Foo {
                                        ctor public Foo();
                                        method public void publicMethod();
                                        field public int publicField;
                                      }
                                    }
                                """
                            ),
                        ),
                    expectedNotNested =
                        """
                            package test.pkg
                              class test.pkg.Foo
                                constructor test.pkg.Foo()
                                method test.pkg.Foo.publicMethod()
                                field test.pkg.Foo.publicField
                        """,
                ),
                wholeCoreTestCase(
                    name = "not emitted class",
                    input =
                        listOf(
                            java(
                                """
                                    package test.pkg;

                                    public class Foo extends NotEmittedClass {
                                        public Foo() {}
                                        public void method() {}
                                    }
                                """
                            ),
                            signature(
                                """
                                    // Signature format: 2.0
                                    package test.pkg {
                                      public class Foo extends test.pkg.NotEmittedClass {
                                        ctor public Foo();
                                        method public void method();
                                      }
                                    }
                                """
                            ),
                        ),
                    expectedNotNested =
                        """
                            package test.pkg
                              class test.pkg.Foo
                                constructor test.pkg.Foo()
                                method test.pkg.Foo.method()
                        """,
                    classpath = listOf(notEmittedClassJar),
                ),
                wholeCoreAndRemovedTestCase(
                    name = "outer and inner class",
                    input =
                        listOf(
                            java(
                                """
                                    package test.pkg;

                                    public class Outer {
                                        public int field;
                                        public Outer() {}
                                        public void method() {}

                                        public static class Inner {
                                            public int innerField;
                                            public Inner() {}
                                            public void innerMethod() {}
                                        }
                                    }

                                    class PackagePrivateClass {
                                        public void method() {}
                                    }
                                """
                            ),
                            signature(
                                """
                                    // Signature format: 2.0
                                    package test.pkg {
                                      public class Outer {
                                        ctor public Outer();
                                        method public void method();
                                        field public int field;
                                      }
                                      public static class Outer.Inner {
                                        ctor public Outer.Inner();
                                        method public void innerMethod();
                                        field public int innerField;
                                      }
                                    }
                                """
                            ),
                        ),
                    expectedNotNested =
                        """
                            package test.pkg
                              class test.pkg.Outer
                                constructor test.pkg.Outer()
                                method test.pkg.Outer.method()
                                field test.pkg.Outer.field
                              class test.pkg.Outer.Inner
                                constructor test.pkg.Outer.Inner()
                                method test.pkg.Outer.Inner.innerMethod()
                                field test.pkg.Outer.Inner.innerField
                        """,
                    expectedNested =
                        """
                            package test.pkg
                              class test.pkg.Outer
                                constructor test.pkg.Outer()
                                method test.pkg.Outer.method()
                                field test.pkg.Outer.field
                                class test.pkg.Outer.Inner
                                  constructor test.pkg.Outer.Inner()
                                  method test.pkg.Outer.Inner.innerMethod()
                                  field test.pkg.Outer.Inner.innerField
                        """,
                ),
                wholeCoreAndRemovedTestCase(
                    name = "class without nested classes",
                    input =
                        listOf(
                            java(
                                """
                                    package test.pkg;

                                    public class Foo {
                                        public Foo() {}
                                        public void method() {}
                                    }
                                """
                            ),
                            signature(
                                """
                                    // Signature format: 2.0
                                    package test.pkg {
                                      public class Foo {
                                        ctor public Foo();
                                        method public void method();
                                      }
                                    }
                                """
                            ),
                        ),
                    expectedNotNested =
                        """
                            package test.pkg
                              class test.pkg.Foo
                                constructor test.pkg.Foo()
                                method test.pkg.Foo.method()
                        """,
                ),
                wholeCoreAndRemovedTestCase(
                    name = "removed method",
                    input =
                        listOf(
                            java(
                                """
                                    package test.pkg;

                                    public class Foo {
                                        public Foo() {}
                                        public void method() {}
                                        $REMOVED_FROM_API
                                        public void removedMethod() {}
                                    }
                                """
                            ),
                            signature(
                                """
                                    // Signature format: 2.0
                                    package test.pkg {
                                      public class Foo {
                                        ctor public Foo();
                                        method public void method();
                                        method public void removedMethod();
                                      }
                                    }
                                """
                            ),
                        ),
                    expectedNotNested =
                        """
                            package test.pkg
                              class test.pkg.Foo
                                constructor test.pkg.Foo()
                                method test.pkg.Foo.method()
                                method test.pkg.Foo.removedMethod()
                        """,
                ),
                forSurfaceTestCase(
                    name = "public class with system method",
                    input =
                        listOf(
                            java(
                                """
                                    package test.pkg;

                                    public class Foo {
                                        public Foo() {}
                                        public void publicMethod() {}
                                        $SYSTEM_API
                                        public void systemMethod() {}
                                    }
                                """
                            ),
                        ),
                    expectedNotNested =
                        """
                            package test.pkg
                              class test.pkg.Foo
                                method test.pkg.Foo.systemMethod()
                        """,
                ),
                forSurfaceTestCase(
                    name = "public class with system field and method",
                    input =
                        listOf(
                            java(
                                """
                                    package test.pkg;

                                    public class Foo {
                                        public Foo() {}
                                        public int publicField;
                                        public void publicMethod() {}
                                        $SYSTEM_API
                                        public int systemField;
                                        $SYSTEM_API
                                        public void systemMethod() {}
                                    }
                                """
                            ),
                        ),
                    expectedNotNested =
                        """
                            package test.pkg
                              class test.pkg.Foo
                                method test.pkg.Foo.systemMethod()
                                field test.pkg.Foo.systemField
                        """,
                ),
                forSurfaceTestCase(
                    name = "system class extending public class",
                    input =
                        listOf(
                            java(
                                """
                                    package test.pkg;

                                    public class PublicBase {
                                        public PublicBase() {}
                                        public void baseMethod() {}
                                    }

                                    $SYSTEM_API
                                    public class SystemSub extends PublicBase {
                                        public SystemSub() {}
                                        public void subMethod() {}
                                    }
                                """
                            ),
                        ),
                    expectedNotNested =
                        """
                            package test.pkg
                              class test.pkg.SystemSub
                                constructor test.pkg.SystemSub()
                                method test.pkg.SystemSub.subMethod()
                        """,
                ),
                forSurfaceTestCase(
                    name = "public class extending system class",
                    input =
                        listOf(
                            java(
                                """
                                    package test.pkg;

                                    $SYSTEM_API
                                    public class SystemBase {
                                        public SystemBase() {}
                                        public void baseMethod() {}
                                    }

                                    public class PublicSub extends SystemBase {
                                        public PublicSub() {}
                                        public void subMethod() {}
                                    }
                                """
                            ),
                        ),
                    expectedNotNested =
                        """
                            package test.pkg
                              class test.pkg.SystemBase
                                constructor test.pkg.SystemBase()
                                method test.pkg.SystemBase.baseMethod()
                              class test.pkg.PublicSub
                        """,
                ),
                forSurfaceTestCase(
                    name = "outer and inner class",
                    input =
                        listOf(
                            java(
                                """
                                    package test.pkg;

                                    $SYSTEM_API
                                    public class Outer {
                                        public int field;
                                        public Outer() {}
                                        public void method() {}

                                        public static class Inner {
                                            public int innerField;
                                            public Inner() {}
                                            public void innerMethod() {}
                                        }
                                    }
                                """
                            ),
                        ),
                    expectedNotNested =
                        """
                            package test.pkg
                              class test.pkg.Outer
                                constructor test.pkg.Outer()
                                method test.pkg.Outer.method()
                                field test.pkg.Outer.field
                              class test.pkg.Outer.Inner
                                constructor test.pkg.Outer.Inner()
                                method test.pkg.Outer.Inner.innerMethod()
                                field test.pkg.Outer.Inner.innerField
                        """,
                    expectedNested =
                        """
                            package test.pkg
                              class test.pkg.Outer
                                constructor test.pkg.Outer()
                                method test.pkg.Outer.method()
                                field test.pkg.Outer.field
                                class test.pkg.Outer.Inner
                                  constructor test.pkg.Outer.Inner()
                                  method test.pkg.Outer.Inner.innerMethod()
                                  field test.pkg.Outer.Inner.innerField
                        """,
                ),
                forSurfaceTestCase(
                    name = "removed system method",
                    input =
                        listOf(
                            java(
                                """
                                    package test.pkg;

                                    public class Foo {
                                        public Foo() {}
                                        public void method() {}
                                        $SYSTEM_API
                                        $REMOVED_FROM_API
                                        public void removedSystemMethod() {}
                                    }
                                """
                            ),
                        ),
                    expectedNotNested =
                        """
                            package test.pkg
                              class test.pkg.Foo
                                method test.pkg.Foo.removedSystemMethod()
                        """,
                    apiType = ApiType.REMOVED,
                ),
            )
    }

    /**
     * Traverses this [Codebase] using an [ApiVisitor] with [TestCase.apiVisitorFilters] and
     * produces a textual representation of the visited [SelectableItem]s with indentation
     * reflecting the visit hierarchy.
     */
    private fun Codebase.dumpWithApiVisitor(
        preserveClassNesting: Boolean = false,
        visitParameterItems: Boolean = false,
    ): String {
        val dumper = SelectableItemDumper()
        val apiFilters = testCase.apiVisitorFilters!!(this)
        accept(
            object :
                ApiVisitor(
                    preserveClassNesting = preserveClassNesting,
                    visitParameterItems = visitParameterItems,
                    apiFilters = apiFilters,
                    orderClassesByName = false,
                ) {
                override fun visitSelectableItem(item: SelectableItem) {
                    dumper.visitSelectableItem(item)
                }

                override fun afterVisitSelectableItem(item: SelectableItem) {
                    dumper.afterVisitSelectableItem()
                }
            }
        )
        return dumper.toString()
    }

    /**
     * Traverses this [Codebase] using an [ApiFiltersVisitor] with
     * [TestCase.apiFiltersVisitorFilters] and produces a textual representation of the visited
     * [SelectableItem]s with indentation reflecting the visit hierarchy.
     */
    private fun Codebase.dumpWithApiFiltersVisitor(
        preserveClassNesting: Boolean = false,
        visitParameterItems: Boolean = false,
    ): String {
        val dumper = SelectableItemDumper()
        val apiFilters = testCase.apiFiltersVisitorFilters!!(this)
        accept(
            object : ApiFiltersVisitor(preserveClassNesting, visitParameterItems, apiFilters) {
                override fun visitSelectableItem(item: SelectableItem) {
                    dumper.visitSelectableItem(item)
                }

                override fun afterVisitSelectableItem(item: SelectableItem) {
                    dumper.afterVisitSelectableItem()
                }
            }
        )
        return dumper.toString()
    }

    /**
     * Traverses this [Codebase] using an [ApiSurfaceVisitor] with [TestCase.filterEmit] and
     * produces a textual representation of the visited [SelectableItem]s with indentation
     * reflecting the visit hierarchy.
     */
    private fun Codebase.dumpWithApiSurfaceVisitor(
        preserveClassNesting: Boolean = false,
        visitParameterItems: Boolean = false,
    ): String {
        val dumper = SelectableItemDumper()
        val filterEmit = testCase.filterEmit!!(this)
        accept(
            object : ApiSurfaceVisitor(preserveClassNesting, visitParameterItems, filterEmit) {
                override fun visitSelectableItem(item: SelectableItem) {
                    dumper.visitSelectableItem(item)
                }

                override fun afterVisitSelectableItem(item: SelectableItem) {
                    dumper.afterVisitSelectableItem()
                }
            }
        )
        return dumper.toString()
    }

    /**
     * Runs [dump] on the codebase created from [testCase] input files and asserts that the
     * resulting output matches the expected output (either [TestCase.expectedNested] if
     * [preserveClassNesting] is `true`, or [TestCase.expectedNotNested] if `false`).
     */
    private fun runTest(
        preserveClassNesting: Boolean,
        dump: Codebase.() -> String,
    ) {
        val expected =
            if (preserveClassNesting) testCase.expectedNested else testCase.expectedNotNested
        runCodebaseTest(
            *testCase.input.toTypedArray(),
            testFixture =
                TestFixture(
                    additionalClassPath = testCase.classpath.map { it.toFile() },
                    apiSurfaceRules = publicSystemModuleRules,
                ),
        ) {
            codebase.initializeSelectedApiInstances()
            val actual = codebase.dump()
            assertEquals(expected.trimIndent().trim(), actual.trim())
        }
    }

    /** Test [ApiVisitor] without preserving class nesting. */
    @SupportedInputFormats(InputFormat.JAVA)
    @Test
    fun `test ApiVisitor without preserving class nesting`() {
        assumeTrue(testCase.apiVisitorFilters != null)
        runTest(preserveClassNesting = false) { dumpWithApiVisitor(preserveClassNesting = false) }
    }

    /** Test [ApiVisitor] preserving class nesting. */
    @SupportedInputFormats(InputFormat.JAVA)
    @Test
    fun `test ApiVisitor preserving class nesting`() {
        assumeTrue(testCase.apiVisitorFilters != null)
        runTest(preserveClassNesting = true) { dumpWithApiVisitor(preserveClassNesting = true) }
    }

    /** Test [ApiFiltersVisitor] without preserving class nesting. */
    @SupportedInputFormats(InputFormat.JAVA)
    @Test
    fun `test ApiFiltersVisitor without preserving class nesting`() {
        assumeTrue(testCase.apiFiltersVisitorFilters != null)
        runTest(preserveClassNesting = false) {
            dumpWithApiFiltersVisitor(preserveClassNesting = false)
        }
    }

    /** Test [ApiFiltersVisitor] preserving class nesting. */
    @SupportedInputFormats(InputFormat.JAVA)
    @Test
    fun `test ApiFiltersVisitor preserving class nesting`() {
        assumeTrue(testCase.apiFiltersVisitorFilters != null)
        runTest(preserveClassNesting = true) {
            dumpWithApiFiltersVisitor(preserveClassNesting = true)
        }
    }

    /** Test [ApiSurfaceVisitor] without preserving class nesting. */
    @Test
    fun `test ApiSurfaceVisitor without preserving class nesting`() {
        assumeTrue(testCase.filterEmit != null)
        runTest(preserveClassNesting = false) {
            dumpWithApiSurfaceVisitor(preserveClassNesting = false)
        }
    }

    /** Test [ApiSurfaceVisitor] preserving class nesting. */
    @Test
    fun `test ApiSurfaceVisitor preserving class nesting`() {
        assumeTrue(testCase.filterEmit != null)
        runTest(preserveClassNesting = true) {
            dumpWithApiSurfaceVisitor(preserveClassNesting = true)
        }
    }
}
