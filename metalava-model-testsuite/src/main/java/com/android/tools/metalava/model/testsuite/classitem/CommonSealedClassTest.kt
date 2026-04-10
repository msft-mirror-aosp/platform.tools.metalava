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
import com.android.tools.metalava.model.testing.classTypeItem
import com.android.tools.metalava.model.testsuite.BaseModelTest
import com.android.tools.metalava.testing.java
import com.android.tools.metalava.testing.kotlin
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.Test

class CommonSealedClassTest : BaseModelTest() {
    @Test
    fun `sealed class - basic`() {
        runCodebaseTest(
            signature(
                """
                    // Signature format: 2.0
                    package test.pkg {
                      public abstract sealed class SealedClass {
                      }
                      public static final class SealedClass.SubclassA extends test.pkg.SealedClass {
                      }
                      public static final class SealedClass.SubclassB extends test.pkg.SealedClass {
                      }
                    }
                """
            ),
            java(
                """
                    package test.pkg;

                    public sealed class SealedClass {
                        private SealedClass() {}
                        public static final class SubclassA extends SealedClass {
                            private SubclassA() {}
                        }
                        public static final class SubclassB extends SealedClass {
                            private SubclassB() {}
                        }
                    }
               """
            ),
            kotlin(
                """
                    package test.pkg

                    sealed class SealedClass private constructor() {
                        class SubclassA private constructor() : SealedClass() {}
                        class SubclassB private constructor() : SealedClass() {}
                    }
                """
            ),
        ) {
            val testClass = codebase.assertClass("test.pkg.SealedClass")
            assertTrue(testClass.modifiers.isSealed())
        }
    }

    @SupportedInputFormats(InputFormat.SIGNATURE, InputFormat.JAVA)
    @Test
    fun `sealed class - non-sealed - not kotlin`() {
        runCodebaseTest(
            signature(
                """
                    // Signature format: 2.0
                    package test.pkg {
                      public abstract sealed class SealedClass {
                      }
                      public static final class SealedClass.SubclassA extends test.pkg.SealedClass {
                      }
                      public static non-sealed class SealedClass.SubclassB extends test.pkg.SealedClass {
                      }
                    }
                """
            ),
            java(
                """
                    package test.pkg;

                    public sealed class SealedClass {
                        private SealedClass() {}
                        public static final class SubclassA extends SealedClass {
                            private SubclassA() {}
                        }
                        public static non-sealed class SubclassB extends SealedClass {
                            private SubclassB() {}
                        }
                    }
               """
            ),
        ) {
            val testClass = codebase.assertClass("test.pkg.SealedClass")
            assertTrue(testClass.modifiers.isSealed())

            val subclassB = codebase.assertClass("test.pkg.SealedClass.SubclassB")
            assertTrue(subclassB.modifiers.isNonSealed())
        }
    }

    @SupportedInputFormats(InputFormat.SIGNATURE, InputFormat.JAVA)
    @Test
    fun `sealed class - explicit permits - not kotlin`() {
        runCodebaseTest(
            inputSet(
                signature(
                    """
                        // Signature format: 2.0
                        package test.pkg {
                          public abstract sealed class SealedClass permits test.pkg.SubclassB, test.pkg.SubclassA {
                          }
                          public static final class SubclassA extends test.pkg.SealedClass {
                          }
                          public static final class SubclassB extends test.pkg.SealedClass {
                          }
                        }
                    """
                ),
            ),
            inputSet(
                java(
                    """
                        package test.pkg;

                        public sealed class SealedClass permits SubclassB, SubclassA {
                            private SealedClass() {}
                        }
                    """
                ),
                java(
                    """
                        package test.pkg;

                        public final class SubclassA extends SealedClass {
                            private SubclassA() {}
                        }
                    """
                ),
                java(
                    """
                        package test.pkg;

                        public final class SubclassB extends SealedClass {
                            private SubclassB() {}
                        }
                    """
                ),
            ),
        ) {
            val testClass = codebase.assertClass("test.pkg.SealedClass")

            val permits = testClass.permitTypes
            assertEquals(
                listOf(classTypeItem("test.pkg.SubclassA"), classTypeItem("test.pkg.SubclassB")),
                permits
            )
        }
    }

    @SupportedInputFormats(InputFormat.JAVA)
    @Test
    fun `sealed class - implicit permits - not kotlin`() {
        runCodebaseTest(
            inputSet(
                java(
                    """
                        package test.pkg;

                        public sealed class SealedClass {
                            private SealedClass() {}
                        }
                    """
                ),
                java(
                    """
                        package test.pkg;

                        public final class SubclassA extends SealedClass {
                            private SubclassA() {}
                        }
                    """
                ),
                java(
                    """
                        package test.pkg;

                        public final class SubclassB extends SealedClass {
                            private SubclassB() {}
                        }
                    """
                ),
            ),
        ) {
            val testClass = codebase.assertClass("test.pkg.SealedClass")

            val permits = testClass.permitTypes
            assertEquals(
                listOf(classTypeItem("test.pkg.SubclassA"), classTypeItem("test.pkg.SubclassB")),
                permits
            )
        }
    }

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
