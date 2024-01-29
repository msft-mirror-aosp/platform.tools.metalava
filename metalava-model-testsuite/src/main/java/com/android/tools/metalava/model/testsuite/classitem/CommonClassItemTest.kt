/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.tools.metalava.model.testsuite.classitem

import com.android.tools.metalava.model.ClassItem
import com.android.tools.metalava.model.ClassTypeItem
import com.android.tools.metalava.model.VariableTypeItem
import com.android.tools.metalava.model.testsuite.BaseModelTest
import com.android.tools.metalava.testing.java
import com.android.tools.metalava.testing.kotlin
import com.google.common.truth.Truth.assertThat
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/** Common tests for implementations of [ClassItem]. */
@RunWith(Parameterized::class)
class CommonClassItemTest : BaseModelTest() {

    @Test
    fun `empty class`() {
        runCodebaseTest(
            signature(
                """
                    // Signature format: 2.0
                    package test.pkg {
                      public class Test {
                        ctor public Test();
                      }
                    }
                """
            ),
            java(
                """
                    package test.pkg;

                    public class Test {
                        public Test() {}
                    }
                """
            ),
        ) {
            val testClass = codebase.assertClass("test.pkg.Test")
            assertEquals("Test", testClass.fullName())
            assertEquals("test/pkg/Test", testClass.internalName())
            assertEquals("test.pkg.Test", testClass.qualifiedName())
            assertEquals("test.pkg.Test", testClass.qualifiedNameWithDollarInnerClasses())
            assertEquals(1, testClass.constructors().size)
            assertEquals(emptyList(), testClass.methods())
            assertEquals(emptyList(), testClass.fields())
            assertEquals(emptyList(), testClass.properties())
        }
    }

    @Test
    fun `Find method with type parameterized by two types`() {
        runCodebaseTest(
            signature(
                """
                    // Signature format: 2.0
                    package test.pkg {
                      public class Foo {
                        method public void foo(java.util.Map<String, Integer>);
                      }
                    }
                """
            ),
            java(
                """
                    package test.pkg;

                    public class Foo {
                        public void foo(java.util.Map<String, Integer> map) {}
                    }
                """
            ),
        ) {
            val fooClass = codebase.assertClass("test.pkg.Foo")
            val fooMethod = fooClass.methods().single()

            // This should not find the method as `findMethod` splits parameters by `,` so it looks
            // for one parameter of type `java.util.Map<String` and one of type `Integer>`.
            val foundMethod = fooClass.findMethod("foo", "java.util.Map<String, Integer>")
            assertNull(
                foundMethod,
                message = "unexpectedly found method with multiple type parameters"
            )

            // This should find the method.
            assertSame(fooMethod, fooClass.findMethod("foo", "java.util.Map"))
        }
    }

    @Test
    fun `Test interface no extends list`() {
        runCodebaseTest(
            signature(
                """
                    // Signature format: 2.0
                    package test.pkg {
                      public interface Foo {
                      }
                    }
                """
            ),
            java(
                """
                    package test.pkg;

                    public interface Foo {}
                """
            ),
        ) {
            val fooInterface = codebase.assertClass("test.pkg.Foo")

            assertNull(fooInterface.superClassType())
            assertNull(fooInterface.superClass())

            val interfaceList = fooInterface.interfaceTypes().map { it.asClass() }
            assertEquals(emptyList(), interfaceList)

            val allInterfaces = fooInterface.allInterfaces().toList()
            assertEquals(listOf(fooInterface), allInterfaces)
        }
    }

    @Test
    fun `Test interface extends list`() {
        runCodebaseTest(
            signature(
                """
                    // Signature format: 2.0
                    package test.pkg {
                      public interface A {
                      }
                      public interface B {
                      }
                      public interface C {
                      }
                      public interface Foo extends test.pkg.A, test.pkg.B, test.pkg.C {
                      }
                    }
                """
            ),
            java(
                """
                    package test.pkg;

                    public interface A {}
                    public interface B {}
                    public interface C {}
                    public interface Foo extends A, B, C {}
                """
            ),
        ) {
            val interfaceA = codebase.assertClass("test.pkg.A")
            val interfaceB = codebase.assertClass("test.pkg.B")
            val interfaceC = codebase.assertClass("test.pkg.C")
            val fooInterface = codebase.assertClass("test.pkg.Foo")

            assertNull(fooInterface.superClassType()?.asClass())
            assertNull(fooInterface.superClass())

            val interfaceList = fooInterface.interfaceTypes().map { it.asClass() }
            assertEquals(listOf(interfaceA, interfaceB, interfaceC), interfaceList)

            val allInterfaces = fooInterface.allInterfaces().toList()
            assertEquals(listOf(fooInterface, interfaceA, interfaceB, interfaceC), allInterfaces)
        }
    }

