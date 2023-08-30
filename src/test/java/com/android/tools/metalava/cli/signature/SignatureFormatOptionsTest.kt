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

package com.android.tools.metalava.cli.signature

import com.android.tools.metalava.cli.common.BaseOptionGroupTest
import com.android.tools.metalava.model.text.ApiParseException
import com.android.tools.metalava.model.text.FileFormat
import com.android.tools.metalava.testing.source
import com.github.ajalt.clikt.core.BadParameterValue
import com.google.common.truth.Truth.assertThat
import java.io.File
import kotlin.test.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

val SIGNATURE_FORMAT_OPTIONS_HELP =
    """
Signature Format Output:

  Options controlling the format of the generated signature files.

  See `metalava help signature-file-formats` for more information.

  --api-overloaded-method-order [source|signature]
                                             Specifies the order of overloaded methods in signature files. Applies to
                                             the contents of the files specified on --api and --removed-api.

                                             source - preserves the order in which overloaded methods appear in the
                                             source files. This means that refactorings of the source files which change
                                             the order but not the API can cause unnecessary changes in the API
                                             signature files.

                                             signature (default) - sorts overloaded methods by their signature. This
                                             means that refactorings of the source files which change the order but not
                                             the API will have no effect on the API signature files.
  --format [v2|v3|v4|latest|recommended|<specifier>]
                                             Specifies the output signature file format.

                                             The preferred way of specifying the format is to use one of the following
                                             values (in no particular order):

                                             latest - The latest in the supported versions. Only use this if you want to
                                             have the very latest and are prepared to update signature files on a
                                             continuous basis.

                                             recommended (default) - The recommended version to use. This is currently
                                             set to `v2` and will only change very infrequently so can be considered
                                             stable.

                                             <specifier> - which has the following syntax:

                                             <version>[:<property>=<value>[,<property>=<value>]*]

                                             Where:

                                             The following values are still supported but should be considered
                                             deprecated.

                                             v2 - The main version used in Android.

                                             v3 - Adds support for using kotlin style syntax to embed nullability
                                             information instead of using explicit and verbose @NonNull and @Nullable
                                             annotations. This can be used for Java files and Kotlin files alike.

                                             v4 - Adds support for using concise default values in parameters. Instead
                                             of specifying the actual default values it just uses the `default` keyword.
                                             (default: recommended)
  --use-same-format-as <file>                Specifies that the output format should be the same as the format used in
                                             the specified file. It is an error if the file does not exist. If the file
                                             is empty then this will behave as if it was not specified. If the file is
                                             not a valid signature file then it will fail. Otherwise, the format read
                                             from the file will be used.

                                             If this is specified (and the file is not empty) then this will be used in
                                             preference to most of the other options in this group. Those options will
                                             be validated but otherwise ignored. The exception is the
                                             --api-overloaded-method-order option which if present will be used.

                                             The intention is that the other options will be used to specify the default
                                             for new empty API files (e.g. created using `touch`) while this option is
                                             used to specify the format for generating updates to the existing non-empty
                                             files.
    """
        .trimIndent()

