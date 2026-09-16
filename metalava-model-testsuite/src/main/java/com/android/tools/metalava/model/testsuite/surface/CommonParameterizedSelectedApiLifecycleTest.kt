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

import com.android.tools.metalava.model.api.flags.ApiFlag
import com.android.tools.metalava.model.api.flags.ApiFlagAction.REVERT
import com.android.tools.metalava.model.api.flags.ApiFlags
import com.android.tools.metalava.model.testing.surfaces.TestableApiSurfaces.DOC_ONLY
import com.android.tools.metalava.model.testing.surfaces.TestableApiSurfaces.REMOVED_FROM_API
import com.android.tools.metalava.model.testing.surfaces.TestableApiSurfaces.SYSTEM_API
import com.android.tools.metalava.model.testing.surfaces.TestableApiSurfaces.publicSystemModuleRules
import com.android.tools.metalava.testing.java
import org.junit.runners.Parameterized

/**
 * Tests verifying selected API variants for API lifecycle status, evolution, and stability.
 *
 * Add tests to this class for:
 * - `@doconly` annotations/tags and the `DOC_ONLY` API surface.
 * - `@removed` annotations/tags and the `REMOVED_FROM_API` API surface.
 * - Interactions between `doconly` and `removed` statuses.
 * - Flagged APIs using [ApiFlags] (e.g. `@FlaggedApi`).
 * - API stability and reverting flagged API items against previously released codebase sources.
 *
 * For tests focusing on general declaration visibility, see
 * [CommonParameterizedSelectedApiVisibilityTest].
 */
class CommonParameterizedSelectedApiLifecycleTest : BaseCommonParameterizedSelectedApiTest() {

    companion object : BaseCompanion() {
        @JvmStatic
        @Parameterized.Parameters
        fun params() = buildList {
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
        }
    }
}
