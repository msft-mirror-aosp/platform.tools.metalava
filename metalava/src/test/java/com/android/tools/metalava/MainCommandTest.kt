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

import com.android.tools.lint.checks.infrastructure.TestFiles
import com.android.tools.metalava.cli.common.BaseCommandTest
import com.android.tools.metalava.cli.common.COMMON_BASELINE_OPTIONS_HELP
import com.android.tools.metalava.cli.common.CommonOptions
import com.android.tools.metalava.cli.common.ISSUE_REPORTING_OPTIONS_HELP
import com.android.tools.metalava.cli.common.SOURCE_OPTIONS_HELP
import com.android.tools.metalava.cli.compatibility.COMPATIBILITY_CHECK_OPTIONS_HELP
import com.android.tools.metalava.cli.lint.API_LINT_OPTIONS_HELP
import com.android.tools.metalava.cli.signature.SIGNATURE_FORMAT_OPTIONS_HELP
import com.android.tools.metalava.model.source.DEFAULT_JAVA_LANGUAGE_LEVEL
import com.android.tools.metalava.model.source.DEFAULT_KOTLIN_LANGUAGE_LEVEL
import com.android.tools.metalava.reporter.Issues
import java.io.File
import java.util.Locale
import kotlin.test.assertEquals
import org.junit.Assert
import org.junit.Test

