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

    @Test
    fun `Fields and enum constants`() {
        checkSignatureFiles(
            """
                // Signature format: 2.0
                package test.pkg {
                  public class Foo {
                    field public static final String CONST = "hello";
                    field protected int count;
                    field int packagePrivateField;
                  }
                  public enum Status {
                    enum_constant public static final test.pkg.Status ACTIVE;
                    enum_constant public static final deprecated test.pkg.Status LEGACY;
                  }
                }
            """,
            """
                // Signature format: 2.0
                package test.pkg {
                  public deprecated class Foo {
                    field public static final String CONST = "hello";
                    field protected deprecated int count;
                    field public String addedInDeprecatedClass;
                  }
                  public enum Status {
                    enum_constant public static final test.pkg.Status ACTIVE;
                  }
                }
            """,
            expectedXml =
                """
                    <?xml version="1.0" encoding="utf-8"?>
                    <api version="3">
                        <class name="test/pkg/Foo" since="1" deprecated="2">
                            <extends name="java/lang/Object"/>
                            <field name="CONST"/>
                            <field name="addedInDeprecatedClass" since="2"/>
                            <field name="count"/>
                        </class>
                        <class name="test/pkg/Status" since="1">
                            <extends name="java/lang/Enum"/>
                            <method name="valueOf(Ljava/lang/String;)Ltest/pkg/Status;"/>
                            <method name="values()[Ltest/pkg/Status;"/>
                            <field name="ACTIVE"/>
                            <field name="LEGACY" deprecated="1" removed="2"/>
                        </class>
                    </api>
                """,
        )
    }

    @Test
    fun `Explicit extends and implements supertypes`() {
        checkSignatureFiles(
            """
                // Signature format: 2.0
                package test.pkg {
                  public class Base {
                  }
                  public interface FirstInterface {
                  }
                  public interface SecondInterface extends test.pkg.FirstInterface, Runnable {
                  }
                  public class Sub extends test.pkg.Base implements test.pkg.SecondInterface, Comparable<test.pkg.Sub> {
                  }
                }
            """,
            expectedXml =
                """
                    <?xml version="1.0" encoding="utf-8"?>
                    <api version="3">
                        <class name="test/pkg/Base" since="1">
                            <extends name="java/lang/Object"/>
                        </class>
                        <class name="test/pkg/FirstInterface" since="1">
                            <extends name="java/lang/Object"/>
                        </class>
                        <class name="test/pkg/SecondInterface" since="1">
                            <extends name="java/lang/Object"/>
                            <implements name="java/lang/Runnable"/>
                            <implements name="test/pkg/FirstInterface"/>
                        </class>
                        <class name="test/pkg/Sub" since="1">
                            <extends name="test/pkg/Base"/>
                            <implements name="java/lang/Comparable"/>
                            <implements name="test/pkg/SecondInterface"/>
                        </class>
                    </api>
                """,
        )
    }

    @Test
    fun `Constructors`() {
        checkSignatureFiles(
            """
                // Signature format: 4.0
                package test.pkg {
                  public class Foo {
                    ctor public Foo();
                    ctor protected Foo(int, String...);
                    ctor Foo(boolean);
                    ctor public deprecated Foo(String?[]);
                  }
                }
            """,
            expectedXml =
                """
                    <?xml version="1.0" encoding="utf-8"?>
                    <api version="3">
                        <class name="test/pkg/Foo" since="1">
                            <extends name="java/lang/Object"/>
                            <method name="&lt;init>()V"/>
                            <method name="&lt;init>(I[Ljava/lang/String;)V"/>
                            <method name="&lt;init>([Ljava/lang/String;)V" deprecated="1"/>
                        </class>
                    </api>
                """,
        )
    }

    @Test
    fun `Methods and enum synthetic methods`() {
        checkSignatureFiles(
            """
                // Signature format: 2.0
                package test.pkg {
                  public class Foo {
                    method public void doSomething();
                    method public String compute(int, boolean, String...);
                    method protected deprecated int[] legacyCompute(test.pkg.Foo);
                    method void packagePrivateMethod();
                  }
                }
            """,
            expectedXml =
                """
                    <?xml version="1.0" encoding="utf-8"?>
                    <api version="3">
                        <class name="test/pkg/Foo" since="1">
                            <extends name="java/lang/Object"/>
                            <method name="compute(IZ[Ljava/lang/String;)Ljava/lang/String;"/>
                            <method name="doSomething()V"/>
                            <method name="legacyCompute(Ltest/pkg/Foo;)[I" deprecated="1"/>
                        </class>
                    </api>
                """,
        )
    }
}
