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

import com.android.tools.metalava.model.ArrayTypeItem
import com.android.tools.metalava.model.ClassTypeItem
import com.android.tools.metalava.model.Codebase
import com.android.tools.metalava.model.PrimitiveTypeItem
import com.android.tools.metalava.model.VisibilityLevel
import com.android.tools.metalava.model.text.ApiFile
import com.android.tools.metalava.model.text.FileFormat
import com.android.tools.metalava.model.text.TextMethodItem
import com.android.tools.metalava.model.text.assertSignatureFilesMatch
import com.android.tools.metalava.model.visitors.ApiVisitor
import com.google.common.truth.Truth.assertThat
import java.io.PrintWriter
import java.io.StringWriter
import org.junit.Test

class SignatureInputOutputTest {
    /**
     * Parses the API (without a header line, the header from [format] will be added) from the
     * [signature], runs the [codebaseTest] on the parsed codebase, and then writes the codebase
     * back out in the [format], verifying that the output matches the original [signature].
     *
     * This tests both [ApiFile] and [SignatureWriter].
     */
    private fun runInputOutputTest(
        signature: String,
        format: FileFormat,
        codebaseTest: (Codebase) -> Unit
    ) {
        val fullSignature = format.header() + signature
        val codebase = ApiFile.parseApi("test", fullSignature)

        codebaseTest(codebase)

        val output =
            StringWriter().use { stringWriter ->
                PrintWriter(stringWriter).use { printWriter ->
                    val signatureWriter =
                        SignatureWriter(
                            writer = printWriter,
                            filterEmit = { true },
                            filterReference = { true },
                            preFiltered = false,
                            emitHeader = EmitFileHeader.IF_NONEMPTY_FILE,
                            fileFormat = format,
                            showUnannotated = false,
                            apiVisitorConfig = ApiVisitor.Config(),
                        )
                    codebase.accept(signatureWriter)
                }
                stringWriter.toString()
            }

        assertSignatureFilesMatch(signature, output, format)
    }

    @Test
    fun `Test basic signature file`() {
        val api =
            """
                package test.pkg {
                  public class Foo {
                    ctor public Foo();
                  }
                }
            """
                .trimIndent()

        runInputOutputTest(api, kotlinStyleFormat) { codebase ->
            val foo = codebase.findClass("test.pkg.Foo")
            assertThat(foo).isNotNull()
            assertThat(foo!!.constructors()).hasSize(1)
            val ctor = foo.constructors().single()
            assertThat(ctor.parameters()).isEmpty()
        }
    }

    @Test
    fun `Test property`() {
        val api =
            """
                package test.pkg {
                  public class Foo {
                    property public foo: String;
                  }
                }
            """
                .trimIndent()
        runInputOutputTest(api, kotlinStyleFormat) { codebase ->
            val foo = codebase.findClass("test.pkg.Foo")
            assertThat(foo).isNotNull()
            assertThat(foo!!.properties()).hasSize(1)

            val prop = foo.properties().single()
            assertThat(prop.name()).isEqualTo("foo")
            assertThat(prop.type().isString()).isTrue()
            assertThat(prop.modifiers.getVisibilityLevel()).isEqualTo(VisibilityLevel.PUBLIC)
        }
    }

    @Test
    fun `Test field without value`() {
        val api =
            """
                package test.pkg {
                  public class Foo {
                    field protected foo: String;
                  }
                }
            """
                .trimIndent()
        runInputOutputTest(api, kotlinStyleFormat) { codebase ->
            val foo = codebase.findClass("test.pkg.Foo")
            assertThat(foo).isNotNull()
            assertThat(foo!!.fields()).hasSize(1)

            val field = foo.fields().single()
            assertThat(field.name()).isEqualTo("foo")
            assertThat(field.type().isString()).isTrue()
            assertThat(field.modifiers.getVisibilityLevel()).isEqualTo(VisibilityLevel.PROTECTED)
            assertThat(field.initialValue()).isNull()
        }
    }

