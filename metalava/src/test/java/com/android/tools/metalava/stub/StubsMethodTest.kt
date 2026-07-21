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

package com.android.tools.metalava.stub

import com.android.tools.lint.checks.infrastructure.TestFile
import com.android.tools.metalava.KnownApiSurface
import com.android.tools.metalava.testing.java
import org.junit.Test

class StubsMethodTest : AbstractStubsTest() {
    @Test
    fun `Test hiding override of public method`() {
        check(
            apiSurface = KnownApiSurface.PUBLIC,
            sourceFiles =
                arrayOf(
                    java(
                        """
                            package test.pkg;
                            public class Parent {
                                public void method() {}
                            }
                        """
                    ),
                    java(
                        """
                            package test.pkg;
                            import android.annotation.Hide;
                            public class Child extends Parent {
                                @Hide
                                @Override
                                public void method() {}
                            }
                        """
                    ),
                ),
            expectedApiSignature =
                """
                    // Signature format: 5.0
                    package test.pkg {
                      public class Child extends test.pkg.Parent {
                        ctor public Child();
                      }
                      public class Parent {
                        ctor public Parent();
                        method public void method();
                      }
                    }
                """,
            stubPaths =
                arrayOf(
                    "test/pkg/Child.java",
                    "test/pkg/Parent.java",
                ),
            expectedStubFiles =
                arrayOf(
                    java(
                        """
                            package test.pkg;
                            @SuppressWarnings({"unchecked", "deprecation", "all"})
                            public class Child extends test.pkg.Parent {
                            public Child() { throw new RuntimeException("Stub!"); }
                            public void method() { throw new RuntimeException("Stub!"); }
                            }
                        """
                    ),
                    java(
                        """
                            package test.pkg;
                            @SuppressWarnings({"unchecked", "deprecation", "all"})
                            public class Parent {
                            public Parent() { throw new RuntimeException("Stub!"); }
                            public void method() { throw new RuntimeException("Stub!"); }
                            }
                        """
                    ),
                ),
        )
    }

    /**
     * Helper to test hiding an override of a `@SystemApi` method.
     *
     * Defines a `@SystemApi` class `test.pkg.Parent` with a public method, and a subclass
     * `test.pkg.Child` that overrides the method and hides it with `@Hide`.
     */
    private fun checkPublicOverrideOfSystemApiMethod(
        apiSurface: KnownApiSurface,
        expectedIssues: String? = "",
        expectedApiSignature: String,
        stubPaths: Array<String>,
        expectedStubFiles: Array<TestFile>,
    ) {
        check(
            apiSurface = apiSurface,
            sourceFiles =
                arrayOf(
                    java(
                        """
                            package test.pkg;
                            import android.annotation.SystemApi;
                            @SystemApi
                            public class Parent {
                                public void method() {}
                            }
                        """
                    ),
                    java(
                        """
                            package test.pkg;
                            import android.annotation.Hide;
                            public class Child extends Parent {
                                @Hide
                                @Override
                                public void method() {}
                            }
                        """
                    ),
                ),
            expectedIssues = expectedIssues,
            expectedApiSignature = expectedApiSignature,
            stubPaths = stubPaths,
            expectedStubFiles = expectedStubFiles,
        )
    }

    @Test
    fun `Test hiding override of SystemApi method in public API`() {
        checkPublicOverrideOfSystemApiMethod(
            apiSurface = KnownApiSurface.PUBLIC,
            expectedIssues =
                """
                    src/test/pkg/Child.java:3: warning: Public class test.pkg.Child stripped of unavailable superclass test.pkg.Parent [HiddenSuperclass]
                """,
            expectedApiSignature =
                """
                    // Signature format: 5.0
                    package test.pkg {
                      public class Child {
                        ctor public Child();
                      }
                    }
                """,
            stubPaths =
                arrayOf(
                    "test/pkg/Child.java",
                ),
            expectedStubFiles =
                arrayOf(
                    java(
                        """
                            package test.pkg;
                            @SuppressWarnings({"unchecked", "deprecation", "all"})
                            public class Child {
                            public Child() { throw new RuntimeException("Stub!"); }
                            }
                        """
                    ),
                ),
        )
    }

    @Test
    fun `Test hiding of public override of SystemApi method in system API`() {
        checkPublicOverrideOfSystemApiMethod(
            apiSurface = KnownApiSurface.SYSTEM,
            expectedApiSignature =
                """
                    // Signature format: 5.0
                    package test.pkg {
                      public class Child extends test.pkg.Parent {
                      }
                      public class Parent {
                        ctor public Parent();
                        method public void method();
                      }
                    }
                """,
            stubPaths =
                arrayOf(
                    "test/pkg/Child.java",
                    "test/pkg/Parent.java",
                ),
            expectedStubFiles =
                arrayOf(
                    java(
                        """
                            package test.pkg;
                            @SuppressWarnings({"unchecked", "deprecation", "all"})
                            public class Child extends test.pkg.Parent {
                            public Child() { throw new RuntimeException("Stub!"); }
                            public void method() { throw new RuntimeException("Stub!"); }
                            }
                        """
                    ),
                    java(
                        """
                            package test.pkg;
                            @SuppressWarnings({"unchecked", "deprecation", "all"})
                            public class Parent {
                            public Parent() { throw new RuntimeException("Stub!"); }
                            public void method() { throw new RuntimeException("Stub!"); }
                            }
                        """
                    ),
                ),
        )
    }

    @Test
    fun `Test final and non-final methods inherited into final class`() {
        checkStubs(
            sourceFiles =
                arrayOf(
                    java(
                        """
                            package test.pkg;
                            class Hidden {
                                public final void finalMethod() {}
                                public void nonFinalMethod() {}
                            }
                        """
                    ),
                    java(
                        """
                            package test.pkg;
                            public final class Foo extends Hidden {
                            }
                        """
                    ),
                ),
            expectedStubFiles =
                arrayOf(
                    java(
                        """
                            package test.pkg;
                            @SuppressWarnings({"unchecked", "deprecation", "all"})
                            public final class Foo {
                            public Foo() { throw new RuntimeException("Stub!"); }
                            public void finalMethod() { throw new RuntimeException("Stub!"); }
                            public void nonFinalMethod() { throw new RuntimeException("Stub!"); }
                            }
                        """
                    ),
                ),
        )
    }

    @Test
    fun `Test final and non-final methods inherited into non-final class`() {
        checkStubs(
            sourceFiles =
                arrayOf(
                    java(
                        """
                            package test.pkg;
                            class Hidden {
                                public final void finalMethod() {}
                                public void nonFinalMethod() {}
                            }
                        """
                    ),
                    java(
                        """
                            package test.pkg;
                            public class Foo extends Hidden {
                            }
                        """
                    ),
                ),
            expectedStubFiles =
                arrayOf(
                    java(
                        """
                            package test.pkg;
                            @SuppressWarnings({"unchecked", "deprecation", "all"})
                            public class Foo {
                            public Foo() { throw new RuntimeException("Stub!"); }
                            public final void finalMethod() { throw new RuntimeException("Stub!"); }
                            public void nonFinalMethod() { throw new RuntimeException("Stub!"); }
                            }
                        """
                    ),
                ),
        )
    }
}
