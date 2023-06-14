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

import com.android.sdklib.SdkVersionInfo
import com.android.tools.lint.detector.api.ApiConstraint
import com.android.tools.metalava.ARG_ANDROID_JAR_PATTERN
import com.android.tools.metalava.ARG_API_VERSION_NAMES
import com.android.tools.metalava.ARG_API_VERSION_SIGNATURE_FILES
import com.android.tools.metalava.ARG_CURRENT_CODENAME
import com.android.tools.metalava.ARG_CURRENT_VERSION
import com.android.tools.metalava.ARG_FIRST_VERSION
import com.android.tools.metalava.ARG_GENERATE_API_LEVELS
import com.android.tools.metalava.ARG_GENERATE_API_VERSION_HISTORY
import com.android.tools.metalava.DriverTest
import com.android.tools.metalava.getApiLookup
import com.android.tools.metalava.java
import com.google.gson.GsonBuilder
import com.google.gson.JsonElement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test
import java.io.File
import kotlin.text.Charsets.UTF_8

class ApiGeneratorTest : DriverTest() {
    companion object {
        // As per ApiConstraint that uses a bit vector, API has to be between 1..61.
        private const val MAGIC_VERSION_INT = 57 // [SdkVersionInfo.MAX_LEVEL] - 4
        private const val MAGIC_VERSION_STR = MAGIC_VERSION_INT.toString()

        @JvmStatic
        @BeforeClass
        fun beforeClass() {
            assert(MAGIC_VERSION_INT > SdkVersionInfo.HIGHEST_KNOWN_API)
            // Trigger <clinit> of [SdkApiConstraint] to call `isValidApiLevel` in its companion
            ApiConstraint.UNKNOWN
            // This checks if MAGIC_VERSION_INT is not bigger than [SdkVersionInfo.MAX_LEVEL]
            assert(ApiConstraint.SdkApiConstraint.isValidApiLevel(MAGIC_VERSION_INT))
        }
    }

    @Test
    fun `Extract API levels`() {
        var oldSdkJars = File("prebuilts/tools/common/api-versions")
        if (!oldSdkJars.isDirectory) {
            oldSdkJars = File("../../prebuilts/tools/common/api-versions")
            if (!oldSdkJars.isDirectory) {
                println("Ignoring ${ApiGeneratorTest::class.java}: prebuilts not found - is \$PWD set to an Android source tree?")
                return
            }
        }

        var platformJars = File("prebuilts/sdk")
        if (!platformJars.isDirectory) {
            platformJars = File("../../prebuilts/sdk")
            if (!platformJars.isDirectory) {
                println("Ignoring ${ApiGeneratorTest::class.java}: prebuilts not found: $platformJars")
                return
            }
        }
        val output = File.createTempFile("api-info", "xml")
        output.deleteOnExit()
        val outputPath = output.path

        check(
            extraArguments = arrayOf(
                ARG_GENERATE_API_LEVELS,
                outputPath,
                ARG_ANDROID_JAR_PATTERN,
                "${oldSdkJars.path}/android-%/android.jar",
                ARG_ANDROID_JAR_PATTERN,
                "${platformJars.path}/%/public/android.jar",
                ARG_CURRENT_CODENAME,
                "Z",
                ARG_CURRENT_VERSION,
                MAGIC_VERSION_STR // not real api level of Z
            ),
            sourceFiles = arrayOf(
                java(
                    """
                    package android.pkg;
                    public class MyTest {
                    }
                    """
                )
            )
        )

        assertTrue(output.isFile)

        val xml = output.readText(UTF_8)
        val nextVersion = MAGIC_VERSION_INT + 1
        assertTrue(xml.contains("<class name=\"android/Manifest\$permission\" since=\"1\">"))
        assertTrue(xml.contains("<field name=\"BIND_CARRIER_MESSAGING_SERVICE\" since=\"22\" deprecated=\"23\"/>"))
        assertTrue(xml.contains("<class name=\"android/pkg/MyTest\" since=\"$nextVersion\""))
        assertFalse(xml.contains("<implements name=\"java/lang/annotation/Annotation\" removed=\""))
        assertFalse(xml.contains("<extends name=\"java/lang/Enum\" removed=\""))
        assertFalse(xml.contains("<method name=\"append(C)Ljava/lang/AbstractStringBuilder;\""))

        // Also make sure package private super classes are pruned
        assertFalse(xml.contains("<extends name=\"android/icu/util/CECalendar\""))

        val apiLookup = getApiLookup(output, temporaryFolder.newFolder())

        // Make sure we're really using the correct database, not the SDK one. (This placeholder
        // class is provided as a source file above.)
        @Suppress("DEPRECATION")
        assertEquals(nextVersion, apiLookup.getClassVersion("android.pkg.MyTest"))

        @Suppress("DEPRECATION")
        apiLookup.getClassVersion("android.v")
        @Suppress("DEPRECATION")
        assertEquals(5, apiLookup.getFieldVersion("android.Manifest\$permission", "AUTHENTICATE_ACCOUNTS"))

        @Suppress("DEPRECATION")
        val methodVersion = apiLookup.getMethodVersion("android/icu/util/CopticCalendar", "computeTime", "()")
        assertEquals(24, methodVersion)
    }