    @Test
    fun `Test field with value`() {
        val api =
            """
                package test.pkg {
                  public class Foo {
                    field public static foo: String = "hi";
                  }
                }
            """
                .trimIndent()
        runInputOutputTest(api, kotlinStyleFormat) { codebase ->
            val foo = codebase.findClass("test.pkg.Foo")
            assertThat(foo).isNotNull()
            assertThat(foo!!.fields()).hasSize(1)

            val field = foo.fields().single()
            assertThat(field.name()).isEqualTo("foo")
            assertThat(field.type().isString()).isTrue()
            assertThat(field.modifiers.getVisibilityLevel()).isEqualTo(VisibilityLevel.PUBLIC)
            assertThat(field.modifiers.isStatic()).isTrue()
            assertThat(field.initialValue()).isEqualTo("hi")
        }
    }

    @Test
    fun `Test method without parameters`() {
        val api =
            """
                package test.pkg {
                  public class Foo {
                    method public foo(): String;
                  }
                }
            """
                .trimIndent()
        runInputOutputTest(api, kotlinStyleFormat) { codebase ->
            val foo = codebase.findClass("test.pkg.Foo")
            assertThat(foo).isNotNull()
            assertThat(foo!!.methods()).hasSize(1)

            val method = foo.methods().single()
            assertThat(method.name()).isEqualTo("foo")
            assertThat(method.modifiers.getVisibilityLevel()).isEqualTo(VisibilityLevel.PUBLIC)
            assertThat(method.returnType().isString()).isTrue()
            assertThat(method.parameters()).isEmpty()
        }
    }

    @Test
    fun `Test method without parameters with throws list`() {
        val api =
            """
                package test.pkg {
                  public class Foo {
                    method public foo(): void throws java.lang.IllegalStateException;
                  }
                }
            """
                .trimIndent()
        runInputOutputTest(api, kotlinStyleFormat) { codebase ->
            val foo = codebase.findClass("test.pkg.Foo")
            assertThat(foo).isNotNull()
            assertThat(foo!!.methods()).hasSize(1)

            val method = foo.methods().single()
            assertThat(method.name()).isEqualTo("foo")
            assertThat(method.modifiers.getVisibilityLevel()).isEqualTo(VisibilityLevel.PUBLIC)
            assertThat((method.returnType() as PrimitiveTypeItem).kind)
                .isEqualTo(PrimitiveTypeItem.Primitive.VOID)
            assertThat(method.parameters()).isEmpty()

            assertThat(method.throwsTypes()).hasSize(1)
            assertThat(method.throwsTypes().single().qualifiedName())
                .isEqualTo("java.lang.IllegalStateException")
        }
    }

    @Test
    fun `Test method without parameters with default value`() {
        val api =
            """
                package test.pkg {
                  public @interface Foo {
                    method public foo(): int default java.lang.Integer.MIN_VALUE;
                  }
                }
            """
                .trimIndent()
        runInputOutputTest(api, kotlinStyleFormat) { codebase ->
            val foo = codebase.findClass("test.pkg.Foo")
            assertThat(foo).isNotNull()
            assertThat(foo!!.methods()).hasSize(1)

            val method = foo.methods().single()
            assertThat(method.name()).isEqualTo("foo")
            assertThat(method.modifiers.getVisibilityLevel()).isEqualTo(VisibilityLevel.PUBLIC)
            assertThat((method.returnType() as PrimitiveTypeItem).kind)
                .isEqualTo(PrimitiveTypeItem.Primitive.INT)
            assertThat(method.parameters()).isEmpty()

            assertThat(method.hasDefaultValue()).isTrue()
            assertThat(method.defaultValue()).isEqualTo("java.lang.Integer.MIN_VALUE")
        }
    }

    @Test
    fun `Test method with one named parameter`() {
        val api =
            """
                package test.pkg {
                  public class Foo {
                    method public foo(arg: int): String;
                  }
                }
            """
                .trimIndent()
        runInputOutputTest(api, kotlinStyleFormat) { codebase ->
            val foo = codebase.findClass("test.pkg.Foo")
            assertThat(foo).isNotNull()
            val method = foo!!.methods().single()

            assertThat(method.parameters()).hasSize(1)
            val param = method.parameters().single()
            assertThat(param.name()).isEqualTo("arg")
            assertThat(param.publicName()).isEqualTo("arg")
            assertThat((param.type() as PrimitiveTypeItem).kind)
                .isEqualTo(PrimitiveTypeItem.Primitive.INT)
        }
    }

