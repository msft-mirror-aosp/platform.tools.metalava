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
import com.android.tools.metalava.cli.common.CommonOptions
import com.android.tools.metalava.cli.common.REPORTING_OPTIONS_HELP
import com.android.tools.metalava.cli.signature.SIGNATURE_FORMAT_OPTIONS_HELP
import org.junit.Test

class MainCommandTest :
    BaseCommandTest<MainCommand>({ MainCommand(commonOptions = CommonOptions()) }) {

    private val EXPECTED_HELP =
        """
Usage: metalava main [options] [flags]...

  The default sub-command that is run if no sub-command is specified.

Options:
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
  --typedefs-in-signatures [none|ref|inline]
                                             Whether to include typedef annotations in signature files.

                                             none (default) - will not include typedef annotations in signature.

                                             ref - will include just a reference to the typedef class, which is not
                                             itself part of the API and is not included as a class

                                             inline - will include the constants themselves into each usage site
  --add-nonessential-overrides-classes TEXT  Specifies a list of qualified class names where all visible overriding
                                             methods are added to signature files. This is a no-op when --format does
                                             not specify --add-additional-overrides=yes.

                                             The list of qualified class names should be separated with ':'(colon).
                                             (default: [])
  -h, --help                                 Show this message and exit

$REPORTING_OPTIONS_HELP

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

Arguments:
  flags                                      See below.


${OptionsTest().FLAGS.trimIndent()}
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
}