    @Test
    fun `Test class no super class or implements lists`() {
        runCodebaseTest(
            signature(
                """
                    // Signature format: 2.0
                    package test.pkg {
                      public class Foo {
                      }
                    }
                """
            ),
            java(
                """
                    package test.pkg;

                    public class Foo {}
                """
            ),
        ) {
            val objectClass = codebase.assertClass("java.lang.Object")
            val fooClass = codebase.assertClass("test.pkg.Foo")

            assertSame(objectClass, fooClass.superClassType()?.asClass())
            assertSame(objectClass, fooClass.superClass())

            val interfaceList = fooClass.interfaceTypes().map { it.asClass() }
            assertEquals(emptyList(), interfaceList)

            val allInterfaces = fooClass.allInterfaces().toList()
            assertEquals(emptyList(), allInterfaces)
        }
    }

    @Test
    fun `Test class super class no implements lists`() {
        runCodebaseTest(
            signature(
                """
                    // Signature format: 2.0
                    package test.pkg {
                      public class Bar {
                      }
                      public class Foo extends test.pkg.Bar {
                      }
                    }
                """
            ),
            java(
                """
                    package test.pkg;

                    public class Bar {}
                    public class Foo extends Bar {}
                """
            ),
        ) {
            val barClass = codebase.assertClass("test.pkg.Bar")
            val fooClass = codebase.assertClass("test.pkg.Foo")

            assertSame(barClass, fooClass.superClassType()?.asClass())
            assertSame(barClass, fooClass.superClass())

            val interfaceList = fooClass.interfaceTypes().map { it.asClass() }
            assertEquals(emptyList(), interfaceList)

            val allInterfaces = fooClass.allInterfaces().toList()
            assertEquals(emptyList(), allInterfaces)
        }
    }

    @Test
    fun `Test class no super class but implements lists`() {
        runCodebaseTest(
            signature(
                """
                    // Signature format: 2.0
                    package test.pkg {
                      public interface A {
                      }
                      public interface B {
                      }
                      public interface C {
                      }
                      public class Foo implements test.pkg.A, test.pkg.B, test.pkg.C {
                      }
                    }
                """
            ),
            java(
                """
                    package test.pkg;

                    public interface A {}
                    public interface B {}
                    public interface C {}
                    public class Foo implements A, B, C {}
                """
            ),
        ) {
            val interfaceA = codebase.assertClass("test.pkg.A")
            val interfaceB = codebase.assertClass("test.pkg.B")
            val interfaceC = codebase.assertClass("test.pkg.C")
            val objectClass = codebase.assertClass("java.lang.Object")
            val fooClass = codebase.assertClass("test.pkg.Foo")

            assertSame(objectClass, fooClass.superClassType()?.asClass())
            assertSame(objectClass, fooClass.superClass())

            val interfaceList = fooClass.interfaceTypes().map { it.asClass() }
            assertEquals(listOf(interfaceA, interfaceB, interfaceC), interfaceList)

            val allInterfaces = fooClass.allInterfaces().toList()
            assertEquals(listOf(interfaceA, interfaceB, interfaceC), allInterfaces)
        }
    }

