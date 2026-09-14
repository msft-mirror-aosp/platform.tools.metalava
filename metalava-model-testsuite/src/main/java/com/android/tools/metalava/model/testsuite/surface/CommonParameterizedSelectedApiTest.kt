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
import com.android.tools.metalava.model.api.flags.ApiFlag
import com.android.tools.metalava.model.api.flags.ApiFlagAction.*
import com.android.tools.metalava.model.api.flags.ApiFlags
import com.android.tools.metalava.model.testing.surfaces.TestableApiSurfaces.DOC_ONLY
import com.android.tools.metalava.model.testing.surfaces.TestableApiSurfaces.HIDE
import com.android.tools.metalava.model.testing.surfaces.TestableApiSurfaces.MODULE_API
import com.android.tools.metalava.model.testing.surfaces.TestableApiSurfaces.MODULE_API_NON_RECURSIVE
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
import com.android.tools.metalava.testing.java
import com.android.tools.metalava.testing.kotlin
import org.junit.runners.Parameterized

class CommonParameterizedSelectedApiTest : BaseCommonParameterizedSelectedApiTest() {

    companion object : BaseCompanion() {
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
                name =
                    "public class overriding method from superclass marked as @Hide with specialized return type",
                surfaceRules = publicSystemModuleRules,
                sources =
                    listOf(
                        java(
                            """
                                package test.pkg;

                                public abstract class Base<T> {
                                    public abstract T method();
                                }
                            """
                        ),
                        java(
                            """
                                package test.pkg;

                                public class Middle extends Base<String> {
                                    $HIDE
                                    @Override
                                    public String method() {
                                        return null;
                                    }
                                }
                            """
                        ),
                        java(
                            """
                                package test.pkg;

                                public class Sub extends Middle {
                                    @Override
                                    public String method() {
                                        return null;
                                    }
                                }
                            """
                        ),
                    ),
            ) {
                // Middle.method() is marked @Hide and overrides a class method, so it does
                // not inherit the public(C) API variant from Base.method().
                surfaceTest(
                    surface = "module",
                    expected =
                        """
                            package test.pkg
                                   self - ApiVariantSet[public(C)]
                                content - ApiVariantSet[]
                              class test.pkg.Base
                                     self - ApiVariantSet[public(C)]
                                  content - ApiVariantSet[]
                                constructor test.pkg.Base()
                                       self - ApiVariantSet[public(C)]
                                    content - ApiVariantSet[]
                                method test.pkg.Base.method()
                                       self - ApiVariantSet[public(C)]
                                    content - ApiVariantSet[]
                              class test.pkg.Middle
                                     self - ApiVariantSet[public(C)]
                                  content - ApiVariantSet[]
                                constructor test.pkg.Middle()
                                       self - ApiVariantSet[public(C)]
                                    content - ApiVariantSet[]
                                method test.pkg.Middle.method()
                                       self - ApiVariantSet[]
                                    content - ApiVariantSet[]
                              class test.pkg.Sub
                                     self - ApiVariantSet[public(C)]
                                  content - ApiVariantSet[]
                                constructor test.pkg.Sub()
                                       self - ApiVariantSet[public(C)]
                                    content - ApiVariantSet[]
                                method test.pkg.Sub.method()
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
                                content - ApiVariantSet[]
                              class test.pkg.PublishedClass
                                     self - ApiVariantSet[]
                                  content - ApiVariantSet[]
                                constructor test.pkg.PublishedClass()
                                       self - ApiVariantSet[]
                                    content - ApiVariantSet[]
                                method test.pkg.PublishedClass.method()
                                       self - ApiVariantSet[]
                                    content - ApiVariantSet[]
                              class test.pkg.PublicClass
                                     self - ApiVariantSet[public(C)]
                                  content - ApiVariantSet[]
                                constructor test.pkg.PublicClass()
                                       self - ApiVariantSet[public(C)]
                                    content - ApiVariantSet[]
                                method test.pkg.PublicClass.publishedMethod()
                                       self - ApiVariantSet[]
                                    content - ApiVariantSet[]
                                method test.pkg.PublicClass.internalMethod${'$'}src()
                                       self - ApiVariantSet[]
                                    content - ApiVariantSet[]
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
                                content - ApiVariantSet[]
                              class test.pkg.PublishedClass
                                     self - ApiVariantSet[public(C)]
                                  content - ApiVariantSet[]
                                constructor test.pkg.PublishedClass()
                                       self - ApiVariantSet[]
                                    content - ApiVariantSet[]
                                method test.pkg.PublishedClass.method()
                                       self - ApiVariantSet[public(C)]
                                    content - ApiVariantSet[]
                              class test.pkg.PublicClass
                                     self - ApiVariantSet[public(C)]
                                  content - ApiVariantSet[]
                                constructor test.pkg.PublicClass()
                                       self - ApiVariantSet[public(C)]
                                    content - ApiVariantSet[]
                                method test.pkg.PublicClass.publishedMethod()
                                       self - ApiVariantSet[public(C)]
                                    content - ApiVariantSet[]
                                method test.pkg.PublicClass.internalMethod${'$'}src()
                                       self - ApiVariantSet[]
                                    content - ApiVariantSet[]
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
                                content - ApiVariantSet[]
                              class test.api.PublicApi
                                     self - ApiVariantSet[public(C)]
                                  content - ApiVariantSet[]
                            package test.pkg
                                   self - ApiVariantSet[public(C)]
                                content - ApiVariantSet[]
                              class test.pkg.PublicClass
                                     self - ApiVariantSet[public(C)]
                                  content - ApiVariantSet[]
                                constructor test.pkg.PublicClass()
                                       self - ApiVariantSet[public(C)]
                                    content - ApiVariantSet[]
                                method test.pkg.PublicClass.showMethod${'$'}src()
                                       self - ApiVariantSet[public(C)]
                                    content - ApiVariantSet[]
                                method test.pkg.PublicClass.getShowProperty${'$'}src()
                                       self - ApiVariantSet[public(C)]
                                    content - ApiVariantSet[]
                                method test.pkg.PublicClass.internalMethod${'$'}src()
                                       self - ApiVariantSet[]
                                    content - ApiVariantSet[]
                                property test.pkg.PublicClass#showProperty
                                       self - ApiVariantSet[public(C)]
                                    content - ApiVariantSet[]
                                field test.pkg.PublicClass.showProperty
                                       self - ApiVariantSet[]
                                    content - ApiVariantSet[]
                              class test.pkg.ShowClass
                                     self - ApiVariantSet[public(C)]
                                  content - ApiVariantSet[]
                                constructor test.pkg.ShowClass()
                                       self - ApiVariantSet[]
                                    content - ApiVariantSet[]
                                method test.pkg.ShowClass.method()
                                       self - ApiVariantSet[public(C)]
                                    content - ApiVariantSet[]
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
                                content - ApiVariantSet[]
                              class test.api.Hide
                                     self - ApiVariantSet[public(C)]
                                  content - ApiVariantSet[]
                            package test.pkg
                                   self - ApiVariantSet[public(C)]
                                content - ApiVariantSet[]
                              class test.pkg.Foo
                                     self - ApiVariantSet[public(C)]
                                  content - ApiVariantSet[]
                                constructor test.pkg.Foo()
                                       self - ApiVariantSet[public(C)]
                                    content - ApiVariantSet[]
                                property test.pkg.Foo#bar
                                       self - ApiVariantSet[]
                                    content - ApiVariantSet[]
                                field test.pkg.Foo.bar
                                       self - ApiVariantSet[]
                                    content - ApiVariantSet[]
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
                                content - ApiVariantSet[]
                              class test.api.Hide
                                     self - ApiVariantSet[public(C)]
                                  content - ApiVariantSet[]
                            package test.pkg
                                   self - ApiVariantSet[public(C)]
                                content - ApiVariantSet[]
                              class test.pkg.Foo
                                     self - ApiVariantSet[public(C)]
                                  content - ApiVariantSet[]
                                constructor test.pkg.Foo()
                                       self - ApiVariantSet[public(C)]
                                    content - ApiVariantSet[]
                                method test.pkg.Foo.getBar()
                                       self - ApiVariantSet[public(C)]
                                    content - ApiVariantSet[]
                                property test.pkg.Foo#bar
                                       self - ApiVariantSet[]
                                    content - ApiVariantSet[]
                                field test.pkg.Foo.bar
                                       self - ApiVariantSet[]
                                    content - ApiVariantSet[]
                        """,
                )
            }
        }
    }
}
