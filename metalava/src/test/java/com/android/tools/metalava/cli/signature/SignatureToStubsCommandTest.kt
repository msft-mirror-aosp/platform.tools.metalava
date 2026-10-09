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
import com.android.tools.metalava.ARG_FORCE_CONVERT_TO_WARNING_NULLABILITY_ANNOTATIONS
import com.android.tools.metalava.ARG_STUBS
import com.android.tools.metalava.CONFIG_FILE_OPTIONS_HELP
import com.android.tools.metalava.cli.common.ARG_ERROR
import com.android.tools.metalava.cli.common.BaseCommandTest
import com.android.tools.metalava.cli.common.ISSUE_REPORTING_OPTIONS_HELP
import com.android.tools.metalava.testing.signature
import com.android.tools.metalava.testing.xml
import kotlin.test.assertEquals
import org.junit.Test

/** Expected help text for the `signature-to-stubs` sub-command. */
private val signatureToStubsHelp =
    """
Usage: metalava signature-to-stubs [options] <files>...

  Generate stub source files from API signature files.

Options:
  --api-surface <surface>                    The API surface currently being generated. Must correspond to an
                                             <api-surface> element in a --config-file.
  --force-convert-to-warning-nullability-annotations <package1:-package2:...>
                                             On every API declared in a class referenced by the given filter, makes
                                             nullability issues appear to callers as warnings rather than errors by
                                             replacing @Nullable/@NonNull in these APIs with
                                             @RecentlyNullable/@RecentlyNonNull.

                                             See `metalava help package-filters` for more information.
  --stubs <dir>                              Base directory to output the generated stub source files for the API.
                                             (required)
  -h, -?, --help                             Show this message and exit

$CONFIG_FILE_OPTIONS_HELP

$ISSUE_REPORTING_OPTIONS_HELP

Arguments:
  <files>                                    Signature files from which stub source files will be generated.
    """
        .trimIndent()

