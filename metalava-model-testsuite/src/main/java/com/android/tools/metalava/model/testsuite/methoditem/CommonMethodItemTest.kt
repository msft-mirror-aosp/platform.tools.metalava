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

package com.android.tools.metalava.model.testsuite.methoditem

import com.android.tools.metalava.model.JAVA_LANG_THROWABLE
import com.android.tools.metalava.model.MethodItem
import com.android.tools.metalava.model.testsuite.BaseModelTest
import com.android.tools.metalava.testing.java
import com.android.tools.metalava.testing.kotlin
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/** Common tests for implementations of [MethodItem]. */
@RunWith(Parameterized::class)
class CommonMethodItemTest : BaseModelTest() {

    @Test
    fun `Test access type parameter of outer class`() {
        runCodebaseTest(
            signature(
                """
                    // Signature format: 2.0
                    package test.pkg {
                      public class Outer<O> {
                      }
                      public class Outer.Middle {
                      }
                      public abstract class Outer.Middle.Inner {
                        method public abstract O method();
                      }
                    }
                """
            ),
            java(
                """
                    package test.pkg;

                    public class Outer<O> {
                        private Outer() {}

                        public class Middle {
                            private Middle() {}
                            public class Inner {
                                private Inner() {}
                                public abstract O method();
                            }
                        }
                    }
                """
            ),
            kotlin(
                """
                    package test.pkg

                    class Outer<O> private constructor() {
                        inner class Middle private constructor() {
                            abstract inner class Inner private constructor() {
                                abstract fun method(): O
                            }
                        }
                    }
                """
            ),
        ) {
            val oTypeParameter =
                codebase.assertClass("test.pkg.Outer").typeParameterList().typeParameters().single()
            val methodType =
                codebase
                    .assertClass("test.pkg.Outer.Middle.Inner")
                    .assertMethod("method", "")
                    .type()

            methodType.assertReferencesTypeParameter(oTypeParameter)
        }
    }

    @Test
    fun `MethodItem type`() {
        runCodebaseTest(
            signature(
                """
                    // Signature format: 2.0
                    package test.pkg {
                      public class Test {
                        ctor public Test();
                        method public abstract boolean foo(test.pkg.Test, int...);
                        method public abstract void bar(test.pkg.Test... tests);
                      }
                    }
                """
            ),
            java(
                """
                    package test.pkg;

                    public abstract class Test {
                        public Test() {}

                        public abstract boolean foo(Test test, int... ints);
                        public abstract void bar(Test... tests);
                    }
                """
            ),
        ) {
            val testClass = codebase.assertClass("test.pkg.Test")

            val actual = buildString {
                testClass.methods().forEach {
                    append(it.returnType())
                    append(" ")
                    append(it.name())
                    append("(")
                    it.parameters().forEachIndexed { i, p ->
                        if (i > 0) {
                            append(", ")
                        }
                        append(p.type())
                    }
                    append(")\n")
                }
            }

            assertEquals(
                """
                    boolean foo(test.pkg.Test, int...)
                    void bar(test.pkg.Test...)
                """
                    .trimIndent(),
                actual.trim()
            )
        }
    }

    @Test
    fun `MethodItem superMethods() on constructor`() {
        runCodebaseTest(
            inputSet(
                signature(
                    """
                        // Signature format: 2.0
                        package test.pkg {
                          public class Base {
                            ctor public Base();
                          }
                          public class Test extends test.pkg.Base {
                            ctor public Test();
                          }
                        }
                    """
                ),
            ),
            inputSet(
                java(
                    """
                        package test.pkg;

                        public class Base {
                            public Base() {}
                        }
                    """
                ),
                java(
                    """
                        package test.pkg;

                        public class Test extends Base {
                            public Test() {}
                        }
                    """
                ),
            ),
        ) {
            val testClass = codebase.assertClass("test.pkg.Test")
            val testConstructor = testClass.constructors().single()
            assertEquals(emptyList(), testConstructor.superMethods())
        }
    }

