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
import com.android.tools.metalava.model.EMITTED_ONLY
import com.android.tools.metalava.model.FilterPredicate
import com.android.tools.metalava.model.SelectableItem
import com.android.tools.metalava.model.api.surface.ApiSurfacePredicate
import com.android.tools.metalava.model.provider.Capability
import com.android.tools.metalava.model.provider.InputFormat
import com.android.tools.metalava.model.testing.SupportedInputFormats
import com.android.tools.metalava.model.testing.surfaces.initializeSelectedApiInstances
import com.android.tools.metalava.model.testsuite.BaseModelTest
import com.android.tools.metalava.model.visitors.ApiFilters
import com.android.tools.metalava.model.visitors.ApiPredicate
import com.android.tools.metalava.model.visitors.ApiSurfaceVisitor
import com.android.tools.metalava.model.visitors.ApiVisitor
import com.android.tools.metalava.testing.EntryPoint
import com.android.tools.metalava.testing.EntryPointCallerRule
import com.android.tools.metalava.testing.EntryPointCallerTracker
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
        val apiFilters: Codebase.() -> ApiFilters?,
        val filterEmit: Codebase.() -> FilterPredicate?,
        val requiresApiVariantSelectors: Boolean = false,
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

        /**
         * Create a [TestCase] comparing [ApiSurfaceVisitor] using [ApiSurfacePredicate.wholeApi]
         * with [ApiVisitor] using an [ApiPredicate] configured to match the whole API surface (with
         * `ignoreRemoved = true` and `includeDocOnly = true`).
         *
         * This matches the whole API surface across all surfaces, verifying that
         * [ApiSurfaceVisitor] visits the exact same items as [ApiVisitor] when inspecting the
         * entire API surface (such as when updating deprecation status across extended and base
         * surfaces).
         */
        @EntryPoint
        fun wholeApiTestCase(
            name: String,
            input: List<TestFile>,
            expectedNotNested: String,
            expectedNested: String = expectedNotNested,
            requiresApiVariantSelectors: Boolean = false,
            classpath: List<TestFile> = emptyList(),
        ) =
            TestCase(
                name = "whole API/$name",
                input = input,
                expectedNotNested = expectedNotNested,
                expectedNested = expectedNested,
                apiFilters = {
                    val predicate =
                        EMITTED_ONLY.and(
                            ApiPredicate(
                                ignoreRemoved = true,
                                includeDocOnly = true,
                                // Match the whole API surface so deprecation is updated for items
                                // in extended/base surfaces as well.
                                config = ApiPredicate.Config(),
                            )
                        )
                    ApiFilters(predicate, predicate)
                },
                filterEmit = { EMITTED_ONLY.and(ApiSurfacePredicate.wholeApi()) },
                requiresApiVariantSelectors = requiresApiVariantSelectors,
                classpath = classpath,
            )

        /**
         * Create a [TestCase] comparing [ApiSurfaceVisitor] using
         * [ApiSurfacePredicate.wholeCoreApi] with [ApiVisitor] using [ApiFilters] from
         * [ApiPredicate.Config.defaultFilters].
         */
        @EntryPoint
        fun wholeCoreTestCase(
            name: String,
            input: List<TestFile>,
            expectedNotNested: String,
            expectedNested: String = expectedNotNested,
            requiresApiVariantSelectors: Boolean = false,
            classpath: List<TestFile> = emptyList(),
        ) =
            TestCase(
                name = "whole core/$name",
                input = input,
                expectedNotNested = expectedNotNested,
                expectedNested = expectedNested,
                apiFilters = { ApiPredicate.Config().defaultFilters() },
                filterEmit = { ApiSurfacePredicate.wholeCoreEmittableApi(apiSurfaces.main) },
                requiresApiVariantSelectors = requiresApiVariantSelectors,
                classpath = classpath,
            )

        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun params() =
            listOf(
                wholeApiTestCase(
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
                    requiresApiVariantSelectors = true,
                ),
                wholeApiTestCase(
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
                wholeApiTestCase(
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
                    requiresApiVariantSelectors = true,
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
                    requiresApiVariantSelectors = true,
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
                    requiresApiVariantSelectors = true,
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
                                        /** @removed */
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
                    requiresApiVariantSelectors = true,
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
                    requiresApiVariantSelectors = true,
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
                    requiresApiVariantSelectors = true,
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
            )
    }

    /**
     * Constructs a string representing the visited [SelectableItem]s with indentation reflecting
     * the visit hierarchy.
     */
    private class SelectableItemDumper {
        private val sb = StringBuilder()
        private var indent = ""

        fun visitSelectableItem(item: SelectableItem) {
            sb.append("$indent${item.describe()}\n")
            indent += "  "
        }

        fun afterVisitSelectableItem() {
            indent = indent.removeSuffix("  ")
        }

        override fun toString(): String = sb.toString()
    }

    /**
     * Traverses this [Codebase] using an [ApiVisitor] with [TestCase.apiFilters] and produces a
     * textual representation of the visited [SelectableItem]s with indentation reflecting the visit
     * hierarchy.
     */
    private fun Codebase.dumpWithApiVisitor(
        preserveClassNesting: Boolean = false,
        visitParameterItems: Boolean = false,
    ): String {
        val dumper = SelectableItemDumper()
        val apiFilters = testCase.apiFilters(this)
        accept(
            object : ApiVisitor(preserveClassNesting, visitParameterItems, apiFilters) {
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
        val filterEmit = testCase.filterEmit(this)
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
        if (testCase.requiresApiVariantSelectors) {
            assumeTrue(
                "Provider does not support API_VARIANT_SELECTORS",
                codebaseCreatorHasCapability(Capability.API_VARIANT_SELECTORS),
            )
        }
        val expected =
            if (preserveClassNesting) testCase.expectedNested else testCase.expectedNotNested
        runCodebaseTest(
            *testCase.input.toTypedArray(),
            testFixture =
                TestFixture(
                    additionalClassPath = testCase.classpath.map { it.toFile() },
                ),
        ) {
            codebase.initializeSelectedApiInstances()
            val actual = codebase.dump()
            assertEquals(expected.trimIndent().trim(), actual.trim())
        }
    }

    /** Test [ApiVisitor] without preserving class nesting. */
    @Test
    fun `test ApiVisitor without preserving class nesting`() {
        runTest(preserveClassNesting = false) { dumpWithApiVisitor(preserveClassNesting = false) }
    }

    /** Test [ApiVisitor] preserving class nesting. */
    @Test
    fun `test ApiVisitor preserving class nesting`() {
        runTest(preserveClassNesting = true) { dumpWithApiVisitor(preserveClassNesting = true) }
    }

    /** Test [ApiSurfaceVisitor] without preserving class nesting. */
    @Test
    fun `test ApiSurfaceVisitor without preserving class nesting`() {
        runTest(preserveClassNesting = false) {
            dumpWithApiSurfaceVisitor(preserveClassNesting = false)
        }
    }

    /** Test [ApiSurfaceVisitor] preserving class nesting. */
    @Test
    fun `test ApiSurfaceVisitor preserving class nesting`() {
        runTest(preserveClassNesting = true) {
            dumpWithApiSurfaceVisitor(preserveClassNesting = true)
        }
    }
}
