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

package com.android.tools.metalava.cli.signature

import com.android.tools.metalava.ARG_API_SURFACE
import com.android.tools.metalava.ARG_CONFIG_FILE
import com.android.tools.metalava.CONFIG_FILE_OPTIONS_HELP
import com.android.tools.metalava.KnownConfigFiles
import com.android.tools.metalava.cli.common.BaseCommandTest
import com.android.tools.metalava.testing.signature
import com.google.gson.GsonBuilder
import com.google.gson.JsonElement
import java.io.File
import kotlin.test.assertEquals
import org.junit.Test

/** Expected help text for the `api-history` sub-command. */
private val apiHistoryHelp =
    """
Usage: metalava api-history [options]

  Generate an API versions XML or JSON file from API signature files.

Options:
  --output-file <file>                       Output XML or JSON API versions file to generate. The format is determined
                                             from the file extension, which must be `.xml` or `.json` respectively.
                                             (required)
  --pattern <historical-api-pattern>         Pattern to use to locate historical signature files. Typically ends with
                                             `.txt`.

                                             See `metalava help historical-api-patterns` for more information.
  --historical-files <files>                 Historical signature files (separated by `${File.pathSeparator}`) to scan using --pattern.
  --historical-versions <api-version>:<api-version>
                                             The optional range of historical versions that can be included in the API
                                             version history. The `from` and `to` parts of the range are separated by a
                                             `:` and are both inclusive. See --current-version for acceptable
                                             `<api-version>`s.

                                             If unspecified, version history will default to include every historical
                                             version.
  --current-version <api-version>            Sets the API version for the current API signature files specified by
                                             --current-files. This supports a single integer level, `major.minor`,
                                             `major.minor.patch` and `major.minor.patch-quality` formats. Where `major`,
                                             `minor` and `patch` are all non-negative integers and `quality` is an
                                             alphanumeric string.
  --current-files <files>                    Current API signature files (separated by `${File.pathSeparator}`) for the API version
                                             specified by --current-version.
  --api-surface <surface>                    The API surface currently being generated. Must correspond to an
                                             <api-surface> element in a --config-file.
  --missing-class-action [keep|remove|report]
                                             Determines what to do when generating an XML API versions file and a class
                                             extends or implements a class that is not defined within any of the API
                                             versions loaded. Ignored when generating a JSON API versions file.

                                             keep - Keep references to missing classes.

                                             remove - Remove references to missing classes.

                                             report (default) - Report references to missing classes as errors.
  -h, -?, --help                             Show this message and exit

$CONFIG_FILE_OPTIONS_HELP
    """
        .trimIndent()

/** Tests for [ApiHistoryCommand]. */
class ApiHistoryCommandTest : BaseCommandTest<ApiHistoryCommand>({ ApiHistoryCommand() }) {

    private fun File.checkApiVersionsJsonContent(expectedContent: String) {
        val gson = GsonBuilder().disableHtmlEscaping().setPrettyPrinting().create()
        val outputJson = gson.fromJson(readText(), JsonElement::class.java)
        assertEquals(expectedContent.trimIndent(), gson.toJson(outputJson))
    }

    @Test
    fun `Test help`() {
        commandTest {
            args += listOf("api-history", "--help")

            expectedStdout = apiHistoryHelp
        }
    }

