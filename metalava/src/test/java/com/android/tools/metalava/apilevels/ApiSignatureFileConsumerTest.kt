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

package com.android.tools.metalava.apilevels

import com.android.tools.metalava.model.CodebaseFragment
import com.android.tools.metalava.model.snapshot.EmittableDelegatingVisitor
import com.android.tools.metalava.model.text.ApiFile
import com.android.tools.metalava.model.text.SignatureFile
import java.io.PrintWriter
import java.io.StringWriter
import kotlin.test.assertEquals
import org.junit.Test

/** Unit tests for [ApiSignatureFileConsumer]. */
class ApiSignatureFileConsumerTest {

    /**
     * Populates an [Api] across [versions] using [ApiSignatureFileConsumer], verifies that its XML
     * output matches the XML produced by [ApiFile.parseApi] + [addApisFromCodebase], and optionally
     * asserts that the XML matches [expectedXml].
     */
    private fun checkSignatureFiles(
        vararg versions: String,
        expectedXml: String? = null,
    ) {
        val consumerApi = Api(useInternalNames = true)
        val codebaseApi = Api(useInternalNames = true)

        val versionedApis =
            versions.mapIndexed { index, contents ->
                val apiVersion = ApiVersion.fromLevel(index + 1)
                val updater = ApiHistoryUpdater.forApiVersion(apiVersion)
                val signatureFiles = listOf(SignatureFile.fromText("$apiVersion.txt", contents))

                ApiFile.consumeApi(
                    signatureFiles,
                    ApiSignatureFileConsumer(consumerApi, updater),
                )

                val codebase = ApiFile.parseApi(signatureFiles)
                val fragment =
                    CodebaseFragment.create(
                        codebase,
                        factory = ::EmittableDelegatingVisitor,
                    )
                addApisFromCodebase(codebaseApi, updater, fragment)

                VersionedSourceApi({ fragment }, apiVersion)
            }

        consumerApi.clean()
        codebaseApi.clean()

        val consumerXml = printXml(consumerApi, versionedApis)
        val codebaseXml = printXml(codebaseApi, versionedApis)

        if (expectedXml != null) {
            assertEquals(expectedXml.trimIndent(), consumerXml)
        }
        assertEquals(codebaseXml, consumerXml)
    }

    /** Formats [api] using [ApiXmlPrinter] with 4-space indentation. */
    private fun printXml(api: Api, versionedApis: List<VersionedApi>): String {
        val writer = StringWriter()
        val printer = ApiXmlPrinter(null, versionedApis)
        PrintWriter(writer).use { printWriter -> printer.print(api, printWriter) }
        return writer.toString().trimIndent().replace("\t", "    ")
    }

    @Test
    fun `Packages and top-level class kinds`() {
        checkSignatureFiles(
            """
                // Signature format: 5.0
                package java.lang {
                  public class Object {
                  }
                }
                package test.pkg {
                  public class Foo {
                  }
                  public interface Bar {
                  }
                  public enum Baz {
                  }
                  public @interface MyAnnotation {
                  }
                  public final record MyRecord {
                  }
                }
            """,
            expectedXml =
                """
                    <?xml version="1.0" encoding="utf-8"?>
                    <api version="3">
                        <class name="java/lang/Object" since="1">
                        </class>
                        <class name="test/pkg/Bar" since="1">
                            <extends name="java/lang/Object"/>
                        </class>
                        <class name="test/pkg/Baz" since="1">
                            <extends name="java/lang/Enum"/>
                            <method name="valueOf(Ljava/lang/String;)Ltest/pkg/Baz;"/>
                            <method name="values()[Ltest/pkg/Baz;"/>
                        </class>
                        <class name="test/pkg/Foo" since="1">
                            <extends name="java/lang/Object"/>
                        </class>
                        <class name="test/pkg/MyAnnotation" since="1">
                            <extends name="java/lang/Object"/>
                            <implements name="java/lang/annotation/Annotation"/>
                        </class>
                        <class name="test/pkg/MyRecord" since="1">
                            <extends name="java/lang/Record"/>
                        </class>
                    </api>
                """,
        )
    }

    @Test
    fun `Class deprecation and history across versions`() {
        checkSignatureFiles(
            """
                // Signature format: 2.0
                package test.pkg {
                  @SomeAnnotation("ignored") public class Foo {
                  }
                }
            """,
            """
                // Signature format: 2.0
                package test.pkg {
                  public deprecated class Foo {
                  }
                  public class Bar {
                  }
                }
            """,
            expectedXml =
                """
                    <?xml version="1.0" encoding="utf-8"?>
                    <api version="3">
                        <class name="test/pkg/Bar" since="2">
                            <extends name="java/lang/Object"/>
                        </class>
                        <class name="test/pkg/Foo" since="1" deprecated="2">
                            <extends name="java/lang/Object"/>
                        </class>
                    </api>
                """,
        )
    }
}
