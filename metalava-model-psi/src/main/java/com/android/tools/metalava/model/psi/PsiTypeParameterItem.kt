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

package com.android.tools.metalava.model.psi

import com.android.tools.metalava.model.TypeParameterItem
import com.android.tools.metalava.model.VariableTypeItem
import com.intellij.psi.PsiTypeParameter
import org.jetbrains.kotlin.asJava.elements.KotlinLightTypeParameterBuilder
import org.jetbrains.kotlin.asJava.elements.KtLightDeclaration
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtTypeParameter

internal class PsiTypeParameterItem(
    codebase: PsiBasedCodebase,
    private val psiClass: PsiTypeParameter,
    private val name: String,
    modifiers: PsiModifierItem
) :
    PsiItem(
        codebase = codebase,
        element = psiClass,
        modifiers = modifiers,
        documentation = "",
    ),
    TypeParameterItem {

    override fun simpleName() = name

    override fun toType(): VariableTypeItem {
        return codebase.getType(codebase.getClassType(psiClass)) as VariableTypeItem
    }

    override fun psi() = psiClass

    override fun typeBounds(): List<PsiTypeItem> = bounds

    override fun isReified(): Boolean {
        return isReified(psiClass as? PsiTypeParameter)
    }

    private lateinit var bounds: List<PsiTypeItem>

    override fun finishInitialization() {
        super.finishInitialization()

        val refs = psiClass.extendsList.referencedTypes
        bounds =
            if (refs.isEmpty()) {
                emptyList()
            } else {
                refs.mapNotNull { codebase.getType(it) }
            }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is TypeParameterItem) return false

        return name == other.simpleName()
    }

    override fun hashCode(): Int {
        return name.hashCode()
    }

    companion object {
        fun create(codebase: PsiBasedCodebase, psiClass: PsiTypeParameter): PsiTypeParameterItem {
            val simpleName = psiClass.name!!
            val modifiers = modifiers(codebase, psiClass, "")

            val item =
                PsiTypeParameterItem(
                    codebase = codebase,
                    psiClass = psiClass,
                    name = simpleName,
                    modifiers = modifiers
                )
            item.modifiers.setOwner(item)
            item.finishInitialization()
            return item
        }

        fun isReified(element: PsiTypeParameter?): Boolean {
            element ?: return false
            // TODO(jsjeon): Handle PsiElementWithOrigin<*> when available
            if (
                element is KtLightDeclaration<*, *> &&
                    element.kotlinOrigin is KtTypeParameter &&
                    element.kotlinOrigin?.text?.startsWith(KtTokens.REIFIED_KEYWORD.value) == true
            ) {
                return true
            } else if (
                element is KotlinLightTypeParameterBuilder &&
                    element.origin.text.startsWith(KtTokens.REIFIED_KEYWORD.value)
            ) {
                return true
            }
            return false
        }
    }
}