    @Test
    fun `Test XML generation from historical and current signature files`() {
        commandTest {
            args += "api-history"

            val outputXml = outputFile("api-versions.xml")
            args += "--output-file" to outputXml

            val v1 =
                signature(
                        "sdk/1/public/api/android.txt",
                        """
                            // Signature format: 2.0
                            package java.lang {
                              public class Object {
                                ctor public Object();
                              }
                            }
                            package test.pkg {
                              public class Foo {
                                ctor public Foo();
                                method public void v1Method();
                              }
                            }
                        """,
                    )
                    .createFile(temporaryFolder.root)

            val v2 =
                signature(
                        "sdk/2/public/api/android.txt",
                        """
                            // Signature format: 2.0
                            package java.lang {
                              public class Object {
                                ctor public Object();
                              }
                            }
                            package test.pkg {
                              public class Foo {
                                ctor public Foo();
                                method @Deprecated public void v1Method();
                                method public void v2Method();
                              }
                            }
                        """,
                    )
                    .createFile(temporaryFolder.root)

            // Create an extra file on disk that matches --pattern but is NOT in --historical-files
            // to verify LimitedFileSystemProvider prevents undeclared file access.
            signature(
                    "sdk/3/public/api/android.txt",
                    """
                        // Signature format: 2.0
                        package java.lang {
                          public class Object {
                            ctor public Object();
                          }
                        }
                        package test.pkg {
                          public class Undeclared {
                            ctor public Undeclared();
                          }
                        }
                    """,
                )
                .createFile(temporaryFolder.root)

            val currentBase =
                signature(
                        "current/android.txt",
                        """
                            // Signature format: 2.0
                            package java.lang {
                              public class Object {
                                ctor public Object();
                              }
                            }
                            package test.pkg {
                              public class Foo {
                                ctor public Foo();
                                method @Deprecated public void v1Method();
                                method public void v2Method();
                                method public void currentMethod();
                              }
                            }
                        """,
                    )
                    .createFile(temporaryFolder.root)

            val currentExtra =
                signature(
                        "current/extra.txt",
                        """
                            // Signature format: 2.0
                            package test.pkg {
                              public class Bar {
                                ctor public Bar();
                              }
                            }
                        """,
                    )
                    .createFile(temporaryFolder.root)

            args +=
                listOf(
                    "--pattern",
                    "${temporaryFolder.root}/sdk/{version:level}/public/api/android.txt",
                    "--historical-files",
                    listOf(v1, v2).joinToString(File.pathSeparator) { it.path },
                    "--current-version",
                    "4",
                    "--current-files",
                    listOf(currentBase, currentExtra).joinToString(File.pathSeparator) { it.path },
                )

            verify {
                assertEquals(
                    """
                        <?xml version="1.0" encoding="utf-8"?>
                        <api version="3">
                        	<class name="java/lang/Object" since="1">
                        		<method name="&lt;init>()V"/>
                        	</class>
                        	<class name="test/pkg/Bar" since="4">
                        		<extends name="java/lang/Object"/>
                        		<method name="&lt;init>()V"/>
                        	</class>
                        	<class name="test/pkg/Foo" since="1">
                        		<extends name="java/lang/Object"/>
                        		<method name="&lt;init>()V"/>
                        		<method name="currentMethod()V" since="4"/>
                        		<method name="v1Method()V" deprecated="2"/>
                        		<method name="v2Method()V" since="2"/>
                        	</class>
                        </api>
                    """
                        .trimIndent(),
                    outputXml.readText().trim(),
                )
            }
        }
    }

