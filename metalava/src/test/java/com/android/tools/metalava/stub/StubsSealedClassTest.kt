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

package com.android.tools.metalava.stub

import com.android.tools.metalava.model.text.FORMAT_V6_WITH_JAVA_SEALED_CLASSES
import com.android.tools.metalava.testing.java
import org.junit.Test

class StubsSealedClassTest : AbstractStubsTest() {
    @Test
    fun `Test exhaustive abstract sealed class with java-sealed-classes=yes`() {
        checkStubs(
            format = FORMAT_V6_WITH_JAVA_SEALED_CLASSES,
            sourceFiles =
                arrayOf(
                    java(
                        """
                            package test.pkg;

                            public abstract sealed class Sealed {
                                public Sealed(int a) {}
                            }
                        """
                    ),
                    java(
                        """
                            package test.pkg;

                            public final class SubclassA extends Sealed {
                            }
                        """
                    ),
                    java(
                        """
                            package test.pkg;

                            public non-sealed class SubclassB extends Sealed {
                            }
                        """
                    ),
                ),
            api =
                """
                    // Signature format: 6.0
                    // - style=java
                    // - java-sealed-classes=yes
                    package test.pkg {
                      public abstract sealed exhaustive class Sealed permits test.pkg.SubclassA test.pkg.SubclassB {
                      }
                      public final class SubclassA extends test.pkg.Sealed {
                        ctor public SubclassA();
                      }
                      public non-sealed class SubclassB extends test.pkg.Sealed {
                        ctor public SubclassB();
                      }
                    }
                """,
            stubFiles =
                arrayOf(
                    java(
                        """
                            package test.pkg;
                            @SuppressWarnings({"unchecked", "deprecation", "all"})
                            public abstract sealed class Sealed permits test.pkg.SubclassA, test.pkg.SubclassB {
                            Sealed() { throw new RuntimeException("Stub!"); }
                            }
                        """
                    ),
                    java(
                        """
                            package test.pkg;
                            @SuppressWarnings({"unchecked", "deprecation", "all"})
                            public final class SubclassA extends test.pkg.Sealed {
                            public SubclassA() { throw new RuntimeException("Stub!"); }
                            }
                        """
                    ),
                    java(
                        """
                            package test.pkg;
                            @SuppressWarnings({"unchecked", "deprecation", "all"})
                            public non-sealed class SubclassB extends test.pkg.Sealed {
                            public SubclassB() { throw new RuntimeException("Stub!"); }
                            }
                        """
                    ),
                ),
        )
    }

    @Test
    fun `Test non-exhaustive abstract sealed class with java-sealed-classes=yes`() {
        checkStubs(
            format = FORMAT_V6_WITH_JAVA_SEALED_CLASSES,
            sourceFiles =
                arrayOf(
                    java(
                        """
                            package test.pkg;

                            public abstract sealed class Sealed {
                                public Sealed(int a) {}
                            }
                        """
                    ),
                    java(
                        """
                            package test.pkg;

                            public final class SubclassA extends Sealed {
                            }
                        """
                    ),
                    java(
                        """
                            package test.pkg;

                            public non-sealed class SubclassB extends Sealed {
                            }
                        """
                    ),
                    java(
                        """
                            package test.pkg;

                            non-sealed class SubclassPrivate extends Sealed {
                            }
                        """
                    ),
                ),
            api =
                """
                    // Signature format: 6.0
                    // - style=java
                    // - java-sealed-classes=yes
                    package test.pkg {
                      public abstract sealed non-exhaustive class Sealed permits test.pkg.SubclassA test.pkg.SubclassB {
                      }
                      public final class SubclassA extends test.pkg.Sealed {
                        ctor public SubclassA();
                      }
                      public non-sealed class SubclassB extends test.pkg.Sealed {
                        ctor public SubclassB();
                      }
                    }
                """,
            stubFiles =
                arrayOf(
                    java(
                        """
                            package test.pkg;
                            @SuppressWarnings({"unchecked", "deprecation", "all"})
                            public abstract sealed class Sealed permits test.pkg.SubclassA, test.pkg.SubclassB {
                            Sealed() { throw new RuntimeException("Stub!"); }
                            }
                        """
                    ),
                    java(
                        """
                            package test.pkg;
                            @SuppressWarnings({"unchecked", "deprecation", "all"})
                            public final class SubclassA extends test.pkg.Sealed {
                            public SubclassA() { throw new RuntimeException("Stub!"); }
                            }
                        """
                    ),
                    java(
                        """
                            package test.pkg;
                            @SuppressWarnings({"unchecked", "deprecation", "all"})
                            public non-sealed class SubclassB extends test.pkg.Sealed {
                            public SubclassB() { throw new RuntimeException("Stub!"); }
                            }
                        """
                    ),
                ),
        )
    }

