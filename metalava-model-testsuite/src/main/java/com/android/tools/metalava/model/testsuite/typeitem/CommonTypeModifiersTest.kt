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

package com.android.tools.metalava.model.testsuite.typeitem

import com.android.tools.metalava.model.ArrayTypeItem
import com.android.tools.metalava.model.ClassTypeItem
import com.android.tools.metalava.model.VariableTypeItem
import com.android.tools.metalava.model.testsuite.BaseModelTest
import com.android.tools.metalava.model.testsuite.TestParameters
import com.android.tools.metalava.testing.java
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@RunWith(Parameterized::class)
class CommonTypeModifiersTest(parameters: TestParameters) : BaseModelTest(parameters) {
    @Test
    fun `Test inner parameterized types with annotations`() {
        runCodebaseTest(
            java(
                """
                    package test.pkg;
                    public class Outer<O> {
                        public class Inner<I> {
                        }

                        public <P1, P2> @test.pkg.A Outer<@test.pkg.B P1>.@test.pkg.C Inner<@test.pkg.D P2> foo() {
                            return new Outer<P1>.Inner<P2>();
                        }
                    }
                """
            ),
            signature(
                """
                    // Signature format: 3.0
                    package test.pkg {
                      public class Outer<O> {
                        ctor public Outer();
                        method public <P1, P2> test.pkg.@test.pkg.A Outer<@test.pkg.B P1!>.@test.pkg.C Inner<@test.pkg.D P2!>! foo();
                      }
                      public class Outer.Inner<I> {
                        ctor public Outer.Inner();
                      }
                    }
                """
                    .trimIndent()
            )
        ) { codebase ->
            val method = codebase.assertClass("test.pkg.Outer").methods().single()
            val methodTypeParameters = method.typeParameterList().typeParameters()
            assertThat(methodTypeParameters).hasSize(2)
            val p1 = methodTypeParameters[0]
            val p2 = methodTypeParameters[1]

            // TODO: test the annotations
            // Outer<P1>.Inner<P2>
            val innerType = method.returnType()
            assertThat(innerType).isInstanceOf(ClassTypeItem::class.java)
            assertThat((innerType as ClassTypeItem).qualifiedName).isEqualTo("test.pkg.Outer.Inner")
            assertThat(innerType.parameters).hasSize(1)
            val innerTypeParameter = innerType.parameters.single()
            assertThat(innerTypeParameter).isInstanceOf(VariableTypeItem::class.java)
            assertThat((innerTypeParameter as VariableTypeItem).name).isEqualTo("P2")
            assertThat(innerTypeParameter.asTypeParameter).isEqualTo(p2)

            val outerType = innerType.outerClassType
            assertThat(outerType).isNotNull()
            assertThat(outerType!!.qualifiedName).isEqualTo("test.pkg.Outer")
            assertThat(outerType.outerClassType).isNull()
            assertThat(outerType.parameters).hasSize(1)
            val outerClassParameter = outerType.parameters.single()
            assertThat(outerClassParameter).isInstanceOf(VariableTypeItem::class.java)
            assertThat((outerClassParameter as VariableTypeItem).name).isEqualTo("P1")
            assertThat(outerClassParameter.asTypeParameter).isEqualTo(p1)
        }
    }

    @Test
    fun `Test interface types`() {
        runCodebaseTest(
            java(
                """
                    package test.pkg;
                    public class Foo implements test.pkg.@test.pkg.A Bar, test.pkg.Baz {}
                """
            ),
            signature(
                """
                    // Signature format: 4.0
                    package test.pkg {
                      public class Foo implements test.pkg.@test.pkg.A Bar, test.pkg.Baz {
                      }
                    }
                """
                    .trimIndent()
            )
        ) { codebase ->
            val foo = codebase.assertClass("test.pkg.Foo")
            val interfaces = foo.interfaceTypes()
            assertThat(interfaces).hasSize(2)

            val bar = interfaces[0]
            assertThat(bar).isInstanceOf(ClassTypeItem::class.java)
            assertThat((bar as ClassTypeItem).qualifiedName).isEqualTo("test.pkg.Bar")

            val baz = interfaces[1]
            assertThat(baz).isInstanceOf(ClassTypeItem::class.java)
            assertThat((baz as ClassTypeItem).qualifiedName).isEqualTo("test.pkg.Baz")
        }
    }

