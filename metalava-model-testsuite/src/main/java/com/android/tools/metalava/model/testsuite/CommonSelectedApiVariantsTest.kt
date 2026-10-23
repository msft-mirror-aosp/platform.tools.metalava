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

package com.android.tools.metalava.model.testsuite

import com.android.tools.metalava.model.PackageFilter
import com.android.tools.metalava.model.api.SelectedApi
import com.android.tools.metalava.model.provider.Capability
import com.android.tools.metalava.model.provider.InputFormat
import com.android.tools.metalava.model.testing.SupportedInputFormats
import com.android.tools.metalava.model.testing.surfaces.TestableApiSurfaces
import com.android.tools.metalava.model.testing.surfaces.TestableApiSurfaces.SYSTEM_API
import com.android.tools.metalava.model.testing.surfaces.TestableApiSurfaces.publicSystemModuleRules
import com.android.tools.metalava.testing.java
import com.android.tools.metalava.testing.kotlin
import kotlin.test.assertEquals
import org.junit.Test

/**
 * Tests verifying that [SelectedApi] variants are correctly computed across different
 * [InputFormat]s (such as [InputFormat.SIGNATURE] and [InputFormat.JAVA]) and API surface
 * configurations.
 */
class CommonSelectedApiVariantsTest : BaseModelTest() {
    /**
     * Tests that a single API surface produces identical [SelectedApi] variants for both signature
     * files and Java source files.
     *
     * All items (package, class, constructor, and method) belong to the `main` API surface and have
     * `self - ApiVariantSet[main(C)]`.
     */
    @SupportedInputFormats(InputFormat.SIGNATURE, InputFormat.JAVA)
    @Test
    fun `Test single signature file and api surface`() {
        runCodebaseTest(
            signature(
                """
                    // Signature format: 2.0
                    package test.pkg {
                      public class Test {
                        ctor public Test();
                        method public void method();
                      }
                    }
                """
            ),
            java(
                """
                    package test.pkg;

                    public class Test {
                        public Test() {}
                        public void method() {}
                    }
                """
            ),
        ) {
            val expected =
                """
                    package test.pkg
                           self - ApiVariantSet[main(C)]
                      class test.pkg.Test
                             self - ApiVariantSet[main(C)]
                        constructor test.pkg.Test()
                               self - ApiVariantSet[main(C)]
                        method test.pkg.Test.method()
                               self - ApiVariantSet[main(C)]
                """
            codebase.assertSelectedApiVariants(expected)
        }
    }

    /**
     * Tests that a single API surface produces identical [SelectedApi] variants for both signature
     * files and Kotlin source files containing a type alias.
     */
    @SupportedInputFormats(InputFormat.SIGNATURE, InputFormat.KOTLIN)
    @Test
    fun `Test typealias in single signature file and api surface`() {
        runCodebaseTest(
            signature(
                """
                    // Signature format: 5.0
                    package test.pkg {
                      public typealias Foo = String;
                    }
                """
            ),
            kotlin(
                """
                    package test.pkg
                    typealias Foo = String
                """
            ),
        ) {
            val expected = "ApiVariantSet[main(C)]"
            val typeAlias = codebase.assertTypeAlias("test.pkg.Foo")
            typeAlias.assertItemApiVariants(expected)
        }
    }

