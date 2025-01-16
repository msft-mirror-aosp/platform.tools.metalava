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

import com.android.tools.metalava.apilevels.GenerateApiHistoryConfig
import com.android.tools.metalava.apilevels.VersionedJarApi
import com.android.tools.metalava.cli.common.BaseOptionGroupTest
import com.android.tools.metalava.cli.common.MetalavaCliException
import com.android.tools.metalava.cli.common.SignatureFileLoader
import com.android.tools.metalava.model.ClassResolver
import com.android.tools.metalava.model.text.SignatureFile
import com.google.common.truth.Truth.assertThat
import org.junit.Assert.assertThrows
import org.junit.Test

val API_LEVELS_GENERATION_OPTIONS_HELP =
    """
Api Levels Generation:

  Options controlling the API levels file, e.g. `api-versions.xml` file.

  --generate-api-levels <xmlfile>            Reads android.jar SDK files and generates an XML file recording the API
                                             level for each class, method and field. The --current-version must also be
                                             provided and must be greater than or equal to 27.
  --remove-missing-class-references-in-api-levels
                                             Removes references to missing classes when generating the API levels XML
                                             file. This can happen when generating the XML file for the non-updatable
                                             portions of the module-lib sdk, as those non-updatable portions can
                                             reference classes that are part of an updatable apex.
  --first-version <api-version>              Sets the first API version to include in the API history file. See
                                             --current-version for acceptable `<api-version>`s. (default: 1)
  --current-version <api-version>            Sets the current API version of the current source code. This supports a
                                             single integer level, `major.minor`, `major.minor.patch` and
                                             `major.minor.patch-quality` formats. Where `major`, `minor` and `patch` are
                                             all non-negative integers and `quality` is an alphanumeric string.
  --current-codename <version-codename>      Sets the code name for the current source code.
  --android-jar-pattern <android-jar-pattern>
                                             Pattern to use to locate Android JAR files. Each pattern must contain a
                                             {version:level} placeholder that will be replaced with each API level that
                                             is being included and if the result is an existing jar file then it will be
                                             taken as the definition of the API at that level.
  --sdk-extensions-root <sdk-jar-root>       Points to root of prebuilt extension SDK jars, if any. This directory is
                                             expected to contain snapshots of historical extension SDK versions in the
                                             form of stub jars. The paths should be on the format
                                             \"<int>/public/<module-name>.jar\", where <int> corresponds to the
                                             extension SDK version, and <module-name> to the name of the mainline
                                             module.
  --sdk-extensions-info <sdk-info-file>      Points to map of extension SDK APIs to include, if any. The file is a plain
                                             text file and describes, per extension SDK, what APIs from that extension
                                             to include in the file created via --generate-api-levels. The format of
                                             each line is one of the following: \"<module-name> <pattern> <ext-name>
                                             [<ext-name> [...]]\", where <module-name> is the name of the mainline
                                             module this line refers to, <pattern> is a common Java name prefix of the
                                             APIs this line refers to, and <ext-name> is a list of extension SDK names
                                             in which these SDKs first appeared, or \"<ext-name> <ext-id> <type>\",
                                             where <ext-name> is the name of an SDK, <ext-id> its numerical ID and
                                             <type> is one of \"platform\" (the Android platform SDK), \"platform-ext\"
                                             (an extension to the Android platform SDK), \"standalone\" (a separate
                                             SDK). Fields are separated by whitespace. A mainline module may be listed
                                             multiple times. The special pattern \"*\" refers to all APIs in the given
                                             mainline module. Lines beginning with # are comments.
  --generate-api-version-history <output-file>
                                             Reads API signature files and generates a JSON or XML file depending on the
                                             extension, which must be one of `json` or `xml` respectively. The JSON file
                                             will record the API version in which each class, method, and field. was
                                             added in and (if applicable) deprecated in. The XML file will include that
                                             information and more but will be optimized to exclude information from
                                             class members which is the same as the containing class.
  --api-version-signature-files <files>      An ordered list of text API signature files. The oldest API version should
                                             be first, the newest last. This should not include a signature file for the
                                             current API version, which will be parsed from the provided source files.
                                             Not required to generate API version JSON if the current version is the
                                             only version.
  --api-version-names <api-versions>         An ordered list of strings with the names to use for the API versions from
                                             --api-version-signature-files. If --current-version is not provided then
                                             this must include an additional version at the end which is used for the
                                             current API version. Required for --generate-api-version-history.
    """
        .trimIndent()