    @Test
    fun `Test exhaustive concrete sealed class with java-sealed-classes=yes`() {
        checkStubs(
            format = FORMAT_V6_WITH_JAVA_SEALED_CLASSES,
            sourceFiles =
                arrayOf(
                    java(
                        """
                            package test.pkg;

                            public sealed class Sealed {
                                public Sealed(int a) {}
                            }
                        """
                    ),
                    java(
                        """
                            package test.pkg;

                            public final class SubclassA extends Sealed {
                            }
                        """
                    ),
                    java(
                        """
                            package test.pkg;

                            public non-sealed class SubclassB extends Sealed {
                            }
                        """
                    ),
                ),
            api =
                """
                    // Signature format: 6.0
                    // - style=java
                    // - java-sealed-classes=yes
                    package test.pkg {
                      public sealed exhaustive class Sealed permits test.pkg.SubclassA test.pkg.SubclassB {
                        ctor public Sealed(int);
                      }
                      public final class SubclassA extends test.pkg.Sealed {
                        ctor public SubclassA();
                      }
                      public non-sealed class SubclassB extends test.pkg.Sealed {
                        ctor public SubclassB();
                      }
                    }
                """,
            stubFiles =
                arrayOf(
                    java(
                        """
                            package test.pkg;
                            @SuppressWarnings({"unchecked", "deprecation", "all"})
                            public sealed class Sealed permits test.pkg.SubclassA, test.pkg.SubclassB {
                            public Sealed(int a) { throw new RuntimeException("Stub!"); }
                            }
                        """
                    ),
                    java(
                        """
                            package test.pkg;
                            @SuppressWarnings({"unchecked", "deprecation", "all"})
                            public final class SubclassA extends test.pkg.Sealed {
                            public SubclassA() { super(0); throw new RuntimeException("Stub!"); }
                            }
                        """
                    ),
                    java(
                        """
                            package test.pkg;
                            @SuppressWarnings({"unchecked", "deprecation", "all"})
                            public non-sealed class SubclassB extends test.pkg.Sealed {
                            public SubclassB() { super(0); throw new RuntimeException("Stub!"); }
                            }
                        """
                    ),
                ),
        )
    }

    @Test
    fun `Test non-exhaustive concrete sealed class with java-sealed-classes=yes`() {
        checkStubs(
            format = FORMAT_V6_WITH_JAVA_SEALED_CLASSES,
            sourceFiles =
                arrayOf(
                    java(
                        """
                            package test.pkg;

                            public sealed class Sealed {
                                public Sealed(int a) {}
                            }
                        """
                    ),
                    java(
                        """
                            package test.pkg;

                            public final class SubclassA extends Sealed {
                            }
                        """
                    ),
                    java(
                        """
                            package test.pkg;

                            public non-sealed class SubclassB extends Sealed {
                            }
                        """
                    ),
                    java(
                        """
                            package test.pkg;

                            non-sealed class SubclassPrivate extends Sealed {
                            }
                        """
                    ),
                ),
            api =
                """
                    // Signature format: 6.0
                    // - style=java
                    // - java-sealed-classes=yes
                    package test.pkg {
                      public sealed non-exhaustive class Sealed permits test.pkg.SubclassA test.pkg.SubclassB {
                        ctor public Sealed(int);
                      }
                      public final class SubclassA extends test.pkg.Sealed {
                        ctor public SubclassA();
                      }
                      public non-sealed class SubclassB extends test.pkg.Sealed {
                        ctor public SubclassB();
                      }
                    }
                """,
            stubFiles =
                arrayOf(
                    java(
                        """
                            package test.pkg;
                            @SuppressWarnings({"unchecked", "deprecation", "all"})
                            public sealed class Sealed permits test.pkg.SubclassA, test.pkg.SubclassB {
                            public Sealed(int a) { throw new RuntimeException("Stub!"); }
                            }
                        """
                    ),
                    java(
                        """
                            package test.pkg;
                            @SuppressWarnings({"unchecked", "deprecation", "all"})
                            public final class SubclassA extends test.pkg.Sealed {
                            public SubclassA() { super(0); throw new RuntimeException("Stub!"); }
                            }
                        """
                    ),
                    java(
                        """
                            package test.pkg;
                            @SuppressWarnings({"unchecked", "deprecation", "all"})
                            public non-sealed class SubclassB extends test.pkg.Sealed {
                            public SubclassB() { super(0); throw new RuntimeException("Stub!"); }
                            }
                        """
                    ),
                ),
        )
    }