    @Test
    fun `Test super class type`() {
        runCodebaseTest(
            java(
                """
                    package test.pkg;
                    public class Foo extends test.pkg.@test.pkg.A Bar {}
                """
            ),
            signature(
                """
                    // Signature format: 4.0
                    package test.pkg {
                      public class Foo extends test.pkg.@test.pkg.A Bar {
                      }
                    }
                """
                    .trimIndent()
            )
        ) { codebase ->
            val foo = codebase.assertClass("test.pkg.Foo")
            val superClass = foo.superClassType()
            assertThat(superClass).isNotNull()
            assertThat(superClass).isInstanceOf(ClassTypeItem::class.java)
            assertThat((superClass as ClassTypeItem).qualifiedName).isEqualTo("test.pkg.Bar")
        }
    }

    @Test
    fun `Test super class and interface types of interface`() {
        runCodebaseTest(
            java(
                """
                    package test.pkg;
                    public interface Foo extends test.pkg.@test.pkg.A Bar, test.pkg.@test.pkg.A Baz<@test.pkg.A String>, test.pkg.Biz {}
                """
            ),
            signature(
                """
                    // Signature format: 4.0
                    package test.pkg {
                      public interface Foo extends test.pkg.@test.pkg.A Bar test.pkg.@test.pkg.A Baz<@test.pkg.A String> test.pkg.Biz {
                      }
                    }
                """
                    .trimIndent()
            )
        ) { codebase ->
            val foo = codebase.assertClass("test.pkg.Foo")

            val bar = foo.superClassType()
            assertThat(bar).isNotNull()
            assertThat(bar).isInstanceOf(ClassTypeItem::class.java)
            assertThat((bar as ClassTypeItem).qualifiedName).isEqualTo("test.pkg.Bar")

            val interfaces = foo.interfaceTypes()
            assertThat(interfaces).hasSize(2)

            val baz = interfaces[0]
            assertThat(baz).isInstanceOf(ClassTypeItem::class.java)
            assertThat((baz as ClassTypeItem).qualifiedName).isEqualTo("test.pkg.Baz")
            assertThat(baz.parameters).hasSize(1)
            val bazParam = baz.parameters.single()
            assertThat(bazParam.isString()).isTrue()

            val biz = interfaces[1]
            assertThat(biz).isInstanceOf(ClassTypeItem::class.java)
            assertThat((biz as ClassTypeItem).qualifiedName).isEqualTo("test.pkg.Biz")
        }
    }

    @Test
    fun `Test annotated array types in multiple contexts`() {
        runCodebaseTest(
            java(
                """
                    package test.pkg;
                    public class Foo {
                        public test.pkg.Foo @test.pkg.A [] method(test.pkg.Foo @test.pkg.A [] arg) {}
                        public test.pkg.Foo @test.pkg.A [] field;
                    }
                """
            ),
            signature(
                """
                    // Signature format: 4.0
                    package test.pkg {
                      public class Foo {
                        method public test.pkg.Foo @test.pkg.A [] method(test.pkg.Foo @test.pkg.A []);
                        field public test.pkg.Foo @test.pkg.A [] field;
                        property public test.pkg.Foo @test.pkg.A [] prop;
                      }
                    }
                """
                    .trimIndent()
            )
        ) { codebase ->
            val foo = codebase.assertClass("test.pkg.Foo")
            val method = foo.methods().single()
            val returnType = method.returnType()
            val paramType = method.parameters().single().type()
            val fieldType = foo.fields().single().type()
            // Properties can't be defined in java, this is only present for signature type
            val propertyType = foo.properties().singleOrNull()?.type()

            // Do full check for one type, then verify the others are equal
            assertThat(returnType).isInstanceOf(ArrayTypeItem::class.java)
            val componentType = (returnType as ArrayTypeItem).componentType
            assertThat(componentType).isInstanceOf(ClassTypeItem::class.java)
            assertThat((componentType as ClassTypeItem).qualifiedName).isEqualTo("test.pkg.Foo")
            // TODO: test the annotations

            assertThat(returnType).isEqualTo(paramType)
            assertThat(returnType).isEqualTo(fieldType)
            if (propertyType != null) {
                assertThat(returnType).isEqualTo(propertyType)
            }
        }
    }
}
