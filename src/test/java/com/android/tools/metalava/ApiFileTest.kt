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

@file:Suppress("ALL")

package com.android.tools.metalava

import org.junit.Test

class ApiFileTest : DriverTest() {
/*
   Conditions to test:
   - test all the error scenarios found in the notStrippable case!
   - split up test into many individual test cases
   - try referencing a class from an annotation!
   - test having a throws list where some exceptions are hidden but extend
     public exceptions: do we map over to the referenced ones?

   - test type reference from all the possible places -- in type signatures - interfaces,
     extends, throws, type bounds, etc.
   - method which overrides @hide method: should appear in subclass (test chain
     of two nested too)
   - BluetoothGattCharacteristic.java#describeContents: Was marked @hide,
     but is unhidden because it extends a public interface method
   - package javadoc (also make sure merging both!, e.g. try having @hide in each)
   - StopWatchMap -- inner class with @hide marks allh top levels!
   - Test field inlining: should I include fields from an interface, if that
     inteface was implemented by the parent class (and therefore appears there too?)
     What if the superclass is abstract?
   - Exposing package private classes. Test that I only do this for package private
     classes, NOT Those marked @hide (is that, having @hide on a used type, illegal?)
   - Test error handling (invalid @hide combinations))
   - Consider what happens if we promote a package private class (because it's
     extended by a public class), and then we restore its public members; the
     override logic there isn't quite right. We've duplicated the significant-override
     code to not skip private members, but that could change semantics. This isn't
     ideal; instead we should now mark this class as public, and re-run the analysis
     again (with the new hidden state for this class).
   - compilation unit sorting - top level classes out of order
   - Massive classes such as android.R.java? Maybe do synthetic test.
   - HttpResponseCache implemented a public OkHttp interface, but the sole implementation
     method was marked @hide, so the method doesn't show up. Is that some other rule --
     that we skip interfaces if their implementation methods are marked @hide?
   - Test recursive package filtering.
 */

    @Test
    fun `Basic class signature extraction`() {
        // Basic class; also checks that default constructor is made explicit
        check(
            sourceFiles = *arrayOf(
                java(
                    """
                    package test.pkg;
                    public class Foo {
                    }
                    """
                )
            ),
            api = """
                    package test.pkg {
                      public class Foo {
                        ctor public Foo();
                      }
                    }
                    """
        )
    }

    @Test
    fun `Parameter Names in Java`() {
        // Java code which explicitly specifies parameter names
        check(
            compatibilityMode = false, // parameter names only in v2
            sourceFiles = *arrayOf(
                java(
                    """
                    package test.pkg;
                    import androidx.annotation.ParameterName;

                    public class Foo {
                        public void foo(int javaParameter1, @ParameterName("publicParameterName") int javaParameter2) {
                        }
                    }
                    """
                ),
                supportParameterName
            ),
            api = """
                    package test.pkg {
                      public class Foo {
                        ctor public Foo();
                        method public void foo(int, int publicParameterName);
                      }
                    }
                 """,
            extraArguments = arrayOf(ARG_HIDE_PACKAGE, "androidx.annotation"),
            checkDoclava1 = false /* doesn't support parameter names */
        )
    }

    @Test
    fun `Default Values Names in Java`() {
        // Java code which explicitly specifies parameter names
        check(
            format = FileFormat.V3,
            sourceFiles = *arrayOf(
                java(
                    """
                    package test.pkg;
                    import androidx.annotation.DefaultValue;

                    public class Foo {
                        public void foo(
                            @DefaultValue("null") String prefix,
                            @DefaultValue("\"Hello World\"") String greeting,
                            @DefaultValue("42") int meaning) {
                        }
                    }
                    """
                ),
                supportDefaultValue
            ),
            api = """
                // Signature format: 3.0
                package test.pkg {
                  public class Foo {
                    ctor public Foo();
                    method public void foo(String! = null, String! = "Hello World", int = 42);
                  }
                }
                 """,
            extraArguments = arrayOf(ARG_HIDE_PACKAGE, "androidx.annotation"),
            checkDoclava1 = false /* doesn't support default Values */
        )
    }

    @Test
    fun `Default Values and Names in Kotlin`() {
        // Kotlin code which explicitly specifies parameter names
        check(
            format = FileFormat.V3,
            compatibilityMode = false,
            sourceFiles = *arrayOf(
                kotlin(
                    """
                    package test.pkg
                    import some.other.pkg.Constants.Misc.SIZE
                    import android.graphics.Bitmap
                    import android.view.View

                    class Foo {
                        fun method1(int: Int = 42,
                            int2: Int? = null,
                            byte: Int = 2 * 21,
                            str: String = "hello " + "world",
                            vararg args: String) { }

                        fun method2(int: Int, int2: Int = (2*int) * SIZE) { }

                        fun method3(str: String, int: Int, int2: Int = double(int) + str.length) { }

                        fun emptyLambda(sizeOf: () -> Unit = {  }) {}

                        fun View.drawToBitmap(config: Bitmap.Config = Bitmap.Config.ARGB_8888): Bitmap? = null

                        companion object {
                            fun double(int: Int) = 2 * int
                            fun print(foo: Foo = Foo()) { println(foo) }
                        }
                    }
                    """
                ),
                java(
                    """
                    package some.other.pkg;
                    public class Constants {
                        public static class Misc {
                            public static final int SIZE = 5;
                        }
                    }
                    """
                )
            ),
            api = """
                // Signature format: 3.0
                package test.pkg {
                  public final class Foo {
                    ctor public Foo();
                    method public android.graphics.Bitmap? drawToBitmap(android.view.View, android.graphics.Bitmap.Config config = android.graphics.Bitmap.Config.ARGB_8888);
                    method public void emptyLambda(kotlin.jvm.functions.Function0<kotlin.Unit> sizeOf = {});
                    method public void method1(int p = 42, Integer? int2 = null, int p1 = 42, String str = "hello world", java.lang.String... args);
                    method public void method2(int p, int int2 = (2 * int) * some.other.pkg.Constants.Misc.SIZE);
                    method public void method3(String str, int p, int int2 = double(int) + str.length);
                    field public static final test.pkg.Foo.Companion! Companion;
                  }
                  public static final class Foo.Companion {
                    method public int double(int p);
                    method public void print(test.pkg.Foo foo = test.pkg.Foo());
                  }
                }
                """,
            extraArguments = arrayOf(ARG_HIDE_PACKAGE, "androidx.annotation", ARG_HIDE_PACKAGE, "some.other.pkg"),
            includeSignatureVersion = true,
            checkDoclava1 = false /* doesn't support default Values */
        )
    }

    @Test
    fun `Default Values in Kotlin for expressions`() {
        // Testing trickier default values; regression test for problem
        // observed in androidx.core.util with LruCache
        check(
            format = FileFormat.V3,
            sourceFiles = *arrayOf(
                kotlin(
                    """
                    package androidx.core.util

                    import android.util.LruCache

                    inline fun <K : Any, V : Any> lruCache(
                        maxSize: Int,
                        crossinline sizeOf: (key: K, value: V) -> Int = { _, _ -> 1 },
                        @Suppress("USELESS_CAST") // https://youtrack.jetbrains.com/issue/KT-21946
                        crossinline create: (key: K) -> V? = { null as V? },
                        crossinline onEntryRemoved: (evicted: Boolean, key: K, oldValue: V, newValue: V?) -> Unit =
                            { _, _, _, _ -> }
                    ): LruCache<K, V> {
                        return object : LruCache<K, V>(maxSize) {
                            override fun sizeOf(key: K, value: V) = sizeOf(key, value)
                            override fun create(key: K) = create(key)
                            override fun entryRemoved(evicted: Boolean, key: K, oldValue: V, newValue: V?) {
                                onEntryRemoved(evicted, key, oldValue, newValue)
                            }
                        }
                    }
                    """
                ),
                java(
                    """
                    package androidx.collection;

                    import androidx.annotation.NonNull;
                    import androidx.annotation.Nullable;

                    import java.util.LinkedHashMap;
                    import java.util.Locale;
                    import java.util.Map;

                    public class LruCache<K, V> {
                        @Nullable
                        protected V create(@NonNull K key) {
                            return null;
                        }

                        protected int sizeOf(@NonNull K key, @NonNull V value) {
                            return 1;
                        }

                        protected void entryRemoved(boolean evicted, @NonNull K key, @NonNull V oldValue,
                                @Nullable V newValue) {
                        }
                    }
                    """
                ),
                androidxNullableSource,
                androidxNonNullSource
            ),
            api = """
                // Signature format: 3.0
                package androidx.core.util {
                  public final class TestKt {
                    ctor public TestKt();
                    method public static inline <K, V> android.util.LruCache<K,V> lruCache(int maxSize, kotlin.jvm.functions.Function2<? super K,? super V,java.lang.Integer> sizeOf = { _, _ -> 1 }, kotlin.jvm.functions.Function1<? super K,? extends V> create = { (java.lang.Object)null }, kotlin.jvm.functions.Function4<? super java.lang.Boolean,? super K,? super V,? super V,kotlin.Unit> onEntryRemoved = { _, _, _, _ ->  });
                  }
                }
                """,
            extraArguments = arrayOf(ARG_HIDE_PACKAGE, "androidx.annotation", ARG_HIDE_PACKAGE, "androidx.collection"),
            includeSignatureVersion = true,
            checkDoclava1 = false /* doesn't support default Values */
        )
    }

    @Test
    fun `Basic Kotlin class`() {
        check(
            format = FileFormat.V1,
            extraArguments = arrayOf("--parameter-names=true"),
            sourceFiles = *arrayOf(
                kotlin(
                    """
                    package test.pkg
                    class Kotlin(val property1: String = "Default Value", arg2: Int) : Parent() {
                        override fun method() = "Hello World"
                        fun otherMethod(ok: Boolean, times: Int) {
                        }

                        var property2: String? = null

                        private var someField = 42
                        @JvmField
                        var someField2 = 42

                        internal var myHiddenVar = false
                        internal fun myHiddenMethod(): Unit { }
                        internal data class myHiddenClass(): Unit { }

                        companion object {
                            const val MY_CONST = 42
                        }
                    }

                    //@get:RequiresApi(26)
                    inline val @receiver:String Long.isSrgb get() = true
                    inline val /*@receiver:ColorInt*/ Int.red get() = 0
                    inline operator fun String.component1() = ""

                    open class Parent {
                        open fun method(): String? = null
                        open fun method2(value: Boolean, value: Boolean?): String? = null
                        open fun method3(value: Int?, value2: Int): Int = null
                    }
                    """
                )
            ),
            api = """
                package test.pkg {
                  public final class Kotlin extends test.pkg.Parent {
                    ctor public Kotlin(java.lang.String property1, int arg2);
                    method public java.lang.String getProperty1();
                    method public java.lang.String getProperty2();
                    method public void otherMethod(boolean ok, int times);
                    method public void setProperty2(java.lang.String p);
                    property public final java.lang.String property2;
                    field public static final test.pkg.Kotlin.Companion Companion;
                    field public static final int MY_CONST = 42; // 0x2a
                    field public int someField2;
                  }
                  public static final class Kotlin.Companion {
                  }
                  public final class KotlinKt {
                    ctor public KotlinKt();
                    method public static inline operator java.lang.String component1(java.lang.String);
                    method public static inline int getRed(int);
                    method public static inline boolean isSrgb(long);
                  }
                  public class Parent {
                    ctor public Parent();
                    method public java.lang.String method();
                    method public java.lang.String method2(boolean value, java.lang.Boolean value);
                    method public int method3(java.lang.Integer value, int value2);
                  }
                }
                """,
            privateApi = """
                package test.pkg {
                  public final class Kotlin extends test.pkg.Parent {
                    method internal boolean getMyHiddenVar${"$"}lintWithKotlin();
                    method internal void myHiddenMethod${"$"}lintWithKotlin();
                    method internal void setMyHiddenVar${"$"}lintWithKotlin(boolean p);
                    property internal final boolean myHiddenVar;
                    field internal boolean myHiddenVar;
                    field private final java.lang.String property1;
                    field private java.lang.String property2;
                    field private int someField;
                  }
                  public static final class Kotlin.Companion {
                    ctor private Kotlin.Companion();
                  }
                  internal static final class Kotlin.myHiddenClass extends kotlin.Unit {
                    ctor public Kotlin.myHiddenClass();
                    method internal test.pkg.Kotlin.myHiddenClass copy();
                  }
                }
                """,
            checkDoclava1 = false /* doesn't support Kotlin... */
        )
    }