    @Test
    fun `Test exhaustive sealed interface with java-sealed-classes=yes`() {
        checkStubs(
            format = FORMAT_V6_WITH_JAVA_SEALED_CLASSES,
            sourceFiles =
                arrayOf(
                    java(
                        """
                            package test.pkg;

                            public sealed interface Sealed {
                            }
                        """
                    ),
                    java(
                        """
                            package test.pkg;

                            public non-sealed interface SubclassA extends Sealed {
                            }
                        """
                    ),
                    java(
                        """
                            package test.pkg;

                            public non-sealed interface SubclassB extends Sealed {
                            }
                        """
                    ),
                ),
            api =
                """
                    // Signature format: 6.0
                    // - style=java
                    // - java-sealed-classes=yes
                    package test.pkg {
                      public sealed exhaustive interface Sealed permits test.pkg.SubclassA test.pkg.SubclassB {
                      }
                      public non-sealed interface SubclassA extends test.pkg.Sealed {
                      }
                      public non-sealed interface SubclassB extends test.pkg.Sealed {
                      }
                    }
                """,
            stubFiles =
                arrayOf(
                    java(
                        """
                            package test.pkg;
                            @SuppressWarnings({"unchecked", "deprecation", "all"})
                            public sealed interface Sealed permits test.pkg.SubclassA, test.pkg.SubclassB {
                            }
                        """
                    ),
                    java(
                        """
                            package test.pkg;
                            @SuppressWarnings({"unchecked", "deprecation", "all"})
                            public non-sealed interface SubclassA extends test.pkg.Sealed {
                            }
                        """
                    ),
                    java(
                        """
                            package test.pkg;
                            @SuppressWarnings({"unchecked", "deprecation", "all"})
                            public non-sealed interface SubclassB extends test.pkg.Sealed {
                            }
                        """
                    ),
                ),
        )
    }

    @Test
    fun `Test non-exhaustive sealed interface with java-sealed-classes=yes`() {
        checkStubs(
            format = FORMAT_V6_WITH_JAVA_SEALED_CLASSES,
            sourceFiles =
                arrayOf(
                    java(
                        """
                            package test.pkg;

                            public sealed interface Sealed {
                            }
                        """
                    ),
                    java(
                        """
                            package test.pkg;

                            public non-sealed interface SubclassA extends Sealed {
                            }
                        """
                    ),
                    java(
                        """
                            package test.pkg;

                            public non-sealed interface SubclassB extends Sealed {
                            }
                        """
                    ),
                    java(
                        """
                            package test.pkg;

                            non-sealed interface SubclassPrivate extends Sealed {
                            }
                        """
                    ),
                ),
            api =
                """
                    // Signature format: 6.0
                    // - style=java
                    // - java-sealed-classes=yes
                    package test.pkg {
                      public sealed non-exhaustive interface Sealed permits test.pkg.SubclassA test.pkg.SubclassB {
                      }
                      public non-sealed interface SubclassA extends test.pkg.Sealed {
                      }
                      public non-sealed interface SubclassB extends test.pkg.Sealed {
                      }
                    }
                """,
            stubFiles =
                arrayOf(
                    java(
                        """
                            package test.pkg;
                            @SuppressWarnings({"unchecked", "deprecation", "all"})
                            public sealed interface Sealed permits test.pkg.SubclassA, test.pkg.SubclassB {
                            }
                        """
                    ),
                    java(
                        """
                            package test.pkg;
                            @SuppressWarnings({"unchecked", "deprecation", "all"})
                            public non-sealed interface SubclassB extends test.pkg.Sealed {
                            }
                        """
                    ),
                    java(
                        """
                            package test.pkg;
                            @SuppressWarnings({"unchecked", "deprecation", "all"})
                            public non-sealed interface SubclassA extends test.pkg.Sealed {
                            }
                        """
                    ),
                ),
        )
    }
}
