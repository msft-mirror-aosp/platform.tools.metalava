/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.tools.metalava.model.testsuite.methoditem

import com.android.tools.metalava.model.source.SourceLanguage
import com.android.tools.metalava.model.testsuite.BaseModelTest
import com.android.tools.metalava.testing.KnownSourceFiles
import com.android.tools.metalava.testing.java
import com.android.tools.metalava.testing.kotlin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/** Common tests for implementations of [ParameterItem]. */
@RunWith(Parameterized::class)
class CommonParameterItemTest : BaseModelTest() {

    @Test
    fun `Test deprecated parameter by annotation`() {
        runCodebaseTest(
            signature(
                """
                    // Signature format: 2.0
                    package test.pkg {
                      public class Bar {
                        method public void foo(@Deprecated int);
                      }
                    }
                """
            ),
            java(
                """
                    package test.pkg;

                    public class Bar {
                        public void foo(@Deprecated int i) {}
                    }
                """
            ),
            kotlin(
                """
                    package test.pkg

                    class Bar {
                        fun foo(@Deprecated i: Int) {}
                    }
                """
            ),
        ) {
            val parameterItem =
                codebase.assertClass("test.pkg.Bar").methods().single().parameters().single()
            val annotation = parameterItem.modifiers.annotations().single()
            if (inputFormat.sourceLanguage == SourceLanguage.KOTLIN) {
                assertEquals("kotlin.Deprecated", annotation.qualifiedName)
            } else {
                assertEquals("java.lang.Deprecated", annotation.qualifiedName)
            }
            assertEquals("deprecated", true, parameterItem.deprecated)
            assertEquals("originallyDeprecated", true, parameterItem.originallyDeprecated)
        }
    }

    @Test
    fun `Test not deprecated parameter`() {
        runCodebaseTest(
            signature(
                """
                    // Signature format: 2.0
                    package test.pkg {
                      public class Bar {
                        method public void foo(int);
                      }
                    }
                """
            ),
            java(
                """
                    package test.pkg;

                    public class Bar {
                        public void foo(int i) {}
                    }
                """
            ),
            kotlin(
                """
                    package test.pkg

                    class Bar {
                        fun foo(i: Int) {}
                    }
                """
            ),
        ) {
            val parameterItem =
                codebase.assertClass("test.pkg.Bar").methods().single().parameters().single()
            assertEquals("deprecated", false, parameterItem.deprecated)
            assertEquals("originallyDeprecated", false, parameterItem.originallyDeprecated)
        }
    }

    @Test
    fun `Test publicName reports correct name when specified`() {
        runCodebaseTest(
            inputSet(
                signature(
                    """
                        // Signature format: 2.0
                        package test.pkg {
                          public class Bar {
                            method public void foo(int baz);
                          }
                        }
                    """
                ),
            ),
            inputSet(
                KnownSourceFiles.supportParameterName,
                java(
                    """
                        package test.pkg;

                        import androidx.annotation.ParameterName;

                        public class Bar {
                            public void foo(@ParameterName("baz") int baz) {}
                        }
                    """
                ),
            ),
            inputSet(
                kotlin(
                    """
                        package test.pkg

                        class Bar {
                            fun foo(baz: Int) {}
                        }
                    """
                ),
            ),
        ) {
            val parameterItem =
                codebase.assertClass("test.pkg.Bar").methods().single().parameters().single()
            assertEquals("name()", "baz", parameterItem.name())
            assertEquals("publicName()", "baz", parameterItem.publicName())
        }
    }

    @Test
    fun `Test publicName reports correct name when not specified`() {
        runCodebaseTest(
            signature(
                """
                    // Signature format: 2.0
                    package test.pkg {
                      public class Bar {
                        method public void foo(int);
                      }
                    }
                """
            ),
            java(
                """
                    package test.pkg;

                    public class Bar {
                        public void foo(int baz) {}
                    }
                """
            ),
            // Kotlin treats all parameter names as public.
        ) {
            val parameterItem =
                codebase.assertClass("test.pkg.Bar").methods().single().parameters().single()
            assertNull("publicName()", parameterItem.publicName())
        }
    }

    @Test
    fun `Test publicName reports correct name when called on binary class`() {
        runCodebaseTest(
            java(
                """
                    package test.pkg;

                    public class Bar {
                    }
                """
            ),
            // Kotlin treats all parameter names as public.
        ) {
            val parameterItem =
                codebase
                    .assertClass("java.lang.Object")
                    .assertMethod("equals", "java.lang.Object")
                    .parameters()
                    .single()
            assertEquals("name()", "obj", parameterItem.name())
            assertEquals("publicName()", "obj", parameterItem.publicName())
        }
    }
}