    @Test
    fun `Extract System API`() {
        // These are the wrong jar paths but this test doesn't actually care what the
        // content of the jar files, just checking the logic of starting the database
        // at some higher number than 1
        var platformJars = File("prebuilts/sdk")
        if (!platformJars.isDirectory) {
            platformJars = File("../../prebuilts/sdk")
            if (!platformJars.isDirectory) {
                println("Ignoring ${ApiGeneratorTest::class.java}: prebuilts not found: $platformJars")
                return
            }
        }

        val output = File.createTempFile("api-info", "xml")
        output.deleteOnExit()
        val outputPath = output.path

        check(
            extraArguments = arrayOf(
                ARG_GENERATE_API_LEVELS,
                outputPath,
                ARG_ANDROID_JAR_PATTERN,
                "${platformJars.path}/%/public/android.jar",
                ARG_FIRST_VERSION,
                "21"
            )
        )

        assertTrue(output.isFile)
        val xml = output.readText(UTF_8)
        assertTrue(xml.contains("<api version=\"2\" min=\"21\">"))
        assertTrue(xml.contains("<class name=\"android/Manifest\" since=\"21\">"))
        assertTrue(xml.contains("<field name=\"showWhenLocked\" since=\"27\"/>"))

        val apiLookup = getApiLookup(output)
        @Suppress("DEPRECATION")
        apiLookup.getClassVersion("android.v")
        // This field was added in API level 5, but when we're starting the count higher
        // (as in the system API), the first introduced API level is the one we use
        @Suppress("DEPRECATION")
        assertEquals(21, apiLookup.getFieldVersion("android.Manifest\$permission", "AUTHENTICATE_ACCOUNTS"))

        @Suppress("DEPRECATION")
        val methodVersion = apiLookup.getMethodVersion("android/icu/util/CopticCalendar", "computeTime", "()")
        assertEquals(24, methodVersion)
    }

