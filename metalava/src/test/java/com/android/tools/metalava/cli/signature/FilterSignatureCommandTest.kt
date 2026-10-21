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

import com.android.tools.metalava.ARG_CONFIG_FILE
import com.android.tools.metalava.CONFIG_FILE_OPTIONS_HELP
import com.android.tools.metalava.cli.common.BaseCommandTest
import com.android.tools.metalava.cli.common.ISSUE_REPORTING_OPTIONS_HELP
import com.android.tools.metalava.model.text.assertSignatureContents
import com.android.tools.metalava.testing.signature
import com.android.tools.metalava.testing.xml
import java.io.File
import org.junit.Test

/** Name of the `--output-file` option. */
private const val ARG_OUTPUT_FILE = "--output-file"

/** Expected help text for the `filter-signature` sub-command. */
private val filterSignatureHelp =
    """
Usage: metalava filter-signature [options]

  Filter a signature file using configured API flags.

  Reads signature files defining an API surface and writes a filtered signature file after applying any `<api-flags>`
  from `--config-file`.

Options:
  --api-files <files>                        One or more signature files (separated by `${File.pathSeparator}`) defining the current API.
                                             (required)
  --output-file <file>                       The output file into which the filtered signature file will be written.
                                             (required)
  -h, -?, --help                             Show this message and exit

$CONFIG_FILE_OPTIONS_HELP

$ISSUE_REPORTING_OPTIONS_HELP
    """
        .trimIndent()

