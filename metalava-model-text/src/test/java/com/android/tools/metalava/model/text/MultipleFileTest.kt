/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.tools.metalava.model.text

import com.android.tools.lint.checks.infrastructure.TestFile
import kotlin.test.assertSame
import org.junit.Test

/** Contains tests for when loading multiple files into a single [TextCodebase]. */
class MultipleFileTest : BaseTextCodebaseTest() {

    @Test
    fun `Test parse multiple files correctly updates super class`() {
        val testFiles =
            listOf(
                signature(
                    "first.txt",
                    """
                        // Signature format: 2.0
                        package test.pkg {
                            public class Foo {
                            }
                        }
                    """
                ),
                signature(
                    "second.txt",
                    """
                        // Signature format: 2.0
                        package test.pkg {
                            public class Bar {
                            }
                            public class Foo extends test.pkg.Bar {
                            }
                        }
                    """
                ),
                signature(
                    "third.txt",
                    """
                        // Signature format: 2.0
                        package test.pkg {
                            public class Bar {
                            }
                            public class Baz {
                            }
                            public class Foo extends test.pkg.Baz {
                            }
                        }
                    """
                ),
            )

        fun checkSuperClass(files: List<TestFile>, order: String, expectedSuperClass: String) {
            runSignatureTest(*files.toTypedArray()) {
                val fooClass = codebase.assertClass("test.pkg.Foo")
                assertSame(
                    codebase.assertClass(expectedSuperClass),
                    fooClass.superClass(),
                    message = "incorrect super class from $order"
                )
            }
        }

        // Order matters, the last, non-null super class wins.
        checkSuperClass(testFiles, "narrowest to widest", "test.pkg.Baz")
        checkSuperClass(testFiles.reversed(), "widest to narrowest", "test.pkg.Bar")
    }
}