    @Test
    fun `Test method with one named parameter with concise default value`() {
        val api =
            """
                package test.pkg {
                  public class Foo {
                    method public foo(optional arg: int): String;
                  }
                }
            """
                .trimIndent()
        runInputOutputTest(api, kotlinStyleFormat) { codebase ->
            val foo = codebase.findClass("test.pkg.Foo")
            assertThat(foo).isNotNull()
            val method = foo!!.methods().single()

            assertThat(method.parameters()).hasSize(1)
            val param = method.parameters().single()
            assertThat(param.name()).isEqualTo("arg")
            assertThat(param.publicName()).isEqualTo("arg")
            assertThat((param.type() as PrimitiveTypeItem).kind)
                .isEqualTo(PrimitiveTypeItem.Primitive.INT)

            assertThat(param.hasDefaultValue()).isTrue()
            assertThat(param.isDefaultValueKnown()).isFalse()
        }
    }

    @Test
    fun `Test method with one named parameter with non-concise default value`() {
        val format = kotlinStyleFormat.copy(conciseDefaultValues = false)
        val api =
            """
                package test.pkg {
                  public class Foo {
                    method public foo(arg: int = 3): String;
                  }
                }
            """
                .trimIndent()
        runInputOutputTest(api, format) { codebase ->
            val foo = codebase.findClass("test.pkg.Foo")
            assertThat(foo).isNotNull()
            val method = foo!!.methods().single()

            assertThat(method.parameters()).hasSize(1)
            val param = method.parameters().single()
            assertThat(param.name()).isEqualTo("arg")
            assertThat(param.publicName()).isEqualTo("arg")
            assertThat((param.type() as PrimitiveTypeItem).kind)
                .isEqualTo(PrimitiveTypeItem.Primitive.INT)

            assertThat(param.hasDefaultValue()).isTrue()
            assertThat(param.isDefaultValueKnown()).isTrue()
            assertThat(param.defaultValue()).isEqualTo("3")
        }
    }

    @Test
    fun `Test method with one named vararg param`() {
        val api =
            """
                package test.pkg {
                  public class Foo {
                    method public foo(vals: int...): String;
                  }
                }
            """
                .trimIndent()
        runInputOutputTest(api, kotlinStyleFormat) { codebase ->
            val foo = codebase.findClass("test.pkg.Foo")
            assertThat(foo).isNotNull()
            val method = foo!!.methods().single()

            assertThat(method.parameters()).hasSize(1)
            val param = method.parameters().single()
            assertThat(param.name()).isEqualTo("vals")
            assertThat(param.publicName()).isEqualTo("vals")

            assertThat((param.type() as ArrayTypeItem).isVarargs).isTrue()
            assertThat(param.isVarArgs()).isTrue()
            assertThat(param.modifiers.isVarArg()).isTrue()
            assertThat((method as TextMethodItem).isVarArg()).isTrue()
        }
    }

    @Test
    fun `Test method with one unnamed parameter`() {
        val api =
            kotlinStyleFormat.header() +
                """
                package test.pkg {
                  public class Foo {
                    method public foo(_: int): String;
                  }
                }
            """
                    .trimIndent()
        runInputOutputTest(api, kotlinStyleFormat) { codebase ->
            val foo = codebase.findClass("test.pkg.Foo")
            assertThat(foo).isNotNull()
            val method = foo!!.methods().single()

            assertThat(method.parameters()).hasSize(1)
            val param = method.parameters().single()
            assertThat(param.name()).isEqualTo("_")
            assertThat(param.publicName()).isNull()
            assertThat((param.type() as PrimitiveTypeItem).kind)
                .isEqualTo(PrimitiveTypeItem.Primitive.INT)
        }
    }

