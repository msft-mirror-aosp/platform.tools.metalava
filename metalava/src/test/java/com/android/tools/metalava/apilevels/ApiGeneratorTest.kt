/*
 * Copyright (C) 2018 The Android Open Source Project
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

import com.android.tools.metalava.ARG_ANDROID_JAR_PATTERN
import com.android.tools.metalava.ARG_API_VERSION_NAMES
import com.android.tools.metalava.ARG_API_VERSION_SIGNATURE_FILES
import com.android.tools.metalava.ARG_CURRENT_CODENAME
import com.android.tools.metalava.ARG_CURRENT_VERSION
import com.android.tools.metalava.ARG_FIRST_VERSION
import com.android.tools.metalava.ARG_GENERATE_API_LEVELS
import com.android.tools.metalava.ARG_GENERATE_API_VERSION_HISTORY
import com.android.tools.metalava.ARG_REMOVE_MISSING_CLASS_REFERENCES_IN_API_LEVELS
import com.android.tools.metalava.ARG_SDK_INFO_FILE
import com.android.tools.metalava.ARG_SDK_JAR_ROOT
import com.android.tools.metalava.DriverTest
import com.android.tools.metalava.testing.java
import com.android.tools.metalava.testing.kotlin
import com.android.tools.metalava.testing.signature
import com.google.common.truth.Truth.assertThat
import com.google.gson.GsonBuilder
import com.google.gson.JsonElement
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

// Constants to avoid having to quote $ in expected XML contents.
const val VERSION_CODES = "${'\$'}VERSION_CODES"
const val R = "${'\$'}R"
const val S = "${'\$'}S"

class ApiGeneratorTest : DriverTest() {

    /** Check this `api-versions.xml` file has the correct content. */
    private fun File.checkApiVersionsXmlContent(expectedContent: String) {
        assertTrue("$this was expected to be a plain file but is not", isFile)
        val xml = readText()

        // The generated XML is indented using tabs which do not work well in a raw string as
        // editors can replace them with normal spaces. So, this replaces all tabs with 4 spaces.
        val indentedWithSpaces = xml.replace("\t", "    ").trim()
        assertEquals(expectedContent.trimIndent(), indentedWithSpaces)
    }

    /** Check this `api-versions.json` file has the correct content. */
    private fun File.checkApiVersionsJsonContent(expectedContent: String) {
        assertTrue("$this was expected to be a plain file but is not", isFile)
        val json = readText()

        // Read output and reprint with pretty printing enabled to make test failures easier to read
        val gson = GsonBuilder().disableHtmlEscaping().setPrettyPrinting().create()
        val outputJson = gson.fromJson(json, JsonElement::class.java)
        val prettyOutput = gson.toJson(outputJson)

        assertEquals(expectedContent.trimIndent(), prettyOutput)
    }

    // TODO(b/378479241): Fix this test to make it more realistic by including definitions of the
    //  current API or stopping it from including the current API.
    @Test
    fun `Generate API for test prebuilts`() {
        val testPrebuiltsRoot = File(System.getenv("METALAVA_TEST_PREBUILTS_SDK_ROOT"))
        if (!testPrebuiltsRoot.isDirectory) {
            fail("test prebuilts not found: $testPrebuiltsRoot")
        }

        val apiVersionsXml = temporaryFolder.newFile("api-versions.xml")

        check(
            extraArguments =
                arrayOf(
                    ARG_GENERATE_API_LEVELS,
                    apiVersionsXml.path,
                    ARG_ANDROID_JAR_PATTERN,
                    "${testPrebuiltsRoot.path}/%/public/android.jar",
                    ARG_SDK_JAR_ROOT,
                    "${testPrebuiltsRoot.path}/extensions",
                    ARG_SDK_INFO_FILE,
                    "${testPrebuiltsRoot.path}/sdk-extensions-info.xml",
                    ARG_FIRST_VERSION,
                    "30",
                    ARG_CURRENT_VERSION,
                    "32",
                    ARG_CURRENT_CODENAME,
                    "Foo"
                ),
            // Apply the api-versions.xml file that is being generated by this.
            applyApiLevelsXml = apiVersionsXml.path,
            // Do not prevent java.lang.Object from being included in the API.
            skipEmitPackages = emptyList(),
            sourceFiles =
                arrayOf(
                    java(
                        """
                    package android.test;
                    public class ClassAddedInApi31AndExt2 {
                        private ClassAddedInApi31AndExt2() {}
                        public static final int FIELD_ADDED_IN_API_31_AND_EXT_2 = 1;
                        public static final int FIELD_ADDED_IN_EXT_3 = 2;
                        public void methodAddedInApi31AndExt2() { throw new RuntimeException("Stub!"); }
                        public void methodAddedInExt3() { throw new RuntimeException("Stub!"); }
                        public void methodNotFinalized() { throw new RuntimeException("Stub!"); }
                    }
                    """
                    ),
                    java(
                        """
                            package java.lang;
                            public class Object {
                            }
                        """
                    ),
                ),
            docStubs = true,
            stubFiles =
                arrayOf(
                    java(
                        """
                            package android.test;
                            /**
                             * @apiSince 31
                             * @sdkExtSince R Extensions 2
                             */
                            @SuppressWarnings({"unchecked", "deprecation", "all"})
                            public class ClassAddedInApi31AndExt2 {
                            ClassAddedInApi31AndExt2() { throw new RuntimeException("Stub!"); }
                            /**
                             * @apiSince 31
                             * @sdkExtSince R Extensions 2
                             */
                            public void methodAddedInApi31AndExt2() { throw new RuntimeException("Stub!"); }
                            /** @sdkExtSince R Extensions 3 */
                            public void methodAddedInExt3() { throw new RuntimeException("Stub!"); }
                            /** @apiSince Foo */
                            public void methodNotFinalized() { throw new RuntimeException("Stub!"); }
                            /**
                             * @apiSince 31
                             * @sdkExtSince R Extensions 2
                             */
                            public static final int FIELD_ADDED_IN_API_31_AND_EXT_2 = 1; // 0x1
                            /** @sdkExtSince R Extensions 3 */
                            public static final int FIELD_ADDED_IN_EXT_3 = 2; // 0x2
                            }
                        """
                    ),
                ),
        )

        val expected =
            """
                <?xml version="1.0" encoding="utf-8"?>
                <api version="3" min="30">
                    <sdk id="30" shortname="R-ext" name="R Extensions" reference="android/os/Build$VERSION_CODES$R"/>
                    <sdk id="31" shortname="S-ext" name="S Extensions" reference="android/os/Build$VERSION_CODES$S"/>
                    <class name="android/test/ClassAddedAndDeprecatedInApi30" since="30" deprecated="30" removed="33">
                        <extends name="java/lang/Object"/>
                        <method name="&lt;init>(F)V"/>
                        <method name="&lt;init>(I)V"/>
                        <method name="methodExplicitlyDeprecated()V"/>
                        <method name="methodImplicitlyDeprecated()V"/>
                        <field name="FIELD_EXPLICITLY_DEPRECATED"/>
                        <field name="FIELD_IMPLICITLY_DEPRECATED"/>
                    </class>
                    <class name="android/test/ClassAddedInApi30" module="framework-ext" since="30" sdks="30:2,0:30">
                        <extends name="android/test/MarkerSuperClass" since="33" sdks="30:2,31:2"/>
                        <extends name="java/lang/Object" removed="33"/>
                        <implements name="android/test/MarkerInterface" since="33" sdks="30:2,31:2"/>
                        <method name="methodAddedInApi30()V"/>
                        <method name="methodAddedInApi31()V" since="31" sdks="30:2,31:2,0:31"/>
                    </class>
                    <class name="android/test/ClassAddedInApi31AndExt2" module="framework-ext" since="31" sdks="30:2,31:2,0:31">
                        <extends name="java/lang/Object"/>
                        <method name="methodAddedInApi31AndExt2()V"/>
                        <method name="methodAddedInExt3()V" since="33" sdks="30:3,31:3"/>
                        <method name="methodNotFinalized()V" since="33" sdks="0:33"/>
                        <field name="FIELD_ADDED_IN_API_31_AND_EXT_2"/>
                        <field name="FIELD_ADDED_IN_EXT_3" since="33" sdks="30:3,31:3"/>
                    </class>
                    <class name="android/test/ClassAddedInExt1" module="framework-ext" since="31" sdks="30:1,31:1,0:31">
                        <extends name="java/lang/Object"/>
                        <method name="methodAddedInApi31AndExt2()V" sdks="30:2,31:2,0:31"/>
                        <method name="methodAddedInExt1()V"/>
                        <method name="methodAddedInExt3()V" since="33" sdks="30:3,31:3"/>
                        <field name="FIELD_ADDED_IN_API_31_AND_EXT_2" sdks="30:2,31:2,0:31"/>
                        <field name="FIELD_ADDED_IN_EXT_1"/>
                        <field name="FIELD_ADDED_IN_EXT_3" since="33" sdks="30:3,31:3"/>
                    </class>
                    <class name="android/test/ClassAddedInExt3" module="framework-ext" since="33" sdks="30:3,31:3">
                        <extends name="java/lang/Object"/>
                        <method name="methodAddedInExt3()V"/>
                        <field name="FIELD_ADDED_IN_EXT_3"/>
                    </class>
                    <class name="android/test/MarkerInterface" module="framework-ext" since="33" sdks="30:2,31:2">
                        <extends name="java/lang/Object"/>
                    </class>
                    <class name="android/test/MarkerSuperClass" module="framework-ext" since="33" sdks="30:2,31:2">
                        <extends name="java/lang/Object"/>
                        <method name="&lt;init>()V"/>
                    </class>
                    <class name="java/lang/Object" since="30">
                        <method name="&lt;init>()V"/>
                    </class>
                </api>
            """

        apiVersionsXml.checkApiVersionsXmlContent(expected)
    }

    @Test
    fun `Generate API while removing missing class references`() {
        val apiVersionsXml = temporaryFolder.newFile("api-versions.xml")

        check(
            extraArguments =
                arrayOf(
                    ARG_GENERATE_API_LEVELS,
                    apiVersionsXml.path,
                    ARG_REMOVE_MISSING_CLASS_REFERENCES_IN_API_LEVELS,
                    ARG_FIRST_VERSION,
                    "30",
                    ARG_CURRENT_VERSION,
                    "32",
                    ARG_CURRENT_CODENAME,
                    "Foo"
                ),
            sourceFiles =
                arrayOf(
                    java(
                        """
                    package android.test;
                    public class ClassThatImplementsMethodFromApex implements ClassFromApex {
                    }
                    """
                    )
                )
        )

        val expected =
            """
            <?xml version="1.0" encoding="utf-8"?>
            <api version="3" min="33">
                <class name="android/test/ClassThatImplementsMethodFromApex" since="33">
                    <method name="&lt;init>()V"/>
                </class>
            </api>
        """

        apiVersionsXml.checkApiVersionsXmlContent(expected)
    }

    @Test
    fun `Generate API finds missing class references`() {
        val testPrebuiltsRoot = File(System.getenv("METALAVA_TEST_PREBUILTS_SDK_ROOT"))
        if (!testPrebuiltsRoot.isDirectory) {
            fail("test prebuilts not found: $testPrebuiltsRoot")
        }

        val apiVersionsXml = temporaryFolder.newFile("api-versions.xml")

        var exception: IllegalStateException? = null
        try {
            check(
                extraArguments =
                    arrayOf(
                        ARG_GENERATE_API_LEVELS,
                        apiVersionsXml.path,
                        ARG_FIRST_VERSION,
                        "30",
                        ARG_CURRENT_VERSION,
                        "32",
                        ARG_CURRENT_CODENAME,
                        "Foo"
                    ),
                sourceFiles =
                    arrayOf(
                        java(
                            """
                        package android.test;
                        // Really this class should implement some interface that doesn't exist,
                        // but that's hard to set up in the test harness, so just verify that
                        // metalava complains about java/lang/Object not existing because we didn't
                        // include the testdata prebuilt jars.
                        public class ClassThatImplementsMethodFromApex {
                        }
                        """
                        )
                    )
            )
        } catch (e: IllegalStateException) {
            exception = e
        }

        assertNotNull(exception)
        assertThat(exception?.message ?: "")
            .contains(
                "There are classes in this API that reference other classes that do not exist in this API."
            )
        assertThat(exception?.message ?: "")
            .contains(
                "java/lang/Object referenced by:\n    android/test/ClassThatImplementsMethodFromApex"
            )
    }

    @Test
    fun `Create JSON and XML API versions from signature files`() {
        val pastVersions =
            listOf(
                createTextFile(
                    "1.1.0",
                    """
                        // Signature format: 2.0
                        package test.pkg {
                          public class Foo {
                            method public <T extends java.lang.String> void methodV1(T);
                            field public int fieldV1;
                          }
                          public class Foo.Bar {
                          }
                        }
                    """
                ),
                createTextFile(
                    "1.2.0",
                    """
                        // Signature format: 2.0
                        package test.pkg {
                          public class Foo {
                            method public <T extends java.lang.String> void methodV1(T);
                            method @Deprecated public <T> void methodV2(String);
                            method @Deprecated public <T> void methodV2(String, int);
                            field public int fieldV1;
                            field public int fieldV2;
                          }
                          public class Foo.Bar {
                          }
                        }
                    """
                ),
                createTextFile(
                    "1.3.0",
                    """
                        // Signature format: 2.0
                        package test.pkg {
                          public class Foo {
                            method @Deprecated public <T extends java.lang.String> void methodV1(T);
                            method public <T> void methodV2(String);
                            method public void methodV3();
                            field public int fieldV1;
                            field public int fieldV2;
                          }
                          @Deprecated public class Foo.Bar {
                          }
                        }
                    """
                ),
            )
        val currentVersion =
            """
                package test.pkg {
                  public class Foo {
                    method @Deprecated public <T extends java.lang.String> void methodV1(T);
                    method public void methodV3();
                    method public void methodV4();
                    field public int fieldV1;
                    field public int fieldV2;
                  }
                  @Deprecated public class Foo.Bar {
                  }
                }
            """

        val apiVersionsJson = temporaryFolder.newFile("api-info.json")

        fun createExtraArguments(output: File) =
            arrayOf(
                ARG_GENERATE_API_VERSION_HISTORY,
                output.path,
                ARG_API_VERSION_SIGNATURE_FILES,
                pastVersions.joinToString(":") { it.absolutePath },
                ARG_API_VERSION_NAMES,
                listOf("1.1.0", "1.2.0", "1.3.0", "1.4.0").joinToString(" "),
            )

        check(
            signatureSource = currentVersion,
            extraArguments = createExtraArguments(apiVersionsJson),
        )

        apiVersionsJson.checkApiVersionsJsonContent(
            """
                [
                  {
                    "class": "test.pkg.Foo.Bar",
                    "addedIn": "1.1.0",
                    "deprecatedIn": "1.3.0",
                    "methods": [],
                    "fields": []
                  },
                  {
                    "class": "test.pkg.Foo",
                    "addedIn": "1.1.0",
                    "methods": [
                      {
                        "method": "methodV1<T extends java.lang.String>(T)",
                        "addedIn": "1.1.0",
                        "deprecatedIn": "1.3.0"
                      },
                      {
                        "method": "methodV2<T>(java.lang.String)",
                        "addedIn": "1.2.0"
                      },
                      {
                        "method": "methodV2<T>(java.lang.String,int)",
                        "addedIn": "1.2.0",
                        "deprecatedIn": "1.2.0"
                      },
                      {
                        "method": "methodV3()",
                        "addedIn": "1.3.0"
                      },
                      {
                        "method": "methodV4()",
                        "addedIn": "1.4.0"
                      }
                    ],
                    "fields": [
                      {
                        "field": "fieldV2",
                        "addedIn": "1.2.0"
                      },
                      {
                        "field": "fieldV1",
                        "addedIn": "1.1.0"
                      }
                    ]
                  }
                ]
            """
        )

        val apiVersionsXml = temporaryFolder.newFile("api-versions.xml")

        check(
            signatureSource = currentVersion,
            extraArguments = createExtraArguments(apiVersionsXml),
        )

        apiVersionsXml.checkApiVersionsXmlContent(
            """
                <?xml version="1.0" encoding="utf-8"?>
                <api version="3" min="1.1.0">
                    <class name="test.pkg.Foo" since="1.1.0">
                        <extends name="java.lang.Object"/>
                        <method name="methodV1&lt;T extends java.lang.String>(T)" deprecated="1.3.0"/>
                        <method name="methodV2&lt;T>(java.lang.String)" since="1.2.0" removed="1.4.0"/>
                        <method name="methodV2&lt;T>(java.lang.String,int)" since="1.2.0" deprecated="1.2.0" removed="1.3.0"/>
                        <method name="methodV3()" since="1.3.0"/>
                        <method name="methodV4()" since="1.4.0"/>
                        <field name="fieldV1"/>
                        <field name="fieldV2" since="1.2.0"/>
                    </class>
                    <class name="test.pkg.Foo.Bar" since="1.1.0" deprecated="1.3.0">
                        <extends name="java.lang.Object"/>
                    </class>
                </api>
            """
        )
    }

    @Test
    fun `Ensure class missing in current codebase is marked as removed`() {
        val pastVersions =
            listOf(
                createTextFile(
                    "1.1.0",
                    """
                        // Signature format: 2.0
                        package test.pkg {
                          public class Bar {
                            field public int barField;
                          }
                          public class Foo extends test.pkg.Bar {
                            field public int fooField;
                          }
                        }
                    """
                ),
            )
        val currentVersion =
            """
                // Signature format: 2.0
                package test.pkg {
                  public class Foo {
                  }
                }
            """

        val apiVersionsXml = temporaryFolder.newFile("api-versions.xml")

        check(
            signatureSource = currentVersion,
            extraArguments =
                arrayOf(
                    ARG_GENERATE_API_VERSION_HISTORY,
                    apiVersionsXml.path,
                    ARG_API_VERSION_SIGNATURE_FILES,
                    pastVersions.joinToString(":") { it.absolutePath },
                    ARG_API_VERSION_NAMES,
                    listOf("1.1.0", "1.2.0").joinToString(" "),
                ),
        )

        apiVersionsXml.checkApiVersionsXmlContent(
            """
                <?xml version="1.0" encoding="utf-8"?>
                <api version="3" min="1.1.0">
                    <class name="test.pkg.Bar" since="1.1.0" removed="1.2.0">
                        <extends name="java.lang.Object"/>
                        <field name="barField"/>
                    </class>
                    <class name="test.pkg.Foo" since="1.1.0">
                        <extends name="java.lang.Object" since="1.2.0"/>
                        <extends name="test.pkg.Bar" removed="1.2.0"/>
                        <field name="fooField" removed="1.2.0"/>
                    </class>
                </api>
            """
        )
    }

    @Test
    fun `Correct error with different number of API signature files and API version names`() {
        val output = temporaryFolder.newFile("api-info.json")

        val filePaths =
            listOf("1.1.0", "1.2.0", "1.3.0").map { name ->
                val file = createTextFile("$name.txt", "")
                file.path
            }

        check(
            extraArguments =
                arrayOf(
                    ARG_GENERATE_API_VERSION_HISTORY,
                    output.path,
                    ARG_API_VERSION_SIGNATURE_FILES,
                    filePaths.joinToString(":"),
                    ARG_API_VERSION_NAMES,
                    listOf("1.1.0", "1.2.0").joinToString(" ")
                ),
            expectedFail =
                "Aborting: --api-version-names must have one more version than --api-version-signature-files to include the current version name"
        )
    }

    @Test
    fun `API levels can be generated from just the current codebase`() {
        val output = temporaryFolder.newFile("api-info.json")

        val api =
            """
                // Signature format: 3.0
                package test.pkg {
                  public class Foo {
                    method public void foo(String?);
                  }
                }
            """

        check(
            signatureSource = api,
            extraArguments =
                arrayOf(
                    ARG_GENERATE_API_VERSION_HISTORY,
                    output.path,
                    ARG_API_VERSION_NAMES,
                    "0.0.0-alpha01"
                )
        )

        val expectedJson =
            "[{\"class\":\"test.pkg.Foo\",\"addedIn\":\"0.0.0-alpha01\",\"methods\":[{\"method\":\"foo(java.lang.String)\",\"addedIn\":\"0.0.0-alpha01\"}],\"fields\":[]}]"
        assertEquals(expectedJson, output.readText())
    }

    @Test
    fun `API levels using source as current version does not include inherited methods excluded from signatures`() {
        val apiVersionsJson = temporaryFolder.newFile("api-info.json")

        val pastVersions =
            listOf(
                createTextFile(
                    "1.1.0",
                    """
                        // Signature format: 4.0
                        package test.pkg {
                          public class Bar extends test.pkg.Foo {
                            ctor public Bar();
                          }
                          public class Foo {
                            ctor public Foo();
                            method public void inherited();
                          }
                        }
                    """
                )
            )
        val currentVersion =
            arrayOf(
                java(
                    """
                        package test.pkg;

                        public class Foo {
                            public void inherited() {}
                        }
                    """
                ),
                java(
                    """
                        package test.pkg;

                        public class Bar extends Foo {
                            @Override
                            public void inherited() {}
                        }
                    """
                )
            )

        check(
            sourceFiles = currentVersion,
            extraArguments =
                arrayOf(
                    ARG_GENERATE_API_VERSION_HISTORY,
                    apiVersionsJson.path,
                    ARG_API_VERSION_SIGNATURE_FILES,
                    pastVersions.joinToString(":") { it.absolutePath },
                    ARG_API_VERSION_NAMES,
                    listOf("1.1.0", "1.2.0").joinToString(" ")
                )
        )

        apiVersionsJson.checkApiVersionsJsonContent(
            """
                [
                  {
                    "class": "test.pkg.Bar",
                    "addedIn": "1.1.0",
                    "methods": [
                      {
                        "method": "Bar()",
                        "addedIn": "1.1.0"
                      }
                    ],
                    "fields": []
                  },
                  {
                    "class": "test.pkg.Foo",
                    "addedIn": "1.1.0",
                    "methods": [
                      {
                        "method": "Foo()",
                        "addedIn": "1.1.0"
                      },
                      {
                        "method": "inherited()",
                        "addedIn": "1.1.0"
                      }
                    ],
                    "fields": []
                  }
                ]
            """
        )
    }

    @Test
    fun `APIs annotated with suppress-compatibility-meta-annotations appear in output`() {
        val apiVersionsJson = temporaryFolder.newFile("api-info.json")

        val pastVersions =
            listOf(
                createTextFile(
                    "1.1.0.txt",
                    """
                        // Signature format: 4.0
                        package test.pkg {
                          public final class Foo {
                            ctor public Foo();
                            method @SuppressCompatibility @kotlin.RequiresOptIn public void experimentalFunction();
                            method public void regularFunction();
                          }
                        }
                    """
                )
            )
        val currentVersion =
            arrayOf(
                kotlin(
                    """
                        package test.pkg
                        class Foo {
                            @RequiresOptIn fun experimentalFunction() {}
                            @RequiresOptIn fun newExperimentalFunction() {}
                            fun regularFunction() {}
                        }
                    """
                )
            )

        check(
            sourceFiles = currentVersion,
            suppressCompatibilityMetaAnnotations = arrayOf("kotlin.RequiresOptIn"),
            extraArguments =
                arrayOf(
                    ARG_GENERATE_API_VERSION_HISTORY,
                    apiVersionsJson.path,
                    ARG_API_VERSION_SIGNATURE_FILES,
                    pastVersions.joinToString(":") { it.absolutePath },
                    ARG_API_VERSION_NAMES,
                    listOf("1.1.0", "1.2.0").joinToString(" ")
                )
        )

        apiVersionsJson.checkApiVersionsJsonContent(
            """
                [
                  {
                    "class": "test.pkg.Foo",
                    "addedIn": "1.1.0",
                    "methods": [
                      {
                        "method": "experimentalFunction()",
                        "addedIn": "1.1.0"
                      },
                      {
                        "method": "Foo()",
                        "addedIn": "1.1.0"
                      },
                      {
                        "method": "newExperimentalFunction()",
                        "addedIn": "1.2.0"
                      },
                      {
                        "method": "regularFunction()",
                        "addedIn": "1.1.0"
                      }
                    ],
                    "fields": []
                  }
                ]
            """
        )
    }

    private fun createTextFile(name: String, contents: String) =
        signature(name, contents).createFile(temporaryFolder.newFolder())
}