    @Test
    fun `Kotlin Reified Methods`() {
        check(
            sourceFiles = *arrayOf(
                java(
                    """
                    package test.pkg;

                    public class Context {
                        @SuppressWarnings("unchecked")
                        public final <T> T getSystemService(Class<T> serviceClass) {
                            return null;
                        }
                    }
                    """
                ),
                kotlin(
                    """
                    package test.pkg

                    inline fun <reified T> Context.systemService1() = getSystemService(T::class.java)
                    inline fun Context.systemService2() = getSystemService(String::class.java)
                    """
                )
            ),
            api = """
                package test.pkg {
                  public class Context {
                    ctor public Context();
                    method public final <T> T getSystemService(java.lang.Class<T>);
                  }
                  public final class _java_Kt {
                    ctor public _java_Kt();
                    method public static inline <reified T> T systemService1(test.pkg.Context);
                    method public static inline java.lang.String systemService2(test.pkg.Context);
                  }
                }
                """,
            checkDoclava1 = false /* doesn't support Kotlin... */
        )
    }

    @Test
    fun `Kotlin Reified Methods 2`() {
        check(
            compatibilityMode = false,
            sourceFiles = *arrayOf(
                kotlin(
                    """
                    @file:Suppress("NOTHING_TO_INLINE", "RedundantVisibilityModifier", "unused")

                    package test.pkg

                    inline fun <T> a(t: T) { }
                    inline fun <reified T> b(t: T) { }
                    private inline fun <reified T> c(t: T) { } // hide
                    internal inline fun <reified T> d(t: T) { } // hide
                    public inline fun <reified T> e(t: T) { }
                    inline fun <reified T> T.f(t: T) { }
                    """
                )
            ),
            api = """
                package test.pkg {
                  public final class TestKt {
                    ctor public TestKt();
                    method public static inline <T> void a(@Nullable T t);
                    method public static inline <reified T> void b(@Nullable T t);
                    method public static inline <reified T> void e(@Nullable T t);
                    method public static inline <reified T> void f(@Nullable T, @Nullable T t);
                  }
                }
                """,
            checkDoclava1 = false /* doesn't support Kotlin... */
        )
    }

    @Test
    fun `Suspend functions`() {
        check(
            compatibilityMode = false,
            sourceFiles = *arrayOf(
                kotlin(
                    """
                    package test.pkg
                    suspend inline fun hello() { }
                    """
                )
            ),
            api = """
                package test.pkg {
                  public final class TestKt {
                    ctor public TestKt();
                    method public static suspend inline Object hello(@NonNull kotlin.coroutines.Continuation<? super kotlin.Unit> p);
                  }
                }
                """,
            checkDoclava1 = false /* doesn't support Kotlin... */
        )
    }

    @Test
    fun `Kotlin Generics`() {
        check(
            format = FileFormat.V3,
            sourceFiles = *arrayOf(
                kotlin(
                    """
                    package test.pkg
                    class Bar
                    class Type<in T> {
                        fun foo(param: Type<Bar>) {
                        }
                    }
                    """
                )
            ),
            compatibilityMode = false,
            api = """
                // Signature format: 3.0
                package test.pkg {
                  public final class Bar {
                    ctor public Bar();
                  }
                  public final class Type<T> {
                    ctor public Type();
                    method public void foo(test.pkg.Type<? super test.pkg.Bar> param);
                  }
                }
                """,
            checkDoclava1 = false /* doesn't support Kotlin... */
        )
    }

    @Test
    fun `Propagate Platform types in Kotlin`() {
        check(
            compatibilityMode = false,
            outputKotlinStyleNulls = true,
            sourceFiles = *arrayOf(
                kotlin(
                    """
                    // Nullable Pair in Kotlin
                    package androidx.util

                    class NullableKotlinPair<out F, out S>(val first: F?, val second: S?)
                    """
                ),
                kotlin(
                    """
                    // Non-nullable Pair in Kotlin
                    package androidx.util
                    class NonNullableKotlinPair<out F: Any, out S: Any>(val first: F, val second: S)
                    """
                ),
                java(
                    """
                    // Platform nullability Pair in Java
                    package androidx.util;

                    @SuppressWarnings("WeakerAccess")
                    public class PlatformJavaPair<F, S> {
                        public final F first;
                        public final S second;

                        public PlatformJavaPair(F first, S second) {
                            this.first = first;
                            this.second = second;
                        }
                    }
                """
                ),
                java(
                    """
                    // Platform nullability Pair in Java
                    package androidx.util;
                    import androidx.annotation.NonNull;
                    import androidx.annotation.Nullable;

                    @SuppressWarnings("WeakerAccess")
                    public class NullableJavaPair<F, S> {
                        public final @Nullable F first;
                        public final @Nullable S second;

                        public NullableJavaPair(@Nullable F first, @Nullable S second) {
                            this.first = first;
                            this.second = second;
                        }
                    }
                    """
                ),
                java(
                    """
                    // Platform nullability Pair in Java
                    package androidx.util;

                    import androidx.annotation.NonNull;

                    @SuppressWarnings("WeakerAccess")
                    public class NonNullableJavaPair<F, S> {
                        public final @NonNull F first;
                        public final @NonNull S second;

                        public NonNullableJavaPair(@NonNull F first, @NonNull S second) {
                            this.first = first;
                            this.second = second;
                        }
                    }
                    """
                ),
                kotlin(
                    """
                    package androidx.util

                    @Suppress("HasPlatformType") // Intentionally propagating platform type with unknown nullability.
                    inline operator fun <F, S> PlatformJavaPair<F, S>.component1() = first
                    """
                ),
                androidxNonNullSource,
                androidxNullableSource
            ),
            api = """
                // Signature format: 3.0
                package androidx.util {
                  public class NonNullableJavaPair<F, S> {
                    ctor public NonNullableJavaPair(F, S);
                    field public final F first;
                    field public final S second;
                  }
                  public final class NonNullableKotlinPair<F, S> {
                    ctor public NonNullableKotlinPair(F first, S second);
                    method public F getFirst();
                    method public S getSecond();
                  }
                  public class NullableJavaPair<F, S> {
                    ctor public NullableJavaPair(F?, S?);
                    field public final F? first;
                    field public final S? second;
                  }
                  public final class NullableKotlinPair<F, S> {
                    ctor public NullableKotlinPair(F? first, S? second);
                    method public F? getFirst();
                    method public S? getSecond();
                  }
                  public class PlatformJavaPair<F, S> {
                    ctor public PlatformJavaPair(F!, S!);
                    field public final F! first;
                    field public final S! second;
                  }
                  public final class TestKt {
                    ctor public TestKt();
                    method public static inline operator <F, S> F! component1(androidx.util.PlatformJavaPair<F,S>);
                  }
                }
                """,
            extraArguments = arrayOf(ARG_HIDE_PACKAGE, "androidx.annotation"),
            checkDoclava1 = false /* doesn't support Kotlin... */
        )
    }

    @Test
    fun `Known nullness`() {
        // Don't emit platform types for some unannotated elements that we know the
        // nullness for: annotation type members, equals-parameters, initialized constants, etc.
        check(
            compatibilityMode = false,
            outputKotlinStyleNulls = true,
            sourceFiles = *arrayOf(
                java(
                    """
                    // Platform nullability Pair in Java
                    package test;

                    import androidx.annotation.NonNull;

                    public class MyClass {
                        public static final String MY_CONSTANT1 = "constant"; // Not nullable
                        public final String MY_CONSTANT2 = "constant"; // Not nullable
                        public String MY_CONSTANT3 = "constant"; // Unknown

                        /** @deprecated */
                        @Deprecated
                        @Override
                        public boolean equals(
                            Object parameter  // nullable
                        ) {
                            return super.equals(parameter);
                        }

                        /** @deprecated */
                        @Deprecated
                        @Override // Not nullable
                        public String toString() {
                            return super.toString();
                        }
                    }
                    """
                ),
                java(
                    """
                    package test.pkg;

                    import static java.lang.annotation.ElementType.*;
                    import java.lang.annotation.*;
                    public @interface MyAnnotation {
                        String[] value(); // Not nullable
                    }
                    """
                ).indented(),
                java(
                    """
                    package test.pkg;
                    @SuppressWarnings("ALL")
                    public enum Foo {
                        A, B;
                    }
                    """
                ),
                androidxNonNullSource,
                androidxNullableSource
            ),
            api = """
                // Signature format: 3.0
                package test {
                  public class MyClass {
                    ctor public MyClass();
                    method @Deprecated public boolean equals(Object?);
                    method @Deprecated public String toString();
                    field public static final String MY_CONSTANT1 = "constant";
                    field public final String MY_CONSTANT2 = "constant";
                    field public String! MY_CONSTANT3;
                  }
                }
                package test.pkg {
                  public enum Foo {
                    enum_constant public static final test.pkg.Foo A;
                    enum_constant public static final test.pkg.Foo B;
                  }
                  @java.lang.annotation.Retention(java.lang.annotation.RetentionPolicy.CLASS) public @interface MyAnnotation {
                    method public abstract String[] value();
                  }
                }
                """,
            extraArguments = arrayOf(ARG_HIDE_PACKAGE, "androidx.annotation"),
            checkDoclava1 = false /* doesn't support Kotlin... */
        )
    }

    @Test
    fun `JvmOverloads`() {
        // Regression test for https://github.com/android/android-ktx/issues/366
        check(
            format = FileFormat.V3,
            compatibilityMode = false,
            sourceFiles = *arrayOf(
                kotlin(
                    """
                        package androidx.content

                        import android.annotation.SuppressLint
                        import android.content.SharedPreferences

                        @SuppressLint("ApplySharedPref")
                        @JvmOverloads
                        inline fun SharedPreferences.edit(
                            commit: Boolean = false,
                            action: SharedPreferences.Editor.() -> Unit
                        ) {
                            val editor = edit()
                            action(editor)
                            if (commit) {
                                editor.commit()
                            } else {
                                editor.apply()
                            }
                        }

                        @JvmOverloads
                        fun String.blahblahblah(firstArg: String = "hello", secondArg: Int = 42, thirdArg: String = "world") {
                        }
                    """
                )
            ),
            api = """
                // Signature format: 3.0
                package androidx.content {
                  public final class TestKt {
                    ctor public TestKt();
                    method public static void blahblahblah(String, String firstArg = "hello", int secondArg = 42, String thirdArg = "world");
                    method public static void blahblahblah(String, String firstArg = "hello", int secondArg = 42);
                    method public static void blahblahblah(String, String firstArg = "hello");
                    method public static void blahblahblah(String);
                    method public static inline void edit(android.content.SharedPreferences, boolean commit = false, kotlin.jvm.functions.Function1<? super android.content.SharedPreferences.Editor,kotlin.Unit> action);
                    method public static inline void edit(android.content.SharedPreferences, kotlin.jvm.functions.Function1<? super android.content.SharedPreferences.Editor,kotlin.Unit> action);
                  }
                }
                """,
            extraArguments = arrayOf(ARG_HIDE_PACKAGE, "androidx.annotation"),
            checkDoclava1 = false /* doesn't support default Values */
        )
    }

