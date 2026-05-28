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

package com.android.tools.metalava.model.psi

import com.android.tools.metalava.model.ANNOTATION_ATTR_VALUE
import com.android.tools.metalava.model.AnnotationItem
import com.android.tools.metalava.model.CallableBody
import com.android.tools.metalava.model.CallableItem
import com.android.tools.metalava.model.ClassItem
import com.android.tools.metalava.model.value.FieldReferenceValue
import com.android.tools.metalava.reporter.Issues
import com.intellij.psi.JavaRecursiveElementVisitor
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiField
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiModifier
import com.intellij.psi.PsiReferenceExpression
import com.intellij.psi.PsiReturnStatement

internal class PsiCallableBody(
    private val psiCodebase: PsiBasedCodebase,
    private val callable: CallableItem,
    private val psiMethod: PsiMethod,
) : CallableBody {
    override fun duplicate(callableItem: CallableItem) =
        PsiCallableBody(psiCodebase, callableItem, psiMethod)

    // Cannot create a copy of this as callableItem cannot be cast to PsiCallableItem. There is no
    // easy way to capture the state of this sufficiently well to implement the necessary behavior
    // so just pretend it is unavailable for now.
    override fun snapshot(callableItem: CallableItem): CallableBody {
        return CallableBody.UNAVAILABLE
    }

    /**
     * Given a method whose return value is annotated with a typedef, runs checks on the typedef and
     * flags any returned constants not in the list.
     */
    override fun verifyReturnedConstants(
        typeDefAnnotation: AnnotationItem,
        typeDefClass: ClassItem,
    ) {
        val body = psiMethod.body ?: return

        body.accept(
            object : JavaRecursiveElementVisitor() {
                private var constants: List<String>? = null

                override fun visitReturnStatement(statement: PsiReturnStatement) {
                    val value = statement.returnValue
                    if (value is PsiReferenceExpression) {
                        val resolved = value.resolve() as? PsiField ?: return
                        val modifiers = resolved.modifierList ?: return
                        if (
                            modifiers.hasModifierProperty(PsiModifier.STATIC) &&
                                modifiers.hasModifierProperty(PsiModifier.FINAL)
                        ) {
                            if (resolved.type.arrayDimensions > 0) {
                                return
                            }
                            val name = resolved.name

                            // Make sure this is one of the allowed annotations
                            val names =
                                constants
                                    ?: run {
                                        constants = computeValidConstantNames(typeDefAnnotation)
                                        constants!!
                                    }
                            if (names.isNotEmpty() && !names.contains(name)) {
                                val expected = names.joinToString { it }
                                psiCodebase.reporter.report(
                                    Issues.RETURNING_UNEXPECTED_CONSTANT,
                                    value as PsiElement,
                                    "Returning unexpected constant $name; is @${typeDefClass.simpleName()} missing this constant? Expected one of $expected"
                                )
                            }
                        }
                    }
                }
            }
        )
    }

    private fun computeValidConstantNames(annotation: AnnotationItem): List<String> {
        val constants = annotation.findAttribute(ANNOTATION_ATTR_VALUE)?.value ?: return emptyList()
        return constants.asFlatList().mapNotNull { (it as? FieldReferenceValue)?.fieldName }
    }
}