/** Tests for [SignatureToStubsCommand]. */
class SignatureToStubsCommandTest :
    BaseCommandTest<SignatureToStubsCommand>({ SignatureToStubsCommand() }) {

    @Test
    fun `Test help`() {
        commandTest {
            args += listOf("signature-to-stubs", "--help")

            expectedStdout = signatureToStubsHelp
        }
    }

    @Test
    fun `Test basic stub generation from signature file`() {
        commandTest {
            args += "signature-to-stubs"

            val stubsDir = folder("stubs")
            args += ARG_STUBS to stubsDir

            args +=
                signature(
                    "api.txt",
                    """
                        // Signature format: 2.0
                        package test.pkg {
                          public class Foo {
                            ctor public Foo();
                            method @Nullable public String bar(@NonNull String);
                          }
                        }
                    """,
                )

            verify {
                val fooStub = stubsDir.resolve("test/pkg/Foo.java")
                assertEquals(
                    """
                        package test.pkg;
                        @SuppressWarnings({"unchecked", "deprecation", "all"})
                        public class Foo {
                        public Foo() { throw new RuntimeException("Stub!"); }
                        @android.annotation.Nullable
                        public java.lang.String bar(@android.annotation.NonNull java.lang.String arg1) { throw new RuntimeException("Stub!"); }
                        }
                    """
                        .trimIndent(),
                    fooStub.readText().trim(),
                )
            }
        }
    }

    @Test
    fun `Test api-flags in config-file control RequiresFlag annotations in stubs`() {
        commandTest {
            args += "signature-to-stubs"

            args +=
                ARG_CONFIG_FILE to
                    xml(
                        "config.xml",
                        """
                            <config xmlns="http://www.google.com/tools/metalava/config">
                                <api-flags>
                                    <api-flag package="test.pkg" name="kept_flag" mutability="mutable" status="disabled"/>
                                    <api-flag package="test.pkg" name="finalized_flag" mutability="immutable" status="enabled"/>
                                </api-flags>
                            </config>
                        """,
                    )

            val stubsDir = folder("stubs")
            args += ARG_STUBS to stubsDir

            args +=
                signature(
                    "api.txt",
                    """
                        // Signature format: 2.0
                        package test.pkg {
                          public class Foo {
                            ctor public Foo();
                            method @FlaggedApi("test.pkg.kept_flag") public void keptMethod();
                            method @FlaggedApi("test.pkg.finalized_flag") public void finalizedMethod();
                          }
                        }
                    """,
                )

            verify {
                val fooStub = stubsDir.resolve("test/pkg/Foo.java")
                assertEquals(
                    """
                        package test.pkg;
                        @SuppressWarnings({"unchecked", "deprecation", "all"})
                        public class Foo {
                        public Foo() { throw new RuntimeException("Stub!"); }
                        public void finalizedMethod() { throw new RuntimeException("Stub!"); }
                        @android.annotation.RequiresFlag("test.pkg.kept_flag")
                        public void keptMethod() { throw new RuntimeException("Stub!"); }
                        }
                    """
                        .trimIndent(),
                    fooStub.readText().trim(),
                )
            }
        }
    }

    @Test
    fun `Test issue reporting options fail on error`() {
        commandTest {
            args += "signature-to-stubs"
            args += listOf(ARG_ERROR, "SignatureFileError")

            val stubsDir = folder("stubs")
            args += ARG_STUBS to stubsDir

            args +=
                signature(
                    "api.txt",
                    """
                        // Signature format: 2.0
                        package test.pkg {
                          public class Foo {
                            ctor public Foo();
                            field public int bar = 1;
                          }
                        }
                    """,
                )

            expectedStderr =
                "TESTROOT/api.txt:5: error: Field bar in class test.pkg.Foo has a value of `1` but is not `static` and `final`; ignoring value [SignatureFileError]"

            verify { assertEquals(-1, exitCode) }
        }
    }

    @Test
    fun `Test api-surface without configured api-surfaces in config-file`() {
        commandTest {
            args += "signature-to-stubs"
            args += listOf(ARG_API_SURFACE, "public")
            args += ARG_STUBS to folder("stubs")
            args +=
                signature(
                    "api.txt",
                    """
                        // Signature format: 2.0
                    """,
                )

            expectedStderr =
                "Aborting: --api-surface requires at least one <api-surface> to have been configured in a --config-file"
        }
    }

    @Test
    fun `Test api-surface with unknown surface name`() {
        commandTest {
            args += "signature-to-stubs"
            args +=
                ARG_CONFIG_FILE to
                    xml(
                        "config.xml",
                        """
                            <config xmlns="http://www.google.com/tools/metalava/config">
                                <api-surfaces>
                                    <api-surface name="public">
                                        <selection-criteria unannotated="show"/>
                                    </api-surface>
                                    <api-surface name="system" extends="public">
                                        <selection-criteria/>
                                    </api-surface>
                                </api-surfaces>
                            </config>
                        """,
                    )
            args += listOf(ARG_API_SURFACE, "unknown")
            args += ARG_STUBS to folder("stubs")
            args +=
                signature(
                    "api.txt",
                    """
                        // Signature format: 2.0
                    """,
                )

            expectedStderr =
                "Aborting: --api-surface (`unknown`) does not match an <api-surface> in a --config-file, expected one of `public`, `system`"
        }
    }

    @Test
    fun `Test api-surface with configured extending surface`() {
        commandTest {
            args += "signature-to-stubs"
            args +=
                ARG_CONFIG_FILE to
                    xml(
                        "config.xml",
                        """
                            <config xmlns="http://www.google.com/tools/metalava/config">
                                <api-surfaces>
                                    <api-surface name="public">
                                        <selection-criteria unannotated="show"/>
                                    </api-surface>
                                    <api-surface name="system" extends="public">
                                        <selection-criteria/>
                                    </api-surface>
                                </api-surfaces>
                            </config>
                        """,
                    )
            args += listOf(ARG_API_SURFACE, "system")

            val stubsDir = folder("stubs")
            args += ARG_STUBS to stubsDir

            args +=
                signature(
                    "public.txt",
                    """
                        // Signature format: 2.0
                        package test.pkg {
                          public class Foo {
                            ctor public Foo();
                            method public void publicMethod();
                          }
                        }
                    """,
                )
            args +=
                signature(
                    "system.txt",
                    """
                        // Signature format: 2.0
                        package test.pkg {
                          public class Foo {
                            method public void systemMethod();
                          }
                        }
                    """,
                )

            verify {
                val fooStub = stubsDir.resolve("test/pkg/Foo.java")
                assertEquals(
                    """
                        package test.pkg;
                        @SuppressWarnings({"unchecked", "deprecation", "all"})
                        public class Foo {
                        public Foo() { throw new RuntimeException("Stub!"); }
                        public void publicMethod() { throw new RuntimeException("Stub!"); }
                        public void systemMethod() { throw new RuntimeException("Stub!"); }
                        }
                    """
                        .trimIndent(),
                    fooStub.readText().trim(),
                )
            }
        }
    }

    @Test
    fun `Test force-convert-to-warning-nullability-annotations`() {
        commandTest {
            args += "signature-to-stubs"
            args +=
                listOf(
                    ARG_FORCE_CONVERT_TO_WARNING_NULLABILITY_ANNOTATIONS,
                    "test.pkg",
                )

            val stubsDir = folder("stubs")
            args += ARG_STUBS to stubsDir

            args +=
                signature(
                    "api.txt",
                    """
                        // Signature format: 2.0
                        package test.pkg {
                          public class Foo {
                            ctor public Foo();
                            method @Nullable public String bar(@NonNull String);
                          }
                        }
                        package other.pkg {
                          public class Bar {
                            ctor public Bar();
                            method @Nullable public String baz(@NonNull String);
                          }
                        }
                    """,
                )

            verify {
                val fooStub = stubsDir.resolve("test/pkg/Foo.java")
                assertEquals(
                    """
                        package test.pkg;
                        @SuppressWarnings({"unchecked", "deprecation", "all"})
                        public class Foo {
                        public Foo() { throw new RuntimeException("Stub!"); }
                        @androidx.annotation.RecentlyNullable
                        public java.lang.String bar(@androidx.annotation.RecentlyNonNull java.lang.String arg1) { throw new RuntimeException("Stub!"); }
                        }
                    """
                        .trimIndent(),
                    fooStub.readText().trim(),
                )

                val barStub = stubsDir.resolve("other/pkg/Bar.java")
                assertEquals(
                    """
                        package other.pkg;
                        @SuppressWarnings({"unchecked", "deprecation", "all"})
                        public class Bar {
                        public Bar() { throw new RuntimeException("Stub!"); }
                        @android.annotation.Nullable
                        public java.lang.String baz(@android.annotation.NonNull java.lang.String arg1) { throw new RuntimeException("Stub!"); }
                        }
                    """
                        .trimIndent(),
                    barStub.readText().trim(),
                )
            }
        }
    }
}
