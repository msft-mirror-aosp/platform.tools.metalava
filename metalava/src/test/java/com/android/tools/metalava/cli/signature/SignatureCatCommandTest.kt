/*
 * Copyright (C) 2025 The Android Open Source Project
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

import com.android.tools.metalava.cli.common.BaseCommandTest
import org.junit.Assert.*
import org.junit.Test

private val signatureCatHelp =
    """
Usage: metalava signature-cat [options] [<files>]...

  Cats signature files.

  Reads signature files either provided on the command line, or in stdin into a combined API surface and then writes it
  out to stdout according to the format options. The resulting output will be different to the input if the input does
  not already conform to the selected format.

Options:
  -h, -?, --help                             Show this message and exit

$SIGNATURE_FORMAT_OPTIONS_HELP

Arguments:
  <files>                                    Signature files to read, if not specified then they stdin is read instead.
    """
        .trimIndent()

class SignatureCatCommandTest : BaseCommandTest<SignatureCatCommand>({ SignatureCatCommand() }) {

    @Test
    fun `Test help`() {
        commandTest {
            args += listOf("signature-cat", "--help")

            expectedStdout = signatureCatHelp
        }
    }

    @Test
    fun `Cat from stdin to stdout`() {
        commandTest {
            args +=
                listOf(
                    "signature-cat",
                    "--format",
                    "2.0",
                    "--format-defaults",
                    // Strip java.lang. prefixes just to show that it does transform the input.
                    "strip-java-lang-prefix=always",
                )

            stdin =
                """
                    // Signature format: 2.0
                    package test.pkg {
                      public interface Foo extends java.lang.Comparable<test.pkg.Foo> {
                      }
                    }
                """
                    .trimIndent()

            expectedStdout =
                """
                    // Signature format: 2.0
                    package test.pkg {

                      public interface Foo extends Comparable<test.pkg.Foo> {
                      }

                    }
                """
                    .trimIndent()
        }
    }

    @Test
    fun `Cat from files to stdout`() {
        commandTest {
            args +=
                listOf(
                    "signature-cat",
                    inputFile(
                            "foo.txt",
                            """
                            // Signature format: 2.0
                            package test.pkg {
                              public interface Foo {
                              }
                            }
                        """
                                .trimIndent()
                        )
                        .path,
                    inputFile(
                            "bar.txt",
                            """
                            // Signature format: 2.0
                            package test.pkg {
                              public interface Bar {
                              }
                            }
                        """
                                .trimIndent()
                        )
                        .path,
                )

            // Stdin should be ignored when files are provided on the command line.
            stdin = "Stdin should be ignored when files are provided on the command line."

            expectedStdout =
                """
                    // Signature format: 2.0
                    package test.pkg {

                      public interface Foo {
                      }

                      public interface Bar {
                      }

                    }
                """
                    .trimIndent()
        }
    }
}
