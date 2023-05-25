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

// Base class to collect test inputs whose behaviors (API/lint) vary depending on UAST versions.
abstract class UastTestBase : DriverTest() {

    protected fun `Kotlin language level`(isK2: Boolean) {
        // static method in interface is not overridable.
        // TODO: SLC in 1.9 will not put `final` on @JvmStatic method in interface
        //  Put this back to Java9LanguageFeaturesTest, before `Basic class signature extraction`
        val f = if (isK2) " final" else ""
        // See https://kotlinlang.org/docs/reference/whatsnew13.html
        check(
            format = FileFormat.V1,
            sourceFiles = arrayOf(
                kotlin(
                    """
                    package test.pkg
                    interface Foo {
                        companion object {
                            @JvmField
                            const val answer: Int = 42
                            @JvmStatic
                            fun sayHello() {
                                println("Hello, world!")
                            }
                        }
                    }
                    """
                )
            ),
            api =
            """
                package test.pkg {
                  public interface Foo {
                    method public default static$f void sayHello();
                    field @NonNull public static final test.pkg.Foo.Companion Companion;
                    field public static final int answer = 42; // 0x2a
                  }
                  public static final class Foo.Companion {
                    method public void sayHello();
                  }
                }
                """,
            // The above source uses 1.3 features, though UAST currently
            // seems to still treat it as 1.3 despite being passed 1.2
            extraArguments = arrayOf(ARG_KOTLIN_SOURCE, "1.2")
        )
    }

    protected fun `Test RequiresOptIn and OptIn`(isK2: Boolean) {
        // See b/248341155 for more details
        val klass = if (isK2) "Class" else "kotlin.reflect.KClass"
        check(
            sourceFiles = arrayOf(
                kotlin(
                    """
                    package test.pkg

                    @RequiresOptIn
                    @Retention(AnnotationRetention.BINARY)
                    @Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION)
                    annotation class ExperimentalBar

                    @ExperimentalBar
                    class FancyBar

                    @OptIn(FancyBar::class) // @OptIn should not be tracked as it is not API
                    class SimpleClass {
                        fun methodUsingFancyBar() {
                            val fancyBar = FancyBar()
                        }
                    }

                    @androidx.annotation.experimental.UseExperimental(FancyBar::class) // @UseExperimental should not be tracked as it is not API
                    class AnotherSimpleClass {
                        fun methodUsingFancyBar() {
                            val fancyBar = FancyBar()
                        }
                    }
                """
                ),
                kotlin(
                    """
                    package androidx.annotation.experimental

                    import kotlin.annotation.Retention
                    import kotlin.annotation.Target
                    import kotlin.reflect.KClass

                    @Retention(AnnotationRetention.BINARY)
                    @Target(
                        AnnotationTarget.CLASS,
                        AnnotationTarget.PROPERTY,
                        AnnotationTarget.LOCAL_VARIABLE,
                        AnnotationTarget.VALUE_PARAMETER,
                        AnnotationTarget.CONSTRUCTOR,
                        AnnotationTarget.FUNCTION,
                        AnnotationTarget.PROPERTY_GETTER,
                        AnnotationTarget.PROPERTY_SETTER,
                        AnnotationTarget.FILE,
                        AnnotationTarget.TYPEALIAS
                    )
                    annotation class UseExperimental(
                        /**
                         * Defines the experimental API(s) whose usage this annotation allows.
                         */
                        vararg val markerClass: KClass<out Annotation>
                    )
                """
                )
            ),
            format = FileFormat.V3,
            api = """
                // Signature format: 3.0
                package androidx.annotation.experimental {
                  @kotlin.annotation.Retention(kotlin.annotation.AnnotationRetention.BINARY) @kotlin.annotation.Target(allowedTargets={kotlin.annotation.AnnotationTarget.CLASS, kotlin.annotation.AnnotationTarget.PROPERTY, kotlin.annotation.AnnotationTarget.LOCAL_VARIABLE, kotlin.annotation.AnnotationTarget.VALUE_PARAMETER, kotlin.annotation.AnnotationTarget.CONSTRUCTOR, kotlin.annotation.AnnotationTarget.FUNCTION, kotlin.annotation.AnnotationTarget.PROPERTY_GETTER, kotlin.annotation.AnnotationTarget.PROPERTY_SETTER, kotlin.annotation.AnnotationTarget.FILE, kotlin.annotation.AnnotationTarget.TYPEALIAS}) public @interface UseExperimental {
                    method public abstract $klass<? extends java.lang.annotation.Annotation>[] markerClass();
                    property public abstract $klass<? extends java.lang.annotation.Annotation>[] markerClass;
                  }
                }
                package test.pkg {
                  public final class AnotherSimpleClass {
                    ctor public AnotherSimpleClass();
                    method public void methodUsingFancyBar();
                  }
                  @kotlin.RequiresOptIn @kotlin.annotation.Retention(kotlin.annotation.AnnotationRetention.BINARY) @kotlin.annotation.Target(allowedTargets={kotlin.annotation.AnnotationTarget.CLASS, kotlin.annotation.AnnotationTarget.FUNCTION}) public @interface ExperimentalBar {
                  }
                  @test.pkg.ExperimentalBar public final class FancyBar {
                    ctor public FancyBar();
                  }
                  public final class SimpleClass {
                    ctor public SimpleClass();
                    method public void methodUsingFancyBar();
                  }
                }
            """
        )
    }