    @Test
    fun `Extract class with generics`() {
        // Basic interface with generics; makes sure <T extends Object> is written as just <T>
        // Also include some more complex generics expressions to make sure they're serialized
        // correctly (in particular, using fully qualified names instead of what appears in
        // the source code.)
        check(
            checkDoclava1 = true,
            sourceFiles = *arrayOf(
                java(
                    """
                    package test.pkg;
                    @SuppressWarnings("ALL")
                    public interface MyInterface<T extends Object>
                            extends MyBaseInterface {
                    }
                    """
                ), java(
                    """
                    package a.b.c;
                    @SuppressWarnings("ALL")
                    public interface MyStream<T, S extends MyStream<T, S>> extends test.pkg.AutoCloseable {
                    }
                    """
                ), java(
                    """
                    package test.pkg;
                    @SuppressWarnings("ALL")
                    public interface MyInterface2<T extends Number>
                            extends MyBaseInterface {
                        class TtsSpan<C extends MyInterface<?>> { }
                        abstract class Range<T extends Comparable<? super T>> {
                            protected String myString;
                        }
                    }
                    """
                ),
                java(
                    """
                    package test.pkg;
                    public interface MyBaseInterface {
                        void fun(int a, String b);
                    }
                    """
                ),
                java(
                    """
                    package test.pkg;
                    public interface MyOtherInterface extends MyBaseInterface, AutoCloseable {
                        void fun(int a, String b);
                    }
                    """
                ),
                java(
                    """
                    package test.pkg;
                    public interface AutoCloseable {
                    }
                    """
                )
            ),
            api = """
                    package a.b.c {
                      public abstract interface MyStream<T, S extends a.b.c.MyStream<T, S>> implements test.pkg.AutoCloseable {
                      }
                    }
                    package test.pkg {
                      public abstract interface AutoCloseable {
                      }
                      public abstract interface MyBaseInterface {
                        method public abstract void fun(int, java.lang.String);
                      }
                      public abstract interface MyInterface<T> implements test.pkg.MyBaseInterface {
                      }
                      public abstract interface MyInterface2<T extends java.lang.Number> implements test.pkg.MyBaseInterface {
                      }
                      public static abstract class MyInterface2.Range<T extends java.lang.Comparable<? super T>> {
                        ctor public MyInterface2.Range();
                        field protected java.lang.String myString;
                      }
                      public static class MyInterface2.TtsSpan<C extends test.pkg.MyInterface<?>> {
                        ctor public MyInterface2.TtsSpan();
                      }
                      public abstract interface MyOtherInterface implements test.pkg.AutoCloseable test.pkg.MyBaseInterface {
                      }
                    }
                """,
            extraArguments = arrayOf(ARG_HIDE, "KotlinKeyword")
        )
    }

    @Test
    fun `Basic class without default constructor, has constructors with args`() {
        // Class without private constructors (shouldn't insert default constructor)
        check(
            sourceFiles = *arrayOf(
                java(
                    """
                    package test.pkg;
                    public class Foo {
                        public Foo(int i) {

                        }
                        public Foo(int i, int j) {
                        }
                    }
                    """
                )
            ),
            api = """
                package test.pkg {
                  public class Foo {
                    ctor public Foo(int);
                    ctor public Foo(int, int);
                  }
                }
                """
        )
    }

    @Test
    fun `Basic class without default constructor, has private constructor`() {
        // Class without private constructors; no default constructor should be inserted
        check(
            sourceFiles = *arrayOf(
                java(
                    """
                    package test.pkg;
                    @SuppressWarnings("ALL")
                    public class Foo {
                        private Foo() {
                        }
                    }
                    """
                )
            ),
            api = """
                package test.pkg {
                  public class Foo {
                  }
                }
                """
        )
    }

    @Test
    fun `Interface class extraction`() {
        // Interface: makes sure the right modifiers etc are shown (and that "package private" methods
        // in the interface are taken to be public etc)
        check(
            sourceFiles = *arrayOf(
                java(
                    """
                    package test.pkg;
                    @SuppressWarnings("ALL")
                    public interface Foo {
                        void foo();
                    }
                    """
                )
            ),
            api = """
                package test.pkg {
                  public abstract interface Foo {
                    method public abstract void foo();
                  }
                }
                """
        )
    }

    @Test
    fun `Enum class extraction`() {
        // Interface: makes sure the right modifiers etc are shown (and that "package private" methods
        // in the interface are taken to be public etc)
        check(
            sourceFiles = *arrayOf(
                java(
                    """
                    package test.pkg;
                    @SuppressWarnings("ALL")
                    public enum Foo {
                        A, B;
                    }
                    """
                )
            ),
            api = """
                package test.pkg {
                  public final class Foo extends java.lang.Enum {
                    method public static test.pkg.Foo valueOf(java.lang.String);
                    method public static final test.pkg.Foo[] values();
                    enum_constant public static final test.pkg.Foo A;
                    enum_constant public static final test.pkg.Foo B;
                  }
                }
                """
        )
    }

    @Test
    fun `Enum class, non-compat mode`() {
        // Interface: makes sure the right modifiers etc are shown (and that "package private" methods
        // in the interface are taken to be public etc)
        check(
            sourceFiles = *arrayOf(
                java(
                    """
                    package test.pkg;
                    @SuppressWarnings("ALL")
                    public enum Foo {
                        A, B;
                    }
                    """
                )
            ),
            compatibilityMode = false,
            api = """
                package test.pkg {
                  public enum Foo {
                    enum_constant public static final test.pkg.Foo A;
                    enum_constant public static final test.pkg.Foo B;
                  }
                }
                """
        )
    }

    @Test
    fun `Annotation class extraction`() {
        // Interface: makes sure the right modifiers etc are shown (and that "package private" methods
        // in the interface are taken to be public etc)
        check(
            // For unknown reasons, doclava1 behaves differently here than when invoked on the
            // whole platform
            checkDoclava1 = false,
            sourceFiles = *arrayOf(
                java(
                    """
                    package test.pkg;
                    @SuppressWarnings("ALL")
                    public @interface Foo {
                        String value();
                    }
                    """
                ),
                java(
                    """
                    package android.annotation;
                    import static java.lang.annotation.ElementType.*;
                    import java.lang.annotation.*;
                    @Target({TYPE, FIELD, METHOD, PARAMETER, CONSTRUCTOR, LOCAL_VARIABLE})
                    @Retention(RetentionPolicy.CLASS)
                    @SuppressWarnings("ALL")
                    public @interface SuppressLint {
                        String[] value();
                    }
                """
                )
            ),
            api = """
                package android.annotation {
                  public abstract class SuppressLint implements java.lang.annotation.Annotation {
                  }
                }
                package test.pkg {
                  public abstract class Foo implements java.lang.annotation.Annotation {
                  }
                }
                """
        )
    }

    @Test
    fun `Do not include inherited public methods from private parents in compat mode`() {
        // Real life example: StringBuilder.setLength, in compat mode
        check(
            compatibilityMode = true,
            sourceFiles = *arrayOf(
                java(
                    """
                    package test.pkg;
                    public class MyStringBuilder extends AbstractMyStringBuilder {
                    }
                    """
                ),
                java(
                    """
                    package test.pkg;
                    class AbstractMyStringBuilder {
                        public void setLength(int length) {
                        }
                    }
                    """
                )
            ),
            api = """
                package test.pkg {
                  public class MyStringBuilder {
                    ctor public MyStringBuilder();
                  }
                }
                """
        )
    }

    @Test
    fun `Include inherited public methods from private parents`() {
        // In non-compat mode, include public methods from hidden parents too.
        // Real life example: StringBuilder.setLength
        // This is just like the above test, but with compat mode disabled.
        check(
            compatibilityMode = false,
            sourceFiles = *arrayOf(
                java(
                    """
                    package test.pkg;
                    public class MyStringBuilder extends AbstractMyStringBuilder {
                    }
                    """
                ),
                java(
                    """
                    package test.pkg;
                    class AbstractMyStringBuilder {
                        public void setLength(int length) {
                        }
                    }
                    """
                )
            ),
            api = """
                package test.pkg {
                  public class MyStringBuilder {
                    ctor public MyStringBuilder();
                    method public void setLength(int);
                  }
                }
                """
        )
    }

    @Test
    fun `Skip inherited package private methods from private parents`() {
        // In non-compat mode, include public methods from hidden parents too.
        // Real life example: StringBuilder.setLength
        // This is just like the above test, but with compat mode disabled.
        check(
            compatibilityMode = false,
            sourceFiles = *arrayOf(
                java(
                    """
                    package test.pkg;
                    public class MyStringBuilder<A,B> extends AbstractMyStringBuilder<A,B> {
                    }
                    """
                ),
                java(
                    """
                    package test.pkg;
                    class AbstractMyStringBuilder<C,D> extends PublicSuper<C,D> {
                        public void setLength(int length) {
                        }
                        @Override boolean isContiguous() {
                            return true;
                        }
                        @Override boolean concrete() {
                            return false;
                        }
                    }
                    """
                ),
                java(
                    """
                    package test.pkg;
                    public class PublicSuper<E,F> {
                        abstract boolean isContiguous();
                        boolean concrete() {
                            return false;
                        }
                    }
                    """
                )
            ),
            api = """
                package test.pkg {
                  public class MyStringBuilder<A, B> extends test.pkg.PublicSuper<A,B> {
                    ctor public MyStringBuilder();
                    method public void setLength(int);
                  }
                  public class PublicSuper<E, F> {
                    ctor public PublicSuper();
                  }
                }
                """
        )
    }

    @Test
    fun `Annotation class extraction, non-compat mode`() {
        // Interface: makes sure the right modifiers etc are shown (and that "package private" methods
        // in the interface are taken to be public etc)
        check(
            sourceFiles = *arrayOf(
                java(
                    """
                    package test.pkg;
                    public @interface Foo {
                        String value();
                    }
                    """
                ),
                java(
                    """
                    package android.annotation;
                    import static java.lang.annotation.ElementType.*;
                    import java.lang.annotation.*;
                    @Target({TYPE, FIELD, METHOD, PARAMETER, CONSTRUCTOR, LOCAL_VARIABLE})
                    @Retention(RetentionPolicy.CLASS)
                    @SuppressWarnings("ALL")
                    public @interface SuppressLint {
                        String[] value();
                    }
                    """
                )
            ),
            compatibilityMode = false,
            api = """
                package android.annotation {
                  @java.lang.annotation.Retention(java.lang.annotation.RetentionPolicy.CLASS) @java.lang.annotation.Target({java.lang.annotation.ElementType.TYPE, java.lang.annotation.ElementType.FIELD, java.lang.annotation.ElementType.METHOD, java.lang.annotation.ElementType.PARAMETER, java.lang.annotation.ElementType.CONSTRUCTOR, java.lang.annotation.ElementType.LOCAL_VARIABLE}) public @interface SuppressLint {
                    method public abstract String[] value();
                  }
                }
                package test.pkg {
                  @java.lang.annotation.Retention(java.lang.annotation.RetentionPolicy.CLASS) public @interface Foo {
                    method public abstract String value();
                  }
                }
                """
        )
    }