/** Tests for [FilterSignatureCommand]. */
class FilterSignatureCommandTest :
    BaseCommandTest<FilterSignatureCommand>({ FilterSignatureCommand() }) {

    @Test
    fun `Test help`() {
        commandTest {
            args += listOf("filter-signature", "--help")

            expectedStdout = filterSignatureHelp
        }
    }

    @Test
    fun `Test standalone signature file filtering with kept, finalized, and reverted flags`() {
        commandTest {
            args += "filter-signature"

            args +=
                ARG_CONFIG_FILE to
                    xml(
                        "config.xml",
                        """
                            <config xmlns="http://www.google.com/tools/metalava/config">
                              <api-flags>
                                <api-flag package="test.pkg" name="kept_flag" mutability="mutable" status="enabled"/>
                                <api-flag package="test.pkg" name="finalized_flag" mutability="immutable" status="enabled"/>
                                <api-flag package="test.pkg" name="disabled_flag" mutability="immutable" status="disabled"/>
                              </api-flags>
                            </config>
                        """,
                    )

            val output = outputFile("filtered.txt")
            args += ARG_OUTPUT_FILE to output

            args +=
                ARG_API_FILES to
                    signature(
                        "api.txt",
                        """
                            // Signature format: 2.0
                            package test.pkg {
                              public class Foo {
                                ctor public Foo();
                                ctor @FlaggedApi("test.pkg.disabled_flag") public Foo(int);
                                method @FlaggedApi("test.pkg.kept_flag") public void keptMethod();
                                method @FlaggedApi("test.pkg.finalized_flag") public void finalizedMethod();
                                method @FlaggedApi("test.pkg.disabled_flag") public void disabledMethod();
                                method @FlaggedApi("test.pkg.unlisted_flag") public void unlistedMethod();
                                field @FlaggedApi("test.pkg.kept_flag") public static final int KEPT_FIELD = 1; // 0x1
                                field @FlaggedApi("test.pkg.finalized_flag") public static final int FINALIZED_FIELD = 2; // 0x2
                                field @FlaggedApi("test.pkg.disabled_flag") public static final int DISABLED_FIELD = 3; // 0x3
                              }
                              @FlaggedApi("test.pkg.kept_flag") public class KeptClass {
                                ctor public KeptClass();
                                method public void keptClassMethod();
                                method @FlaggedApi("test.pkg.disabled_flag") public void disabledMemberInKeptClass();
                              }
                              @FlaggedApi("test.pkg.finalized_flag") public class FinalizedClass {
                                ctor public FinalizedClass();
                                method public void finalizedClassMethod();
                              }
                              @FlaggedApi("test.pkg.disabled_flag") public class DisabledClass {
                                ctor public DisabledClass();
                                method public void disabledClassMethod();
                              }
                              public class DisabledClass.NestedInDisabledClass {
                                ctor public DisabledClass.NestedInDisabledClass();
                              }
                            }
                        """,
                    )

            verify {
                output.assertSignatureContents(
                    """
                        // Signature format: 2.0
                        package test.pkg {
                          public class FinalizedClass {
                            ctor public FinalizedClass();
                            method public void finalizedClassMethod();
                          }
                          public class Foo {
                            ctor public Foo();
                            method public void finalizedMethod();
                            method @FlaggedApi("test.pkg.kept_flag") public void keptMethod();
                            field public static final int FINALIZED_FIELD = 2; // 0x2
                            field @FlaggedApi("test.pkg.kept_flag") public static final int KEPT_FIELD = 1; // 0x1
                          }
                          @FlaggedApi("test.pkg.kept_flag") public class KeptClass {
                            ctor public KeptClass();
                            method public void keptClassMethod();
                          }
                        }
                    """
                )
            }
        }
    }

    @Test
    fun `Test superclass and interface promotion when flagged supertypes are dropped`() {
        commandTest {
            args += "filter-signature"

            args +=
                ARG_CONFIG_FILE to
                    xml(
                        "config.xml",
                        """
                            <config xmlns="http://www.google.com/tools/metalava/config">
                              <api-flags/>
                            </config>
                        """,
                    )

            val output = outputFile("filtered.txt")
            args += ARG_OUTPUT_FILE to output

            args +=
                ARG_API_FILES to
                    signature(
                        "api.txt",
                        """
                            // Signature format: 2.0
                            package test.pkg {
                              public class BaseClass {
                                ctor public BaseClass();
                              }
                              public interface BaseInterface {
                              }
                              @FlaggedApi("test.pkg.disabled_flag") public class FlaggedSuperClass extends test.pkg.BaseClass {
                                ctor public FlaggedSuperClass();
                              }
                              @FlaggedApi("test.pkg.disabled_flag") public interface FlaggedInterface extends test.pkg.BaseInterface {
                              }
                              public class SubClass extends test.pkg.FlaggedSuperClass implements test.pkg.FlaggedInterface {
                                ctor public SubClass();
                              }
                            }
                        """,
                    )

            verify {
                output.assertSignatureContents(
                    """
                        // Signature format: 2.0
                        package test.pkg {
                          public class BaseClass {
                            ctor public BaseClass();
                          }
                          public interface BaseInterface {
                          }
                          public class SubClass extends test.pkg.BaseClass implements test.pkg.BaseInterface {
                            ctor public SubClass();
                          }
                        }
                    """
                )
            }
        }
    }

    @Test
    fun `Test dropped annotation class removed from annotated items`() {
        commandTest {
            args += "filter-signature"

            args +=
                ARG_CONFIG_FILE to
                    xml(
                        "config.xml",
                        """
                            <config xmlns="http://www.google.com/tools/metalava/config">
                              <api-flags>
                                <api-flag package="test.pkg" name="kept_flag" mutability="mutable" status="enabled"/>
                              </api-flags>
                            </config>
                        """,
                    )

            val output = outputFile("filtered.txt")
            args += ARG_OUTPUT_FILE to output

            args +=
                ARG_API_FILES to
                    signature(
                        "api.txt",
                        """
                            // Signature format: 2.0
                            package test.pkg {
                              @FlaggedApi("test.pkg.disabled_flag") @java.lang.annotation.Retention(java.lang.annotation.RetentionPolicy.CLASS) public @interface DroppedAnnotation {
                              }
                              @FlaggedApi("test.pkg.kept_flag") @java.lang.annotation.Retention(java.lang.annotation.RetentionPolicy.CLASS) public @interface KeptAnnotation {
                              }
                              @test.pkg.DroppedAnnotation @test.pkg.KeptAnnotation public class Foo {
                                ctor public Foo();
                                method @NonNull @test.pkg.DroppedAnnotation @test.pkg.KeptAnnotation public String bar(@Nullable @test.pkg.DroppedAnnotation String);
                              }
                            }
                        """,
                    )

            verify {
                output.assertSignatureContents(
                    """
                        // Signature format: 2.0
                        package test.pkg {
                          @test.pkg.KeptAnnotation public class Foo {
                            ctor public Foo();
                            method @NonNull @test.pkg.KeptAnnotation public String bar(@Nullable String);
                          }
                          @FlaggedApi("test.pkg.kept_flag") @java.lang.annotation.Retention(java.lang.annotation.RetentionPolicy.CLASS) public @interface KeptAnnotation {
                          }
                        }
                    """
                )
            }
        }
    }

    @Test
    fun `Test preserving signature format from target signature file`() {
        commandTest {
            args += "filter-signature"

            args +=
                ARG_CONFIG_FILE to
                    xml(
                        "config.xml",
                        """
                            <config xmlns="http://www.google.com/tools/metalava/config">
                              <api-flags>
                                <api-flag package="test.pkg" name="finalized_flag" mutability="immutable" status="enabled"/>
                              </api-flags>
                            </config>
                        """,
                    )

            val output = outputFile("filtered.txt")
            args += ARG_OUTPUT_FILE to output

            args +=
                ARG_API_FILES to
                    signature(
                        "api.txt",
                        """
                            // Signature format: 5.0
                            package test.pkg {
                              public final class Foo {
                                ctor public Foo();
                                method @FlaggedApi("test.pkg.finalized_flag") public String? finalizedMethod(String);
                                method @FlaggedApi("test.pkg.disabled_flag") public String? disabledMethod(String);
                              }
                            }
                        """,
                    )

            verify {
                output.assertSignatureContents(
                    """
                        // Signature format: 5.0
                        package test.pkg {
                          public final class Foo {
                            ctor public Foo();
                            method public String? finalizedMethod(String);
                          }
                        }
                    """
                )
            }
        }
    }
}
