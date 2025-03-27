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

package com.android.tools.metalava.model.psi

import com.android.tools.lint.detector.api.ConstantEvaluator
import com.android.tools.metalava.model.AnnotationItem
import com.android.tools.metalava.model.FieldItem
import com.android.tools.metalava.model.MethodItem
import com.android.tools.metalava.model.TypeItem
import com.android.tools.metalava.model.VariableTypeItem
import com.android.tools.metalava.model.type.ContextNullability
import com.android.tools.metalava.model.value.CachingAnnotationValueProvider
import com.android.tools.metalava.model.value.CachingValueProvider
import com.android.tools.metalava.model.value.ClassObjectValue
import com.android.tools.metalava.model.value.CombinedValueProvider
import com.android.tools.metalava.model.value.ConstantValue
import com.android.tools.metalava.model.value.ImplementationValueToModelFactory
import com.android.tools.metalava.model.value.Value
import com.android.tools.metalava.model.value.ValueFactory
import com.android.tools.metalava.model.value.ValueProvider
import com.android.tools.metalava.model.value.ValueProviderException
import com.intellij.psi.PsiAnnotationMemberValue
import com.intellij.psi.PsiClassObjectAccessExpression
import com.intellij.psi.PsiLiteralExpression
import org.jetbrains.uast.UExpression
import org.jetbrains.uast.ULiteralExpression

/**
 * Creates [ValueProvider]s that will delegate to [implementationValueToModelValue] to create
 * [Value]s when requested.
 *
 * @param globalTypeItemFactory the global [PsiTypeItemFactory] used for creating [TypeItem]s for
 *   [ClassObjectValue]s. It uses the global factory as the types will never be [VariableTypeItem]s
 *   and so there is no need to use a factory that has access to the in scope type parameters.
 */
internal class PsiValueFactory(
    private val globalTypeItemFactory: PsiTypeItemFactory,
) : ValueFactory, ImplementationValueToModelFactory<Any> {
    /**
     * Get a [CombinedValueProvider] that will create (and cache) a [Value] of [typeItem] from
     * [anyValue].
     *
     * @param typeItem the required type for the value, e.g. [MethodItem.returnType] or
     *   [FieldItem.type].
     * @param anyValue the underlying Psi specific value. It is of type [Any] to avoid having to
     *   duplicate everything for [UExpression] and [PsiAnnotationMemberValue].
     */
    fun providerFor(typeItem: TypeItem, anyValue: Any): CombinedValueProvider =
        CachingValueProvider(this, typeItem, anyValue)

    /**
     * Get a [CombinedValueProvider] that will create (and cache) a [Value] for attribute
     * [attributeName] of [annotationItem] from [anyValue].
     *
     * @param annotationItem the containing [AnnotationItem].
     * @param attributeName the name of the attribute whose value it will provide.
     * @param anyValue the underlying Psi specific value. It is of type [Any] to avoid having to
     *   duplicate everything for [UExpression] and [PsiAnnotationMemberValue].
     */
    fun providerForAnnotationValue(
        annotationItem: AnnotationItem,
        attributeName: String,
        anyValue: Any
    ): CombinedValueProvider =
        CachingAnnotationValueProvider(this, annotationItem, attributeName, anyValue)

    /**
     * Create a [Value] of [optionalTypeItem] from [implementationValue].
     *
     * Uses [Any] to avoid having to duplicate everything for [UExpression] and
     * [PsiAnnotationMemberValue].
     */
    override fun implementationValueToModelValue(
        optionalTypeItem: TypeItem?,
        implementationValue: Any,
    ): Value {
        return when (implementationValue) {
            is UExpression -> uExpressionToValue(optionalTypeItem, implementationValue)
            is PsiAnnotationMemberValue -> psiToValue(optionalTypeItem, implementationValue)
            else ->
                throw ValueProviderException(
                    "Unknown value '$implementationValue' of ${implementationValue.javaClass} for type $optionalTypeItem"
                )
        }
    }

    /** Create a [Value] of [optionalTypeItem] from [uExpression]. */
    private fun uExpressionToValue(optionalTypeItem: TypeItem?, uExpression: UExpression): Value {
        if (uExpression is ULiteralExpression) {
            uExpression.value?.let { underlyingValue ->
                // Check to see if the underlying value has been already been cast from the source
                // literal type to a type appropriate for where it is being used. If it has then
                // reverse the cast to preserve the information about the source literal type. That
                // is needed to enable consistent processing with legacy value handling which often
                // uses the source type directly, e.g. when parsing `longValue = 1` it may write it
                // as `longValue = 1` instead of the more consistent `longValue = 1L`.
                val transformedValue =
                    if (underlyingValue is Long) {
                        uExpression.sourcePsi?.text?.let { text ->
                            // If the text ends with `L` or `l` then it was a long literal so keep
                            // it as such.
                            if (text.endsWith("L") || text.endsWith("l")) underlyingValue
                            else {
                                // Otherwise, try and see if it can be cast to an int without loss.
                                // If it can then use the int, otherwise keep the long.
                                val asInt = underlyingValue.toInt()
                                if (asInt.toLong() == underlyingValue) asInt else underlyingValue
                            }
                        } ?: underlyingValue
                    } else underlyingValue
                return createLiteralValue(optionalTypeItem, transformedValue)
            }
        }

        // All others expressions are evaluated to a literal, if possible and returned.
        ConstantEvaluator.evaluate(null, uExpression)?.let { value ->
            return createLiteralValue(optionalTypeItem, value)
        }

        // Drop through to throw an exception to document why it failed.
        throw ValueProviderException(
            "Unknown value '$uExpression' of ${uExpression.javaClass} for type $optionalTypeItem"
        )
    }

    /** Create a [Value] of [optionalTypeItem] from [psiValue]. */
    private fun psiToValue(
        optionalTypeItem: TypeItem?,
        psiValue: PsiAnnotationMemberValue,
    ): Value {
        when (psiValue) {
            // Class literal, e.g. `SomeClass.class`.
            is PsiClassObjectAccessExpression -> {
                // Get the type of the class literal. e.g. if the expression was `X.class` then this
                // will be of type `X`, or if the expression was of type `X[].class` then this will
                // be of type `X[]`. `X` may be a primitive type.
                val classLiteralTypeItem =
                    globalTypeItemFactory.getType(
                        psiValue.operand.type,
                        contextNullability = ContextNullability.forceNonNull,
                    )

                return createClassObjectValue(classLiteralTypeItem)
            }
        }

        // All others drop through.
        return psiToConstant(optionalTypeItem, psiValue)
    }

    /** Create a [ConstantValue] of [optionalTypeItem] from [psiValue]. */
    private fun psiToConstant(
        optionalTypeItem: TypeItem?,
        psiValue: PsiAnnotationMemberValue,
    ): ConstantValue {
        // Literal primitive or String.
        if (psiValue is PsiLiteralExpression) {
            return psiValue.value?.let { underlyingValue ->
                createLiteralValue(optionalTypeItem, underlyingValue)
            }
                ?: error(
                    "Unknown value '$psiValue' of ${psiValue.javaClass} for type $optionalTypeItem"
                )
        }

        // All others expressions are evaluated to a literal, if possible and returned.
        ConstantEvaluator.evaluate(null, psiValue)?.let { value ->
            return createLiteralValue(optionalTypeItem, value)
        }

        // Drop through to throw an exception to document why it failed.
        throw ValueProviderException(
            "Unknown value '$psiValue' of ${psiValue.javaClass} for type $optionalTypeItem"
        )
    }
}