    @Test
    fun `Annotation retention`() {
        // For annotations where the java.lang.annotation classes themselves are not
        // part of the source tree, ensure that we compute the right retention (runtime, meaning
        // it should show up in the stubs file.).
        check(
            extraArguments = arrayOf(ARG_EXCLUDE_ANNOTATIONS),
            sourceFiles = *arrayOf(
                java(
                    """
                    package test.pkg;
                    public @interface Foo {
                        String value();
                    }
                    """
                ),
                java(
                    """
                    package android.annotation;
                    import static java.lang.annotation.ElementType.*;
                    import java.lang.annotation.*;
                    @Target({TYPE, FIELD, METHOD, PARAMETER, CONSTRUCTOR, LOCAL_VARIABLE})
                    @Retention(RetentionPolicy.CLASS)
                    @SuppressWarnings("ALL")
                    public @interface SuppressLint {
                        String[] value();
                    }
                    """
                )
            ),
            compatibilityMode = true,
            stubs = arrayOf(
                // For annotations where the java.lang.annotation classes themselves are not
                // part of the source tree, ensure that we compute the right retention (runtime, meaning
                // it should show up in the stubs file.).
                """
                package test.pkg;
                @SuppressWarnings({"unchecked", "deprecation", "all"})
                public @interface Foo {
                public java.lang.String value();
                }
                """,
                """
                package android.annotation;
                @SuppressWarnings({"unchecked", "deprecation", "all"})
                @java.lang.annotation.Retention(java.lang.annotation.RetentionPolicy.CLASS)
                @java.lang.annotation.Target({java.lang.annotation.ElementType.TYPE, java.lang.annotation.ElementType.FIELD, java.lang.annotation.ElementType.METHOD, java.lang.annotation.ElementType.PARAMETER, java.lang.annotation.ElementType.CONSTRUCTOR, java.lang.annotation.ElementType.LOCAL_VARIABLE})
                public @interface SuppressLint {
                public java.lang.String[] value();
                }
                """
            ),
            checkDoclava1 = false
        )
    }

    @Test
    fun `Superclass signature extraction`() {
        // Make sure superclass statement is correct; inherited method from parent that has same
        // signature isn't included in the child
        check(
            sourceFiles = *arrayOf(
                java(
                    """
                    package test.pkg;
                    @SuppressWarnings("ALL")
                    public class Foo extends Super {
                        @Override public void base() { }
                        public void child() { }
                    }
                    """
                ),
                java(
                    """
                    package test.pkg;
                    @SuppressWarnings("ALL")
                    public class Super {
                        public void base() { }
                    }
                    """
                )
            ),
            api = """
                package test.pkg {
                  public class Foo extends test.pkg.Super {
                    ctor public Foo();
                    method public void child();
                  }
                  public class Super {
                    ctor public Super();
                    method public void base();
                  }
                }
                """
        )
    }

    @Test
    fun `Extract fields with types and initial values`() {
        check(
            sourceFiles = *arrayOf(
                java(
                    """
                    package test.pkg;
                    @SuppressWarnings("ALL")
                    public class Foo {
                        private int hidden = 1;
                        int hidden2 = 2;
                        /** @hide */
                        int hidden3 = 3;

                        protected int field00; // No value
                        public static final boolean field01 = true;
                        public static final int field02 = 42;
                        public static final long field03 = 42L;
                        public static final short field04 = 5;
                        public static final byte field05 = 5;
                        public static final char field06 = 'c';
                        public static final float field07 = 98.5f;
                        public static final double field08 = 98.5;
                        public static final String field09 = "String with \"escapes\" and \u00a9...";
                        public static final double field10 = Double.NaN;
                        public static final double field11 = Double.POSITIVE_INFINITY;

                        public static final String GOOD_IRI_CHAR = "a-zA-Z0-9\u00a0-\ud7ff\uf900-\ufdcf\ufdf0-\uffef";
                        public static final char HEX_INPUT = 61184;
                    }
                    """
                )
            ),
            api = """
                package test.pkg {
                  public class Foo {
                    ctor public Foo();
                    field public static final java.lang.String GOOD_IRI_CHAR = "a-zA-Z0-9\u00a0-\ud7ff\uf900-\ufdcf\ufdf0-\uffef";
                    field public static final char HEX_INPUT = 61184; // 0xef00 '\uef00'
                    field protected int field00;
                    field public static final boolean field01 = true;
                    field public static final int field02 = 42; // 0x2a
                    field public static final long field03 = 42L; // 0x2aL
                    field public static final short field04 = 5; // 0x5
                    field public static final byte field05 = 5; // 0x5
                    field public static final char field06 = 99; // 0x0063 'c'
                    field public static final float field07 = 98.5f;
                    field public static final double field08 = 98.5;
                    field public static final java.lang.String field09 = "String with \"escapes\" and \u00a9...";
                    field public static final double field10 = (0.0/0.0);
                    field public static final double field11 = (1.0/0.0);
                  }
                }
                """
        )
    }

    @Test
    fun `Check all modifiers`() {
        // Include as many modifiers as possible to see which ones are included
        // in the signature files, and the expected sorting order.
        // Note that the signature files treat "deprecated" as a fake modifier.
        // Note also how the "protected" modifier on the interface method gets
        // promoted to public.
        check(
            checkDoclava1 = true,
            sourceFiles = *arrayOf(
                java(
                    """
                    package test.pkg;

                    @SuppressWarnings("ALL")
                    public abstract class Foo {
                        @Deprecated private static final long field1 = 5;
                        @Deprecated private static volatile long field2 = 5;
                        @Deprecated public static strictfp final synchronized void method1() { }
                        @Deprecated public static final synchronized native void method2();
                        @Deprecated protected static final class Inner1 { }
                        @Deprecated protected static abstract  class Inner2 { }
                        @Deprecated protected interface Inner3 {
                            default void method3() { }
                            static void method4(final int arg) { }
                        }
                    }
                    """
                )
            ),

            warnings = """
                src/test/pkg/Foo.java:7: error: Method test.pkg.Foo.method1(): @Deprecated annotation (present) and @deprecated doc tag (not present) do not match [DeprecationMismatch]
                src/test/pkg/Foo.java:8: error: Method test.pkg.Foo.method2(): @Deprecated annotation (present) and @deprecated doc tag (not present) do not match [DeprecationMismatch]
                src/test/pkg/Foo.java:9: error: Class test.pkg.Foo.Inner1: @Deprecated annotation (present) and @deprecated doc tag (not present) do not match [DeprecationMismatch]
                src/test/pkg/Foo.java:10: error: Class test.pkg.Foo.Inner2: @Deprecated annotation (present) and @deprecated doc tag (not present) do not match [DeprecationMismatch]
                src/test/pkg/Foo.java:11: error: Class test.pkg.Foo.Inner3: @Deprecated annotation (present) and @deprecated doc tag (not present) do not match [DeprecationMismatch]
                """,

            api = """
                    package test.pkg {
                      public abstract class Foo {
                        ctor public Foo();
                        method public static final deprecated synchronized void method1();
                        method public static final deprecated synchronized void method2();
                      }
                      protected static final deprecated class Foo.Inner1 {
                        ctor protected Foo.Inner1();
                      }
                      protected static abstract deprecated class Foo.Inner2 {
                        ctor protected Foo.Inner2();
                      }
                      protected static abstract deprecated interface Foo.Inner3 {
                        method public default void method3();
                        method public static void method4(int);
                      }
                    }
                """
        )
    }

    @Test
    fun `Warn about findViewById`() {
        // Include as many modifiers as possible to see which ones are included
        // in the signature files, and the expected sorting order.
        // Note that the signature files treat "deprecated" as a fake modifier.
        // Note also how the "protected" modifier on the interface method gets
        // promoted to public.
        check(
            checkDoclava1 = false,
            compatibilityMode = false,
            outputKotlinStyleNulls = false,
            sourceFiles = *arrayOf(
                java(
                    """
                    package test.pkg;
                    import android.annotation.Nullable;

                    @SuppressWarnings("ALL")
                    public abstract class Foo {
                        @Nullable public String findViewById(int id) { return ""; }
                    }
                    """
                ),
                nullableSource
            ),

            warnings = """
                src/test/pkg/Foo.java:6: warning: method test.pkg.Foo.findViewById(int) should not be annotated @Nullable; it should be left unspecified to make it a platform type [ExpectedPlatformType]
                """,
            extraArguments = arrayOf(ARG_WARNING, "ExpectedPlatformType"),
            api = """
                package test.pkg {
                  public abstract class Foo {
                    ctor public Foo();
                    method public String findViewById(int);
                  }
                }
                """
        )
    }

    @Test
    fun `Check all modifiers, non-compat mode`() {
        // Like testModifiers but turns off compat mode, such that we have
        // a modifier order more in line with standard code conventions
        check(
            compatibilityMode = false,
            sourceFiles = *arrayOf(
                java(
                    """
                    package test.pkg;

                    @SuppressWarnings("ALL")
                    public abstract class Foo {
                        @Deprecated private static final long field1 = 5;
                        @Deprecated private static volatile long field2 = 5;
                        /** @deprecated */ @Deprecated public static strictfp final synchronized void method1() { }
                        /** @deprecated */ @Deprecated public static final synchronized native void method2();
                        /** @deprecated */ @Deprecated protected static final class Inner1 { }
                        /** @deprecated */ @Deprecated protected static abstract class Inner2 { }
                        /** @deprecated */ @Deprecated protected interface Inner3 {
                            protected default void method3() { }
                            static void method4(final int arg) { }
                        }
                    }
                    """
                )
            ),
            api = """
                package test.pkg {
                  public abstract class Foo {
                    ctor public Foo();
                    method @Deprecated public static final void method1();
                    method @Deprecated public static final void method2();
                  }
                  @Deprecated protected static final class Foo.Inner1 {
                    ctor @Deprecated protected Foo.Inner1();
                  }
                  @Deprecated protected abstract static class Foo.Inner2 {
                    ctor @Deprecated protected Foo.Inner2();
                  }
                  @Deprecated protected static interface Foo.Inner3 {
                    method @Deprecated public default void method3();
                    method @Deprecated public static void method4(int);
                  }
                }
                """
        )
    }