    @Test
    fun `MethodItem superMethods() on simple method`() {
        runCodebaseTest(
            inputSet(
                signature(
                    """
                        // Signature format: 2.0
                        package test.pkg {
                          public class Base {
                            ctor public Base();
                            method public void foo();
                          }
                          public class Test extends test.pkg.Base {
                            ctor public Test();
                            method public void foo();
                          }
                        }
                    """
                ),
            ),
            inputSet(
                java(
                    """
                        package test.pkg;

                        public class Base {
                            public Base() {}
                            public void foo() {}
                        }
                    """
                ),
                java(
                    """
                        package test.pkg;

                        public class Test extends Base {
                            public Test() {}
                            public void foo() {}
                        }
                    """
                ),
            ),
        ) {
            val baseClass = codebase.assertClass("test.pkg.Base")
            val testClass = codebase.assertClass("test.pkg.Test")

            val baseFoo = baseClass.methods().single()
            val testFoo = testClass.methods().single()

            assertEquals(listOf(baseFoo), testFoo.superMethods())
        }
    }

    @Test
    fun `Test equality of methods with type parameters`() {
        runCodebaseTest(
            java(
                """
                    package test.pkg;
                    public class Foo {
                        public <T extends Number> void foo(T t) {}
                        public <T extends String> void foo(T t) {}
                    }
                """
            ),
            signature(
                """
                    // Signature format: 2.0
                    package test.pkg {
                      public class Foo {
                        method public <T extends java.lang.Number> void foo(T);
                        method public <T extends java.lang.String> void foo(T);
                      }
                    }
                """
            )
        ) {
            val methods = codebase.assertClass("test.pkg.Foo").methods()
            assertEquals(methods.size, 2)

            val numBounds = methods[0]
            val strBounds = methods[1]
            // These methods look the same besides their type parameter bounds
            assertNotEquals(numBounds, strBounds)
        }
    }

    @Test
    fun `Test throws method type parameter extends Throwable`() {
        runCodebaseTest(
            java(
                """
                    package test.pkg;

                    @SuppressWarnings("ALL")
                    public final class Test {
                        private Test() {}
                        public <X extends Throwable> void throwsTypeParameter() throws X {
                            return null;
                        }
                    }
                """
            ),
            signature(
                """
                    // Signature format: 2.0
                    package test.pkg {
                      public final class Test {
                        method public <X extends Throwable> void throwsTypeParameter() throws X;
                      }
                    }
                """
            ),
        ) {
            val methodItem = codebase.assertClass("test.pkg.Test").methods().single()
            val typeParameterItem = methodItem.typeParameterList().typeParameters().single()
            val throwsType = methodItem.throwsTypes().single()
            assertEquals(typeParameterItem, throwsType.typeParameterItem)
            assertEquals(throwsType.throwableClass?.qualifiedName(), JAVA_LANG_THROWABLE)
        }
    }

    @Test
    fun `Test throws method type parameter does not extend Throwable`() {
        // This is an error but Metalava should try not to fail on an error.
        runCodebaseTest(
            java(
                """
                    package test.pkg;

                    @SuppressWarnings("ALL")
                    public final class Test {
                        private Test() {}
                        public <X> void throwsTypeParameter() throws X {
                            return null;
                        }
                    }
                """
            ),
            signature(
                """
                    // Signature format: 2.0
                    package test.pkg {
                      public final class Test {
                        method public <X> void throwsTypeParameter() throws X;
                      }
                    }
                """
            ),
        ) {
            val methodItem = codebase.assertClass("test.pkg.Test").methods().single()
            val typeParameterItem = methodItem.typeParameterList().typeParameters().single()
            val throwsType = methodItem.throwsTypes().single()
            assertEquals(typeParameterItem, throwsType.typeParameterItem)
            // The type parameter does not extend a throwable type.
            assertNull(throwsType.throwableClass)
        }
    }

    @Test
    fun `Test throws method class does not exist`() {
        // This is an error but Metalava should try not to fail on an error.
        runCodebaseTest(
            java(
                """
                    package test.pkg;

                    @SuppressWarnings("ALL")
                    public final class Test {
                        private Test() {}
                        public void throwsUnknownException() throws UnknownException {
                            return null;
                        }
                    }
                """
            ),
            // No signature test as that will just fabricate an UnknownException.
        ) {
            val methodItem = codebase.assertClass("test.pkg.Test").methods().single()
            val throwsType = methodItem.throwsTypes().single()
            // Neither the class nor throwable class is available.
            assertNull(throwsType.classItem)
            assertNull(throwsType.throwableClass)
        }
    }
}
