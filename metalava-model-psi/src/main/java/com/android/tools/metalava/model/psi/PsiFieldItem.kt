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

package com.android.tools.metalava.model.psi

import com.android.tools.metalava.model.ApiVariantSelectors
import com.android.tools.metalava.model.BaseModifierList
import com.android.tools.metalava.model.ClassItem
import com.android.tools.metalava.model.ClassKind
import com.android.tools.metalava.model.FieldItem
import com.android.tools.metalava.model.ItemDocumentationFactory
import com.android.tools.metalava.model.PropertyItem
import com.android.tools.metalava.model.TypeItem
import com.android.tools.metalava.model.TypeNullability
import com.android.tools.metalava.model.VisibilityLevel
import com.android.tools.metalava.model.isNonNullAnnotation
import com.android.tools.metalava.model.item.DefaultFieldItem
import com.android.tools.metalava.model.item.FieldValue
import com.intellij.psi.PsiCallExpression
import com.intellij.psi.PsiClassType
import com.intellij.psi.PsiEnumConstant
import com.intellij.psi.PsiField
import com.intellij.psi.PsiModifierListOwner
import com.intellij.psi.PsiPrimitiveType
import com.intellij.psi.PsiReference
import com.intellij.psi.impl.JavaConstantExpressionEvaluator

internal class PsiFieldItem(
    override val codebase: PsiBasedCodebase,
    private val psiField: PsiField,
    modifiers: BaseModifierList,
    documentationFactory: ItemDocumentationFactory,
    name: String,
    containingClass: ClassItem,
    type: TypeItem,
    private val isEnumConstant: Boolean,
    override val legacyFieldValue: FieldValue?,
) :
    DefaultFieldItem(
        codebase = codebase,
        fileLocation = PsiFileLocation(psiField),
        itemLanguage = psiField.itemLanguage,
        modifiers = modifiers,
        documentationFactory = documentationFactory,
        variantSelectorsFactory = ApiVariantSelectors.MUTABLE_FACTORY,
        name = name,
        containingClass = containingClass,
        type = type,
        isEnumConstant = isEnumConstant,
        legacyFieldValue = legacyFieldValue,
    ),
    FieldItem,
    PsiItem {

    override fun psi(): PsiField = psiField

    override var property: PropertyItem? = null

    override fun duplicate(targetContainingClass: ClassItem) =
        create(
                codebase,
                targetContainingClass,
                psiField,
                codebase.globalTypeItemFactory.from(targetContainingClass),
            )
            .also { duplicated -> duplicated.inheritedFrom = containingClass() }

    companion object {
        internal fun create(
            codebase: PsiBasedCodebase,
            containingClass: ClassItem,
            psiField: PsiField,
            enclosingClassTypeItemFactory: PsiTypeItemFactory,
        ): PsiFieldItem {
            val name = psiField.name
            val modifiers = PsiModifierItem.create(codebase, psiField)

            if (containingClass.classKind == ClassKind.INTERFACE) {
                // All interface fields are implicitly public and static.
                modifiers.setVisibilityLevel(VisibilityLevel.PUBLIC)
                modifiers.setStatic(true)
            }

            val isEnumConstant = psiField is PsiEnumConstant

            // Wrap the PsiField in a PsiFieldValue that can provide the field's initial value.
            val fieldValue = PsiFieldValue(psiField)

            // Create a type for the field, taking into account the modifiers, whether it is an
            // enum constant and whether the field's initial value is non-null.
            val fieldType =
                enclosingClassTypeItemFactory.getFieldType(
                    underlyingType = PsiTypeInfo(psiField.type, psiField),
                    itemAnnotations = modifiers.annotations(),
                    isEnumConstant = isEnumConstant,
                    isFinal = modifiers.isFinal(),
                    isInitialValueNonNull = {
                        // The initial value is non-null if the field initializer is a method that
                        // is annotated as being non-null so would produce a non-null value, or the
                        // value is a literal which is not null.
                        psiField.isFieldInitializerNonNull() ||
                            fieldValue.initialValue(false) != null
                    },
                )

            return PsiFieldItem(
                codebase = codebase,
                psiField = psiField,
                documentationFactory = PsiItemDocumentation.factory(psiField, codebase),
                modifiers = modifiers,
                name = name,
                containingClass = containingClass,
                type = fieldType,
                isEnumConstant = isEnumConstant,
                legacyFieldValue = fieldValue
            )
        }
    }
}

/**
 * Check to see whether the [PsiField] on which this is called has an initializer whose
 * [TypeNullability] is known to be [TypeNullability.NONNULL].
 */
private fun PsiField.isFieldInitializerNonNull(): Boolean {
    // If we're looking at a final field, look on the right hand side of the field to the
    // field initialization. If that right hand side for example represents a method call,
    // and the method we're calling is annotated with @NonNull, then the field (since it is
    // final) will always be @NonNull as well.
    val resolved =
        when (val initializer = initializer) {
            is PsiReference -> {
                initializer.resolve()
            }
            is PsiCallExpression -> {
                initializer.resolveMethod()
            }
            else -> null
        } ?: return false

    return resolved is PsiModifierListOwner &&
        resolved.annotations.any { isNonNullAnnotation(it.qualifiedName ?: "") }
}

/**
 * Wrapper around a [PsiField] that will provide access to the initial value of the field, if
 * available, or `null` otherwise.
 */
class PsiFieldValue(private val psiField: PsiField) : FieldValue {

    override fun initialValue(requireConstant: Boolean): Any? {
        val constant = psiField.computeConstantValue()
        // Offset [ClsFieldImpl#computeConstantValue] for [TYPE] field in boxed primitive types.
        // Those fields hold [Class] object, but the constant value should not be of [PsiType].
        if (
            constant is PsiPrimitiveType &&
                psiField.name == "TYPE" &&
                (psiField.type as? PsiClassType)?.computeQualifiedName() == "java.lang.Class"
        ) {
            return null
        }
        if (constant != null) {
            return constant
        }

        return if (!requireConstant) {
            val initializer = psiField.initializer ?: return null
            JavaConstantExpressionEvaluator.computeConstantExpression(initializer, false)
        } else {
            null
        }
    }
}