    @Test
    fun `Test class super class and implements lists`() {
        runCodebaseTest(
            signature(
                """
                    // Signature format: 2.0
                    package test.pkg {
                      public class Bar {
                      }
                      public interface A {
                      }
                      public interface B {
                      }
                      public interface C {
                      }
                      public class Foo extends test.pkg.Bar implements test.pkg.A, test.pkg.B, test.pkg.C {
                      }
                    }
                """
            ),
            java(
                """
                    package test.pkg;

                    public class Bar {}
                    public interface A {}
                    public interface B {}
                    public interface C {}
                    public class Foo extends Bar implements A, B, C {}
                """
            ),
        ) {
            val barClass = codebase.assertClass("test.pkg.Bar")
            val interfaceA = codebase.assertClass("test.pkg.A")
            val interfaceB = codebase.assertClass("test.pkg.B")
            val interfaceC = codebase.assertClass("test.pkg.C")
            val fooClass = codebase.assertClass("test.pkg.Foo")

            assertSame(barClass, fooClass.superClassType()?.asClass())
            assertSame(barClass, fooClass.superClass())

            val interfaceList = fooClass.interfaceTypes().map { it.asClass() }
            assertEquals(listOf(interfaceA, interfaceB, interfaceC), interfaceList)

            val allInterfaces = fooClass.allInterfaces().toList()
            assertEquals(listOf(interfaceA, interfaceB, interfaceC), allInterfaces)
        }
    }

    @Test
    fun `Test deprecated class by javadoc tag`() {
        runCodebaseTest(
            java(
                """
                    package test.pkg;

                    /**
                     * @deprecated
                     */
                    public class Bar {}
                """
            ),
        ) {
            val barClass = codebase.assertClass("test.pkg.Bar")
            assertEquals(true, barClass.deprecated)
            assertEquals(true, barClass.originallyDeprecated)
        }
    }

    @Test
    fun `Test deprecated class by annotation`() {
        runCodebaseTest(
            signature(
                """
                    // Signature format: 2.0
                    package test.pkg {
                      @Deprecated public class Bar {
                      }
                    }
                """
            ),
            java(
                """
                    package test.pkg;

                    @Deprecated
                    public class Bar {}
                """
            ),
            kotlin(
                """
                    package test.pkg

                    @Deprecated
                    class Bar {}
                """
            ),
        ) {
            val barClass = codebase.assertClass("test.pkg.Bar")
            assertEquals(true, barClass.deprecated)
            assertEquals(true, barClass.originallyDeprecated)
        }
    }

    @Test
    fun `Test not deprecated class`() {
        runCodebaseTest(
            signature(
                """
                    // Signature format: 2.0
                    package test.pkg {
                      public class Bar {
                      }
                    }
                """
            ),
            java(
                """
                    package test.pkg;

                    public class Bar {}
                """
            ),
            kotlin(
                """
                    package test.pkg

                    class Bar {}
                """
            ),
        ) {
            val barClass = codebase.assertClass("test.pkg.Bar")
            assertEquals(false, barClass.deprecated)
            assertEquals(false, barClass.originallyDeprecated)
        }
    }

    @Test
    fun `Test basic mapTypeVariables`() {
        runCodebaseTest(
            inputSet(
                java(
                    """
                        package test.pkg;
                        public class Parent<M, N> {}
                    """
                        .trimIndent()
                ),
                java(
                    """
                        package test.pkg;
                        public class Child<X, Y> extends Parent<X, Y> {}
                    """
                        .trimIndent()
                )
            ),
            inputSet(
                signature(
                    """
                        // Signature format: 5.0
                        package test.pkg {
                          public class Child<X, Y> extends test.pkg.Parent<X,Y> {
                          }
                          public class Parent<M, N> {
                          }
                        }
                    """
                        .trimIndent()
                )
            ),
            inputSet(
                kotlin(
                    """
                        package test.pkg
                        open class Parent<M, N>
                        class Child<X, Y> : Parent<X, Y>()
                    """
                        .trimIndent()
                )
            )
        ) {
            val parent = codebase.assertClass("test.pkg.Parent")
            val parentTypeParams = parent.typeParameterList().typeParameters()
            val m = parentTypeParams[0]
            val n = parentTypeParams[1]

            val child = codebase.assertClass("test.pkg.Child")
            val childTypeParams = child.typeParameterList().typeParameters()
            val x = childTypeParams[0].toType()
            val y = childTypeParams[1].toType()

            assertEquals(mapOf(m to x, n to y), child.mapTypeVariables(parent))

            // Not valid uses of mapTypeVariables
            assertEquals(emptyMap(), parent.mapTypeVariables(child))
            assertEquals(emptyMap(), child.mapTypeVariables(child))
        }
    }

