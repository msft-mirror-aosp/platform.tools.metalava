/*
 * Copyright (C) 2017 The Android Open Source Project
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

import com.android.tools.metalava.model.SUPPORT_TYPE_USE_ANNOTATIONS
import org.junit.Test

class NullnessMigrationTest : DriverTest() {
    @Test
    fun `Test Kotlin-style null signatures`() {
        check(
            compatibilityMode = false,
            signatureSource = """
                package test.pkg {
                  public class MyTest {
                    ctor public MyTest();
                    method public Double convert0(Float);
                    method @Nullable public Double convert1(@NonNull Float);
                    method @Nullable public Double convert2(@NonNull Float);
                    method @Nullable public Double convert3(@NonNull Float);
                    method @Nullable public Double convert4(@NonNull Float);
                  }
                }
                """,
            api = """
                package test.pkg {
                  public class MyTest {
                    ctor public MyTest();
                    method public Double! convert0(Float!);
                    method public Double? convert1(Float);
                    method public Double? convert2(Float);
                    method public Double? convert3(Float);
                    method public Double? convert4(Float);
                  }
                }
                """
        )
    }

    @Test
    fun `Method which is now marked null should be marked as recently migrated null`() {
        check(
            migrateNulls = true,
            outputKotlinStyleNulls = false,
            compatibilityMode = false,
            signatureSource = """
                package test.pkg {
                  public abstract class MyTest {
                    method @Nullable public Double convert1(Float);
                  }
                }
                """,
            previousApi = """
                package test.pkg {
                  public abstract class MyTest {
                    method public Double convert1(Float);
                  }
                }
                """,
            api = """
                package test.pkg {
                  public abstract class MyTest {
                    method @RecentlyNullable public Double convert1(Float);
                  }
                }
                """
        )
    }

    @Test
    fun `Parameter which is now marked null should be marked as recently migrated null`() {
        check(
            migrateNulls = true,
            outputKotlinStyleNulls = false,
            compatibilityMode = false,
            signatureSource = """
                package test.pkg {
                  public abstract class MyTest {
                    method public Double convert1(@NonNull Float);
                  }
                }
                """,
            previousApi = """
                package test.pkg {
                  public abstract class MyTest {
                    method public Double convert1(Float);
                  }
                }
                """,
            api = """
                package test.pkg {
                  public abstract class MyTest {
                    method public Double convert1(@RecentlyNonNull Float);
                  }
                }
                """
        )
    }

    @Test
    fun `Comprehensive check of migration`() {
        check(
            migrateNulls = true,
            outputKotlinStyleNulls = false,
            compatibilityMode = false,
            signatureSource = """
                package test.pkg {
                  public class MyTest {
                    ctor public MyTest();
                    method public Double convert0(Float);
                    method @Nullable public Double convert1(@NonNull Float);
                    method @Nullable public Double convert2(@NonNull Float);
                    method @Nullable public Double convert3(@NonNull Float);
                    method @Nullable public Double convert4(@NonNull Float);
                  }
                }
                """,
            previousApi = """
                package test.pkg {
                  public class MyTest {
                    ctor public MyTest();
                    method public Double convert0(Float);
                    method public Double convert1(Float);
                    method @RecentlyNullable public Double convert2(@RecentlyNonNull Float);
                    method @RecentlyNullable public Double convert3(@RecentlyNonNull Float);
                    method @Nullable public Double convert4(@NonNull Float);
                  }
                }
                """,
            api = """
                package test.pkg {
                  public class MyTest {
                    ctor public MyTest();
                    method public Double convert0(Float);
                    method @RecentlyNullable public Double convert1(@RecentlyNonNull Float);
                    method @Nullable public Double convert2(@NonNull Float);
                    method @Nullable public Double convert3(@NonNull Float);
                    method @Nullable public Double convert4(@NonNull Float);
                  }
                }
                """
        )
    }

    @Test
    fun `Comprehensive check of migration, Kotlin-style output`() {
        check(
            migrateNulls = true,
            outputKotlinStyleNulls = true,
            compatibilityMode = false,
            signatureSource = """
                package test.pkg {
                  public class MyTest {
                    ctor public MyTest();
                    method public Double convert0(Float);
                    method @Nullable public Double convert1(@NonNull Float);
                    method @Nullable public Double convert2(@NonNull Float);
                    method @Nullable public Double convert3(@NonNull Float);
                    method @Nullable public Double convert4(@NonNull Float);
                  }
                }
                """,
            previousApi = """
                package test.pkg {
                  public class MyTest {
                    ctor public MyTest();
                    method public Double convert0(Float);
                    method public Double convert1(Float);
                    method @RecentlyNullable public Double convert2(@RecentlyNonNull Float);
                    method @RecentlyNullable public Double convert3(@RecentlyNonNull Float);
                    method @Nullable public Double convert4(@NonNull Float);
                  }
                }
                """,
            api = """
                package test.pkg {
                  public class MyTest {
                    ctor public MyTest();
                    method public Double! convert0(Float!);
                    method public Double? convert1(Float);
                    method public Double? convert2(Float);
                    method public Double? convert3(Float);
                    method public Double? convert4(Float);
                  }
                }
                """
        )
    }

    @Test
    fun `Convert libcore nullness annotations to support`() {
        check(
            outputKotlinStyleNulls = false,
            compatibilityMode = false,
            sourceFiles = *arrayOf(
                java(
                    """
                    package test.pkg;
                    public class Test {
                        public @libcore.util.NonNull Object compute() {
                            return 5;
                        }
                    }
                    """
                ),
                java(
                    """
                    package libcore.util;
                    import static java.lang.annotation.ElementType.TYPE_USE;
                    import static java.lang.annotation.ElementType.TYPE_PARAMETER;
                    import static java.lang.annotation.RetentionPolicy.SOURCE;
                    import java.lang.annotation.Documented;
                    import java.lang.annotation.Retention;
                    @Documented
                    @Retention(SOURCE)
                    @Target({TYPE_USE})
                    public @interface NonNull {
                       int from() default Integer.MIN_VALUE;
                       int to() default Integer.MAX_VALUE;
                    }
                    """
                )
            ),
            api = """
                    package libcore.util {
                      public @interface NonNull {
                        method public abstract int from() default java.lang.Integer.MIN_VALUE;
                        method public abstract int to() default java.lang.Integer.MAX_VALUE;
                      }
                    }
                    package test.pkg {
                      public class Test {
                        ctor public Test();
                        method @NonNull public Object compute();
                      }
                    }
                """
        )
    }

    @Test
    fun `Check type use annotations`() {
        check(
            outputKotlinStyleNulls = false,
            compatibilityMode = false,
            sourceFiles = *arrayOf(
                java(
                    """
                    package test.pkg;
                    import androidx.annotation.Nullable;
                    import java.util.List;
                    public class Test {
                        public @Nullable Integer compute1(@Nullable java.util.List<@Nullable String> list) {
                            return 5;
                        }
                        public @Nullable Integer compute2(@Nullable java.util.List<@Nullable List<?>> list) {
                            return 5;
                        }
                        // TODO arrays
                    }
                    """
                ),
                androidxNonNullSource,
                androidxNullableSource
            ),
            extraArguments = arrayOf("--hide-package", "androidx.annotation"),
            api = if (SUPPORT_TYPE_USE_ANNOTATIONS) {
                """
                package test.pkg {
                  public class Test {
                    ctor public Test();
                    method @Nullable public Integer compute1(@Nullable java.util.List<@Nullable java.lang.String>);
                    method @Nullable public Integer compute2(@Nullable java.util.List<@Nullable java.util.List<?>>);
                  }
                }
                """
            } else {
                """
                package test.pkg {
                  public class Test {
                    ctor public Test();
                    method @Nullable public Integer compute1(@Nullable java.util.List<java.lang.String>);
                    method @Nullable public Integer compute2(@Nullable java.util.List<java.util.List<?>>);
                  }
                }
                """
            }
        )
    }

    @Test
    fun `Check androidx package annotation`() {
        check(
            outputKotlinStyleNulls = false,
            compatibilityMode = false,
            sourceFiles = *arrayOf(
                java(
                    """
                    package test.pkg;
                    import androidx.annotation.Nullable;
                    import androidx.annotation.NonNull;
                    import java.util.List;
                    public class Test {
                        public @Nullable Integer compute1(@Nullable java.util.List<@Nullable String> list) {
                            return 5;
                        }
                        public @Nullable Integer compute2(@NonNull java.util.List<@NonNull List<?>> list) {
                            return 5;
                        }
                    }
                    """
                ),
                androidxNonNullSource,
                androidxNullableSource
            ),
            extraArguments = arrayOf("--hide-package", "androidx.annotation"),
            api = if (SUPPORT_TYPE_USE_ANNOTATIONS) {
                """
                package test.pkg {
                  public class Test {
                    ctor public Test();
                    method @Nullable public Integer compute1(@Nullable java.util.List<@Nullable java.lang.String>);
                    method @Nullable public Integer compute2(@NonNull java.util.List<@NonNull java.util.List<?>>);
                  }
                }
                """
            } else {
                """
                package test.pkg {
                  public class Test {
                    ctor public Test();
                    method @Nullable public Integer compute1(@Nullable java.util.List<java.lang.String>);
                    method @Nullable public Integer compute2(@NonNull java.util.List<java.util.List<?>>);
                  }
                }
                """
            }
        )
    }

    @Test
    fun `Migrate nullness for type-use annotations`() {
        check(
            outputKotlinStyleNulls = false,
            compatibilityMode = false,
            migrateNulls = true,
            sourceFiles = *arrayOf(
                java(
                    """
                    package test.pkg;
                    import androidx.annotation.Nullable;
                    import androidx.annotation.NonNull;
                    public class Foo {
                       public static char @NonNull [] toChars(int codePoint) { return new char[0]; }
                       public static int codePointAt(char @NonNull [] a, int index) { throw new RuntimeException("Stub!"); }
                       public <T> T @NonNull [] toArray(T @NonNull [] a);
                       // New APIs should not be marked *recently* nullable; they're fully nullable
                       public static @NonNull String newMethod(@Nullable String argument) { return ""; }
                    }
                    """
                ),
                androidxNonNullSource,
                androidxNullableSource
            ),
            extraArguments = arrayOf("--hide-package", "androidx.annotation"),
            // TODO: Handle multiple nullness annotations
            previousApi =
            """
                package test.pkg {
                  public class Foo {
                    ctor public Foo();
                    method public static int codePointAt(char[], int);
                    method public <T> T[] toArray(T[]);
                    method public static char[] toChars(int);
                  }
                }
                """,
            stubs = if (SUPPORT_TYPE_USE_ANNOTATIONS) {
                arrayOf(
                    """
                    package test.pkg;
                    @SuppressWarnings({"unchecked", "deprecation", "all"})
                    public class Foo {
                    public Foo() { throw new RuntimeException("Stub!"); }
                    public static char @androidx.annotation.RecentlyNonNull [] toChars(int codePoint) { throw new RuntimeException("Stub!"); }
                    public static int codePointAt(char @androidx.annotation.RecentlyNonNull [] a, int index) { throw new RuntimeException("Stub!"); }
                    public <T> T @androidx.annotation.RecentlyNonNull [] toArray(T @androidx.annotation.RecentlyNonNull [] a) { throw new RuntimeException("Stub!"); }
                    @androidx.annotation.NonNull public static java.lang.String newMethod(@androidx.annotation.Nullable java.lang.String argument) { throw new RuntimeException("Stub!"); }
                    }
                """
                )
            } else {
                arrayOf(
                    """
                    package test.pkg;
                    @SuppressWarnings({"unchecked", "deprecation", "all"})
                    public class Foo {
                    public Foo() { throw new RuntimeException("Stub!"); }
                    public static char[] toChars(int codePoint) { throw new RuntimeException("Stub!"); }
                    public static int codePointAt(char[] a, int index) { throw new RuntimeException("Stub!"); }
                    public <T> T[] toArray(T[] a) { throw new RuntimeException("Stub!"); }
                    @androidx.annotation.NonNull public static java.lang.String newMethod(@androidx.annotation.Nullable java.lang.String argument) { throw new RuntimeException("Stub!"); }
                    }
                    """
                )
            }
        )
    }

    @Test
    fun `Do not migrate type-use annotations when not changed`() {
        check(
            outputKotlinStyleNulls = false,
            compatibilityMode = false,
            migrateNulls = true,
            sourceFiles = *arrayOf(
                java(
                    """
                    package test.pkg;
                    import androidx.annotation.Nullable;
                    import androidx.annotation.NonNull;
                    public class Foo {
                       public static char @NonNull [] toChars(int codePoint) { return new char[0]; }
                       public static int codePointAt(char @NonNull [] a, int index) { throw new RuntimeException("Stub!"); }
                       public <T> T @NonNull [] toArray(T @NonNull [] a);
                    }
                    """
                ),
                androidxNonNullSource,
                androidxNullableSource
            ),
            extraArguments = arrayOf("--hide-package", "androidx.annotation"),
            // TODO: Handle multiple nullness annotations
            previousApi =
            """
                package test.pkg {
                  public class Foo {
                    ctor public Foo();
                    method public static int codePointAt(char[], int);
                    method public <T> T[] toArray(T[]);
                    method public static char[] toChars(int);
                  }
                }
                """,
            stubs = if (SUPPORT_TYPE_USE_ANNOTATIONS) {
                arrayOf(
                    """
                    package test.pkg;
                    @SuppressWarnings({"unchecked", "deprecation", "all"})
                    public class Foo {
                    public Foo() { throw new RuntimeException("Stub!"); }
                    public static char @androidx.annotation.RecentlyNonNull [] toChars(int codePoint) { throw new RuntimeException("Stub!"); }
                    public static int codePointAt(char @androidx.annotation.RecentlyNonNull [] a, int index) { throw new RuntimeException("Stub!"); }
                    public <T> T @androidx.annotation.RecentlyNonNull [] toArray(T @androidx.annotation.RecentlyNonNull [] a) { throw new RuntimeException("Stub!"); }
                    }
                    """
                )
            } else {
                arrayOf(
                    """
                    package test.pkg;
                    @SuppressWarnings({"unchecked", "deprecation", "all"})
                    public class Foo {
                    public Foo() { throw new RuntimeException("Stub!"); }
                    public static char[] toChars(int codePoint) { throw new RuntimeException("Stub!"); }
                    public static int codePointAt(char[] a, int index) { throw new RuntimeException("Stub!"); }
                    public <T> T[] toArray(T[] a) { throw new RuntimeException("Stub!"); }
                    }
                    """
                )
            }
        )
    }

    @Test
    fun `Regression test for issue 111054266, type use annotations`() {
        check(
            outputKotlinStyleNulls = false,
            compatibilityMode = false,
            migrateNulls = true,
            sourceFiles = *arrayOf(
                java(
                    """
                    package test.pkg;
                    import androidx.annotation.NonNull;
                    import java.lang.reflect.TypeVariable;

                    public class Foo {
                        @NonNull public java.lang.reflect.Constructor<?> @NonNull [] getConstructors() {
                            return null;
                        }

                        public synchronized @NonNull TypeVariable<@NonNull Class<T>> @NonNull [] getTypeParameters() {
                            return null;
                        }
                    }
                    """
                ),
                androidxNonNullSource,
                androidxNullableSource
            ),
            previousApi = """
                package test.pkg {
                  public class Foo {
                    ctor public Foo();
                    method public java.lang.reflect.Constructor<?>[] getConstructors();
                    method public synchronized java.lang.reflect.TypeVariable<@java.lang.Class<T>>[] getTypeParameters();
                  }
                }
            """,
            extraArguments = arrayOf("--hide-package", "androidx.annotation"),
            stubs = if (SUPPORT_TYPE_USE_ANNOTATIONS) {
                arrayOf(
                    """
                    package test.pkg;
                    @SuppressWarnings({"unchecked", "deprecation", "all"})
                    public class Foo {
                    public Foo() { throw new RuntimeException("Stub!"); }
                    @androidx.annotation.RecentlyNonNull public java.lang.reflect.Constructor<?> @androidx.annotation.RecentlyNonNull [] getConstructors() { throw new RuntimeException("Stub!"); }
                    @androidx.annotation.RecentlyNonNull public synchronized java.lang.reflect.TypeVariable<java.lang.@androidx.annotation.RecentlyNonNull Class<T>> @androidx.annotation.RecentlyNonNull [] getTypeParameters() { throw new RuntimeException("Stub!"); }
                    }
                    """
                )
            } else {
                arrayOf(
                    """
                    package test.pkg;
                    @SuppressWarnings({"unchecked", "deprecation", "all"})
                    public class Foo {
                    public Foo() { throw new RuntimeException("Stub!"); }
                    @androidx.annotation.RecentlyNonNull public java.lang.reflect.Constructor<?>[] getConstructors() { throw new RuntimeException("Stub!"); }
                    @androidx.annotation.RecentlyNonNull public synchronized java.lang.reflect.TypeVariable<java.lang.Class<T>>[] getTypeParameters() { throw new RuntimeException("Stub!"); }
                    }
                    """
                )
            }
        )
    }
}