    @Test
    fun `Test JSON generation from historical and current signature files`() {
        commandTest {
            args += "api-history"

            val outputJson = outputFile("apiLevels.json")
            args += "--output-file" to outputJson

            val v1 =
                signature(
                        "api/1.0.0.txt",
                        """
                            // Signature format: 4.0
                            package test.pkg {
                              public final class Foo {
                                ctor public Foo();
                                method public void methodV1(String);
                              }
                            }
                        """,
                    )
                    .createFile(temporaryFolder.root)

            val v2 =
                signature(
                        "api/1.1.0.txt",
                        """
                            // Signature format: 4.0
                            package test.pkg {
                              public final class Foo {
                                ctor public Foo();
                                method @Deprecated public void methodV1(String);
                                method public void methodV2(int);
                              }
                            }
                        """,
                    )
                    .createFile(temporaryFolder.root)

            val current =
                signature(
                        "api/current.txt",
                        """
                            // Signature format: 4.0
                            package test.pkg {
                              public final class Foo {
                                ctor public Foo();
                                method @Deprecated public void methodV1(String);
                                method public void methodV2(int);
                                method public void methodCurrent();
                              }
                            }
                        """,
                    )
                    .createFile(temporaryFolder.root)

            args +=
                listOf(
                    "--pattern",
                    "${temporaryFolder.root}/api/{version:major.minor.patch}.txt",
                    "--historical-files",
                    listOf(v1, v2).joinToString(File.pathSeparator) { it.path },
                    "--current-version",
                    "1.2.0-alpha01",
                    "--current-files",
                    current.path,
                )

            verify {
                outputJson.checkApiVersionsJsonContent(
                    """
                        [
                          {
                            "class": "test.pkg.Foo",
                            "addedIn": "1.0.0",
                            "methods": [
                              {
                                "method": "Foo()",
                                "addedIn": "1.0.0"
                              },
                              {
                                "method": "methodCurrent()",
                                "addedIn": "1.2.0-alpha01"
                              },
                              {
                                "method": "methodV1(java.lang.String)",
                                "addedIn": "1.0.0",
                                "deprecatedIn": "1.1.0"
                              },
                              {
                                "method": "methodV2(int)",
                                "addedIn": "1.1.0"
                              }
                            ],
                            "fields": []
                          }
                        ]
                    """
                )
            }
        }
    }

    @Test
    fun `Test JSON generation from current signature files only`() {
        commandTest {
            args += "api-history"

            val outputJson = outputFile("apiLevels.json")
            args += "--output-file" to outputJson

            val current =
                signature(
                        "api/current.txt",
                        """
                            // Signature format: 4.0
                            package test.pkg {
                              public final class Foo {
                                ctor public Foo();
                              }
                            }
                        """,
                    )
                    .createFile(temporaryFolder.root)

            args +=
                listOf(
                    "--current-version",
                    "1.0.0-alpha01",
                    "--current-files",
                    current.path,
                )

            verify {
                outputJson.checkApiVersionsJsonContent(
                    """
                        [
                          {
                            "class": "test.pkg.Foo",
                            "addedIn": "1.0.0-alpha01",
                            "methods": [
                              {
                                "method": "Foo()",
                                "addedIn": "1.0.0-alpha01"
                              }
                            ],
                            "fields": []
                          }
                        ]
                    """
                )
            }
        }
    }

    @Test
    fun `Test --historical-versions filtering`() {
        commandTest {
            args += "api-history"

            val outputXml = outputFile("api-versions.xml")
            args += "--output-file" to outputXml

            val v1 =
                signature(
                        "sdk/1/android.txt",
                        """
                            // Signature format: 2.0
                            package java.lang {
                              public class Object {
                                ctor public Object();
                              }
                            }
                            package test.pkg {
                              public class V1Class {
                                ctor public V1Class();
                              }
                            }
                        """,
                    )
                    .createFile(temporaryFolder.root)

            val v2 =
                signature(
                        "sdk/2/android.txt",
                        """
                            // Signature format: 2.0
                            package java.lang {
                              public class Object {
                                ctor public Object();
                              }
                            }
                            package test.pkg {
                              public class V2Class {
                                ctor public V2Class();
                              }
                            }
                        """,
                    )
                    .createFile(temporaryFolder.root)

            val v3 =
                signature(
                        "sdk/3/android.txt",
                        """
                            // Signature format: 2.0
                            package java.lang {
                              public class Object {
                                ctor public Object();
                              }
                            }
                            package test.pkg {
                              public class V2Class {
                                ctor public V2Class();
                              }
                              public class V3Class {
                                ctor public V3Class();
                              }
                            }
                        """,
                    )
                    .createFile(temporaryFolder.root)

            args +=
                listOf(
                    "--pattern",
                    "${temporaryFolder.root}/sdk/{version:level}/android.txt",
                    "--historical-files",
                    listOf(v1, v2, v3).joinToString(File.pathSeparator) { it.path },
                    "--historical-versions",
                    "2:2",
                )

            verify {
                assertEquals(
                    """
                        <?xml version="1.0" encoding="utf-8"?>
                        <api version="3" min="2">
                        	<class name="java/lang/Object" since="2">
                        		<method name="&lt;init>()V"/>
                        	</class>
                        	<class name="test/pkg/V2Class" since="2">
                        		<extends name="java/lang/Object"/>
                        		<method name="&lt;init>()V"/>
                        	</class>
                        </api>
                    """
                        .trimIndent(),
                    outputXml.readText().trim(),
                )
            }
        }
    }