    @Test
    fun `Test mapTypeVariables with multiple layers of super classes`() {
        runCodebaseTest(
            inputSet(
                java(
                    """
                        package test.pkg;
                        public class Class4<I> {}
                    """
                        .trimIndent()
                ),
                java(
                    """
                        package test.pkg;
                        public class Class3<G, H> extends Class4<G> {}
                    """
                        .trimIndent()
                ),
                java(
                    """
                        package test.pkg;
                        public class Class2<D, E, F> extends Class3<D, F> {}
                    """
                        .trimIndent()
                ),
                java(
                    """
                        package test.pkg;
                        public class Class1<A, B, C> extends Class2<B, C, A> {}
                    """
                        .trimIndent()
                )
            ),
            inputSet(
                signature(
                    """
                        // Signature format: 5.0
                        package test.pkg {
                          public class Class1<A, B, C> extends test.pkg.Class2<B,C,A> {
                          }
                          public class Class2<D, E, F> extends test.pkg.Class3<D,F> {
                          }
                          public class Class3<G, H> extends test.pkg.Class4<G> {
                          }
                          public class Class4<I> {
                          }
                        }
                    """
                        .trimIndent()
                )
            ),
            inputSet(
                kotlin(
                    """
                        package test.pkg
                        open class Class4<I>
                        open class Class3<G, H> : Class4<G>
                        open class Class2<D, E, F> : Class3<D, F>
                        class Class1<A, B, C> : Class2<B, C, A>
                    """
                        .trimIndent()
                )
            )
        ) {
            val c4 = codebase.assertClass("test.pkg.Class4")
            val i = c4.typeParameterList().typeParameters()[0]

            val c3 = codebase.assertClass("test.pkg.Class3")
            val c3TypeParams = c3.typeParameterList().typeParameters()
            val g = c3TypeParams[0]
            val gType = g.toType()
            val h = c3TypeParams[1]

            val c2 = codebase.assertClass("test.pkg.Class2")
            val c2TypeParams = c2.typeParameterList().typeParameters()
            val d = c2TypeParams[0]
            val dType = d.toType()
            val e = c2TypeParams[1]
            val f = c2TypeParams[2]
            val fType = f.toType()

            val c1 = codebase.assertClass("test.pkg.Class1")
            val c1TypeParams = c1.typeParameterList().typeParameters()
            val aType = c1TypeParams[0].toType()
            val bType = c1TypeParams[1].toType()
            val cType = c1TypeParams[2].toType()

            assertEquals(mapOf(i to gType), c3.mapTypeVariables(c4))

            assertEquals(mapOf(g to dType, h to fType), c2.mapTypeVariables(c3))
            assertEquals(mapOf(i to dType), c2.mapTypeVariables(c4))

            assertEquals(mapOf(d to bType, e to cType, f to aType), c1.mapTypeVariables(c2))
            assertEquals(mapOf(g to bType, h to aType), c1.mapTypeVariables(c3))
            assertEquals(mapOf(i to bType), c1.mapTypeVariables(c4))
        }
    }

    @Test
    fun `Test mapTypeVariables with concrete classes`() {
        runCodebaseTest(
            inputSet(
                java(
                    """
                        package test.pkg;
                        public class Grandparent<A, B> {}
                    """
                        .trimIndent()
                ),
                java(
                    """
                        package test.pkg;
                        public class Parent<T> extends Grandparent<T, Parent<T>> {}
                    """
                        .trimIndent()
                ),
                java(
                    """
                        package test.pkg;
                        public class Child extends Parent<Child> {}
                    """
                        .trimIndent()
                )
            ),
            inputSet(
                signature(
                    """
                        // Signature format: 5.0
                        package test.pkg {
                          public class Child extends test.pkg.Parent<test.pkg.Child> {
                          }
                          public class Grandparent<A, B> {
                          }
                          public class Parent<T> extends test.pkg.Grandparent<T,test.pkg.Parent<T>> {
                          }
                        }
                    """
                        .trimIndent()
                )
            ),
            inputSet(
                kotlin(
                    """
                        package test.pkg
                        open class Grandparent<A, B>
                        open class Parent<T> : Grandparent<T, Parent<T>>
                        class Child : Parent<Child>
                    """
                        .trimIndent()
                )
            )
        ) {
            val grandparent = codebase.assertClass("test.pkg.Grandparent")
            val grandparentTypeParams = grandparent.typeParameterList().typeParameters()
            val a = grandparentTypeParams[0]
            val b = grandparentTypeParams[1]

            val parent = codebase.assertClass("test.pkg.Parent")
            val t = parent.typeParameterList().typeParameters()[0]
            val tType = t.toType()

            val child = codebase.assertClass("test.pkg.Child")

            val erasedParentType = (parent.toType() as ClassTypeItem).duplicate(null, emptyList())
            assertEquals(
                mapOf(a to tType, b to erasedParentType),
                parent.mapTypeVariables(grandparent)
            )
            assertEquals(mapOf(t to child.toType()), child.mapTypeVariables(parent))
            assertEquals(
                mapOf(a to child.toType(), b to erasedParentType),
                child.mapTypeVariables(grandparent)
            )
        }
    }

