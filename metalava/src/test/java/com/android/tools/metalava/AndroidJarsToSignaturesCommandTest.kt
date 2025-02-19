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

import com.android.tools.metalava.cli.common.BaseCommandTest
import com.android.tools.metalava.cli.historical.ARG_API_SURFACES
import com.android.tools.metalava.cli.historical.AndroidJarsToSignaturesCommand
import com.android.tools.metalava.cli.signature.SIGNATURE_FORMAT_OPTIONS_HELP
import com.android.tools.metalava.model.text.FileFormat
import com.android.tools.metalava.model.text.assertSignatureContents
import com.android.tools.metalava.testing.java
import java.io.File
import org.junit.Assert
import org.junit.Test

class AndroidJarsToSignaturesCommandTest :
    BaseCommandTest<AndroidJarsToSignaturesCommand>({ AndroidJarsToSignaturesCommand() }) {

    @Test
    fun `Test help`() {
        commandTest {
            args += listOf("android-jars-to-signatures")

            expectedStdout =
                """
Aborting: Usage: metalava android-jars-to-signatures [options] <android-root-dir>

  Rewrite the signature files in the `prebuilts/sdk` directory in the Android source tree.

  It does this by reading the API defined in the corresponding `android.jar` files.

Options:
  --api-versions <api-version-list>          Comma separated list of api versions to convert. If unspecified then all
                                             versions will be converted.
  --api-surfaces <api-surface-list>          Comma separated list of api surfaces to convert. If unspecified then only
                                             `public` will be converted.
  -h, -?, --help                             Show this message and exit

$CONFIG_FILE_OPTIONS_HELP

$SIGNATURE_FORMAT_OPTIONS_HELP

Arguments:
  <android-root-dir>                         The root directory of the Android source tree. The new signature files will
                                             be generated in the `prebuilts/sdk/<api>/<surface>/api/android.txt`
                                             sub-directories.
            """
                    .trimIndent()
        }
    }

    @Test
    fun `Test not Android dir`() {
        commandTest {
            val notAndroidRoot = folder("not-android-root")

            args += "android-jars-to-signatures"
            args += notAndroidRoot

            expectedStderr =
                """
                    Aborting: <android-root-dir> does not point to an Android source tree
                """
                    .trimIndent()
        }
    }

    private fun currentVersionDir(apiVersion: Int, surface: String): String {
        return "prebuilts/sdk/$apiVersion/$surface"
    }

    private fun currentApiDir(apiVersion: Int, surface: String = "public"): String {
        return "${currentVersionDir(apiVersion, surface)}/api"
    }

    private fun currentAndroidJarFile(apiVersion: Int, surface: String = "public"): String {
        return "${currentVersionDir(apiVersion, surface)}/android.jar"
    }

    private fun currentApiTxtFile(apiVersion: Int, surface: String = "public"): String {
        return "${currentApiDir(apiVersion, surface)}/android.txt"
    }

    @Test
    fun `Test convert jars`() {
        // Get the location of an android.jar in the prebuilts/sdk files generated by the build.
        val prebuiltsSdkDir = File(System.getenv("METALAVA_TEST_PREBUILTS_SDK_ROOT"))
        if (!prebuiltsSdkDir.isDirectory) {
            Assert.fail("test prebuilts not found: $prebuiltsSdkDir")
        }
        val androidJar = prebuiltsSdkDir.resolve("30/public/android.jar")

        commandTest {
            // Copy the android.jar into a temporary folder structure.
            val androidRootDir = folder("android-root-dir")

            data class ApiVersionInfo(
                val version: Int,
                val inputAndroidJarFile: File,
                val inputAndroidTxtFile: File? = null,
            )
            val apiVersionsInfo = mutableListOf<ApiVersionInfo>()

            // All android.jars are in prebuilts/sdk/<N>/public/android.jar.
            for (apiVersion in 1..5) {
                val versionJar = androidRootDir.resolve(currentAndroidJarFile(apiVersion))

                // All android.jar files already have a corresponding android.txt file.
                val androidTxtFile = androidRootDir.resolve(currentApiTxtFile(apiVersion))

                // Add to the list of api versions.
                apiVersionsInfo.add(ApiVersionInfo(apiVersion, versionJar, androidTxtFile))
            }

            // Set up the input file structure.
            for (apiVersionInfo in apiVersionsInfo) {
                // Copy the android.jar created in the build.gradle.kts file.
                androidJar.copyTo(apiVersionInfo.inputAndroidJarFile, overwrite = true)

                // Create an android.txt file, if provided.
                apiVersionInfo.inputAndroidTxtFile?.apply {
                    parentFile.mkdirs()
                    writeText(FileFormat.V2.header())
                }

                // Make sure the directory for the android.txt file exists.
                androidRootDir.resolve(currentApiDir(apiVersionInfo.version)).mkdirs()
            }

            args += "android-jars-to-signatures"
            args += androidRootDir

            args += ARG_CONFIG_FILE
            args += KnownConfigFiles.configPublicAndSystemSurfaces

            // Verify that all generated android.txt files have the correct content. They are
            // currently all the same.
            for (apiVersionInfo in apiVersionsInfo) {
                verify {
                    val apiVersion = apiVersionInfo.version
                    val file = androidRootDir.resolve(currentApiTxtFile(apiVersion))
                    file.assertSignatureContents(
                        """
                            // Signature format: 2.0
                            package android.test {
                              @Deprecated public class ClassAddedAndDeprecatedInApi30 {
                                ctor @Deprecated public ClassAddedAndDeprecatedInApi30(float);
                                ctor @Deprecated public ClassAddedAndDeprecatedInApi30(int);
                                method @Deprecated public void methodExplicitlyDeprecated();
                                method @Deprecated public void methodImplicitlyDeprecated();
                                field @Deprecated public static final int FIELD_EXPLICITLY_DEPRECATED = 1; // 0x1
                                field @Deprecated public static final int FIELD_IMPLICITLY_DEPRECATED = 2; // 0x2
                              }
                              public class ClassAddedInApi30 {
                                method public void methodAddedInApi30();
                              }
                            }
                            package java.lang {
                              public class Object {
                                ctor public Object();
                              }
                            }
                        """,
                        message = "incorrect output for $apiVersionInfo",
                    )
                }
            }
        }
    }

    @Test
    fun `Test simple jar`() {
        val root = buildFileStructure {
            dir("prebuilts/sdk/3/public") {
                jar(
                    "android.jar",
                    java(
                        """
                            package test.pkg;
                            public class Foo {}
                        """
                    ),
                )
                dir("api") { emptyFile("android.txt") }
            }
        }

        commandTest {
            args += "android-jars-to-signatures"
            args += root

            args += ARG_CONFIG_FILE
            args += KnownConfigFiles.configPublicAndSystemSurfaces

            verify {
                root
                    .resolve(currentApiTxtFile(3))
                    .assertSignatureContents(
                        """
                            // Signature format: 2.0
                            package test.pkg {
                              public class Foo {
                                ctor public Foo();
                              }
                            }
                        """
                    )
            }
        }
    }

    @Test
    fun `Test system extends`() {
        val root = buildFileStructure {
            dir("prebuilts/sdk/3/public") {
                jar(
                    "android.jar",
                    java(
                        """
                            package test.pkg;
                            public class Public {
                                public void publicMethod() {}
                            }
                        """
                    ),
                )
                dir("api") { emptyFile("android.txt") }
            }
            dir("prebuilts/sdk/3/system") {
                jar(
                    "android.jar",
                    java(
                        """
                            package test.pkg;
                            public class Public {
                                public void publicMethod() {}
                                public void systemMethod() {}
                            }
                        """
                    ),
                )
                dir("api") { emptyFile("android.txt") }
            }
        }

        commandTest {
            args += "android-jars-to-signatures"
            args += root

            args += ARG_CONFIG_FILE
            args += KnownConfigFiles.configPublicAndSystemSurfaces

            args += ARG_API_SURFACES
            args += "public,system"

            verify {
                root
                    .resolve(currentApiTxtFile(3))
                    .assertSignatureContents(
                        """
                            // Signature format: 2.0
                            package test.pkg {
                              public class Public {
                                ctor public Public();
                                method public void publicMethod();
                              }
                            }
                        """
                    )

                root
                    .resolve(currentApiTxtFile(3, "system"))
                    .assertSignatureContents(
                        """
                            // Signature format: 2.0
                            package test.pkg {
                              public class Public {
                                method public void systemMethod();
                              }
                            }
                        """
                    )
            }
        }
    }

    @Test
    fun `Test copy deprecated from existing signature file`() {
        lateinit var jarFile: File
        val root = buildFileStructure {
            dir("prebuilts/sdk/3/public") {
                jarFile =
                    jar(
                        "android.jar",
                        java(
                            """
                                package test.pkg;
                                public class Foo {}
                            """
                        ),
                    )
                dir("api") {
                    signature(
                        "android.txt",
                        """
                            // Signature format: 2.0
                            package test.pkg {
                              @Deprecated public class Foo {
                              }
                            }
                        """
                    )
                }
            }
            dir("prebuilts/sdk/3/system") {
                // Use the same jar file as for public.
                jarFile.copyTo(dir.resolve("android.jar"))

                dir("api") { emptyFile("android.txt") }
            }
        }

        commandTest {
            args += "android-jars-to-signatures"
            args += root

            args += ARG_CONFIG_FILE
            args += KnownConfigFiles.configPublicAndSystemSurfaces

            args += ARG_API_SURFACES
            args += "public,system"

            verify {
                root
                    .resolve(currentApiTxtFile(3))
                    .assertSignatureContents(
                        """
                            // Signature format: 2.0
                            package test.pkg {
                              @Deprecated public class Foo {
                                ctor @Deprecated public Foo();
                              }
                            }
                        """
                    )

                root
                    .resolve(currentApiTxtFile(3, "system"))
                    .assertSignatureContents(
                        """
                            // Signature format: 2.0
                        """
                    )
            }
        }
    }
}