    @Test
    fun `Test --missing-class-action remove and keep`() {
        val sig =
            signature(
                    "sdk/1/android.txt",
                    """
                        // Signature format: 2.0
                        package java.lang {
                          public class Object {
                            ctor public Object();
                          }
                        }
                        package test.pkg {
                          public class Foo implements test.pkg.MissingInterface {
                            ctor public Foo();
                          }
                        }
                    """,
                )
                .createFile(temporaryFolder.root)

        commandTest {
            val outputXml = outputFile("remove-api-versions.xml")
            args +=
                listOf(
                    "api-history",
                    "--output-file",
                    outputXml.path,
                    "--pattern",
                    "${temporaryFolder.root}/sdk/{version:level}/android.txt",
                    "--historical-files",
                    sig.path,
                    "--missing-class-action",
                    "remove",
                )

            verify {
                assertEquals(
                    """
                        <?xml version="1.0" encoding="utf-8"?>
                        <api version="3">
                        	<class name="java/lang/Object" since="1">
                        		<method name="&lt;init>()V"/>
                        	</class>
                        	<class name="test/pkg/Foo" since="1">
                        		<extends name="java/lang/Object"/>
                        		<method name="&lt;init>()V"/>
                        	</class>
                        </api>
                    """
                        .trimIndent(),
                    outputXml.readText().trim(),
                )
            }
        }

        commandTest {
            val outputXml = outputFile("keep-api-versions.xml")
            args +=
                listOf(
                    "api-history",
                    "--output-file",
                    outputXml.path,
                    "--pattern",
                    "${temporaryFolder.root}/sdk/{version:level}/android.txt",
                    "--historical-files",
                    sig.path,
                    "--missing-class-action",
                    "keep",
                )

            verify {
                assertEquals(
                    """
                        <?xml version="1.0" encoding="utf-8"?>
                        <api version="3">
                        	<class name="java/lang/Object" since="1">
                        		<method name="&lt;init>()V"/>
                        	</class>
                        	<class name="test/pkg/Foo" since="1">
                        		<extends name="java/lang/Object"/>
                        		<implements name="test/pkg/MissingInterface"/>
                        		<method name="&lt;init>()V"/>
                        	</class>
                        </api>
                    """
                        .trimIndent(),
                    outputXml.readText().trim(),
                )
            }
        }
    }

