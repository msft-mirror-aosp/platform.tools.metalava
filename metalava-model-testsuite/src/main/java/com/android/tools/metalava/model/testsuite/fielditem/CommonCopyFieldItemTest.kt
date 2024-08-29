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

package com.android.tools.metalava.model.testsuite.fielditem

import com.android.tools.metalava.model.ClassItem
import com.android.tools.metalava.model.Codebase
import com.android.tools.metalava.model.FieldItem
import com.android.tools.metalava.model.VisibilityLevel
import com.android.tools.metalava.model.testsuite.BaseModelTest
import com.android.tools.metalava.testing.java
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.Test

/** Common tests for [FieldItem.duplicate]. */
class CommonCopyFieldItemTest : BaseModelTest() {

    private class CopyContext(
        override val codebase: Codebase,
        val sourceClassItem: ClassItem,
        val targetClassItem: ClassItem,
        val sourceFieldItem: FieldItem,
        val targetFieldItem: FieldItem,
    ) : CodebaseContext<Codebase>

    private fun runCopyTest(
        vararg inputs: InputSet,
        test: CopyContext.() -> Unit,
    ) {
        runCodebaseTest(
            *inputs,
        ) {
            val sourceClassItem = codebase.assertClass("test.pkg.Source")
            val targetClassItem = codebase.assertClass("test.pkg.Target")

            val sourceFieldItem = sourceClassItem.assertField("field")
            val targetFieldItem = sourceFieldItem.duplicate(targetClassItem)

            val context =
                CopyContext(
                    codebase,
                    sourceClassItem,
                    targetClassItem,
                    sourceFieldItem,
                    targetFieldItem,
                )

            context.test()
        }
    }

    @Test
    fun `test copy field from interface to class uses public visibility`() {
        runCopyTest(
            inputSet(
                signature(
                    """
                        // Signature format: 2.0
                        package test.pkg {
                          public interface Source {
                            field public int field;
                          }
                          public class Target implements Source {
                          }
                        }
                    """
                ),
            ),
            inputSet(
                java(
                    """
                        package test.pkg;

                        import java.io.IOException;

                        public interface Source  {
                            int field;
                        }
                    """
                ),
                java(
                    """
                        package test.pkg;

                        public class Target implements Source {}
                    """
                ),
            ),
        ) {
            // Make sure that the visibility level is public.
            assertEquals(VisibilityLevel.PUBLIC, sourceFieldItem.modifiers.getVisibilityLevel())
            assertEquals(VisibilityLevel.PUBLIC, targetFieldItem.modifiers.getVisibilityLevel())
        }
    }

    @Test
    fun `test copy field from interface to class does copy static modifier`() {
        runCopyTest(
            inputSet(
                signature(
                    """
                        // Signature format: 2.0
                        package test.pkg {
                          public interface Source {
                            field public static int field;
                          }
                          public class Target implements Source {
                          }
                        }
                    """
                ),
            ),
            inputSet(
                java(
                    """
                        package test.pkg;

                        import java.io.IOException;

                        interface Source  {
                            static int field;
                        }
                    """
                ),
                java(
                    """
                        package test.pkg;

                        public class Target implements Source {}
                    """
                ),
            ),
        ) {
            // Make sure that the static modifier is copied from an interface field to a class.
            assertTrue(sourceFieldItem.modifiers.isStatic())
            assertTrue(targetFieldItem.modifiers.isStatic())
        }
    }

    @Test
    fun `test copy field from interface to class does set implicit static modifier`() {
        runCopyTest(
            inputSet(
                java(
                    """
                        package test.pkg;

                        import java.io.IOException;

                        interface Source  {
                            int field;
                        }
                    """
                ),
                java(
                    """
                        package test.pkg;

                        public class Target implements Source {}
                    """
                ),
            ),
        ) {
            // Make sure that the static modifier is copied from an interface field to a class.
            assertTrue(sourceFieldItem.modifiers.isStatic())
            assertTrue(targetFieldItem.modifiers.isStatic())
        }
    }
}