    @Test
    fun `Package with only hidden classes should be removed from signature files`() {
        // Checks that if we have packages that are hidden, or contain only hidden or doconly
        // classes, the entire package is omitted from the signature file. Note how the test.pkg1.sub
        // package is not marked @hide, but doclava now treats subpackages of a hidden package
        // as also hidden.
        check(
            sourceFiles = *arrayOf(
                java(
                    """
                    ${"/** @hide hidden package */" /* avoid dangling javadoc warning */}
                    package test.pkg1;
                    """
                ),
                java(
                    """
                    package test.pkg1;
                    @SuppressWarnings("ALL")
                    public class Foo {
                        // Hidden by package hide
                    }
                    """
                ),
                java(
                    """
                    package test.pkg2;
                    /** @hide hidden class in this package */
                    @SuppressWarnings("ALL")
                    public class Bar {
                    }
                    """
                ),
                java(
                    """
                    package test.pkg2;
                    /** @doconly hidden class in this package */
                    @SuppressWarnings("ALL")
                    public class Baz {
                    }
                    """
                ),
                java(
                    """
                    package test.pkg1.sub;
                    // Hidden by @hide in package above
                    @SuppressWarnings("ALL")
                    public class Test {
                    }
                    """
                ),
                java(
                    """
                    package test.pkg3;
                    // The only really visible class
                    @SuppressWarnings("ALL")
                    public class Boo {
                    }
                    """
                )
            ),
            api = """
                package test.pkg3 {
                  public class Boo {
                    ctor public Boo();
                  }
                }
                """
        )
    }

    @Test
    fun `Enums can be abstract`() {
        // As per https://bugs.openjdk.java.net/browse/JDK-6287639
        // abstract methods in enums should not be listed as abstract,
        // but doclava1 does, so replicate this.
        // Also checks that we handle both enum fields and regular fields
        // and that they are listed separately.

        check(
            sourceFiles = *arrayOf(
                java(
                    """
                    package test.pkg;

                    @SuppressWarnings("ALL")
                    public enum FooBar {
                        ABC {
                            @Override
                            protected void foo() { }
                        }, DEF {
                            @Override
                            protected void foo() { }
                        };

                        protected abstract void foo();
                        public static int field1 = 1;
                        public int field2 = 2;
                    }
                    """
                )
            ),
            api = """
                package test.pkg {
                  public class FooBar extends java.lang.Enum {
                    method protected abstract void foo();
                    method public static test.pkg.FooBar valueOf(java.lang.String);
                    method public static final test.pkg.FooBar[] values();
                    enum_constant public static final test.pkg.FooBar ABC;
                    enum_constant public static final test.pkg.FooBar DEF;
                    field public static int field1;
                    field public int field2;
                  }
                }
            """
        )
    }

    @Test
    fun `Check erasure in throws-list`() {
        // Makes sure that when we have a generic signature in the throws list we take
        // the erasure instead (in compat mode); "Throwable" instead of "X" in the below
        // test. Real world example: Optional.orElseThrow.
        check(
            compatibilityMode = true,
            sourceFiles = *arrayOf(
                java(
                    """
                    package test.pkg;

                    import java.util.function.Supplier;

                    @SuppressWarnings("ALL")
                    public final class Test<T> {
                        public <X extends Throwable> T orElseThrow(Supplier<? extends X> exceptionSupplier) throws X {
                            return null;
                        }
                    }
                    """
                )
            ),
            api = """
                package test.pkg {
                  public final class Test<T> {
                    ctor public Test();
                    method public <X extends java.lang.Throwable> T orElseThrow(java.util.function.Supplier<? extends X>) throws java.lang.Throwable;
                  }
                }
                """
        )
    }

    @Test
    fun `Check various generics signature subtleties`() {
        // Some additional declarations where PSI default type handling diffs from doclava1
        check(
            sourceFiles = *arrayOf(
                java(
                    """
                    package test.pkg;

                    @SuppressWarnings("ALL")
                    public abstract class Collections {
                        public static <T extends java.lang.Object & java.lang.Comparable<? super T>> T max(java.util.Collection<? extends T> collection) {
                            return null;
                        }
                        public abstract <T extends java.util.Collection<java.lang.String>> T addAllTo(T t);
                        public final class Range<T extends java.lang.Comparable<? super T>> { }
                    }
                    """
                ), java(
                    """
                    package test.pkg;

                    import java.util.Set;

                    @SuppressWarnings("ALL")
                    public class MoreAsserts {
                        public static void assertEquals(String arg0, Set<? extends Object> arg1, Set<? extends Object> arg2) { }
                        public static void assertEquals(Set<? extends Object> arg1, Set<? extends Object> arg2) { }
                    }

                    """
                )
            ),

            // This is the output from doclava1; I'm not quite matching this yet (sorting order differs,
            // and my heuristic to remove "extends java.lang.Object" is somehow preserved here. I'm
            // not clear on when they do it and when they don't.
            /*
            api = """
            package test.pkg {
              public abstract class Collections {
                ctor public Collections();
                method public abstract <T extends java.util.Collection<java.lang.String>> T addAllTo(T);
                method public static <T & java.lang.Comparable<? super T>> T max(java.util.Collection<? extends T>);
              }
              public final class Collections.Range<T extends java.lang.Comparable<? super T>> {
                ctor public Collections.Range();
              }
              public class MoreAsserts {
                ctor public MoreAsserts();
                method public static void assertEquals(java.util.Set<? extends java.lang.Object>, java.util.Set<? extends java.lang.Object>);
                method public static void assertEquals(java.lang.String, java.util.Set<? extends java.lang.Object>, java.util.Set<? extends java.lang.Object>);
              }
            }
            """,
            */
            api = """
                package test.pkg {
                  public abstract class Collections {
                    ctor public Collections();
                    method public abstract <T extends java.util.Collection<java.lang.String>> T addAllTo(T);
                    method public static <T extends java.lang.Object & java.lang.Comparable<? super T>> T max(java.util.Collection<? extends T>);
                  }
                  public final class Collections.Range<T extends java.lang.Comparable<? super T>> {
                    ctor public Collections.Range();
                  }
                  public class MoreAsserts {
                    ctor public MoreAsserts();
                    method public static void assertEquals(java.lang.String, java.util.Set<?>, java.util.Set<?>);
                    method public static void assertEquals(java.util.Set<?>, java.util.Set<?>);
                  }
                }
                """,

            // Can't check doclava1 on this: its output doesn't match javac, e.g. for the above declaration
            // of max, javap shows this signature:
            //   public static <T extends java.lang.Comparable<? super T>> T max(java.util.Collection<? extends T>);
            // which matches metalava's output:
            //   method public static <T & java.lang.Comparable<? super T>> T max(java.util.Collection<? extends T>);
            // and not doclava1:
            //   method public static <T extends java.lang.Object & java.lang.Comparable<? super T>> T max(java.util.Collection<? extends T>);

            checkDoclava1 = false
        )
    }

    @Test
    fun `Check instance methods in enums`() {
        // Make sure that when we have instance methods in an enum they're handled
        // correctly (there's some special casing around enums to insert extra methods
        // that was broken, as exposed by ChronoUnit#toString)
        check(
            sourceFiles = *arrayOf(
                java(
                    """
                    package test.pkg;

                    @SuppressWarnings("ALL")
                    public interface TempUnit {
                        @Override
                        String toString();
                    }
                     """
                ),
                java(
                    """
                    package test.pkg;

                    @SuppressWarnings("ALL")
                    public enum ChronUnit implements TempUnit {
                        C, B, A;

                        public String valueOf(int x) {
                            return Integer.toString(x + 5);
                        }

                        public String values(String separator) {
                            return null;
                        }

                        @Override
                        public String toString() {
                            return name();
                        }
                    }
                """
                )
            ),
            importedPackages = emptyList(),
            api = """
                package test.pkg {
                  public final class ChronUnit extends java.lang.Enum implements test.pkg.TempUnit {
                    method public static test.pkg.ChronUnit valueOf(java.lang.String);
                    method public java.lang.String valueOf(int);
                    method public static final test.pkg.ChronUnit[] values();
                    method public final java.lang.String values(java.lang.String);
                    enum_constant public static final test.pkg.ChronUnit A;
                    enum_constant public static final test.pkg.ChronUnit B;
                    enum_constant public static final test.pkg.ChronUnit C;
                  }
                  public abstract interface TempUnit {
                    method public abstract java.lang.String toString();
                  }
                }
                """
        )
    }

    @Test
    fun `Mixing enums and fields`() {
        // Checks sorting order of enum constant values
        val source = """
            package java.nio.file.attribute {
              public final class AclEntryPermission extends java.lang.Enum {
                method public static java.nio.file.attribute.AclEntryPermission valueOf(java.lang.String);
                method public static final java.nio.file.attribute.AclEntryPermission[] values();
                enum_constant public static final java.nio.file.attribute.AclEntryPermission APPEND_DATA;
                enum_constant public static final java.nio.file.attribute.AclEntryPermission DELETE;
                enum_constant public static final java.nio.file.attribute.AclEntryPermission DELETE_CHILD;
                enum_constant public static final java.nio.file.attribute.AclEntryPermission EXECUTE;
                enum_constant public static final java.nio.file.attribute.AclEntryPermission READ_ACL;
                enum_constant public static final java.nio.file.attribute.AclEntryPermission READ_ATTRIBUTES;
                enum_constant public static final java.nio.file.attribute.AclEntryPermission READ_DATA;
                enum_constant public static final java.nio.file.attribute.AclEntryPermission READ_NAMED_ATTRS;
                enum_constant public static final java.nio.file.attribute.AclEntryPermission SYNCHRONIZE;
                enum_constant public static final java.nio.file.attribute.AclEntryPermission WRITE_ACL;
                enum_constant public static final java.nio.file.attribute.AclEntryPermission WRITE_ATTRIBUTES;
                enum_constant public static final java.nio.file.attribute.AclEntryPermission WRITE_DATA;
                enum_constant public static final java.nio.file.attribute.AclEntryPermission WRITE_NAMED_ATTRS;
                enum_constant public static final java.nio.file.attribute.AclEntryPermission WRITE_OWNER;
                field public static final java.nio.file.attribute.AclEntryPermission ADD_FILE;
                field public static final java.nio.file.attribute.AclEntryPermission ADD_SUBDIRECTORY;
                field public static final java.nio.file.attribute.AclEntryPermission LIST_DIRECTORY;
              }
            }
                    """
        check(
            signatureSource = source,
            api = source
        )
    }

    @Test
    fun `Superclass filtering, should skip intermediate hidden classes`() {
        check(
            sourceFiles = *arrayOf(
                java(
                    """
                    package test.pkg;
                    @SuppressWarnings("ALL")
                    public class MyClass extends HiddenParent {
                        public void method4() { }
                    }
                    """
                ),
                java(
                    """
                    package test.pkg;
                    /** @hide */
                    @SuppressWarnings("ALL")
                    public class HiddenParent extends HiddenParent2 {
                        public static final String CONSTANT = "MyConstant";
                        protected int mContext;
                        public void method3() { }
                    }
                    """
                ),
                java(
                    """
                    package test.pkg;
                    /** @hide */
                    @SuppressWarnings("ALL")
                    public class HiddenParent2 extends PublicParent {
                        public void method2() { }
                    }
                    """
                ),
                java(
                    """
                    package test.pkg;
                    @SuppressWarnings("ALL")
                    public class PublicParent {
                        public void method1() { }
                    }
                    """
                )
            ),
            // Notice how the intermediate methods (method2, method3) have been removed
            includeStrippedSuperclassWarnings = true,
            warnings = "src/test/pkg/MyClass.java:2: warning: Public class test.pkg.MyClass stripped of unavailable superclass test.pkg.HiddenParent [HiddenSuperclass]",
            api = """
                package test.pkg {
                  public class MyClass extends test.pkg.PublicParent {
                    ctor public MyClass();
                    method public void method4();
                  }
                  public class PublicParent {
                    ctor public PublicParent();
                    method public void method1();
                  }
                }
                """
        )
    }