    @Test
    fun `Test mapTypeVariables with interfaces`() {
        runCodebaseTest(
            inputSet(
                java(
                    """
                        package test.pkg;
                        public interface Interface3<G, H> {}
                    """
                        .trimIndent()
                ),
                java(
                    """
                        package test.pkg;
                        public interface Interface2<E, F> extends Interface3<E, F> {}
                    """
                        .trimIndent()
                ),
                java(
                    """
                        package test.pkg;
                        public interface Interface1<C, D> {}
                    """
                        .trimIndent()
                ),
                java(
                    """
                        package test.pkg;
                        public class Class<A, B> implements Interface1<A, B>, Interface2<B, A>{}
                    """
                        .trimIndent()
                )
            ),
            inputSet(
                signature(
                    """
                        // Signature format: 5.0
                        package test.pkg {
                          public class Class<A, B> implements test.pkg.Interface1<A,B> test.pkg.Interface2<B,A> {
                          }
                          public interface Interface1<C, D> {
                          }
                          public interface Interface2<E, F> extends test.pkg.Interface3<E,F> {
                          }
                          public interface Interface3<G, H> {
                          }
                        }
                    """
                        .trimIndent()
                )
            ),
            inputSet(
                kotlin(
                    """
                        package test.pkg
                        interface Interface3<G, H>
                        interface Interface2<E, F> : Interface3<E, F>
                        interface Interface1<C, D>
                        class Class<A, B> : Interface1<A, B>, Interface2<B, A>
                    """
                        .trimIndent()
                )
            )
        ) {
            val i3 = codebase.assertClass("test.pkg.Interface3")
            val i3TypeParams = i3.typeParameterList().typeParameters()
            val g = i3TypeParams[0]
            val h = i3TypeParams[1]

            val i2 = codebase.assertClass("test.pkg.Interface2")
            val i2TypeParams = i2.typeParameterList().typeParameters()
            val e = i2TypeParams[0]
            val eType = e.toType()
            val f = i2TypeParams[1]
            val fType = f.toType()

            val i1 = codebase.assertClass("test.pkg.Interface1")
            val i1TypeParams = i1.typeParameterList().typeParameters()
            val c = i1TypeParams[0]
            val d = i1TypeParams[1]

            val cls = codebase.assertClass("test.pkg.Class")
            val clsTypeParams = cls.typeParameterList().typeParameters()
            val aType = clsTypeParams[0].toType()
            val bType = clsTypeParams[1].toType()

            assertEquals(mapOf(c to aType, d to bType), cls.mapTypeVariables(i1))

            assertEquals(mapOf(g to eType, h to fType), i2.mapTypeVariables(i3))
            assertEquals(mapOf(e to bType, f to aType), cls.mapTypeVariables(i2))
            assertEquals(mapOf(g to bType, h to aType), cls.mapTypeVariables(i3))
        }
    }

