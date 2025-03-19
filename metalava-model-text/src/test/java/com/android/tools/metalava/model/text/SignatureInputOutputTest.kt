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

package com.android.tools.metalava.model.text

import com.android.tools.metalava.model.ArrayTypeItem
import com.android.tools.metalava.model.Assertions
import com.android.tools.metalava.model.ClassTypeItem
import com.android.tools.metalava.model.Codebase
import com.android.tools.metalava.model.PrimitiveTypeItem
import com.android.tools.metalava.model.StripJavaLangPrefix
import com.android.tools.metalava.model.VisibilityLevel
import com.android.tools.metalava.model.text.FileFormat.TypeArgumentSpacing
import com.android.tools.metalava.model.visitors.ApiPredicate
import com.android.tools.metalava.model.visitors.ApiType
import com.google.common.truth.Truth.assertThat
import java.io.PrintWriter
import java.io.StringWriter
import org.junit.Test

/**
 * Tests [SignatureWriter] and [ApiFile] by round tripping a signature file and make sure that it
 * matches the original.
 */
class SignatureInputOutputTest : Assertions {

    /**
     * Context against which test code is run.
     *
     * Used as it is easier to extend, simpler and more consistent to use than passing a parameter
     * which requires specifying the parameter name on every use and changing every lambda if new
     * information is passed.
     */
    private data class CodebaseContext(val codebase: Codebase)