    @Test
    fun `Inheriting from package private classes, package private class should be included`() {
        check(
            checkDoclava1 = false, // doclava1 does not include method2, which it should
            compatibilityMode = false,
            sourceFiles =
            *arrayOf(
                java(
                    """
                    package test.pkg;
                    @SuppressWarnings("ALL")
                    public class MyClass extends HiddenParent {
                        public void method1() { }
                    }
                    """
                ),
                java(
                    """
                    package test.pkg;
                    @SuppressWarnings("ALL")
                    class HiddenParent {
                        public static final String CONSTANT = "MyConstant";
                        protected int mContext;
                        public void method2() { }
                    }
                    """
                )
            ),
            warnings = "",
            api = """
                    package test.pkg {
                      public class MyClass {
                        ctor public MyClass();
                        method public void method1();
                        method public void method2();
                        field public static final String CONSTANT = "MyConstant";
                      }
                    }
            """
        )
    }

    @Test
    fun `Using compatibility flag manually`() {
        // Like previous test, but using compatibility mode and explicitly turning on
        // the hidden super class compatibility flag. This test is mostly intended
        // to test the flag handling for individual compatibility flags.
        check(
            checkDoclava1 = false, // doclava1 does not include method2, which it should
            compatibilityMode = true,
            extraArguments = arrayOf("--skip-inherited-methods=false"),
            sourceFiles =
            *arrayOf(
                java(
                    """
                    package test.pkg;
                    @SuppressWarnings("ALL")
                    public class MyClass extends HiddenParent {
                        public void method1() { }
                    }
                    """
                ),
                java(
                    """
                    package test.pkg;
                    @SuppressWarnings("ALL")
                    class HiddenParent {
                        public static final String CONSTANT = "MyConstant";
                        protected int mContext;
                        public void method2() { }
                    }
                    """
                )
            ),
            warnings = "",
            api = """
                    package test.pkg {
                      public class MyClass {
                        ctor public MyClass();
                        method public void method1();
                        method public void method2();
                      }
                    }
            """
        )
    }

    @Test
    fun `When implementing rather than extending package private class, inline members instead`() {
        // If you implement a package private interface, we just remove it and inline the members into
        // the subclass
        check(
            compatibilityMode = true,
            sourceFiles = *arrayOf(
                java(
                    """
                    package test.pkg;
                    public class MyClass implements HiddenInterface {
                        @Override public void method() { }
                        @Override public void other() { }
                    }
                    """
                ),
                java(
                    """
                    package test.pkg;
                    public interface OtherInterface {
                        void other();
                    }
                    """
                ),
                java(
                    """
                    package test.pkg;
                    interface HiddenInterface extends OtherInterface {
                        void method() { }
                        String CONSTANT = "MyConstant";
                    }
                    """
                )
            ),
            api = """
                package test.pkg {
                  public class MyClass implements test.pkg.OtherInterface {
                    ctor public MyClass();
                    method public void method();
                    method public void other();
                    field public static final java.lang.String CONSTANT = "MyConstant";
                  }
                  public abstract interface OtherInterface {
                    method public abstract void other();
                  }
                }
                """
        )
    }

    @Test
    fun `Implementing package private class, non-compat mode`() {
        // Like the previous test, but in non compat mode we correctly
        // include all the non-hidden public interfaces into the signature

        // BUG: Note that we need to implement the parent
        check(
            compatibilityMode = false,
            sourceFiles = *arrayOf(
                java(
                    """
                    package test.pkg;
                    public class MyClass implements HiddenInterface {
                        @Override public void method() { }
                        @Override public void other() { }
                    }
                    """
                ),
                java(
                    """
                    package test.pkg;
                    public interface OtherInterface {
                        void other();
                    }
                    """
                ),
                java(
                    """
                    package test.pkg;
                    interface HiddenInterface extends OtherInterface {
                        void method() { }
                        String CONSTANT = "MyConstant";
                    }
                    """
                )
            ),
            api = """
                package test.pkg {
                  public class MyClass implements test.pkg.OtherInterface {
                    ctor public MyClass();
                    method public void method();
                    method public void other();
                    field public static final String CONSTANT = "MyConstant";
                  }
                  public interface OtherInterface {
                    method public void other();
                  }
                }
                """
        )
    }

    @Test
    fun `Default modifiers should be omitted`() {
        // If signatures vary only by the "default" modifier in the interface, don't show it on the implementing
        // class
        check(
            sourceFiles =
            *arrayOf(
                java(
                    """
                    package test.pkg;

                    public class MyClass implements SuperInterface {
                        @Override public void method() {  }
                        @Override public void method2() { }
                    }
                    """
                ),
                java(
                    """
                    package test.pkg;

                    public interface SuperInterface {
                        void method();
                        default void method2() {
                        }
                    }
                    """
                )
            ),
            api = """
                package test.pkg {
                  public class MyClass implements test.pkg.SuperInterface {
                    ctor public MyClass();
                    method public void method();
                  }
                  public abstract interface SuperInterface {
                    method public abstract void method();
                    method public default void method2();
                  }
                }
            """
        )
    }

    @Test
    fun `Override via different throws list should be included`() {
        // If a method overrides another but changes the throws list, the overriding
        // method must be listed in the subclass. This is observed for example in
        // AbstractCursor#finalize, which omits the throws clause from Object's finalize.
        check(
            sourceFiles =
            *arrayOf(
                java(
                    """
                    package test.pkg;

                    public abstract class AbstractCursor extends Parent {
                        @Override protected void finalize2() {  } // note: not throws Throwable!
                    }
                    """
                ),
                java(
                    """
                    package test.pkg;

                    @SuppressWarnings("RedundantThrows")
                    public class Parent {
                        protected void finalize2() throws Throwable {
                        }
                    }
                    """
                )
            ),
            api = """
                package test.pkg {
                  public abstract class AbstractCursor extends test.pkg.Parent {
                    ctor public AbstractCursor();
                    method protected void finalize2();
                  }
                  public class Parent {
                    ctor public Parent();
                    method protected void finalize2() throws java.lang.Throwable;
                  }
                }
            """
        )
    }

    @Test
    fun `Implementing interface method`() {
        // If you have a public method that implements an interface method,
        // they'll vary in the "abstract" modifier, but it shouldn't be listed on the
        // class. This is an issue for example for the ZonedDateTime#getLong method
        // implementing the TemporalAccessor#getLong method
        check(
            sourceFiles = *arrayOf(
                java(
                    """
                    package test.pkg;
                    public interface SomeInterface2 {
                        @Override default long getLong() {
                            return 42;
                        }
                    }
                    """
                ),
                java(
                    """
                    package test.pkg;
                    public class Foo implements SomeInterface2 {
                        @Override
                        public long getLong() { return 0L; }
                    }
                    """
                )
            ),
            api = """
            package test.pkg {
              public class Foo implements test.pkg.SomeInterface2 {
                ctor public Foo();
              }
              public abstract interface SomeInterface2 {
                method public default long getLong();
              }
            }
        """
        )
    }

    @Test
    fun `Implementing interface method 2`() {
        check(
            sourceFiles = *arrayOf(
                java(
                    """
                    package test.pkg;
                    public interface SomeInterface {
                        long getLong();
                    }
                    """
                ),
                java(
                    """
                    package test.pkg;
                    public interface SomeInterface2 {
                        @Override default long getLong() {
                            return 42;
                        }
                    }
                    """
                ),
                java(
                    """
                    package test.pkg;
                    public class Foo implements SomeInterface, SomeInterface2 {
                        @Override
                        public long getLong() { return 0L; }
                    }
                    """
                )
            ),
            api = """
                package test.pkg {
                  public class Foo implements test.pkg.SomeInterface test.pkg.SomeInterface2 {
                    ctor public Foo();
                  }
                  public abstract interface SomeInterface {
                    method public abstract long getLong();
                  }
                  public abstract interface SomeInterface2 {
                    method public default long getLong();
                  }
                }
                """
        )
    }

    @Test
    fun `Check basic @remove scenarios`() {
        // Test basic @remove handling for methods and fields
        check(
            checkDoclava1 = true,
            sourceFiles = *arrayOf(
                java(
                    """
                    package test.pkg;
                    @SuppressWarnings("JavaDoc")
                    public class Bar {
                        /** @removed */
                        public Bar() { }
                        public int field;
                        public void test() { }
                        /** @removed */
                        public int removedField;
                        /** @removed */
                        public void removedMethod() { }
                        /** @removed and @hide - should not be listed */
                        public int hiddenField;

                        /** @removed */
                        public class Inner { }

                        public class Inner2 {
                            public class Inner3 {
                                /** @removed */
                                public class Inner4 { }
                            }
                        }

                        public class Inner5 {
                            public class Inner6 {
                                public class Inner7 {
                                    /** @removed */
                                    public int removed;
                                }
                            }
                        }
                    }
                    """
                )
            ),
            removedApi = """
                package test.pkg {
                  public class Bar {
                    ctor public Bar();
                    method public void removedMethod();
                    field public int removedField;
                  }
                  public class Bar.Inner {
                    ctor public Bar.Inner();
                  }
                  public class Bar.Inner2.Inner3.Inner4 {
                    ctor public Bar.Inner2.Inner3.Inner4();
                  }
                  public class Bar.Inner5.Inner6.Inner7 {
                    field public int removed;
                  }
                }
                """,
            removedDexApi = "" +
                "Ltest/pkg/Bar;-><init>()V\n" +
                "Ltest/pkg/Bar;->removedMethod()V\n" +
                "Ltest/pkg/Bar;->removedField:I\n" +
                "Ltest/pkg/Bar\$Inner;\n" +
                "Ltest/pkg/Bar\$Inner;-><init>()V\n" +
                "Ltest/pkg/Bar\$Inner2\$Inner3\$Inner4;\n" +
                "Ltest/pkg/Bar\$Inner2\$Inner3\$Inner4;-><init>()V\n" +
                "Ltest/pkg/Bar\$Inner5\$Inner6\$Inner7;->removed:I"
        )
    }

    @Test
    fun `Check @remove class`() {
        // Test removing classes
        check(
            checkDoclava1 = true,
            sourceFiles = *arrayOf(
                java(
                    """
                    package test.pkg;
                    /** @removed */
                    @SuppressWarnings("JavaDoc")
                    public class Foo {
                        public void foo() { }
                        public class Inner {
                        }
                    }
                    """
                ),
                java(
                    """
                    package test.pkg;
                    @SuppressWarnings("JavaDoc")
                    public class Bar implements Parcelable {
                        public int field;
                        public void method();

                        /** @removed */
                        public int removedField;
                        /** @removed */
                        public void removedMethod() { }

                        public class Inner1 {
                        }
                        /** @removed */
                        public class Inner2 {
                        }
                    }
                    """
                ),
                java(
                    """
                    package test.pkg;
                    @SuppressWarnings("ALL")
                    public interface Parcelable {
                        void method();
                    }
                    """
                )
            ),
            /*
            I expected this: but doclava1 doesn't do that (and we now match its behavior)
            package test.pkg {
              public class Bar {
                method public void removedMethod();
                field public int removedField;
              }
              public class Bar.Inner2 {
              }
              public class Foo {
                method public void foo();
              }
            }
             */
            removedApi = """
                    package test.pkg {
                      public class Bar implements test.pkg.Parcelable {
                        method public void removedMethod();
                        field public int removedField;
                      }
                      public class Bar.Inner2 {
                        ctor public Bar.Inner2();
                      }
                      public class Foo {
                        ctor public Foo();
                        method public void foo();
                      }
                      public class Foo.Inner {
                        ctor public Foo.Inner();
                      }
                    }
                """
        )
    }