    @Test
    fun `Test mapTypeVariables with diamond interface`() {
        runCodebaseTest(
            inputSet(
                java(
                    """
                        package test.pkg;
                        public interface Root<T> {}
                    """
                        .trimIndent()
                ),
                java(
                    """
                        package test.pkg;
                        public interface Interface1<T1> extends Root<T1> {}
                    """
                        .trimIndent()
                ),
                java(
                    """
                        package test.pkg;
                        public interface Interface2<T2> extends Root<T2> {}
                    """
                        .trimIndent()
                ),
                java(
                    """
                        package test.pkg;
                        public class Child<X, Y> implements Interface1<X>, Interface2<Y> {}
                    """
                        .trimIndent()
                )
            ),
            inputSet(
                signature(
                    """
                        // Signature format: 5.0
                        package test.pkg {
                          public class Child<X, Y> implements test.pkg.Interface1<X> test.pkg.Interface2<Y> {
                          }
                          public interface Interface1<T1> extends test.pkg.Root<T1> {
                          }
                          public interface Interface2<T2> extends test.pkg.Root<T2> {
                          }
                          public interface Root<T> {
                          }
                        }
                    """
                        .trimIndent()
                )
            ),
            inputSet(
                kotlin(
                    """
                        package test.pkg
                        interface Root<T>
                        interface Interface1<T1> : Root<T1>
                        interface Interface2<T2> : Root<T2>
                        class Child<X, Y> : Interface1<X>, Interface2<Y>
                    """
                        .trimIndent()
                )
            )
        ) {
            val root = codebase.assertClass("test.pkg.Root")
            val t = root.typeParameterList().typeParameters()[0]

            val i1 = codebase.assertClass("test.pkg.Interface1")
            val t1 = i1.typeParameterList().typeParameters()[0]
            val t1Type = t1.toType()

            val i2 = codebase.assertClass("test.pkg.Interface2")
            val t2 = i2.typeParameterList().typeParameters()[0]
            val t2Type = t2.toType()

            val child = codebase.assertClass("test.pkg.Child")
            val childParameterList = child.typeParameterList().typeParameters()
            val xType = childParameterList[0].toType()
            val yType = childParameterList[1].toType()

            assertEquals(mapOf(t to t1Type), i1.mapTypeVariables(root))
            assertEquals(mapOf(t to t2Type), i2.mapTypeVariables(root))
            assertEquals(
                mapOf(t1 to xType),
                child.mapTypeVariables(i1),
            )
            assertEquals(
                mapOf(t2 to yType),
                child.mapTypeVariables(i2),
            )
            assertEquals(
                mapOf(t to xType),
                child.mapTypeVariables(root),
            )
        }
    }

    @Test
    fun `Test inheritMethodFromNonApiAncestor`() {
        runSourceCodebaseTest(
            inputSet(
                java(
                    """
                        package test.pkg;
                        class HiddenClass {
                            public void foo() {}
                        }
                    """
                ),
                java(
                    """
                        package test.pkg;
                        public class PublicClass extends HiddenClass {}
                    """
                ),
            ),
        ) {
            val hiddenClass = codebase.assertClass("test.pkg.HiddenClass")
            val hiddenClassMethod = hiddenClass.methods().single()
            val publicClass = codebase.assertClass("test.pkg.PublicClass")

            val inheritedMethod = publicClass.inheritMethodFromNonApiAncestor(hiddenClassMethod)
            assertSame(hiddenClass, inheritedMethod.inheritedFrom)
            assertTrue(inheritedMethod.inheritedFromAncestor)
        }
    }

    @Test
    fun `Test toType for outer class with type parameter`() {
        runCodebaseTest(
            java(
                """
                    package test.pkg;
                    public class Outer<T> {
                        public class Inner {}
                    }
                """
            ),
            signature(
                """
                    // Signature format: 5.0
                    package test.pkg {
                      public class Outer<T> {
                      }
                      public class Outer.Inner {
                      }
                    }
                """
            )
        ) {
            val innerClass = codebase.assertClass("test.pkg.Outer.Inner")
            val outerClass = codebase.assertClass("test.pkg.Outer")
            val outerClassParameter = outerClass.typeParameterList().typeParameters().single()

            val innerType = innerClass.toType()
            assertThat(innerType).isInstanceOf(ClassTypeItem::class.java)
            assertThat((innerType as ClassTypeItem).qualifiedName).isEqualTo("test.pkg.Outer.Inner")

            val outerType = innerType.outerClassType
            assertThat(outerType).isNotNull()
            assertThat(outerType!!.qualifiedName).isEqualTo("test.pkg.Outer")

            val outerClassVariable = outerType.parameters.single()
            assertThat(outerClassVariable).isInstanceOf(VariableTypeItem::class.java)
            assertThat((outerClassVariable as VariableTypeItem).name).isEqualTo("T")
            assertThat(outerClassVariable.asTypeParameter).isEqualTo(outerClassParameter)
        }
    }
}
