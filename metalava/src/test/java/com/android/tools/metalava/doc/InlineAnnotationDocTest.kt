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

package com.android.tools.metalava.doc

import com.android.tools.metalava.DriverTest
import com.android.tools.metalava.lint.DefaultLintErrorMessage
import com.android.tools.metalava.testing.java
import org.junit.Test

/**
 * Tests for inlining annotation documentation specified using `@classDoc` and similar block tags.
 */
class InlineAnnotationDocTest : DriverTest() {

    @Test
    fun `memberDoc crash`() {
        check(
            sourceFiles =
                arrayOf(
                    java(
                        """
                            package test.pkg;
                            import java.lang.annotation.ElementType;
                            import java.lang.annotation.Retention;
                            import java.lang.annotation.RetentionPolicy;
                            import java.lang.annotation.Target;
                            /**
                             * More text here
                             * @memberDoc Important {@link another.pkg.Bar#BAR}
                             * and here
                             */
                            @Target({ ElementType.FIELD })
                            @Retention(RetentionPolicy.SOURCE)
                            public @interface Foo { }
                        """
                    ),
                    java(
                        """
                            package another.pkg;
                            public class Bar {
                                public String BAR = "BAAAAR";
                            }
                        """
                    ),
                    java(
                        """
                            package yetonemore.pkg;
                            public class Fun {
                                @test.pkg.Foo
                                public Fun() {}

                                /**
                                 * Separate comment
                                 */
                                @test.pkg.Foo
                                public static final String FUN = "FUN";
                            }
                        """
                    )
                ),
            docStubs = true,
            stubFiles =
                arrayOf(
                    java(
                        """
                            package yetonemore.pkg;
                            @SuppressWarnings({"unchecked", "deprecation", "all"})
                            public class Fun {
                            /**
                             * Important {@link another.pkg.Bar#BAR}
                             * and here
                             */
                            public Fun() { throw new RuntimeException("Stub!"); }
                            /**
                             * Separate comment.
                             * <br>
                             * Important {@link another.pkg.Bar#BAR}
                             * and here
                             */
                            public static final java.lang.String FUN = "FUN";
                            }
                        """
                    )
                )
        )
    }

    @Test
    fun `Invalid classDoc`() {
        check(
            sourceFiles =
                arrayOf(
                    java(
                        """
                            package test.pkg;
                            /**
                             * @classDoc {@code unclosed
                             */
                            public @interface Anno { }
                        """
                    ),
                    java(
                        """
                            package test.pkg;
                            @Anno
                            public class Test {
                            }
                        """
                    )
                ),
            docStubs = true,
            stubFiles =
                arrayOf(
                    java(
                        """
                            package test.pkg;
                            /** {@code unclosed} */
                            @SuppressWarnings({"unchecked", "deprecation", "all"})
                            @test.pkg.Anno
                            public class Test {
                            public Test() { throw new RuntimeException("Stub!"); }
                            }
                        """
                    )
                ),
            expectedFail = DefaultLintErrorMessage,
            expectedIssues =
                "src/test/pkg/Anno.java:3: error: unclosed inline '@code' tag [UnclosedInlineTag]",
        )
    }
}