class ApiLevelsGenerationOptionsTest :
    BaseOptionGroupTest<ApiLevelsGenerationOptions>(
        API_LEVELS_GENERATION_OPTIONS_HELP,
    ) {
    override fun createOptions() = ApiLevelsGenerationOptions()

    /** Get an optional [GenerateApiHistoryConfig] for a fake set of signature files. */
    private fun ApiLevelsGenerationOptions.fromFakeSignatureFiles(): GenerateApiHistoryConfig? =
        fromSignatureFilesConfig(
            signatureFileLoader =
                object : SignatureFileLoader {
                    override fun load(
                        signatureFiles: List<SignatureFile>,
                        classResolver: ClassResolver?
                    ) = error("Fake signature file loader cannot load signature files")
                },
            codebaseFragmentProvider = {
                error("Fake CodebaseFragment provider cannot create CodebaseFragment")
            },
        )

    @Test
    fun `sdkJarRoot without sdkInfoFile`() {
        val file = temporaryFolder.newFolder("sdk-jar-root")
        runTest(ARG_SDK_JAR_ROOT, file.path) {
            assertThat(stderr)
                .isEqualTo("--sdk-extensions-root and --sdk-extensions-info must both be supplied")
        }
    }

    @Test
    fun `sdkInfoFile without sdkJarRoot`() {
        val file = temporaryFolder.newFile("sdk-info-file.xml")
        runTest(ARG_SDK_INFO_FILE, file.path) {
            assertThat(stderr)
                .isEqualTo("--sdk-extensions-root and --sdk-extensions-info must both be supplied")
        }
    }

    @Test
    fun `Test current version supports major-minor`() {
        runTest(ARG_CURRENT_VERSION, "1.2") {
            assertThat(options.currentApiVersion.toString()).isEqualTo("1.2")
        }
    }

    @Test
    fun `Test current version supports major-minor-patch`() {
        runTest(ARG_CURRENT_VERSION, "1.2.3") {
            assertThat(options.currentApiVersion.toString()).isEqualTo("1.2.3")
        }
    }

    @Test
    fun `Test current version supports major-minor-patch-preRelease`() {
        runTest(ARG_CURRENT_VERSION, "1.2.3-beta01") {
            assertThat(options.currentApiVersion.toString()).isEqualTo("1.2.3-beta01")
        }
    }

    @Test
    fun `Test --generate-api-version-history without --api-version-names`() {
        val apiVersionsJson = temporaryFolder.newFile("api-versions.json")
        val exception =
            assertThrows(MetalavaCliException::class.java) {
                runTest(
                    ARG_GENERATE_API_VERSION_HISTORY,
                    apiVersionsJson.path,
                ) {
                    val apiHistoryConfig = options.fromFakeSignatureFiles()
                    assertThat(apiHistoryConfig).isNotNull()
                    val apiVersions =
                        apiHistoryConfig!!.versionedApis.map { it.apiVersion }.joinToString()
                    assertThat(apiVersions).isEqualTo("1.2.3-beta01")
                }
            }

        assertThat(exception.message)
            .isEqualTo(
                "Must specify --api-version-names and/or --current-version with --generate-api-version-history"
            )
    }

    @Test
    fun `Test --current-version used alone with --generate-api-version-history`() {
        val apiVersionsJson = newFile("api-versions.json")
        runTest(
            ARG_CURRENT_VERSION,
            "1.2.3-beta01",
            ARG_GENERATE_API_VERSION_HISTORY,
            apiVersionsJson.path
        ) {
            val apiHistoryConfig = options.fromFakeSignatureFiles()
            assertThat(apiHistoryConfig).isNotNull()
            val apiVersions = apiHistoryConfig!!.versionedApis.map { it.apiVersion }.joinToString()
            assertThat(apiVersions).isEqualTo("1.2.3-beta01")
        }
    }

    @Test
    fun `Test --current-version used with --generate-api-version-history and --api-version-names`() {
        val signatureFile = newFile("1.2.0-alpha01/api.txt")
        val apiVersionsJson = temporaryFolder.newFile("api-versions.json")
        runTest(
            ARG_CURRENT_VERSION,
            "1.2.3-beta01",
            ARG_GENERATE_API_VERSION_HISTORY,
            apiVersionsJson.path,
            ARG_API_VERSION_SIGNATURE_FILES,
            signatureFile.path,
            ARG_API_VERSION_NAMES,
            "1.2.0",
        ) {
            val apiHistoryConfig = options.fromFakeSignatureFiles()
            assertThat(apiHistoryConfig).isNotNull()
            val apiVersions = apiHistoryConfig!!.versionedApis.map { it.apiVersion }.joinToString()
            assertThat(apiVersions).isEqualTo("1.2.0, 1.2.3-beta01")
        }
    }

    @Test
    fun `Test extension jar files in forAndroidConfig`() {
        val root = buildFileStructure {
            dir("1") {
                dir("public") {
                    emptyFile("foo.jar")
                    emptyFile("bar.jar")
                }
            }
            dir("2") {
                dir("public") {
                    emptyFile("foo.jar")
                    emptyFile("bar.jar")
                    emptyFile("baz.jar")
                }
            }
        }

        val apiVersionsXml = temporaryFolder.newFile("api-versions.xml")
        val sdkExtensionsInfoXml =
            temporaryFolder.newFile("sdk-extensions-info.xml").apply {
                writeText(
                    """
                    <?xml version="1.0" encoding="utf-8"?>
                    <sdk-extensions-info>
                    <sdk id="7"
                         shortname="seven"
                         name="Seven Extensions"
                         reference="Seven" />
                    <symbol jar="bar" pattern="foo" sdks="seven" />
                    <symbol jar="baz" pattern="foo" sdks="seven" />
                    <symbol jar="foo" pattern="foo" sdks="seven" />
                    </sdk-extensions-info>
                """
                        .trimIndent()
                )
            }
        runTest(
            ARG_CURRENT_VERSION,
            "30",
            ARG_GENERATE_API_LEVELS,
            apiVersionsXml.path,
            ARG_SDK_JAR_ROOT,
            root.path,
            ARG_SDK_INFO_FILE,
            sdkExtensionsInfoXml.path,
        ) {
            val apiHistoryConfig =
                options.forAndroidConfig(apiSurface = null) { error("no codebase fragment") }
            assertThat(apiHistoryConfig).isNotNull()

            // Make sure that there were some versioned jar files found.
            val versionedJarFiles =
                apiHistoryConfig!!.versionedApis.mapNotNull { it as? VersionedJarApi }
            assertThat(versionedJarFiles).isNotEmpty()

            // Compute the list of versioned jar files for extensions.
            val extensionJarFiles =
                versionedJarFiles
                    .filter { it.forExtension() }
                    .joinToString("\n") { it.jar.relativeTo(root).path }
                    .trim()
            assertThat(extensionJarFiles)
                .isEqualTo(
                    """
                        1/public/bar.jar
                        2/public/bar.jar
                        2/public/baz.jar
                        1/public/foo.jar
                        2/public/foo.jar
                    """
                        .trimIndent()
                )
        }
    }
}