    @Test
    fun `Test include overridden @Deprecated even if annotated with @hide`() {
        check(
            checkDoclava1 = false, // line numbers differ; they include comments; we point straight to modifier list
            sourceFiles = *arrayOf(
                java(
                    """
                    package test.pkg;
                    @SuppressWarnings("JavaDoc")
                    public class Child extends Parent {
                        /**
                        * @deprecated
                        * @hide
                        */
                        @Deprecated @Override
                        public String toString() {
                            return "Child";
                        }

                        /**
                         * @hide
                         */
                        public void hiddenApi() {
                        }
                    }
                    """
                ),
                java(
                    """
                    package test.pkg;
                    public class Parent {
                        public String toString() {
                            return "Parent";
                        }
                    }
                    """
                )
            ),
            api = """
                    package test.pkg {
                      public class Child extends test.pkg.Parent {
                        ctor public Child();
                        method public deprecated java.lang.String toString();
                      }
                      public class Parent {
                        ctor public Parent();
                      }
                    }
                    """,
            dexApi = """
                Ltest/pkg/Child;
                Ltest/pkg/Child;-><init>()V
                Ltest/pkg/Child;->toString()Ljava/lang/String;
                Ltest/pkg/Parent;
                Ltest/pkg/Parent;-><init>()V
                Ltest/pkg/Parent;->toString()Ljava/lang/String;
            """,
            dexApiMapping = """
                Ltest/pkg/Child;-><init>()V
                src/test/pkg/Child.java:2
                Ltest/pkg/Child;->hiddenApi()V
                src/test/pkg/Child.java:16
                Ltest/pkg/Child;->toString()Ljava/lang/String;
                src/test/pkg/Child.java:8
                Ltest/pkg/Parent;-><init>()V
                src/test/pkg/Parent.java:2
                Ltest/pkg/Parent;->toString()Ljava/lang/String;
                src/test/pkg/Parent.java:3
            """
        )
    }

    @Test
    fun `Test invalid class name`() {
        // Regression test for b/73018978
        check(
            checkDoclava1 = false,
            sourceFiles = *arrayOf(
                kotlin(
                    "src/test/pkg/Foo.kt",
                    """
                    @file:JvmName("-Foo")

                    package test.pkg

                    @Suppress("unused")
                    inline fun String.printHelloWorld() { println("Hello World") }
                    """
                )
            ),
            api = """
                package test.pkg {
                  public final class -Foo {
                    method public static inline void printHelloWorld(java.lang.String);
                  }
                }
                """
        )
    }

    @Test
    fun `Indirect Field Includes from Interfaces`() {
        // Real-world example: include ZipConstants into ZipFile and JarFile
        check(
            checkDoclava1 = true,
            sourceFiles = *arrayOf(
                java(
                    """
                    package test.pkg1;
                    interface MyConstants {
                        long CONSTANT1 = 12345;
                        long CONSTANT2 = 67890;
                        long CONSTANT3 = 42;
                    }
                    """
                ),
                java(
                    """
                    package test.pkg1;
                    import java.io.Closeable;
                    @SuppressWarnings("WeakerAccess")
                    public class MyParent implements MyConstants, Closeable {
                    }
                    """
                ),
                java(
                    """
                    package test.pkg2;

                    import test.pkg1.MyParent;
                    public class MyChild extends MyParent {
                    }
                    """
                )

            ),
            api = """
                    package test.pkg1 {
                      public class MyParent implements java.io.Closeable {
                        ctor public MyParent();
                        field public static final long CONSTANT1 = 12345L; // 0x3039L
                        field public static final long CONSTANT2 = 67890L; // 0x10932L
                        field public static final long CONSTANT3 = 42L; // 0x2aL
                      }
                    }
                    package test.pkg2 {
                      public class MyChild extends test.pkg1.MyParent {
                        ctor public MyChild();
                        field public static final long CONSTANT1 = 12345L; // 0x3039L
                        field public static final long CONSTANT2 = 67890L; // 0x10932L
                        field public static final long CONSTANT3 = 42L; // 0x2aL
                      }
                    }
                """
        )
    }

    @Test
    fun `Skip interfaces from packages explicitly hidden via arguments`() {
        // Real-world example: HttpResponseCache implements OkCacheContainer but hides the only inherited method
        check(
            checkDoclava1 = true,
            extraArguments = arrayOf(
                ARG_HIDE_PACKAGE, "com.squareup.okhttp"
            ),
            sourceFiles = *arrayOf(
                java(
                    """
                    package android.net.http;
                    import com.squareup.okhttp.Cache;
                    import com.squareup.okhttp.OkCacheContainer;
                    import java.io.Closeable;
                    import java.net.ResponseCache;
                    @SuppressWarnings("JavaDoc")
                    public final class HttpResponseCache implements Closeable, OkCacheContainer {
                        /** @hide Needed for OkHttp integration. */
                        @Override
                        public Cache getCache() {
                            return delegate.getCache();
                        }
                    }
                    """
                ),
                java(
                    """
                    package com.squareup.okhttp;
                    public interface OkCacheContainer {
                      Cache getCache();
                    }
                    """
                )
            ),
            api = """
                package android.net.http {
                  public final class HttpResponseCache implements java.io.Closeable {
                    ctor public HttpResponseCache();
                  }
                }
                """
        )
    }

    @Test
    fun `Private API signatures`() {
        check(
            checkDoclava1 = false, // doclava1 doesn't have the same behavior: see
            // https://android-review.googlesource.com/c/platform/external/doclava/+/589515
            sourceFiles = *arrayOf(
                java(
                    """
                        package test.pkg;
                        public class Class1 implements MyInterface {
                            Class1(int arg) { }
                            /** @hide */
                            public void method1() { }
                            void method2() { }
                            private void method3() { }
                            public int field1 = 1;
                            protected int field2 = 2;
                            int field3 = 3;
                            float[][] field4 = 3;
                            long[] field5 = null;
                            private int field6 = 4;
                            void myVarargsMethod(int x, String... args) { }

                            public class Inner { // Fully public, should not be included
                                 public void publicMethod() { }
                            }
                        }
                    """
                ),

                java(
                    """
                        package test.pkg;
                        class Class2 {
                            public void method4() { }

                            private class Class3 {
                                public void method5() { }
                            }
                        }
                    """
                ),

                java(
                    """
                        package test.pkg;
                        /** @doconly */
                        class Class4 {
                            public void method5() { }
                        }
                    """
                ),

                java(
                    """
                        package test.pkg;
                        /** @hide */
                        @SuppressWarnings("UnnecessaryInterfaceModifier")
                        public interface MyInterface {
                            public static final String MY_CONSTANT = "5";
                        }
                    """
                )
            ),
            privateApi = """
                package test.pkg {
                  public class Class1 implements test.pkg.MyInterface {
                    ctor Class1(int);
                    method public void method1();
                    method void method2();
                    method private void method3();
                    method void myVarargsMethod(int, java.lang.String...);
                    field int field3;
                    field float[][] field4;
                    field long[] field5;
                    field private int field6;
                  }
                  class Class2 {
                    ctor Class2();
                    method public void method4();
                  }
                  private class Class2.Class3 {
                    ctor private Class2.Class3();
                    method public void method5();
                  }
                  class Class4 {
                    ctor Class4();
                    method public void method5();
                  }
                  public abstract interface MyInterface {
                    field public static final java.lang.String MY_CONSTANT = "5";
                  }
                }
                """,
            privateDexApi = """
                Ltest/pkg/Class1;-><init>(I)V
                Ltest/pkg/Class1;->method1()V
                Ltest/pkg/Class1;->method2()V
                Ltest/pkg/Class1;->method3()V
                Ltest/pkg/Class1;->myVarargsMethod(I[Ljava/lang/String;)V
                Ltest/pkg/Class1;->field3:I
                Ltest/pkg/Class1;->field4:[[F
                Ltest/pkg/Class1;->field5:[J
                Ltest/pkg/Class1;->field6:I
                Ltest/pkg/Class2;
                Ltest/pkg/Class2;-><init>()V
                Ltest/pkg/Class2;->method4()V
                Ltest/pkg/Class2${"$"}Class3;
                Ltest/pkg/Class2${"$"}Class3;-><init>()V
                Ltest/pkg/Class2${"$"}Class3;->method5()V
                Ltest/pkg/Class4;
                Ltest/pkg/Class4;-><init>()V
                Ltest/pkg/Class4;->method5()V
                Ltest/pkg/MyInterface;
                Ltest/pkg/MyInterface;->MY_CONSTANT:Ljava/lang/String;
                """
        )
    }

    @Test
    fun `Private API signature corner cases`() {
        // Some corner case scenarios exposed by differences in output from doclava and metalava
        check(
            checkDoclava1 = false,
            sourceFiles = *arrayOf(
                java(
                    """
                        package test.pkg;
                        import android.os.Parcel;
                        import android.os.Parcelable;
                        import java.util.concurrent.FutureTask;

                        public class Class1 extends PrivateParent implements MyInterface {
                            Class1(int arg) { }

                            @Override public String toString() {
                                return "Class1";
                            }

                            private abstract class AmsTask extends FutureTask<String> {
                                @Override
                                protected void set(String bundle) {
                                    super.set(bundle);
                                }
                            }

                            /** @hide */
                            public abstract static class TouchPoint implements Parcelable {
                            }
                        }
                    """
                ),

                java(
                    """
                        package test.pkg;
                        class PrivateParent {
                            final String getValue() {
                                return "";
                            }
                        }
                    """
                ),

                java(
                    """
                        package test.pkg;
                        /** @hide */
                        public enum MyEnum {
                            FOO, BAR
                        }
                    """
                ),

                java(
                    """
                        package test.pkg;
                        @SuppressWarnings("UnnecessaryInterfaceModifier")
                        public interface MyInterface {
                            public static final String MY_CONSTANT = "5";
                        }
                    """
                )
            ),
            privateApi = """
                package test.pkg {
                  public class Class1 extends test.pkg.PrivateParent implements test.pkg.MyInterface {
                    ctor Class1(int);
                  }
                  private abstract class Class1.AmsTask extends java.util.concurrent.FutureTask {
                  }
                  public static abstract class Class1.TouchPoint implements android.os.Parcelable {
                    ctor public Class1.TouchPoint();
                  }
                  public final class MyEnum extends java.lang.Enum {
                    ctor private MyEnum();
                    enum_constant public static final test.pkg.MyEnum BAR;
                    enum_constant public static final test.pkg.MyEnum FOO;
                  }
                  class PrivateParent {
                    ctor PrivateParent();
                    method final java.lang.String getValue();
                  }
                }
                """,
            privateDexApi = """
                Ltest/pkg/Class1;-><init>(I)V
                Ltest/pkg/Class1${"$"}AmsTask;
                Ltest/pkg/Class1${"$"}TouchPoint;
                Ltest/pkg/Class1${"$"}TouchPoint;-><init>()V
                Ltest/pkg/MyEnum;
                Ltest/pkg/MyEnum;-><init>()V
                Ltest/pkg/MyEnum;->valueOf(Ljava/lang/String;)Ltest/pkg/MyEnum;
                Ltest/pkg/MyEnum;->values()[Ltest/pkg/MyEnum;
                Ltest/pkg/MyEnum;->BAR:Ltest/pkg/MyEnum;
                Ltest/pkg/MyEnum;->FOO:Ltest/pkg/MyEnum;
                Ltest/pkg/PrivateParent;
                Ltest/pkg/PrivateParent;-><init>()V
                Ltest/pkg/PrivateParent;->getValue()Ljava/lang/String;
                """
        )
    }