    @Test
    fun `Correct API Level for release`() {
        var oldSdkJars = File("prebuilts/tools/common/api-versions")
        if (!oldSdkJars.isDirectory) {
            oldSdkJars = File("../../prebuilts/tools/common/api-versions")
            if (!oldSdkJars.isDirectory) {
                println("Ignoring ${ApiGeneratorTest::class.java}: prebuilts not found - is \$PWD set to an Android source tree?")
                return
            }
        }

        var platformJars = File("prebuilts/sdk")
        if (!platformJars.isDirectory) {
            platformJars = File("../../prebuilts/sdk")
            if (!platformJars.isDirectory) {
                println("Ignoring ${ApiGeneratorTest::class.java}: prebuilts not found: $platformJars")
                return
            }
        }
        val output = File.createTempFile("api-info", "xml")
        output.deleteOnExit()
        val outputPath = output.path

        check(
            extraArguments = arrayOf(
                ARG_GENERATE_API_LEVELS,
                outputPath,
                ARG_ANDROID_JAR_PATTERN,
                "${oldSdkJars.path}/android-%/android.jar",
                ARG_ANDROID_JAR_PATTERN,
                "${platformJars.path}/%/public/android.jar",
                ARG_CURRENT_CODENAME,
                "REL",
                ARG_CURRENT_VERSION,
                MAGIC_VERSION_STR // not real api level
            ),
            sourceFiles = arrayOf(
                java(
                    """
                        package android.pkg;
                        public class MyTest {
                        }
                        """
                )
            )
        )

        assertTrue(output.isFile)
        // Anything with a REL codename is in the current API level
        val xml = output.readText(UTF_8)
        assertTrue(xml.contains("<class name=\"android/pkg/MyTest\" since=\"$MAGIC_VERSION_STR\""))
        val apiLookup = getApiLookup(output, temporaryFolder.newFolder())
        @Suppress("DEPRECATION")
        assertEquals(MAGIC_VERSION_INT, apiLookup.getClassVersion("android.pkg.MyTest"))
    }

    @Test
    fun `Correct API Level for non-release`() {
        var oldSdkJars = File("prebuilts/tools/common/api-versions")
        if (!oldSdkJars.isDirectory) {
            oldSdkJars = File("../../prebuilts/tools/common/api-versions")
            if (!oldSdkJars.isDirectory) {
                println("Ignoring ${ApiGeneratorTest::class.java}: prebuilts not found - is \$PWD set to an Android source tree?")
                return
            }
        }

        var platformJars = File("prebuilts/sdk")
        if (!platformJars.isDirectory) {
            platformJars = File("../../prebuilts/sdk")
            if (!platformJars.isDirectory) {
                println("Ignoring ${ApiGeneratorTest::class.java}: prebuilts not found: $platformJars")
                return
            }
        }
        val output = File.createTempFile("api-info", "xml")
        output.deleteOnExit()
        val outputPath = output.path

        check(
            extraArguments = arrayOf(
                ARG_GENERATE_API_LEVELS,
                outputPath,
                ARG_ANDROID_JAR_PATTERN,
                "${oldSdkJars.path}/android-%/android.jar",
                ARG_ANDROID_JAR_PATTERN,
                "${platformJars.path}/%/public/android.jar",
                ARG_CURRENT_CODENAME,
                "ZZZ", // not just Z, but very ZZZ
                ARG_CURRENT_VERSION,
                MAGIC_VERSION_STR // not real api level
            ),
            sourceFiles = arrayOf(
                java(
                    """
                        package android.pkg;
                        public class MyTest {
                        }
                        """
                )
            )
        )

        assertTrue(output.isFile)
        // Metalava should understand that a codename means "current api + 1"
        val nextVersion = MAGIC_VERSION_INT + 1
        val xml = output.readText(UTF_8)
        assertTrue(xml.contains("<class name=\"android/pkg/MyTest\" since=\"$nextVersion\""))
        val apiLookup = getApiLookup(output, temporaryFolder.newFolder())
        @Suppress("DEPRECATION")
        assertEquals(nextVersion, apiLookup.getClassVersion("android.pkg.MyTest"))
    }