    /**
     * Tests that signature files and Java source files produce identical [SelectedApi] variants
     * across multiple API surfaces.
     *
     * In this test:
     * - `Base` and `Test` class and constructors belong to `public` (via `base.txt` or standard
     *   Java visibility).
     * - `Test.method()` belongs to `system` (via `current.txt` or annotated with `$SYSTEM_API`).
     *
     * This causes:
     * - `Test` to have `public` in [SelectedApi.itemApiVariants] and `system` in
     *   [SelectedApi.contentApiVariants].
     * - Package `test.pkg` to have both `public` and `system` in [SelectedApi.itemApiVariants]
     *   because it directly contains elements belonging to both surfaces.
     */
    @SupportedInputFormats(InputFormat.SIGNATURE, InputFormat.JAVA)
    @Test
    fun `Test two signature files and api surfaces`() {
        val testFixture =
            TestFixture(
                apiPackages = PackageFilter.parse("test.*:-test.api.*"),
                apiSurfaceRules = publicSystemModuleRules.retargetAt("system"),
            )
        runCodebaseTest(
            inputSet(
                signature(
                    "base.txt",
                    """
                        // Signature format: 2.0
                        package test.pkg {
                          public class Base {
                            ctor public Base();
                            method public void baseMethod();
                          }
                          public class Test {
                            ctor public Test();
                          }
                        }
                    """
                ),
                signature(
                    "current.txt",
                    """
                        // Signature format: 2.0
                        package test.pkg {
                          public class Test {
                            method public void method();
                          }
                        }
                    """
                ),
            ),
            inputSet(
                listOf(
                    java(
                        """
                            package test.pkg;

                            public class Base {
                                public Base() {}
                                public void baseMethod() {}
                            }
                        """
                    ),
                    java(
                        """
                            package test.pkg;

                            public class Test {
                                public Test() {}

                                $SYSTEM_API
                                public void method() {}
                            }
                        """
                    ),
                ) + TestableApiSurfaces.annotationSources
            ),
            testFixture = testFixture,
        ) {
            val expected =
                """
                    package test.pkg
                           self - ApiVariantSet[public(C),system(C)]
                      class test.pkg.Base
                             self - ApiVariantSet[public(C)]
                        constructor test.pkg.Base()
                               self - ApiVariantSet[public(C)]
                        method test.pkg.Base.baseMethod()
                               self - ApiVariantSet[public(C)]
                      class test.pkg.Test
                             self - ApiVariantSet[public(C)]
                          content - ApiVariantSet[system(C)]
                        constructor test.pkg.Test()
                               self - ApiVariantSet[public(C)]
                        method test.pkg.Test.method()
                               self - ApiVariantSet[system(C)]
                """
            // Verify that accessing package itemApiVariants and class contentApiVariants directly
            // (before dumpSelectedApiVariants traverses the codebase) lazily initializes child
            // SelectedApi instances.
            val isSourceModel = codebaseCreatorHasCapability(Capability.HIDDEN_ITEMS)
            val pkg = codebase.assertPackage("test.pkg")
            // TODO(b/568477816): PackageSourceSelectedApi does not lazily initialize its classes'
            //  SelectedApi instances when itemApiVariants is accessed, so for un-snapshotted source
            //  models it is empty until the classes and their members are visited.
            pkg.assertItemApiVariants(
                if (isSourceModel) "ApiVariantSet[]" else "ApiVariantSet[public(C),system(C)]"
            )
            val testClass = codebase.assertClass("test.pkg.Test")
            // TODO(b/568477816): ClassSourceSelectedApi does not lazily initialize its members'
            //  SelectedApi instances when contentApiVariants is accessed, so for un-snapshotted
            //  source models it is empty until the members are visited.
            assertEquals(
                if (isSourceModel) "ApiVariantSet[]" else "ApiVariantSet[system(C)]",
                testClass.selectedApi.contentApiVariants.formatFor(codebase.apiSurfaces),
            )

            codebase.assertSelectedApiVariants(expected)
        }
    }

    /**
     * Tests that accessing [SelectedApi.itemApiVariants] directly on a Kotlin file facade class and
     * its containing package lazily initializes the file facade's members and propagates their
     * variants.
     */
    @SupportedInputFormats(InputFormat.SIGNATURE, InputFormat.KOTLIN)
    @Test
    fun `Test file facade in single signature file and api surface`() {
        runCodebaseTest(
            signature(
                """
                    // Signature format: 5.0
                    package test.pkg {
                      public final class FooKt {
                        method public static void foo();
                      }
                    }
                """
            ),
            kotlin(
                "test/pkg/Foo.kt",
                """
                    package test.pkg
                    fun foo() {}
                """
            ),
        ) {
            val isSourceModel = codebaseCreatorHasCapability(Capability.HIDDEN_ITEMS)
            val fooKt = codebase.assertClass("test.pkg.FooKt")
            // TODO(b/568477816): ClassSourceSelectedApi does not lazily initialize its members'
            //  SelectedApi instances when itemApiVariants is accessed on a file facade class, so
            //  for un-snapshotted source models both the file facade class and its package have
            //  empty itemApiVariants.
            val expectedVariants =
                if (isSourceModel) "ApiVariantSet[]" else "ApiVariantSet[main(C)]"
            fooKt.assertItemApiVariants(expectedVariants)
            val pkg = codebase.assertPackage("test.pkg")
            pkg.assertItemApiVariants(expectedVariants)
        }
    }
}
