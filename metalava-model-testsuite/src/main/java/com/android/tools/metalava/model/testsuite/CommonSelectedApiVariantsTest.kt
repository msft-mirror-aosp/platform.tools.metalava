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
import com.android.tools.metalava.model.provider.InputFormat
import com.android.tools.metalava.model.testing.SupportedInputFormats
import com.android.tools.metalava.model.testing.surfaces.TestableApiSurfaces
import com.android.tools.metalava.model.testing.surfaces.TestableApiSurfaces.SYSTEM_API
import com.android.tools.metalava.model.testing.surfaces.TestableApiSurfaces.publicSystemModuleRules
import com.android.tools.metalava.testing.java
import com.android.tools.metalava.testing.kotlin
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
     * Tests [SelectedApi] variant calculation across multiple API surfaces (`public` and `system`)
     * for signature files and Java source files.
     *
     * In this setup:
     * - `Base` and its members (`Base()` and `baseMethod()`) belong to `public`.
     * - `Test` class and constructor `Test()` belong to `public`.
     * - `Test.method()` is annotated with `$SYSTEM_API` (or defined in `current.txt`), belonging to
     *   `system`.
     *
     * In Java source files:
     * - `Test` class has `public` in [SelectedApi.itemApiVariants] and `system` in
     *   [SelectedApi.contentApiVariants] because it contains a method belonging to `system`.
     * - Package `test.pkg` has both `public` and `system` in [SelectedApi.itemApiVariants] because
     *   it contains items belonging to both surfaces.
     *
     * Note: Signature file loading currently differs from source loading in that delta signature
     * files do not yet propagate `content` variants to containing classes or multi-surface `self`
     * variants to packages.
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
            // TODO: Signature and source should have the same expected variants.
            val expected =
                when (inputFormat) {
                    InputFormat.SIGNATURE ->
                        """
                            package test.pkg
                                   self - ApiVariantSet[public(C)]
                              class test.pkg.Base
                                     self - ApiVariantSet[public(C)]
                                constructor test.pkg.Base()
                                       self - ApiVariantSet[public(C)]
                                method test.pkg.Base.baseMethod()
                                       self - ApiVariantSet[public(C)]
                              class test.pkg.Test
                                     self - ApiVariantSet[public(C)]
                                constructor test.pkg.Test()
                                       self - ApiVariantSet[public(C)]
                                method test.pkg.Test.method()
                                       self - ApiVariantSet[system(C)]
                        """
                    InputFormat.JAVA ->
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
                    else -> error("unsupported input format: $inputFormat")
                }
            codebase.assertSelectedApiVariants(expected)
        }
    }
}