    @Test
    fun `Extend from multiple interfaces`() {
        // Real-world example: XmlResourceParser
        check(
            checkDoclava1 = true,
            checkCompilation = true,
            sourceFiles = *arrayOf(
                java(
                    """
                    package android.content.res;
                    import android.util.AttributeSet;
                    import org.xmlpull.v1.XmlPullParser;
                    import my.AutoCloseable;

                    @SuppressWarnings("UnnecessaryInterfaceModifier")
                    public interface XmlResourceParser extends XmlPullParser, AttributeSet, AutoCloseable {
                        public void close();
                    }
                    """
                ),
                java(
                    """
                    package android.util;
                    @SuppressWarnings("WeakerAccess")
                    public interface AttributeSet {
                    }
                    """
                ),
                java(
                    """
                    package my;
                    public interface AutoCloseable {
                    }
                    """
                ),
                java(
                    """
                    package org.xmlpull.v1;
                    @SuppressWarnings("WeakerAccess")
                    public interface XmlPullParser {
                    }
                    """
                )
            ),
            api = """
                package android.content.res {
                  public abstract interface XmlResourceParser implements android.util.AttributeSet my.AutoCloseable org.xmlpull.v1.XmlPullParser {
                    method public abstract void close();
                  }
                }
                package android.util {
                  public abstract interface AttributeSet {
                  }
                }
                package my {
                  public abstract interface AutoCloseable {
                  }
                }
                package org.xmlpull.v1 {
                  public abstract interface XmlPullParser {
                  }
                }
                """
        )
    }

    @Test
    fun `Test KDoc suppress`() {
        // Basic class; also checks that default constructor is made explicit
        check(
            sourceFiles = *arrayOf(
                java(
                    """
                    package test.pkg;
                    public class Foo {
                        private Foo() { }
                        /** @suppress */
                        public void hidden() {
                        }
                    }
                    """
                ),
                java(
                    """
                    package test.pkg;
                    /**
                    * Some comment.
                    * @suppress
                    */
                    public class Hidden {
                        private Hidden() { }
                        public void hidden() {
                        }
                        public class Inner {
                        }
                    }
                    """
                )
            ),
            api = """
                    package test.pkg {
                      public class Foo {
                      }
                    }
                """,
            checkDoclava1 = false // doclava is unaware of @suppress
        )
    }

    @Test
    fun `Check skipping implicit final or deprecated override`() {
        // Regression test for 122358225
        check(
            compatibilityMode = false,
            sourceFiles = *arrayOf(
                java(
                    """
                    package test.pkg;

                    public class Parent {
                        public void foo1() { }
                        public void foo2() { }
                        public void foo3() { }
                        public void foo4() { }
                    }
                    """
                ),
                java(
                    """
                    package test.pkg;

                    public final class Child1 extends Parent {
                        private Child1() { }
                        public final void foo1() { }
                        public void foo2() { }
                    }
                    """
                ),
                java(
                    """
                    package test.pkg;

                    /** @deprecated */
                    @Deprecated
                    public final class Child2 extends Parent {
                        private Child2() { }
                        /** @deprecated */
                        @Deprecated
                        public void foo3() { }
                        public void foo4() { }
                    }
                    """
                ),
                java(
                    """
                    package test.pkg;

                    /** @deprecated */
                    @Deprecated
                    public final class Child3 extends Parent {
                        private Child3() { }
                        public final void foo1() { }
                        public void foo2() { }
                        /** @deprecated */
                        @Deprecated
                        public void foo3() { }
                        /** @deprecated */
                        @Deprecated
                        public final void foo4() { }
                    }
                    """
                )
            ),
            api = """
                package test.pkg {
                  public final class Child1 extends test.pkg.Parent {
                  }
                  @Deprecated public final class Child2 extends test.pkg.Parent {
                  }
                  @Deprecated public final class Child3 extends test.pkg.Parent {
                  }
                  public class Parent {
                    ctor public Parent();
                    method public void foo1();
                    method public void foo2();
                    method public void foo3();
                    method public void foo4();
                  }
                }
                """
        )
    }

    @Test
    fun `Ignore synchronized differences`() {
        check(
            compatibilityMode = false,
            sourceFiles = *arrayOf(
                java(
                    """
                    package test.pkg2;

                    public class Parent {
                        public void foo1() { }
                        public synchronized void foo2() { }
                    }
                    """
                ),
                java(
                    """
                    package test.pkg2;

                    public class Child1 extends Parent {
                        private Child1() { }
                        public synchronized void foo1() { }
                        public void foo2() { }
                    }
                    """
                )
            ),
            api = """
                package test.pkg2 {
                  public class Child1 extends test.pkg2.Parent {
                  }
                  public class Parent {
                    ctor public Parent();
                    method public void foo1();
                    method public void foo2();
                  }
                }
                """
        )
    }

    @Test
    fun `Skip incorrect inherit`() {
        check(
            // Simulate test-mock scenario for getIContentProvider
            extraArguments = arrayOf("--stub-packages", "android.test.mock"),
            compatibilityMode = false,
            warnings = "src/android/test/mock/MockContentProvider.java:6: warning: Public class android.test.mock.MockContentProvider stripped of unavailable superclass android.content.ContentProvider [HiddenSuperclass]",
            sourceFiles = *arrayOf(
                java(
                    """
                    package android.test.mock;

                    import android.content.ContentProvider;
                    import android.content.IContentProvider;

                    public abstract class MockContentProvider extends ContentProvider {
                        /**
                         * Returns IContentProvider which calls back same methods in this class.
                         * By overriding this class, we avoid the mechanism hidden behind ContentProvider
                         * (IPC, etc.)
                         *
                         * @hide
                         */
                        @Override
                        public final IContentProvider getIContentProvider() {
                            return mIContentProvider;
                        }
                    }
                    """
                ),
                java(
                    """
                    package android.content;

                    /** @hide */
                    public abstract class ContentProvider {
                        protected boolean isTemporary() {
                            return false;
                        }

                        // This is supposed to be @hide, but in turbine-combined/framework.jar included
                        // by java_sdk_library like test-mock, it's not; this is what the special
                        // flag is used to test
                        public IContentProvider getIContentProvider() {
                            return null;
                        }
                    }
                    """
                ),
                java(
                    """
                    package android.content;
                    import android.os.IInterface;

                    /**
                     * The ipc interface to talk to a content provider.
                     * @hide
                     */
                    public interface IContentProvider extends IInterface {
                    }
                    """
                ),
                java(
                    """
                    package android.content;

                    // Not hidden. Here to make sure that we respect stub-packages
                    // and exclude it from everything, including signatures.
                    public class ClipData {
                    }
                    """
                )
            ),
            api = """
                package android.test.mock {
                  public abstract class MockContentProvider {
                    ctor public MockContentProvider();
                  }
                }
                """
        )
    }

    @Test
    fun `Test Visible For Testing`() {
        // Use the otherwise= visibility in signatures
        // Regression test for issue 118763806
        check(
            sourceFiles = *arrayOf(
                java(
                    """
                    package test.pkg;
                    import androidx.annotation.VisibleForTesting;

                    @SuppressWarnings({"ClassNameDiffersFromFileName", "WeakerAccess"})
                    public class ProductionCodeJava {
                        private ProductionCodeJava() { }

                        @VisibleForTesting(otherwise = VisibleForTesting.PROTECTED)
                        public void shouldBeProtected() {
                        }

                        @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
                        protected void shouldBePrivate1() {
                        }

                        @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
                        public void shouldBePrivate2() {
                        }

                        @VisibleForTesting(otherwise = VisibleForTesting.PACKAGE_PRIVATE)
                        public void shouldBePackagePrivate() {
                        }

                        @VisibleForTesting(otherwise = VisibleForTesting.NONE)
                        public void shouldBeHidden() {
                        }
                    }
                    """
                ).indented(),
                kotlin(
                    """
                    package test.pkg
                    import androidx.annotation.VisibleForTesting

                    open class ProductionCodeKotlin private constructor() {

                        @VisibleForTesting(otherwise = VisibleForTesting.PROTECTED)
                        fun shouldBeProtected() {
                        }

                        @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
                        protected fun shouldBePrivate1() {
                        }

                        @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
                        fun shouldBePrivate2() {
                        }

                        @VisibleForTesting(otherwise = VisibleForTesting.PACKAGE_PRIVATE)
                        fun shouldBePackagePrivate() {
                        }

                        @VisibleForTesting(otherwise = VisibleForTesting.NONE)
                        fun shouldBeHidden() {
                        }
                    }
                    """
                ).indented(),
                visibleForTestingSource
            ),
            api = """
                package test.pkg {
                  public class ProductionCodeJava {
                    method protected void shouldBeProtected();
                  }
                  public class ProductionCodeKotlin {
                    method protected final void shouldBeProtected();
                  }
                }
                """,
            extraArguments = arrayOf(ARG_HIDE_PACKAGE, "androidx.annotation")
        )
    }

    @Test
    fun `References Deprecated`() {
        check(
            extraArguments = arrayOf(
                ARG_ERROR, "ReferencesDeprecated",
                ARG_ERROR, "ExtendsDeprecated"
            ),
            warnings = """
            src/test/pkg/MyClass.java:3: error: Parameter of deprecated type test.pkg.DeprecatedClass in test.pkg.MyClass.method1(): this method should also be deprecated [ReferencesDeprecated]
            src/test/pkg/MyClass.java:4: error: Return type of deprecated type test.pkg.DeprecatedInterface in test.pkg.MyClass.method2(): this method should also be deprecated [ReferencesDeprecated]
            src/test/pkg/MyClass.java:4: error: Returning deprecated type test.pkg.DeprecatedInterface from test.pkg.MyClass.method2(): this method should also be deprecated [ReferencesDeprecated]
            src/test/pkg/MyClass.java:2: error: Extending deprecated super class class test.pkg.DeprecatedClass from test.pkg.MyClass: this class should also be deprecated [ExtendsDeprecated]
            src/test/pkg/MyClass.java:2: error: Implementing interface of deprecated type test.pkg.DeprecatedInterface in test.pkg.MyClass: this class should also be deprecated [ExtendsDeprecated]
            """,
            sourceFiles = *arrayOf(
                java(
                    """
                    package test.pkg;
                    /** @deprecated */
                    @Deprecated
                    public class DeprecatedClass {
                    }
                    """
                ),
                java(
                    """
                    package test.pkg;
                    /** @deprecated */
                    @Deprecated
                    public interface DeprecatedInterface {
                    }
                    """
                ),
                java(
                    """
                    package test.pkg;
                    public class MyClass extends DeprecatedClass implements DeprecatedInterface {
                        public void method1(DeprecatedClass p, int i) { }
                        public DeprecatedInterface method2(int i) { return null; }

                        /** @deprecated */
                        @Deprecated
                        public void method3(DeprecatedClass p, int i) { }
                    }
                    """
                )
            )
        )
    }
}