class MainCommandTest :
    BaseCommandTest<MainCommand>({ executionEnvironment ->
        MainCommand(
            commonOptions = CommonOptions(),
            executionEnvironment = executionEnvironment,
        )
    }) {

    private val EXPECTED_HELP =
        """
Usage: metalava main [options] [flags]...

  The default sub-command that is run if no sub-command is specified.

Options:
  --config-file <file>                       A configuration file that can be consumed by Metalava. This can be
                                             specified multiple times in which case later config files will
                                             override/merge with earlier ones.
  --api-class-resolution [api|api:classpath]
                                             Determines how class resolution is performed when loading API signature
                                             files. Any classes that cannot be found will be treated as empty.",

                                             api - will only look for classes in the API signature files.

                                             api:classpath (default) - will look for classes in the API signature files
                                             first and then in the classpath.
  --suppress-compatibility-meta-annotation <meta-annotation class>
                                             Suppress compatibility checks for any elements within the scope of an
                                             annotation which is itself annotated with the given meta-annotation.
  --manifest <file>                          A manifest file, used to check permissions to cross check APIs and retrieve
                                             min_sdk_version. (default: no manifest)
  --migrate-nullness <api file>              Compare nullness information with the previous stable API and mark newly
                                             annotated APIs as under migration.
  --typedefs-in-signatures [none|ref|inline]
                                             Whether to include typedef annotations in signature files.

                                             none (default) - will not include typedef annotations in signature.

                                             ref - will include just a reference to the typedef class, which is not
                                             itself part of the API and is not included as a class

                                             inline - will include the constants themselves into each usage site
  -h, --help                                 Show this message and exit

$SOURCE_OPTIONS_HELP

$ISSUE_REPORTING_OPTIONS_HELP

$COMMON_BASELINE_OPTIONS_HELP

$GENERAL_REPORTING_OPTIONS_HELP

$API_SELECTION_OPTIONS_HELP

$API_LINT_OPTIONS_HELP

$COMPATIBILITY_CHECK_OPTIONS_HELP

Signature File Output:

  Options controlling the signature file output. The format of the generated file is determined by the options in the
  `Signature Format Output` section.

  --api <file>                               Output file into which the API signature will be generated. If this is not
                                             specified then no API signature file will be created.
  --removed-api <file>                       Output file into which the API signatures for removed APIs will be
                                             generated. If this is not specified then no removed API signature file will
                                             be created.

$SIGNATURE_FORMAT_OPTIONS_HELP

$STUB_GENERATION_OPTIONS_HELP

$API_LEVELS_GENERATION_OPTIONS_HELP

Arguments:
  flags                                      See below.


API sources:
--source-files <files>
                                             A comma separated list of source files to be parsed. Can also be @ followed
                                             by a path to a text file containing paths to the full set of files to
                                             parse.
--classpath <paths>
                                             One or more directories or jars (separated by `:`) containing classes that
                                             should be on the classpath when parsing the source files
--project <xmlfile>
                                             Project description written in XML according to Lint's project model.
--merge-qualifier-annotations <file>
                                             An external annotations file to merge and overlay the sources, or a
                                             directory of such files. Should be used for annotations intended for
                                             inclusion in the API to be written out, e.g. nullability. Formats supported
                                             are: IntelliJ's external annotations database format, .jar or .zip files
                                             containing those, Android signature files, and Java stub files.
--merge-inclusion-annotations <file>
                                             An external annotations file to merge and overlay the sources, or a
                                             directory of such files. Should be used for annotations which determine
                                             inclusion in the API to be written out, i.e. show and hide. The only format
                                             supported is Java stub files.
--validate-nullability-from-merged-stubs
                                             Triggers validation of nullability annotations for any class where
                                             --merge-qualifier-annotations includes a Java stub file.
--validate-nullability-from-list
                                             Triggers validation of nullability annotations for any class listed in the
                                             named file (one top-level class per line, # prefix for comment line).
--nullability-warnings-txt <file>
                                             Specifies where to write warnings encountered during validation of
                                             nullability annotations. (Does not trigger validation by itself.)
--nullability-errors-non-fatal
                                             Specifies that errors encountered during validation of nullability
                                             annotations should not be treated as errors. They will be written out to
                                             the file specified in --nullability-warnings-txt instead.
--hide-annotation <annotation class>
                                             Treat any elements annotated with the given annotation as hidden
--show-unannotated
                                             Include un-annotated public APIs in the signature file as well
--java-source <level>
                                             Sets the source level for Java source files; default is ${DEFAULT_JAVA_LANGUAGE_LEVEL}.
--kotlin-source <level>
                                             Sets the source level for Kotlin source files; default is ${DEFAULT_KOTLIN_LANGUAGE_LEVEL}.
--sdk-home <dir>
                                             If set, locate the `android.jar` file from the given Android SDK
--compile-sdk-version <api>
                                             Use the given API level
--jdk-home <dir>
                                             If set, add the Java APIs from the given JDK to the classpath
--subtract-api <api file>
                                             Subtracts the API in the given signature or jar file from the current API
                                             being emitted via --api, --stubs, --doc-stubs, etc. Note that the
                                             subtraction only applies to classes; it does not subtract members.
--ignore-classes-on-classpath
                                             Prevents references to classes on the classpath from being added to the
                                             generated stub files.
--ignore-comments
                                             Ignore any comments in source files.


Extracting Signature Files:
--proguard <file>
                                             Write a ProGuard keep file for the API
--sdk-values <dir>
                                             Write SDK values files to the given directory


Generating Stubs:
--doc-stubs <dir>
                                             Generate documentation stub source files for the API. Documentation stub
                                             files are similar to regular stub files, but there are some differences.
                                             For example, in the stub files, we'll use special annotations like
                                             @RecentlyNonNull instead of @NonNull to indicate that an element is
                                             recently marked as non null, whereas in the documentation stubs we'll just
                                             list this as @NonNull. Another difference is that @doconly elements are
                                             included in documentation stubs, but not regular stubs, etc.
--kotlin-stubs
                                             [CURRENTLY EXPERIMENTAL] If specified, stubs generated from Kotlin source
                                             code will be written in Kotlin rather than the Java programming language.
--pass-through-annotation <annotation classes>
                                             A comma separated list of fully qualified names of annotation classes that
                                             must be passed through unchanged.
--exclude-annotation <annotation classes>
                                             A comma separated list of fully qualified names of annotation classes that
                                             must be stripped from metalava's outputs.
--enhance-documentation
                                             Enhance documentation in various ways, for example auto-generating
                                             documentation based on source annotations present in the code. This is
                                             implied by --doc-stubs.
--exclude-documentation-from-stubs
                                             Exclude element documentation (javadoc and kdoc) from the generated stubs.
                                             (Copyright notices are not affected by this, they are always included.
                                             Documentation stubs (--doc-stubs) are not affected.)


Extracting Annotations:
--extract-annotations <zipfile>
                                             Extracts source annotations from the source files and writes them into the
                                             given zip file
--include-source-retention
                                             If true, include source-retention annotations in the stub files. Does not
                                             apply to signature files. Source retention annotations are extracted into
                                             the external annotations files instead.


Injecting API Levels:
--apply-api-levels <api-versions.xml>
                                             Reads an XML file containing API level descriptions and merges the
                                             information into the documentation


Environment Variables:
METALAVA_DUMP_ARGV
                                             Set to true to have metalava emit all the arguments it was invoked with.
                                             Helpful when debugging or reproducing under a debugger what the build
                                             system is doing.
METALAVA_PREPEND_ARGS
                                             One or more arguments (concatenated by space) to insert into the command
                                             line, before the documentation flags.
METALAVA_APPEND_ARGS
                                             One or more arguments (concatenated by space) to append to the end of the
                                             command line, after the generate documentation flags.
        """
            .trimIndent()

    @Test
    fun `Test help`() {
        commandTest {
            args += listOf("main", "--help")
            expectedStdout = EXPECTED_HELP
        }
    }

    @Test
    fun `Test invalid option`() {
        commandTest {
            args += listOf("main", "--blah-blah-blah")
            expectedStderr =
                """
Aborting: Error: no such option: "--blah-blah-blah"

$EXPECTED_HELP
                """
                    .trimIndent()
        }
    }

    @Test
    fun `Test deprecated lowercase matching in issue configuration options`() {
        commandTest {
            args +=
                listOf(
                    "main",
                    "--error",
                    Issues.DEPRECATED_OPTION.name,
                    "--hide",
                    Issues.ADDED_FINAL.name.lowercase(Locale.US),
                )

            expectedStderr =
                """
error: Case-insensitive issue matching is deprecated, use --hide AddedFinal instead of --hide addedfinal [DeprecatedOption]
                """
                    .trimIndent()

            verify { assertEquals(-1, exitCode, message = "exitCode") }
        }
    }

    @Test
    fun `Test for @file`() {
        val dir = temporaryFolder.newFolder()
        val files = (1..4).map { TestFiles.source("File$it.java", "File$it").createFile(dir) }
        val fileList =
            TestFiles.source(
                "files.lst",
                """
            ${files[0]}
            ${files[1]} ${files[2]}
            ${files[3]}
        """
                    .trimIndent()
            )

        val file = fileList.createFile(dir)

        commandTest {
            args += listOf("main", "@$file")

            verify {
                fun normalize(f: File): String = f.relativeTo(dir).path
                Assert.assertEquals(
                    files.map { normalize(it) },
                    command.optionGroup.sources.map { normalize(it) }
                )
            }
        }
    }
}
