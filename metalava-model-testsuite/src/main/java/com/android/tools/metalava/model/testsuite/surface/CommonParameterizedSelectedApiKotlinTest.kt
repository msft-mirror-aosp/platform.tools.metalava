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

import com.android.tools.metalava.model.KOTLIN_PUBLISHED_API
import com.android.tools.metalava.model.api.ApiSurfaceRules
import com.android.tools.metalava.model.api.SurfaceSelectionRule
import com.android.tools.metalava.model.testing.surfaces.TestableApiSurfaces.HIDE
import com.android.tools.metalava.model.testing.surfaces.TestableApiSurfaces.MODULE_API
import com.android.tools.metalava.model.testing.surfaces.TestableApiSurfaces.MODULE_API_NON_RECURSIVE
import com.android.tools.metalava.model.testing.surfaces.TestableApiSurfaces.PUBLIC_API
import com.android.tools.metalava.model.testing.surfaces.TestableApiSurfaces.SYSTEM_API
import com.android.tools.metalava.model.testing.surfaces.TestableApiSurfaces.publicSystemModuleRules
import com.android.tools.metalava.testing.java
import com.android.tools.metalava.testing.kotlin
import org.junit.runners.Parameterized

/**
 * Tests verifying selected API variants for Kotlin-specific language features.
 *
 * Add tests to this class for:
 * - Kotlin `internal` visibility declarations and how they interact with show annotations.
 * - Kotlin `@PublishedApi` annotations on classes and methods (both when `@PublishedApi` is
 *   configured as a show annotation and when it is not).
 * - Kotlin properties with exposed backing fields (e.g. `const val` or `@JvmField`), especially
 *   when the backing field has explicit hide annotations (e.g. `@field:Hide`).
 * - Kotlin properties with private backing fields with explicit hide annotations.
 *
 * For Java-only or language-agnostic visibility tests, see
 * [CommonParameterizedSelectedApiVisibilityTest].
 */
class CommonParameterizedSelectedApiKotlinTest : BaseCommonParameterizedSelectedApiTest() {

