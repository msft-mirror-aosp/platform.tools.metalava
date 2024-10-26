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

package com.android.tools.metalava

import com.android.tools.lint.checks.infrastructure.TestFile
import com.android.tools.metalava.model.Assertions
import com.android.tools.metalava.model.ClassItem
import com.android.tools.metalava.model.Codebase
import com.android.tools.metalava.model.MergedCodebase
import com.android.tools.metalava.model.MethodItem
import com.android.tools.metalava.model.text.ApiFile
import com.android.tools.metalava.model.text.SignatureFile
import com.android.tools.metalava.testing.TemporaryFolderOwner
import com.android.tools.metalava.testing.signature
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ComparisonVisitorTest : TemporaryFolderOwner, Assertions {
    @get:Rule override val temporaryFolder = TemporaryFolder()

    @Test
    fun `prefer first's real children even when first is only implied`() {
        val newSignatureFiles =
            listOf(
                SignatureFile.fromText(
                    "first.txt",
                    """
                        // Signature format: 2.0
                        package pkg {
                            public class Outer.Inner {
                                method public TypeInFirst foobar();
                            }
                        }
                    """
                ),
                SignatureFile.fromText(
                    "second.txt",
                    """
                        // Signature format: 2.0
                        package pkg {
                            public class Outer {
                            }
                            public class Outer.Inner {
                                method public TypeInSecond foobar();
                            }
                        }
                    """
                ),
            )
        val new =
            MergedCodebase(
                // Parse each signature file on its own.
                newSignatureFiles.map { ApiFile.parseApi(listOf(it)) }
            )

        val oldSignatureFile =
            SignatureFile.fromText(
                "old.txt",
                """
                    // Signature format: 2.0
                    package pkg {
                        public class Outer {
                        }
                        public class Outer.Inner {
                        }
                    }
                """
            )
        val old = MergedCodebase(listOf(ApiFile.parseApi(listOf(oldSignatureFile))))
        var methodType: String? = null
        CodebaseComparator()
            .compare(
                object : ComparisonVisitor() {
                    override fun addedMethodItem(new: MethodItem) {
                        methodType = new.type().toSimpleType()
                    }
                },
                old,
                new
            )
        assertEquals("TypeInFirst", methodType)
    }

    @Test
    fun `Test make sure that method with emit=false is ignored during comparison`() {

        fun TestFile.readCodebase(): Codebase {
            val signatureFiles = SignatureFile.fromFiles(createFile(temporaryFolder.root))
            return ApiFile.parseApi(signatureFiles, Codebase.Config.NOOP)
        }

        val signatureFile =
            signature(
                "old.txt",
                """
                        // Signature format: 2.0
                        package test.pkg {
                            public class Foo {
                                method public void foo();
                            }
                        }
                    """
            )

        // Create a codebase with a method that has emit = false
        val oldCodebase =
            signatureFile.readCodebase().apply {
                // Mark the method as emit=false
                assertClass("test.pkg.Foo").methods().forEach { it.emit = false }
            }

        // Create an identical codebase except that the method has emit = true, the default.
        val newCodebase = signatureFile.readCodebase()

        // Compare the two.
        val differences = mutableListOf<String>()
        CodebaseComparator()
            .compare(
                object : ComparisonVisitor() {
                    override fun compareMethodItems(old: MethodItem, new: MethodItem) {
                        differences += "$old was changed"
                    }

                    override fun addedMethodItem(new: MethodItem) {
                        differences += "$new was added"
                    }

                    override fun removedMethodItem(old: MethodItem, from: ClassItem?) {
                        differences += "$old was removed"
                    }
                },
                oldCodebase,
                newCodebase
            )
        // TODO(b/347885819): The method should be treated as being added not changed.
        assertEquals("method test.pkg.Foo.foo() was changed", differences.joinToString("\n"))
    }
}
