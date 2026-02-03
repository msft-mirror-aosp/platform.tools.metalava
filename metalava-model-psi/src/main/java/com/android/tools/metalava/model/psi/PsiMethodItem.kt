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
import com.android.tools.metalava.model.ClassItem
import com.android.tools.metalava.model.ClassKind
import com.android.tools.metalava.model.MethodItem
import com.android.tools.metalava.model.TargetLanguage
import com.android.tools.metalava.model.VisibilityLevel
import com.android.tools.metalava.model.item.DefaultMethodItem
import com.android.tools.metalava.model.psi.PsiCallableItem.parameterList
import com.android.tools.metalava.model.psi.PsiCallableItem.throwsTypes
import com.android.tools.metalava.model.type.MethodFingerprint
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiParameter
import org.jetbrains.kotlin.psi.KtAnnotated
import org.jetbrains.kotlin.psi.KtParameter
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtPropertyAccessor
import org.jetbrains.kotlin.psi.psiUtil.isExtensionDeclaration
import org.jetbrains.uast.UAnnotation
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.toUElementOfType

internal class PsiMethodItem {

    companion object {
        /** Create a [PsiMethodItem]. */
        internal fun create(
            codebase: PsiBasedCodebase,
            containingClass: ClassItem,
            psiMethod: PsiMethod,
            enclosingClassTypeItemFactory: PsiTypeItemFactory,
            psiParameters: List<PsiParameter> = psiMethod.psiParameters,
            targetLanguages: Set<TargetLanguage> = containingClass.targetLanguages,
        ): MethodItem {
            assert(!psiMethod.isConstructor)
            // TODO(b/457844210): work around a UAST issue where the accessor methods of internal
            //  PublishedApi properties have mangled names even though the compiler does not mangle
            //  their names.
            val name =
                if (
                    psiMethod.name.contains("$") &&
                        psiMethod.isKotlinProperty() &&
                        sourcePropertyOrParameter(psiMethod)?.hasPublishedApiAnnotation() == true
                ) {
                    psiMethod.name.substringBefore("$")
                } else {
                    psiMethod.name
                }
            val modifiers = PsiModifierItem.create(codebase, psiMethod)

            if (containingClass.classKind == ClassKind.INTERFACE) {
                // All interface methods are implicitly public (except in Java 1.9, where they can
                // be private.
                if (!modifiers.isPrivate()) {
                    modifiers.setVisibilityLevel(VisibilityLevel.PUBLIC)
                }
            }

            if (modifiers.isFinal() && containingClass.modifiers.isFinal()) {
                // The containing class is final, so it is implied that every method is final as
                // well.
                // No need to apply 'final' to each method. (We do it here rather than just in the
                // signature emit code since we want to make sure that the signature comparison
                // methods with super methods also consider this method non-final.)
                modifiers.setFinal(false)
            }

            // Create the TypeParameterList for this before wrapping any of the other types used by
            // it as they may reference a type parameter in the list.
            val (typeParameterList, methodTypeItemFactory) =
                PsiTypeParameterList.create(
                    codebase,
                    enclosingClassTypeItemFactory,
                    "method $name",
                    psiMethod
                )
            val fingerprint = MethodFingerprint(psiMethod.name, psiMethod.parameters.size)
            val isAnnotationElement = containingClass.isAnnotationType() && !modifiers.isStatic()
            val returnType =
                methodTypeItemFactory.getMethodReturnType(
                    underlyingReturnType = PsiTypeInfo(psiMethod.returnType!!, psiMethod),
                    itemAnnotations = modifiers.annotations(),
                    fingerprint = fingerprint,
                    isAnnotationElement = isAnnotationElement,
                )

            val defaultValueProvider = psiMethod.defaultValueProvider(codebase, returnType)

            // Use psi util which works for source kt elements to determine if this is an extension
            val isExtensionMethod =
                (psiMethod as? UMethod)?.sourcePsi?.isExtensionDeclaration() ?: false

            val method =
                DefaultMethodItem(
                    codebase = codebase,
                    fileLocation = PsiFileLocation(psiMethod),
                    sourceLanguage = psiMethod.sourceLanguage,
                    targetLanguages = targetLanguages,
                    modifiers = modifiers,
                    documentationFactory = PsiItemDocumentation.factory(psiMethod, codebase),
                    variantSelectorsFactory = ApiVariantSelectors.MUTABLE_FACTORY,
                    name = name,
                    containingClass = containingClass,
                    typeParameterList = typeParameterList,
                    returnType = returnType,
                    parameterItemsFactory = { containingCallable ->
                        parameterList(
                            codebase,
                            psiMethod,
                            containingCallable,
                            methodTypeItemFactory,
                            modifiers,
                            psiParameters,
                        )
                    },
                    throwsTypes = throwsTypes(psiMethod, methodTypeItemFactory),
                    callableBodyFactory = { PsiCallableBody(codebase, it, psiMethod) },
                    defaultValueProvider = defaultValueProvider,
                    isExtensionMethod = isExtensionMethod,
                    isKotlinProperty = psiMethod.isKotlinProperty(),
                )

            return method
        }

        /**
         * For property accessor [psiMethod], returns the [KtProperty] or [KtParameter] which is the
         * source of the method.
         */
        private fun sourcePropertyOrParameter(psiMethod: PsiMethod): KtAnnotated? {
            return when (val sourcePsi = (psiMethod as? UMethod)?.sourcePsi) {
                is KtProperty -> sourcePsi
                is KtParameter -> sourcePsi
                is KtPropertyAccessor -> sourcePsi.property
                else -> null
            }
        }

        /** Returns whether the element is annotated with @PublishedApi. */
        private fun KtAnnotated.hasPublishedApiAnnotation(): Boolean {
            return annotationEntries.any {
                it.toUElementOfType<UAnnotation>()?.qualifiedName == "kotlin.PublishedApi"
            }
        }
    }
}
