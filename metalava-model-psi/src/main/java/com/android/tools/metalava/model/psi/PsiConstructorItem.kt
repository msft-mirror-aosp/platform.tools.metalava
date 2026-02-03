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
import com.android.tools.metalava.model.ConstructorItem
import com.android.tools.metalava.model.TargetLanguage
import com.android.tools.metalava.model.TargetLanguageSet
import com.android.tools.metalava.model.VisibilityLevel
import com.android.tools.metalava.model.item.DefaultConstructorItem
import com.android.tools.metalava.model.psi.PsiCallableItem.parameterList
import com.android.tools.metalava.model.psi.PsiCallableItem.throwsTypes
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiParameter
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtConstructor
import org.jetbrains.kotlin.psi.KtPrimaryConstructor
import org.jetbrains.uast.UMethod

internal class PsiConstructorItem {

    companion object {
        internal fun create(
            codebase: PsiBasedCodebase,
            containingClass: ClassItem,
            psiMethod: PsiMethod,
            enclosingClassTypeItemFactory: PsiTypeItemFactory,
            psiParameters: List<PsiParameter> = psiMethod.psiParameters,
            targetLanguages: Set<TargetLanguage> = TargetLanguageSet.ALL,
        ): ConstructorItem {
            assert(psiMethod.isConstructor)
            val name = psiMethod.name
            val modifiers = PsiModifierItem.create(codebase, psiMethod)

            // After KT-13495, "all constructors of `sealed` classes now have `protected`
            // visibility by default," and (S|U)LC follows that (hence the same in UAST).
            // However, that change was made to allow more flexible class hierarchy and
            // nesting. If they're compiled to JVM bytecode, sealed class's ctor is still
            // technically `private` to block instantiation from outside class hierarchy.
            // Another synthetic constructor, along with an internal ctor marker, is added
            // for subclasses of a sealed class. Therefore, from Metalava's perspective,
            // it is not necessary to track such semantically protected ctor. Here we force
            // set the visibility to `private` back to ignore it during signature writing.
            if (containingClass.modifiers.isSealed()) {
                modifiers.setVisibilityLevel(VisibilityLevel.PRIVATE)
            }

            // Create the TypeParameterList for this before wrapping any of the other types used by
            // it as they may reference a type parameter in the list.
            val (typeParameterList, constructorTypeItemFactory) =
                PsiTypeParameterList.create(
                    codebase,
                    enclosingClassTypeItemFactory,
                    "constructor $name",
                    psiMethod
                )
            val constructor =
                DefaultConstructorItem(
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
                    returnType = containingClass.type(),
                    parameterItemsFactory = { containingCallable ->
                        parameterList(
                            codebase,
                            psiMethod,
                            containingCallable,
                            constructorTypeItemFactory,
                            modifiers,
                            psiParameters,
                        )
                    },
                    throwsTypes = throwsTypes(psiMethod, constructorTypeItemFactory),
                    callableBodyFactory = { PsiCallableBody(codebase, it, psiMethod) },
                    implicitConstructor = false,
                    isPrimary = (psiMethod as? UMethod)?.isPrimaryConstructor ?: false
                )

            // Undo setting of constructors with value class types to private (b/395472914).
            // Constructors that use value class types are effectively private to java callers, but
            // they can be public in source to kotlin callers, so we want to track them.
            if (
                constructor.modifiers.isPrivate() &&
                    constructor.parameters().any { it.type().isValueClassType }
            ) {
                (psiMethod.sourceElement as? KtConstructor<*>)?.let { sourcePsi ->
                    if (!sourcePsi.hasModifier(KtTokens.PRIVATE_KEYWORD)) {
                        constructor.mutateModifiers {
                            val correctedVisibility =
                                when {
                                    sourcePsi.hasModifier(KtTokens.PROTECTED_KEYWORD) ->
                                        VisibilityLevel.PROTECTED
                                    sourcePsi.hasModifier(KtTokens.INTERNAL_KEYWORD) ->
                                        VisibilityLevel.INTERNAL
                                    else -> VisibilityLevel.PUBLIC
                                }
                            setVisibilityLevel(correctedVisibility)
                        }
                    }
                }
            }

            return constructor
        }

        /**
         * Whether the [UMethod] is the primary constructor of a Kotlin class. A primary constructor
         * is declared in the class header, and all other constructors must delegate to it (see
         * https://kotlinlang.org/docs/classes.html#constructors).
         */
        internal val UMethod.isPrimaryConstructor: Boolean
            get() = sourcePsi is KtPrimaryConstructor || sourcePsi is KtClassOrObject
    }
}