    protected fun `renamed via @JvmName`(api: String) {
        // Regression test from b/257444932: @get:JvmName on constructor property
        check(
            sourceFiles = arrayOf(
                kotlin(
                    """
                        package test.pkg

                        class ColorRamp(
                            val colors: IntArray,
                            @get:JvmName("isInterpolated")
                            val interpolated: Boolean,
                        ) {
                            @get:JvmName("isInitiallyEnabled")
                            val initiallyEnabled: Boolean

                            @set:JvmName("updateOtherColors")
                            var otherColors: IntArray
                        }
                    """
                )
            ),
            api = api
        )
    }

    protected fun `Kotlin Reified Methods`(isK2: Boolean) {
        // TODO: once fix for KT-39209 is available (231),
        //  FE1.0 UAST will have implicit nullability too.
        //  Put this back to ApiFileTest, before `Kotlin Reified Methods 2`
        val n = if (isK2) " @Nullable" else ""
        check(
            format = FileFormat.V1,
            sourceFiles = arrayOf(
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
                    method public final <T> T getSystemService(Class<T>);
                  }
                  public final class TestKt {
                    method$n public static inline <reified T> T systemService1(@NonNull test.pkg.Context);
                    method public static inline String systemService2(@NonNull test.pkg.Context);
                  }
                }
                """
        )
    }

    protected fun `Nullness in reified signatures`(isK2: Boolean) {
        // TODO: once fix for KT-39209 is available (231),
        //  FE1.0 UAST will have implicit nullability too.
        //  Put this back to ApiFileTest, before `Nullness in varargs`
        val n = if (isK2) "" else "!"
        check(
            sourceFiles = arrayOf(
                kotlin(
                    "src/test/pkg/test.kt",
                    """
                    package test.pkg

                    import androidx.annotation.UiThread
                    import test.pkg2.NavArgs
                    import test.pkg2.NavArgsLazy
                    import test.pkg2.Fragment
                    import test.pkg2.Bundle

                    @UiThread
                    inline fun <reified Args : NavArgs> Fragment.navArgs() = NavArgsLazy(Args::class) {
                        throw IllegalStateException("Fragment $this has null arguments")
                    }
                    """
                ),
                kotlin(
                    """
                    package test.pkg2

                    import kotlin.reflect.KClass

                    interface NavArgs
                    class Fragment
                    class Bundle
                    class NavArgsLazy<Args : NavArgs>(
                        private val navArgsClass: KClass<Args>,
                        private val argumentProducer: () -> Bundle
                    )
                    """
                ),
                uiThreadSource
            ),
            api = """
                // Signature format: 3.0
                package test.pkg {
                  public final class TestKt {
                    method @UiThread public static inline <reified Args extends test.pkg2.NavArgs> test.pkg2.NavArgsLazy<Args>$n navArgs(test.pkg2.Fragment);
                  }
                }
                """,
//            Actual expected API is below. However, due to KT-39209 the nullability information is
//              missing
//            api = """
//                // Signature format: 3.0
//                package test.pkg {
//                  public final class TestKt {
//                    method @UiThread public static inline <reified Args extends test.pkg2.NavArgs> test.pkg2.NavArgsLazy<Args> navArgs(test.pkg2.Fragment);
//                  }
//                }
//                """,
            format = FileFormat.V3,
            extraArguments = arrayOf(
                ARG_HIDE_PACKAGE, "androidx.annotation",
                ARG_HIDE_PACKAGE, "test.pkg2",
                ARG_HIDE, "ReferencesHidden",
                ARG_HIDE, "UnavailableSymbol",
                ARG_HIDE, "HiddenTypeParameter",
                ARG_HIDE, "HiddenSuperclass"
            )
        )
    }

    protected fun `Annotations aren't dropped when DeprecationLevel is HIDDEN`(isK2: Boolean) {
        // Regression test for b/219792969
        // TODO: once fix for KTIJ-23807 is available (231), FE1.0 UAST will emit that too.
        //  Put this back to ApiFileTest, before `Constants in a file scope annotation`
        val n = if (isK2) " @NonNull" else ""
        check(
            format = FileFormat.V2,
            sourceFiles = arrayOf(
                kotlin(
                    """
                        package test.pkg
                        import androidx.annotation.IntRange
                        @Deprecated(
                            message = "So much regret",
                            level = DeprecationLevel.HIDDEN
                        )
                        @IntRange(from=0)
                        fun myMethod() { TODO() }

                        @Deprecated(
                            message = "Not supported anymore",
                            level = DeprecationLevel.HIDDEN
                        )
                        fun returnsNonNull(): String = "42"

                        @Deprecated(
                            message = "Not supported anymore",
                            level = DeprecationLevel.HIDDEN
                        )
                        fun returnsNonNullImplicitly() = "42"
                    """
                ),
                androidxIntRangeSource
            ),
            extraArguments = arrayOf(ARG_HIDE_PACKAGE, "androidx.annotation"),
            api = """
                // Signature format: 2.0
                package test.pkg {
                  public final class TestKt {
                    method @Deprecated @IntRange(from=0L) public static void myMethod();
                    method @Deprecated @NonNull public static String returnsNonNull();
                    method @Deprecated$n public static String returnsNonNullImplicitly();
                  }
                }
            """
        )
    }
}