    companion object : BaseCompanion() {
        @JvmStatic
        @Parameterized.Parameters
        fun params() = buildList {
            val publishedApiSources =
                listOf(
                    kotlin(
                        """
                            package test.pkg

                            @PublishedApi
                            internal class PublishedClass {
                                fun method() {}
                            }

                            class PublicClass {
                                @PublishedApi
                                internal fun publishedMethod() {}

                                internal fun internalMethod() {}
                            }
                        """
                    ),
                )

            buildTests(
                name = "PublishedApi when not a show annotation",
                surfaceRules = publicSystemModuleRules,
                sources = publishedApiSources,
            ) {
                surfaceTest(
                    surface = "public",
                    expected =
                        """
                            package test.pkg
                                   self - ApiVariantSet[public(C)]
                              class test.pkg.PublishedClass
                                     self - ApiVariantSet[public(C)]
                                constructor test.pkg.PublishedClass()
                                       self - ApiVariantSet[]
                                method test.pkg.PublishedClass.method()
                                       self - ApiVariantSet[public(C)]
                              class test.pkg.PublicClass
                                     self - ApiVariantSet[public(C)]
                                constructor test.pkg.PublicClass()
                                       self - ApiVariantSet[public(C)]
                                method test.pkg.PublicClass.publishedMethod()
                                       self - ApiVariantSet[public(C)]
                                method test.pkg.PublicClass.internalMethod${'$'}src()
                                       self - ApiVariantSet[]
                        """,
                )
            }

            val publishedApiRules =
                ApiSurfaceRules(
                    publicSystemModuleRules.apiSurfaces,
                    mapOf(
                        "public" to
                            listOf(
                                SurfaceSelectionRule.unannotated,
                                SurfaceSelectionRule.createAnnotationRule(
                                    HIDE.qualifiedName,
                                    effect = SurfaceSelectionRule.Effect.HIDE,
                                ),
                                SurfaceSelectionRule.createAnnotationRule(PUBLIC_API.qualifiedName),
                                SurfaceSelectionRule.createAnnotationRule(KOTLIN_PUBLISHED_API),
                            ),
                        "system" to
                            listOf(
                                SurfaceSelectionRule.createAnnotationRule(SYSTEM_API.qualifiedName),
                            ),
                        "module" to
                            listOf(
                                SurfaceSelectionRule.createAnnotationRule(MODULE_API.qualifiedName),
                                SurfaceSelectionRule.createAnnotationRule(
                                    MODULE_API_NON_RECURSIVE.qualifiedName,
                                    recursive = false,
                                ),
                            ),
                    ),
                )

            buildTests(
                name = "PublishedApi when a show annotation",
                surfaceRules = publishedApiRules,
                sources = publishedApiSources,
            ) {
                surfaceTest(
                    surface = "public",
                    expected =
                        """
                            package test.pkg
                                   self - ApiVariantSet[public(C)]
                              class test.pkg.PublishedClass
                                     self - ApiVariantSet[public(C)]
                                constructor test.pkg.PublishedClass()
                                       self - ApiVariantSet[]
                                method test.pkg.PublishedClass.method()
                                       self - ApiVariantSet[public(C)]
                              class test.pkg.PublicClass
                                     self - ApiVariantSet[public(C)]
                                constructor test.pkg.PublicClass()
                                       self - ApiVariantSet[public(C)]
                                method test.pkg.PublicClass.publishedMethod()
                                       self - ApiVariantSet[public(C)]
                                method test.pkg.PublicClass.internalMethod${'$'}src()
                                       self - ApiVariantSet[]
                        """,
                )
            }

            val showOnInternalSources =
                listOf(
                    java(
                        """
                            package test.api;
                            public @interface PublicApi {}
                        """
                    ),
                    kotlin(
                        """
                            package test.pkg

                            class PublicClass {
                                $PUBLIC_API
                                internal fun showMethod() {}

                                $PUBLIC_API
                                internal val showProperty: Int = 0

                                internal fun internalMethod() {}
                            }

                            $PUBLIC_API
                            internal class ShowClass {
                                fun method() {}
                            }
                        """
                    ),
                )

            // There is no point in including an internal API in the API surface if it is not
            // annotated with kotlin.PublishedApi because without that it cannot be called
            // outside the API surface anyway.
            buildTests(
                name = "show annotation on internal declaration",
                surfaceRules = publicSystemModuleRules,
                sources = showOnInternalSources,
            ) {
                surfaceTest(
                    surface = "public",
                    expected =
                        """
                            package test.api
                                   self - ApiVariantSet[public(C)]
                              class test.api.PublicApi
                                     self - ApiVariantSet[public(C)]
                            package test.pkg
                                   self - ApiVariantSet[public(C)]
                              class test.pkg.PublicClass
                                     self - ApiVariantSet[public(C)]
                                constructor test.pkg.PublicClass()
                                       self - ApiVariantSet[public(C)]
                                method test.pkg.PublicClass.showMethod${'$'}src()
                                       self - ApiVariantSet[]
                                method test.pkg.PublicClass.getShowProperty${'$'}src()
                                       self - ApiVariantSet[]
                                method test.pkg.PublicClass.internalMethod${'$'}src()
                                       self - ApiVariantSet[]
                                property test.pkg.PublicClass#showProperty
                                       self - ApiVariantSet[]
                                field test.pkg.PublicClass.showProperty
                                       self - ApiVariantSet[]
                              class test.pkg.ShowClass
                                     self - ApiVariantSet[]
                                constructor test.pkg.ShowClass()
                                       self - ApiVariantSet[]
                                method test.pkg.ShowClass.method()
                                       self - ApiVariantSet[]
                        """,
                )
            }

            buildTests(
                name = "property with hidden backing field",
                surfaceRules = publicSystemModuleRules,
                sources =
                    listOf(
                        java(
                            """
                                package test.api;
                                public @interface Hide {}
                            """
                        ),
                        kotlin(
                            """
                                package test.pkg
                                import ${HIDE.qualifiedName}
                                class Foo {
                                    @field:Hide
                                    @JvmField
                                    val bar: Int = 0
                                }
                            """
                        ),
                    ),
            ) {
                surfaceTest(
                    surface = "public",
                    expected =
                        """
                            package test.api
                                   self - ApiVariantSet[public(C)]
                              class test.api.Hide
                                     self - ApiVariantSet[public(C)]
                            package test.pkg
                                   self - ApiVariantSet[public(C)]
                              class test.pkg.Foo
                                     self - ApiVariantSet[public(C)]
                                constructor test.pkg.Foo()
                                       self - ApiVariantSet[public(C)]
                                property test.pkg.Foo#bar
                                       self - ApiVariantSet[]
                                field test.pkg.Foo.bar
                                       self - ApiVariantSet[]
                        """,
                )
            }

            buildTests(
                name = "property with hidden private backing field",
                surfaceRules = publicSystemModuleRules,
                sources =
                    listOf(
                        java(
                            """
                                package test.api;
                                public @interface Hide {}
                            """
                        ),
                        kotlin(
                            """
                                package test.pkg
                                import ${HIDE.qualifiedName}
                                class Foo {
                                    @field:Hide
                                    val bar: Int = 0
                                }
                            """
                        ),
                    ),
            ) {
                surfaceTest(
                    surface = "public",
                    expected =
                        """
                            package test.api
                                   self - ApiVariantSet[public(C)]
                              class test.api.Hide
                                     self - ApiVariantSet[public(C)]
                            package test.pkg
                                   self - ApiVariantSet[public(C)]
                              class test.pkg.Foo
                                     self - ApiVariantSet[public(C)]
                                constructor test.pkg.Foo()
                                       self - ApiVariantSet[public(C)]
                                method test.pkg.Foo.getBar()
                                       self - ApiVariantSet[public(C)]
                                property test.pkg.Foo#bar
                                       self - ApiVariantSet[]
                                field test.pkg.Foo.bar
                                       self - ApiVariantSet[]
                        """,
                )
            }

            buildTests(
                name = "property in hidden class with field suppression",
                surfaceRules = publishedApiRules,
                sources =
                    listOf(
                        java(
                            """
                                package test.api;
                                public @interface Hide {}
                            """
                        ),
                        kotlin(
                            """
                                package test.pkg
                                import ${HIDE.qualifiedName}

                                @Hide
                                class Foo {
                                    @field:Suppress("ShowingMemberInHiddenClass")
                                    @JvmField
                                    @PublishedApi
                                    internal var bar: Int = -1
                                }
                            """
                        ),
                    ),
            ) {
                surfaceTest(
                    surface = "public",
                    expected =
                        """
                            package test.api
                                   self - ApiVariantSet[public(C)]
                              class test.api.Hide
                                     self - ApiVariantSet[public(C)]
                            package test.pkg
                                   self - ApiVariantSet[]
                              class test.pkg.Foo
                                     self - ApiVariantSet[]
                                constructor test.pkg.Foo()
                                       self - ApiVariantSet[]
                                property test.pkg.Foo#bar
                                       self - ApiVariantSet[]
                                field test.pkg.Foo.bar
                                       self - ApiVariantSet[]
                        """,
                )
            }
        }
    }
}