    @Test
    fun `Test method with one unnamed parameter with modifier`() {
        val api =
            """
                package test.pkg {
                  public class Foo {
                    method public foo(volatile _: test.pkg.Foo): String;
                  }
                }
            """
                .trimIndent()
        runInputOutputTest(api, kotlinStyleFormat) { codebase ->
            val foo = codebase.findClass("test.pkg.Foo")
            assertThat(foo).isNotNull()
            val method = foo!!.methods().single()

            assertThat(method.parameters()).hasSize(1)
            val param = method.parameters().single()
            assertThat(param.name()).isEqualTo("_")
            assertThat(param.publicName()).isNull()
            assertThat((param.type() as ClassTypeItem).qualifiedName).isEqualTo("test.pkg.Foo")
            assertThat(param.modifiers.isVolatile()).isTrue()
        }
    }

    @Test
    fun `Test method with list of named parameters`() {
        val api =
            """
                package test.pkg {
                  public class Foo {
                    method public foo(i: int, map: java.util.Map<java.lang.String, java.lang.Object>, arr: String[]): String;
                  }
                }
            """
                .trimIndent()
        runInputOutputTest(api, kotlinStyleFormat) { codebase ->
            val foo = codebase.findClass("test.pkg.Foo")
            assertThat(foo).isNotNull()
            val method = foo!!.methods().single()

            assertThat(method.parameters()).hasSize(3)

            // i: int
            val p0 = method.parameters()[0]
            assertThat(p0.name()).isEqualTo("i")
            assertThat(p0.publicName()).isEqualTo("i")
            assertThat((p0.type() as PrimitiveTypeItem).kind)
                .isEqualTo(PrimitiveTypeItem.Primitive.INT)

            // map: java.util.Map<java.lang.String, java.lang.Object>
            val p1 = method.parameters()[1]
            assertThat(p1.name()).isEqualTo("map")
            assertThat(p1.publicName()).isEqualTo("map")
            val mapType = p1.type() as ClassTypeItem
            assertThat(mapType.qualifiedName).isEqualTo("java.util.Map")
            assertThat(mapType.parameters).hasSize(2)
            assertThat(mapType.parameters[0].isString()).isTrue()
            assertThat(mapType.parameters[1].isJavaLangObject()).isTrue()

            // arr: String[]
            val p2 = method.parameters()[2]
            assertThat(p2.name()).isEqualTo("arr")
            assertThat(p2.publicName()).isEqualTo("arr")
            assertThat((p2.type() as ArrayTypeItem).componentType.isString()).isTrue()
        }
    }

    @Test
    fun `Test method with list of unnamed parameters`() {
        val api =
            """
                package test.pkg {
                  public class Foo {
                    method public foo(_: int, _: java.util.Map<java.lang.String, java.lang.Object>, _: String[]): String;
                  }
                }
            """
                .trimIndent()
        runInputOutputTest(api, kotlinStyleFormat) { codebase ->
            val foo = codebase.findClass("test.pkg.Foo")
            assertThat(foo).isNotNull()
            val method = foo!!.methods().single()

            assertThat(method.parameters()).hasSize(3)

            // _: int
            val p0 = method.parameters()[0]
            assertThat(p0.name()).isEqualTo("_")
            assertThat(p0.publicName()).isNull()
            assertThat((p0.type() as PrimitiveTypeItem).kind)
                .isEqualTo(PrimitiveTypeItem.Primitive.INT)

            // _: java.util.Map<java.lang.String, java.lang.Object>
            val p1 = method.parameters()[1]
            assertThat(p1.name()).isEqualTo("_")
            assertThat(p1.publicName()).isNull()
            val mapType = p1.type() as ClassTypeItem
            assertThat(mapType.qualifiedName).isEqualTo("java.util.Map")
            assertThat(mapType.parameters).hasSize(2)
            assertThat(mapType.parameters[0].isString()).isTrue()
            assertThat(mapType.parameters[1].isJavaLangObject()).isTrue()

            // _: String[]
            val p2 = method.parameters()[2]
            assertThat(p2.name()).isEqualTo("_")
            assertThat(p2.publicName()).isNull()
            assertThat((p2.type() as ArrayTypeItem).componentType.isString()).isTrue()
        }
    }

    companion object {
        private val kotlinStyleFormat =
            FileFormat.V5.copy(kotlinNameTypeOrder = true, formatDefaults = FileFormat.V5)
    }
}