class SignatureFormatOptionsTest :
    BaseOptionGroupTest<SignatureFormatOptions>(
        { SignatureFormatOptions() },
        SIGNATURE_FORMAT_OPTIONS_HELP
    ) {

    @Test
    fun `V1 not supported`() {
        val e = assertThrows(BadParameterValue::class.java) { runTest("--format=v1") {} }
        assertThat(e.message)
            .startsWith(
                """Invalid value for "--format": invalid version, found 'v1', expected one of '2.0', '3.0', '4.0', 'v2', 'v3', 'v4', 'latest', 'recommended'"""
            )
    }

    @Test
    fun `--use-same-format-as reads from a valid file and ignores --format`() {
        val path = source("api.txt", "// Signature format: 3.0\n").createFile(temporaryFolder.root)
        runTest("--use-same-format-as", path.path, "--format", "v4") {
            assertThat(it.fileFormat).isEqualTo(FileFormat.V3)
        }
    }

    @Test
    fun `--use-same-format-as ignores empty file and falls back to format`() {
        val path = source("api.txt", "").createFile(temporaryFolder.root)
        runTest("--use-same-format-as", path.path, "--format", "v4") {
            assertThat(it.fileFormat).isEqualTo(FileFormat.V4)
        }
    }

    @Test
    fun `--use-same-format-as will honor --api-overloaded-method-order=source`() {
        val path = source("api.txt", "// Signature format: 2.0\n").createFile(temporaryFolder.root)
        runTest("--use-same-format-as", path.path, "--api-overloaded-method-order=source") {
            assertThat(it.fileFormat)
                .isEqualTo(
                    FileFormat.V2.copy(
                        specifiedOverloadedMethodOrder = FileFormat.OverloadedMethodOrder.SOURCE
                    )
                )
        }
    }

    @Test
    fun `--use-same-format-as fails on non-existent file`() {
        val e =
            assertThrows(BadParameterValue::class.java) {
                runTest("--use-same-format-as", "unknown.txt") {}
            }
        val path = File("unknown.txt").absolutePath
        assertEquals("""Invalid value for "--use-same-format-as": $path is not a file""", e.message)
    }

    @Test
    fun `--use-same-format-as fails to read from an invalid file`() {
        val path =
            source("api.txt", "// Not a signature file").createFile(temporaryFolder.root).path
        val e =
            assertThrows(ApiParseException::class.java) {
                runTest("--use-same-format-as", path) {
                    // Get the file format as the file is only read when needed.
                    it.fileFormat
                }
            }
        assertEquals(
            """Unknown file format of $path: invalid prefix, found '// Not a signature fi', expected '// Signature format: '""",
            e.message
        )
    }

    @Test
    fun `--format with no properties`() {
        runTest("--format", "2.0") { assertEquals(FileFormat.V2, it.fileFormat) }
    }

    @Test
    fun `--format with no properties and --api-overloaded-method-order=source`() {
        runTest("--format", "2.0", "--api-overloaded-method-order=source") {
            assertEquals(
                FileFormat.V2.copy(
                    specifiedOverloadedMethodOrder = FileFormat.OverloadedMethodOrder.SOURCE,
                ),
                it.fileFormat
            )
        }
    }

    @Test
    fun `--format with overloaded-method-order=signature`() {
        runTest("--format", "2.0:overloaded-method-order=signature") {
            assertEquals(
                FileFormat.V2.copy(
                    specifiedOverloadedMethodOrder = FileFormat.OverloadedMethodOrder.SIGNATURE,
                ),
                it.fileFormat
            )
        }
    }

    @Test
    fun `--format with overloaded-method-order=signature and --api-overloaded-method-order=source`() {
        runTest(
            "--format",
            "2.0:overloaded-method-order=signature",
            "--api-overloaded-method-order=source",
        ) {
            assertEquals(
                FileFormat.V2.copy(
                    specifiedOverloadedMethodOrder = FileFormat.OverloadedMethodOrder.SIGNATURE,
                ),
                it.fileFormat
            )
        }
    }

    @Test
    fun `--format specifier with all the supported properties`() {
        runTest(
            "--format",
            "2.0:kotlin-style-nulls=yes,concise-default-values=yes,overloaded-method-order=source",
        ) {
            assertEquals(
                FileFormat.V2.copy(
                    specifiedOverloadedMethodOrder = FileFormat.OverloadedMethodOrder.SOURCE,
                    kotlinStyleNulls = true,
                    conciseDefaultValues = true,
                ),
                it.fileFormat
            )
        }
    }

    @Test
    fun `--format-properties gibberish`() {
        val e =
            assertThrows(BadParameterValue::class.java) { runTest("--format", "2.0:gibberish") {} }
        assertEquals(
            """Invalid value for "--format": expected <property>=<value> but found 'gibberish'""",
            e.message
        )
    }

    @Test
    fun `--format specifier unknown property`() {
        val e =
            assertThrows(BadParameterValue::class.java) {
                runTest("--format", "2.0:property=value") {}
            }
        assertEquals(
            """Invalid value for "--format": unknown format property name `property`, expected one of 'concise-default-values', 'kotlin-style-nulls', 'overloaded-method-order'""",
            e.message
        )
    }

    @Test
    fun `--format specifier unknown value (concise-default-values)`() {
        val e =
            assertThrows(BadParameterValue::class.java) {
                runTest("--format", "2.0:concise-default-values=barf") {}
            }
        assertEquals(
            """Invalid value for "--format": unexpected value for concise-default-values, found 'barf', expected one of 'yes' or 'no'""",
            e.message
        )
    }

    @Test
    fun `--format specifier unknown value (kotlin-style-nulls)`() {
        val e =
            assertThrows(BadParameterValue::class.java) {
                runTest("--format", "2.0:kotlin-style-nulls=barf") {}
            }
        assertEquals(
            """Invalid value for "--format": unexpected value for kotlin-style-nulls, found 'barf', expected one of 'yes' or 'no'""",
            e.message
        )
    }

    @Test
    fun `--format specifier unknown value (overloaded-method-order)`() {
        val e =
            assertThrows(BadParameterValue::class.java) {
                runTest("--format", "2.0:overloaded-method-order=barf") {}
            }
        assertEquals(
            """Invalid value for "--format": unexpected value for overloaded-method-order, found 'barf', expected one of 'source' or 'signature'""",
            e.message
        )
    }
}
