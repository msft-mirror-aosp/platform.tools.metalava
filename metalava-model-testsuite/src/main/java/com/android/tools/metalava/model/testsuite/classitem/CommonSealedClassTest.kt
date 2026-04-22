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

package com.android.tools.metalava.model.testsuite.classitem

import com.android.tools.metalava.model.provider.InputFormat
import com.android.tools.metalava.model.testing.SupportedInputFormats
import com.android.tools.metalava.model.testsuite.BaseModelTest
import com.android.tools.metalava.testing.kotlin
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.Test

class CommonSealedClassTest : BaseModelTest() {
    /**
     * This test is to make sure that older signature files without any exhaustivity modifiers are
     * read as non-exhaustive.
     */
    @SupportedInputFormats(InputFormat.SIGNATURE, InputFormat.KOTLIN)
    @Test
    fun `modifiers show nonexhaustive when no exhaustivity modifier is present in signature`() {
        runCodebaseTest(
            inputSet(
                signature(
                    """
                        // Signature format: 2.0
                        package test.pkg {
                          public abstract sealed class SealedClass {
                          }
                          public sealed interface SealedInterface {
                          }
                        }
                    """
                ),
            ),
            inputSet(
                kotlin(
                    """
                        package test.pkg

                        sealed class SealedClass {}
                        private class PrivateChildClass : SealedClass()
                        sealed interface SealedInterface {}
                        private class PrivateInterfaceImplementor : SealedInterface
                    """
                ),
            ),
        ) {
            val testClass = codebase.assertClass("test.pkg.SealedClass")
            assertFalse(testClass.modifiers.isExhaustive())

            val testInterface = codebase.assertClass("test.pkg.SealedInterface")
            assertFalse(testInterface.modifiers.isExhaustive())
        }
    }

    @SupportedInputFormats(InputFormat.SIGNATURE, InputFormat.KOTLIN)
    @Test
    fun `modifiers show exhaustive when class or interface is marked as exhaustive in signature`() {
        runCodebaseTest(
            inputSet(
                signature(
                    """
                        // Signature format: 2.0
                        package test.pkg {
                          public abstract sealed exhaustive class SealedClass {
                          }
                          public sealed exhaustive interface SealedInterface {
                          }
                        }
                    """
                ),
            ),
            inputSet(
                kotlin(
                    """
                        package test.pkg

                        sealed class SealedClass {}
                        sealed interface SealedInterface {}
                    """
                ),
            ),
        ) {
            val testClass = codebase.assertClass("test.pkg.SealedClass")
            assertTrue(testClass.modifiers.isExhaustive())

            val testInterface = codebase.assertClass("test.pkg.SealedInterface")
            assertTrue(testInterface.modifiers.isExhaustive())
        }
    }

    @SupportedInputFormats(InputFormat.SIGNATURE, InputFormat.KOTLIN)
    @Test
    fun `modifiers show nonexhaustive when class or interface is marked as nonexhaustive in signature`() {
        runCodebaseTest(
            inputSet(
                signature(
                    """
                        // Signature format: 2.0
                        package test.pkg {
                          public abstract sealed nonexhaustive class SealedClass {
                          }
                          public sealed nonexhaustive interface SealedInterface {
                          }
                        }
                    """
                ),
            ),
            inputSet(
                kotlin(
                    """
                        package test.pkg

                        sealed class SealedClass {}
                        private class PrivateChildClass : SealedClass()

                        sealed interface SealedInterface
                        private class PrivateInterfaceImplementor : SealedInterface
                    """
                ),
            ),
        ) {
            val testClass = codebase.assertClass("test.pkg.SealedClass")
            assertFalse(testClass.modifiers.isExhaustive())
        }
    }
}