    /**
     * Parses the API (without a header line, the header from [fileFormat] will be added) from the
     * [signature], runs the [codebaseTest] on the parsed codebase, and then writes the codebase
     * back out in the [fileFormat], verifying that the output matches [expectedOutput] which
     * defaults to the original [signature].
     *
     * This tests both [ApiFile] and [SignatureWriter].
     */
    private fun runInputOutputTest(
        signature: String,
        fileFormat: FileFormat,
        expectedOutput: String = signature,
        codebaseTest: CodebaseContext.() -> Unit = {},
    ) {
        val fullSignature = fileFormat.header() + signature
        val signatureFile = SignatureFile.fromText("test", fullSignature)
        val codebase = ApiFile.parseApi(listOf(signatureFile))

        CodebaseContext(codebase).codebaseTest()

        val output =
            StringWriter().use { stringWriter ->
                PrintWriter(stringWriter).use { printWriter ->
                    val signatureWriter =
                        SignatureWriter(
                            writer = printWriter,
                            emitHeader = EmitFileHeader.IF_NONEMPTY_FILE,
                            fileFormat = fileFormat,
                        )

                    val visitor =
                        createFilteringVisitorForSignatures(
                            delegate = signatureWriter,
                            fileFormat = fileFormat,
                            apiType = ApiType.ALL,
                            preFiltered = true,
                            showUnannotated = false,
                            apiPredicateConfig = ApiPredicate.Config()
                        )

                    codebase.accept(visitor)
                }
                stringWriter.toString()
            }

        assertSignatureFilesMatch(expectedOutput, output, fileFormat)
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

        runInputOutputTest(api, kotlinStyleFormat) {
            val foo = codebase.assertClass("test.pkg.Foo")
            assertThat(foo.constructors()).hasSize(1)
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
        runInputOutputTest(api, kotlinStyleFormat) {
            val foo = codebase.assertClass("test.pkg.Foo")
            assertThat(foo.properties()).hasSize(1)

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
        runInputOutputTest(api, kotlinStyleFormat) {
            val foo = codebase.assertClass("test.pkg.Foo")
            assertThat(foo.fields()).hasSize(1)

            val field = foo.fields().single()
            assertThat(field.name()).isEqualTo("foo")
            assertThat(field.type().isString()).isTrue()
            assertThat(field.modifiers.getVisibilityLevel()).isEqualTo(VisibilityLevel.PROTECTED)
            assertThat(field.legacyInitialValue()).isNull()
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
        runInputOutputTest(api, kotlinStyleFormat) {
            val foo = codebase.assertClass("test.pkg.Foo")
            assertThat(foo.fields()).hasSize(1)

            val field = foo.fields().single()
            assertThat(field.name()).isEqualTo("foo")
            assertThat(field.type().isString()).isTrue()
            assertThat(field.modifiers.getVisibilityLevel()).isEqualTo(VisibilityLevel.PUBLIC)
            assertThat(field.modifiers.isStatic()).isTrue()
            assertThat(field.legacyInitialValue()).isEqualTo("hi")
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
        runInputOutputTest(api, kotlinStyleFormat) {
            val foo = codebase.assertClass("test.pkg.Foo")
            assertThat(foo.methods()).hasSize(1)

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
        runInputOutputTest(api, kotlinStyleFormat) {
            val foo = codebase.assertClass("test.pkg.Foo")
            assertThat(foo.methods()).hasSize(1)

            val method = foo.methods().single()
            assertThat(method.name()).isEqualTo("foo")
            assertThat(method.modifiers.getVisibilityLevel()).isEqualTo(VisibilityLevel.PUBLIC)
            assertThat((method.returnType() as PrimitiveTypeItem).kind)
                .isEqualTo(PrimitiveTypeItem.Primitive.VOID)
            assertThat(method.parameters()).isEmpty()

            assertThat(method.throwsTypes()).hasSize(1)
            assertThat(method.throwsTypes().single().toTypeString())
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
        runInputOutputTest(api, kotlinStyleFormat) {
            val foo = codebase.assertClass("test.pkg.Foo")
            assertThat(foo.methods()).hasSize(1)

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
        runInputOutputTest(api, kotlinStyleFormat) {
            val foo = codebase.assertClass("test.pkg.Foo")
            val method = foo.methods().single()

            assertThat(method.parameters()).hasSize(1)
            val param = method.parameters().single()
            assertThat(param.name()).isEqualTo("arg")
            assertThat(param.publicName()).isEqualTo("arg")
            assertThat((param.type() as PrimitiveTypeItem).kind)
                .isEqualTo(PrimitiveTypeItem.Primitive.INT)
        }
    }

    @Test
    fun `Test method with one named parameter with default value`() {
        val api =
            """
                package test.pkg {
                  public class Foo {
                    method public foo(optional arg: int): String;
                  }
                }
            """
                .trimIndent()
        runInputOutputTest(api, kotlinStyleFormat) {
            val foo = codebase.assertClass("test.pkg.Foo")
            val method = foo.methods().single()

            assertThat(method.parameters()).hasSize(1)
            val param = method.parameters().single()
            assertThat(param.name()).isEqualTo("arg")
            assertThat(param.publicName()).isEqualTo("arg")
            assertThat((param.type() as PrimitiveTypeItem).kind)
                .isEqualTo(PrimitiveTypeItem.Primitive.INT)

            assertThat(param.hasDefaultValue()).isTrue()
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
        runInputOutputTest(api, kotlinStyleFormat) {
            val foo = codebase.assertClass("test.pkg.Foo")
            val method = foo.methods().single()

            assertThat(method.parameters()).hasSize(1)
            val param = method.parameters().single()
            assertThat(param.name()).isEqualTo("vals")
            assertThat(param.publicName()).isEqualTo("vals")

            assertThat((param.type() as ArrayTypeItem).isVarargs).isTrue()
            assertThat(param.isVarArgs()).isTrue()
            assertThat(param.modifiers.isVarArg()).isTrue()
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
        runInputOutputTest(api, kotlinStyleFormat) {
            val foo = codebase.assertClass("test.pkg.Foo")
            val method = foo.methods().single()

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
        runInputOutputTest(api, kotlinStyleFormat) {
            val foo = codebase.assertClass("test.pkg.Foo")
            val method = foo.methods().single()

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
                    method public foo(i: int, map: java.util.Map<java.lang.String,java.lang.Object>, arr: String[]): String;
                  }
                }
            """
                .trimIndent()
        runInputOutputTest(api, kotlinStyleFormat) {
            val foo = codebase.assertClass("test.pkg.Foo")
            val method = foo.methods().single()

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
            assertThat(mapType.arguments).hasSize(2)
            assertThat(mapType.arguments[0].isString()).isTrue()
            assertThat(mapType.arguments[1].isJavaLangObject()).isTrue()

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
                    method public foo(_: int, _: java.util.Map<java.lang.String,java.lang.Object>, _: String[]): String;
                  }
                }
            """
                .trimIndent()
        runInputOutputTest(api, kotlinStyleFormat) {
            val foo = codebase.assertClass("test.pkg.Foo")
            val method = foo.methods().single()

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
            assertThat(mapType.arguments).hasSize(2)
            assertThat(mapType.arguments[0].isString()).isTrue()
            assertThat(mapType.arguments[1].isJavaLangObject()).isTrue()

            // _: String[]
            val p2 = method.parameters()[2]
            assertThat(p2.name()).isEqualTo("_")
            assertThat(p2.publicName()).isNull()
            assertThat((p2.type() as ArrayTypeItem).componentType.isString()).isTrue()
        }
    }

    @Test
    fun `Type use annotations`() {
        val format = kotlinStyleFormat.copy(includeTypeUseAnnotations = true)
        val api =
            """
                package test.pkg {
                  public class MyTest {
                    method public abstract getParameterAnnotations(): java.lang.annotation.@C Annotation? @A [] @B []!;
                  }
                }
            """
                .trimIndent()
        runInputOutputTest(api, format) {
            val method = codebase.assertClass("test.pkg.MyTest").methods().single()
            // Return type has platform nullability
            assertThat(method.returnType().modifiers.isPlatformNullability).isTrue()

            val annotationArrayArray = method.returnType()
            assertThat(annotationArrayArray).isInstanceOf(ArrayTypeItem::class.java)
            assertThat(annotationArrayArray.modifiers.annotations.map { it.qualifiedName })
                .containsExactly("androidx.annotation.A")

            val annotationArray = (annotationArrayArray as ArrayTypeItem).componentType
            assertThat(annotationArray).isInstanceOf(ArrayTypeItem::class.java)
            assertThat(annotationArray.modifiers.annotations.map { it.qualifiedName })
                .containsExactly("androidx.annotation.B")

            val annotation = (annotationArray as ArrayTypeItem).componentType
            assertThat(annotation).isInstanceOf(ClassTypeItem::class.java)
            assertThat((annotation as ClassTypeItem).qualifiedName)
                .isEqualTo("java.lang.annotation.Annotation")
            assertThat(annotation.modifiers.annotations.map { it.qualifiedName })
                .containsExactly("androidx.annotation.C")
        }
    }

    @Test
    fun `Type-use annotations in implements and extends section`() {
        val format = kotlinStyleFormat.copy(includeTypeUseAnnotations = true)
        val api =
            """
                package test.pkg {
                  public class Foo extends test.pkg.@test.pkg.A Baz implements test.pkg.@test.pkg.B Bar {
                  }
                }
            """
                .trimIndent()
        runInputOutputTest(api, format) {
            val fooClass = codebase.assertClass("test.pkg.Foo")
            val superClassType = fooClass.superClassType()
            assertThat(superClassType!!.modifiers.annotations.map { it.qualifiedName })
                .containsExactly("test.pkg.A")
            val interfaceType = fooClass.interfaceTypes().single()
            assertThat(interfaceType.modifiers.annotations.map { it.qualifiedName })
                .containsExactly("test.pkg.B")
        }
    }

    @Test
    fun `Test generic super class with nullable type`() {
        val api =
            """
                package test.pkg {
                  public interface Foo extends kotlin.collections.List<java.lang.String?> {
                  }
                }
            """
                .trimIndent()
        runInputOutputTest(api, kotlinStyleFormat)
    }

    @Test
    fun `Test generic super interface with nullable type`() {
        val api =
            """
                package test.pkg {
                  public class Foo implements kotlin.collections.List<java.lang.String?> {
                  }
                }
            """
                .trimIndent()
        runInputOutputTest(api, kotlinStyleFormat)
    }

    @Test
    fun `Test property receivers, java name-type order`() {
        val api =
            """
                package test.pkg {
                  public class Foo {
                    property public int int.intProperty;
                    property public boolean String?.nullableStringProperty;
                    property public boolean String[].stringArrayProperty;
                    property public boolean java.util.List<java.lang.String>.stringListProperty;
                    property public static int String.stringProperty;
                  }
                }
            """
        runInputOutputTest(api, FileFormat.V4)
    }

    @Test
    fun `Test property receivers, kotlin name-type order`() {
        val api =
            """
                package test.pkg {
                  public class Foo {
                    property public int.intProperty: int;
                    property public String?.nullableStringProperty: boolean;
                    property public String[].stringArrayProperty: boolean;
                    property public java.util.List<java.lang.String>.stringListProperty: boolean;
                    property public static String.stringProperty: int;
                  }
                }
            """
        runInputOutputTest(api, kotlinStyleFormat)
    }

    @Test
    fun `Test property type parameters, java name-type order`() {
        // A property with type parameters must have a receiver
        val api =
            """
                package test.pkg {
                  public class Foo {
                    property public static <T> int java.util.List<? extends T>.oneTypeParameterListReceiver;
                    property public static <T> int T.oneTypeParameterReceiver;
                    property public static <T extends java.lang.String> int T.oneTypeParameterWithBoundsReceiver;
                    property public static <T1, T2> int java.util.Map<T1,? extends T2>.twoTypeParameterMapReceiver;
                    property public static <T1 extends java.lang.String, T2 extends java.util.List<? extends T1>> int java.util.Map<T1,? extends T2>.twoTypeParameterWithBoundsMapReceiver;
                  }
                }
            """
        runInputOutputTest(api, FileFormat.V4)
    }

    @Test
    fun `Test property type parameters, kotlin name-type order`() {
        // A property with type parameters must have a receiver
        val api =
            """
                package test.pkg {
                  public class Foo {
                    property public static <T> java.util.List<? extends T>.oneTypeParameterListReceiver: int;
                    property public static <T> T.oneTypeParameterReceiver: int;
                    property public static <T extends java.lang.String> T.oneTypeParameterWithBoundsReceiver: int;
                    property public static <T1, T2> java.util.Map<T1,? extends T2>.twoTypeParameterMapReceiver: int;
                    property public static <T1 extends java.lang.String, T2 extends java.util.List<? extends T1>> java.util.Map<T1,? extends T2>.twoTypeParameterWithBoundsMapReceiver: int;
                  }
                }
            """
        runInputOutputTest(api, kotlinStyleFormat)
    }

    @Test
    fun `Test normalize-final-modifier=yes`() {
        runInputOutputTest(
            """
                package test.pkg {
                  public final class Final {
                    method public final void foo();
                  }
                  public class NotFinal {
                    method public final void foo();
                  }
                }
            """,
            FileFormat.V2.copy(specifiedNormalizeFinalModifier = true),
            expectedOutput =
                """
                    package test.pkg {
                      public final class Final {
                        method public void foo();
                      }
                      public class NotFinal {
                        method public final void foo();
                      }
                    }
                """,
        )
    }

    @Test
    fun `Test normalize-final-modifier=no`() {
        runInputOutputTest(
            """
                package test.pkg {
                  public final class Final {
                    method public final void foo();
                  }
                  public class NotFinal {
                    method public final void foo();
                  }
                }
            """,
            FileFormat.V2.copy(specifiedNormalizeFinalModifier = false),
        )
    }

    /**
     * Make sure that despite the `java.lang.` prefix being stripped from various types when writing
     * the signature file that they have the correct type when the [Codebase] is loaded.
     */
    private fun checkStrippedCodebaseTypes(codebase: Codebase) {
        val fooClass = codebase.assertClass("test.pkg.Foo")
        val superTypes = listOfNotNull(fooClass.superClassType()) + fooClass.interfaceTypes()
        assertThat(superTypes.joinToString { it.toTypeString() })
            .isEqualTo(
                "java.util.AbstractList<java.lang.String>, java.lang.Comparable<java.lang.String>, kotlin.collections.List<java.lang.String>"
            )

        val fooMethod = fooClass.methods().single()
        assertThat(fooMethod.returnType().toTypeString()).isEqualTo("java.lang.String")
        assertThat(fooMethod.parameters().single().type().toTypeString())
            .isEqualTo("java.lang.String...")
        assertThat(fooMethod.throwsTypes().single().toTypeString()).isEqualTo("java.lang.Exception")
    }

    @Test
    fun `Test strip-java-lang-prefix=never`() {
        val api =
            """
                // Signature format: 2.0
                package test.pkg {
                  public class Foo<T extends java.util.Map<java.lang.Integer, java.lang.String>> extends java.util.AbstractList<java.lang.String> implements java.lang.Comparable<java.lang.String> kotlin.collections.List<java.lang.String> {
                    method public java.lang.String foo(java.lang.String...) throws java.lang.Exception;
                  }
                }
            """
                .trimIndent()
        runInputOutputTest(
            api,
            FileFormat.V2.copy(specifiedStripJavaLangPrefix = StripJavaLangPrefix.NEVER)
        ) {
            checkStrippedCodebaseTypes(codebase)
        }
    }

    @Test
    fun `Test strip-java-lang-prefix=legacy`() {
        val api =
            """
                // Signature format: 2.0
                package test.pkg {
                  public class Foo<T extends java.util.Map<java.lang.Integer, java.lang.String>> extends java.util.AbstractList<java.lang.String> implements java.lang.Comparable<java.lang.String> kotlin.collections.List<java.lang.String> {
                    method public String foo(java.lang.String...) throws java.lang.Exception;
                  }
                }
            """
                .trimIndent()
        runInputOutputTest(
            api,
            FileFormat.V2.copy(specifiedStripJavaLangPrefix = StripJavaLangPrefix.LEGACY)
        ) {
            checkStrippedCodebaseTypes(codebase)
        }
    }

    @Test
    fun `Test strip-java-lang-prefix=always`() {
        val api =
            """
                // Signature format: 2.0
                package test.pkg {
                  public abstract class Foo<T extends java.util.Map<Integer, String>> extends java.util.AbstractList<String> implements Comparable<String> kotlin.collections.List<String> {
                    method public String foo(String...) throws Exception;
                  }
                }
            """
                .trimIndent()
        runInputOutputTest(
            api,
            FileFormat.V2.copy(specifiedStripJavaLangPrefix = StripJavaLangPrefix.ALWAYS)
        ) {
            checkStrippedCodebaseTypes(codebase)
        }
    }

    @Test
    fun `Test type-argument-spacing=none`() {
        val api =
            """
                // Signature format: 2.0
                package test.pkg {
                  public interface Foo<T extends java.util.Map<Integer,String>> extends java.util.Map<String,Integer> {
                    method public java.util.Map<String,String> foo(java.util.Map<Integer,Integer>);
                  }
                }
            """
                .trimIndent()
        runInputOutputTest(
            api,
            FileFormat.V2.copy(
                specifiedTypeArgumentSpacing = TypeArgumentSpacing.NONE,
                // Strip java.lang. prefix to make test less verbose.
                specifiedStripJavaLangPrefix = StripJavaLangPrefix.ALWAYS,
            ),
        )
    }

    @Test
    fun `Test type-argument-spacing=legacy`() {
        val api =
            """
                // Signature format: 2.0
                package test.pkg {
                  public interface Foo<T extends java.util.Map<Integer, String>> extends java.util.Map<String,Integer> {
                    method public java.util.Map<String,String> foo(java.util.Map<Integer,Integer>);
                  }
                }
            """
                .trimIndent()
        runInputOutputTest(
            api,
            FileFormat.V2.copy(
                specifiedTypeArgumentSpacing = TypeArgumentSpacing.LEGACY,
                // Strip java.lang. prefix to make test less verbose.
                specifiedStripJavaLangPrefix = StripJavaLangPrefix.ALWAYS,
            ),
        )
    }

    @Test
    fun `Test type-argument-spacing=space`() {
        val api =
            """
                // Signature format: 2.0
                package test.pkg {
                  public interface Foo<T extends java.util.Map<Integer, String>> extends java.util.Map<String, Integer> {
                    method public java.util.Map<String, String> foo(java.util.Map<Integer, Integer>);
                  }
                }
            """
                .trimIndent()
        runInputOutputTest(
            api,
            FileFormat.V2.copy(
                specifiedTypeArgumentSpacing = TypeArgumentSpacing.SPACE,
                // Strip java.lang. prefix to make test less verbose.
                specifiedStripJavaLangPrefix = StripJavaLangPrefix.ALWAYS,
            ),
        )
    }

    @Test
    fun `Check order of SuppressCompatibility annotation`() {
        val api =
            """
                // Signature format: 5.0
                package test.pkg {
                  @SuppressCompatibility @kotlin.RequiresOptIn @kotlin.annotation.Retention(kotlin.annotation.AnnotationRetention.BINARY) @kotlin.annotation.Target(allowedTargets={kotlin.annotation.AnnotationTarget.CLASS, kotlin.annotation.AnnotationTarget.FUNCTION}) public @interface ExperimentalBar {
                  }
                  @SuppressCompatibility @test.pkg.ExperimentalBar public final class FancyBar {
                    ctor public FancyBar();
                    method @SuppressCompatibility @ReturnThis public test.pkg.FancyBar fancy(@SuppressCompatibility int);
                  }
                }
            """
        runInputOutputTest(api, FileFormat.V5)
    }

    @Test
    fun `Check loading signature file with duplicate method signatures`() {
        val api =
            """
                // Signature format: 5.0
                package test.pkg {
                  public class Foo {
                    method public void method(int);
                    method public void method(int);
                  }
                }
            """
        runInputOutputTest(api, FileFormat.V5)
    }

    companion object {
        private val kotlinStyleFormat =
            FileFormat.V5.copy(kotlinNameTypeOrder = true, formatDefaults = FileFormat.V5)
    }
}
