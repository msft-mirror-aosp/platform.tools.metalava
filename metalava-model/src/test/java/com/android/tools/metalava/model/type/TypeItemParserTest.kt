/*
 * Copyright (C) 2018 The Android Open Source Project
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

package com.android.tools.metalava.model.type

import com.android.tools.metalava.model.AnnotationContext
import com.android.tools.metalava.model.ArrayTypeItem
import com.android.tools.metalava.model.ClassTypeItem
import com.android.tools.metalava.model.TypeItem
import com.android.tools.metalava.model.TypeParameterScope
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class TypeItemParserTest {
    // This context is needed because this test compares types with annotations that have
    // been created from text. Comparing those annotations requires comparing the value of
    // the annotation attributes. Getting an attribute value requires resolving the
    // annotation class in order to find the attribute type so that the value can be
    // converted into the correct type. The default context throws an exception when
    // resolving the annotation class. This one returns `null` when resolving the annotation
    // class which just means the value type will be determined from the text.
    private val annotationContext = AnnotationContext.DEFAULT_RESOLVE_NULL

    private val typeParser =
        LegacyTypeItemParser(
            annotationContext,
            UnqualifiedClassHandler.PREFIX_WITH_JAVA_LANG_OR_REPORT_ERROR,
        )

    private fun parseType(type: String) =
        typeParser.obtainTypeFromString(type, TypeParameterScope.empty)

    /**
     * Tests that [inputType] is parsed as an [ArrayTypeItem] with component type equal to
     * [expectedInnerType] and vararg iff [expectedVarargs] is true.
     */
    private fun testArrayType(
        inputType: String,
        expectedInnerType: TypeItem,
        expectedVarargs: Boolean
    ) {
        val type = parseType(inputType)
        assertThat(type).isInstanceOf(ArrayTypeItem::class.java)
        assertThat((type as ArrayTypeItem).componentType).isEqualTo(expectedInnerType)
        assertThat(type.isVarargs).isEqualTo(expectedVarargs)
    }

    @Test
    fun `Test parsing of array types with annotations`() {
        testArrayType(
            inputType = "test.pkg.@A @B Foo @B @C []",
            expectedInnerType = parseType("test.pkg.@A @B Foo"),
            expectedVarargs = false
        )
        testArrayType(
            inputType = "java.lang.annotation.@NonNull Annotation @NonNull []",
            expectedInnerType = parseType("java.lang.annotation.@NonNull Annotation"),
            expectedVarargs = false
        )
        testArrayType(
            inputType = "char @NonNull []",
            expectedInnerType = parseType("char"),
            expectedVarargs = false
        )
    }

    /**
     * Tests that [inputType] is parsed as a [ClassTypeItem] with qualified name equal to
     * [expectedQualifiedName] and [ClassTypeItem.arguments] is equal to [expectedTypeArguments].
     */
    private fun testClassType(
        inputType: String,
        expectedQualifiedName: String,
        expectedTypeArguments: List<TypeItem>
    ) {
        val type = parseType(inputType)
        assertThat(type).isInstanceOf(ClassTypeItem::class.java)
        assertThat((type as ClassTypeItem).qualifiedName).isEqualTo(expectedQualifiedName)
        assertThat(type.arguments).isEqualTo(expectedTypeArguments)
    }

    @Test
    fun `Test parsing of abbreviated java lang types`() {
        testClassType(
            inputType = "String",
            expectedQualifiedName = "java.lang.String",
            expectedTypeArguments = emptyList()
        )
        testArrayType(
            inputType = "String[]",
            expectedInnerType = parseType("java.lang.String"),
            expectedVarargs = false
        )
        testArrayType(
            inputType = "String...",
            expectedInnerType = parseType("java.lang.String"),
            expectedVarargs = true
        )
    }

    @Test
    fun `Test parsing of class types with annotations`() {
        testClassType(
            inputType = "@A @B test.pkg.Foo",
            expectedQualifiedName = "test.pkg.Foo",
            expectedTypeArguments = emptyList()
        )
        testClassType(
            inputType = "@A @B test.pkg.Foo",
            expectedQualifiedName = "test.pkg.Foo",
            expectedTypeArguments = emptyList()
        )
        testClassType(
            inputType = "java.lang.annotation.@NonNull Annotation",
            expectedQualifiedName = "java.lang.annotation.Annotation",
            expectedTypeArguments = emptyList()
        )
        testClassType(
            inputType = "java.util.Map.@NonNull Entry<a.A,b.B>",
            expectedQualifiedName = "java.util.Map.Entry",
            expectedTypeArguments = listOf(parseType("a.A"), parseType("b.B"))
        )
        testClassType(
            inputType = "java.util.@NonNull Set<java.util.Map.@NonNull Entry<a.A,b.B>>",
            expectedQualifiedName = "java.util.Set",
            expectedTypeArguments = listOf(parseType("java.util.Map.@NonNull Entry<a.A,b.B>"))
        )
    }
}