    @Test
    fun `Create API levels from signature files`() {
        val output = File.createTempFile("api-info", ".json")
        output.deleteOnExit()
        val outputPath = output.path

        val versions = listOf(
            createTextFile(
                "1.1.0",
                """
                    package test.pkg {
                      public class Foo {
                        method public <T extends java.lang.String> void methodV1(T);
                        field public int fieldV1;
                      }
                      public class Foo.Bar {
                      }
                    }
                """.trimIndent()
            ),
            createTextFile(
                "1.2.0",
                """
                    package test.pkg {
                      public class Foo {
                        method public <T extends java.lang.String> void methodV1(T);
                        method @Deprecated public <T> void methodV2(String, int);
                        field public int fieldV1;
                        field public int fieldV2;
                      }
                      public class Foo.Bar {
                      }
                    }
                """.trimIndent()
            ),
            createTextFile(
                "1.3.0",
                """
                    package test.pkg {
                      public class Foo {
                        method @Deprecated public <T extends java.lang.String> void methodV1(T);
                        method public void methodV3();
                        field public int fieldV1;
                        field public int fieldV2;
                      }
                      @Deprecated public class Foo.Bar {
                      }
                    }
                """.trimIndent()
            )
        )

        check(
            extraArguments = arrayOf(
                ARG_GENERATE_API_VERSION_HISTORY,
                outputPath,
                ARG_API_VERSION_SIGNATURE_FILES,
                versions.joinToString(":") { it.absolutePath },
                ARG_API_VERSION_NAMES,
                listOf("1.1.0", "1.2.0", "1.3.0").joinToString(" ")
            )
        )

        assertTrue(output.isFile)

        // Read output and reprint with pretty printing enabled to make test failures easier to read
        val gson = GsonBuilder().disableHtmlEscaping().setPrettyPrinting().create()
        val outputJson = gson.fromJson(output.readText(), JsonElement::class.java)
        val prettyOutput = gson.toJson(outputJson)
        assertEquals(
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
                        "method": "methodV2<T>(java.lang.String,int)",
                        "addedIn": "1.2.0",
                        "deprecatedIn": "1.2.0"
                      },
                      {
                        "method": "methodV3()",
                        "addedIn": "1.3.0"
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
            """.trimIndent(),
            prettyOutput
        )
    }

    @Test
    fun `Correct error with different number of API signature files and API version names`() {
        val output = File.createTempFile("api-info", ".json")
        output.deleteOnExit()
        val outputPath = output.path

        val filePaths = listOf("1.1.0", "1.2.0", "1.3.0").map { name ->
            val file = File.createTempFile(name, ".txt")
            file.deleteOnExit()
            file.path
        }

        check(
            extraArguments = arrayOf(
                ARG_GENERATE_API_VERSION_HISTORY,
                outputPath,
                ARG_API_VERSION_SIGNATURE_FILES,
                filePaths.joinToString(":"),
                ARG_API_VERSION_NAMES,
                listOf("1.1.0", "1.2.0").joinToString(" ")
            ),
            expectedFail = "Aborting: --api-version-signature-files and --api-version-names must have equal length"
        )
    }

    @Test
    fun `Kotlin-style nulls with old signature format is parsed`() {
        val output = File.createTempFile("api-info", ".json")
        output.deleteOnExit()
        val outputPath = output.path

        val input = createTextFile(
            "0.0.0",
            """
                // Signature format: 2.0
                package test.pkg {
                  public class Foo {
                    method public void foo(String?);
                  }
                }
            """.trimIndent()
        )

        check(
            inputKotlinStyleNulls = true,
            extraArguments = arrayOf(
                ARG_GENERATE_API_VERSION_HISTORY,
                outputPath,
                ARG_API_VERSION_SIGNATURE_FILES,
                input.absolutePath,
                ARG_API_VERSION_NAMES,
                "0.0.0"
            )
        )

        val expectedJson = "[{\"class\":\"test.pkg.Foo\",\"addedIn\":\"0.0.0\",\"methods\":[{\"method\":\"foo(java.lang.String)\",\"addedIn\":\"0.0.0\"}],\"fields\":[]}]"
        assertEquals(expectedJson, output.readText())
    }

    private fun createTextFile(name: String, contents: String): File {
        val file = File.createTempFile(name, ".txt")
        file.deleteOnExit()
        file.writeText(contents)
        return file
    }
}