    @Test
    fun `Test error cases`() {
        val sig =
            signature(
                    "sdk/1/android.txt",
                    """
                        // Signature format: 2.0
                        package test.pkg {
                          public class Foo {
                          }
                        }
                    """,
                )
                .createFile(temporaryFolder.root)

        // Invalid output extension
        commandTest {
            val outputTxt = outputFile("api-versions.txt")
            args +=
                listOf(
                    "api-history",
                    "--output-file",
                    outputTxt.path,
                    "--current-version",
                    "1",
                    "--current-files",
                    sig.path,
                )

            expectedStderr =
                "Aborting: --output-file (TESTROOT/api-versions.txt) has unexpected extension 'txt', must be 'xml' or 'json'"
        }

        // Neither --historical-files nor --current-files
        commandTest {
            val outputXml = outputFile("api-versions.xml")
            args +=
                listOf(
                    "api-history",
                    "--output-file",
                    outputXml.path,
                )

            expectedStderr =
                "Aborting: Must specify at least one of --historical-files or --current-files"
        }

        // --historical-files without --pattern
        commandTest {
            val outputXml = outputFile("api-versions.xml")
            args +=
                listOf(
                    "api-history",
                    "--output-file",
                    outputXml.path,
                    "--historical-files",
                    sig.path,
                )

            expectedStderr = "Aborting: Must specify --pattern with --historical-files"
        }

        // --current-version without --current-files
        commandTest {
            val outputXml = outputFile("api-versions.xml")
            args +=
                listOf(
                    "api-history",
                    "--output-file",
                    outputXml.path,
                    "--pattern",
                    "${temporaryFolder.root}/sdk/{version:level}/android.txt",
                    "--historical-files",
                    sig.path,
                    "--current-version",
                    "2",
                )

            expectedStderr = "Aborting: Must specify --current-files with --current-version"
        }

        // --current-files without --current-version
        commandTest {
            val outputXml = outputFile("api-versions.xml")
            args +=
                listOf(
                    "api-history",
                    "--output-file",
                    outputXml.path,
                    "--current-files",
                    sig.path,
                )

            expectedStderr = "Aborting: Must specify --current-version with --current-files"
        }

        // --api-surface without --config-file
        commandTest {
            val outputXml = outputFile("api-versions.xml")
            args +=
                listOf(
                    "api-history",
                    "--output-file",
                    outputXml.path,
                    "--pattern",
                    "${temporaryFolder.root}/sdk/{version:level}/{surface}/android.txt",
                    "--historical-files",
                    sig.path,
                    ARG_API_SURFACE,
                    "system",
                )

            expectedStderr =
                "Aborting: --api-surface requires at least one <api-surface> to have been configured in a --config-file"
        }
    }

    @Test
    fun `Test --config-file and --api-surface`() {
        commandTest {
            args += "api-history"

            val outputXml = outputFile("api-versions.xml")
            args += "--output-file" to outputXml

            val configFile =
                KnownConfigFiles.configKnownTestSurfaces.createFile(temporaryFolder.root)

            val v1Public =
                signature(
                        "sdk/1/public/api/android.txt",
                        """
                            // Signature format: 2.0
                            package java.lang {
                              public class Object {
                                ctor public Object();
                              }
                            }
                            package test.pkg {
                              public class Foo {
                                ctor public Foo();
                                method public void publicMethod();
                              }
                            }
                        """,
                    )
                    .createFile(temporaryFolder.root)

            val v1System =
                signature(
                        "sdk/1/system/api/android.txt",
                        """
                            // Signature format: 2.0
                            package test.pkg {
                              public class Foo {
                                method public void systemMethod();
                              }
                            }
                        """,
                    )
                    .createFile(temporaryFolder.root)

            // module-lib should not be included when --api-surface is "system".
            val v1ModuleLib =
                signature(
                        "sdk/1/module-lib/api/android.txt",
                        """
                            // Signature format: 2.0
                            package test.pkg {
                              public class Foo {
                                method public void moduleLibMethod();
                              }
                            }
                        """,
                    )
                    .createFile(temporaryFolder.root)

            args +=
                listOf(
                    ARG_CONFIG_FILE,
                    configFile.path,
                    ARG_API_SURFACE,
                    "system",
                    "--pattern",
                    "${temporaryFolder.root}/sdk/{version:level}/{surface}/api/android.txt",
                    "--historical-files",
                    listOf(v1Public, v1System, v1ModuleLib).joinToString(File.pathSeparator) {
                        it.path
                    },
                )

            verify {
                assertEquals(
                    """
                        <?xml version="1.0" encoding="utf-8"?>
                        <api version="3">
                        	<class name="java/lang/Object" since="1">
                        		<method name="&lt;init>()V"/>
                        	</class>
                        	<class name="test/pkg/Foo" since="1">
                        		<extends name="java/lang/Object"/>
                        		<method name="&lt;init>()V"/>
                        		<method name="publicMethod()V"/>
                        		<method name="systemMethod()V"/>
                        	</class>
                        </api>
                    """
                        .trimIndent(),
                    outputXml.readText().trim(),
                )
            }
        }
    }
}
