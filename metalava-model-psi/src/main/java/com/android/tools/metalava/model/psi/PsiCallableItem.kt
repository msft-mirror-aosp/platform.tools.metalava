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

import com.android.tools.metalava.model.ApiVariantSelectors
import com.android.tools.metalava.model.CallableBody
import com.android.tools.metalava.model.CallableItem
import com.android.tools.metalava.model.ClassItem
import com.android.tools.metalava.model.DefaultModifierList
import com.android.tools.metalava.model.ExceptionTypeItem
import com.android.tools.metalava.model.ItemDocumentationFactory
import com.android.tools.metalava.model.ParameterItem
import com.android.tools.metalava.model.TypeItem
import com.android.tools.metalava.model.TypeParameterList
import com.android.tools.metalava.model.item.DefaultCallableItem
import com.android.tools.metalava.model.type.MethodFingerprint
import com.android.tools.metalava.reporter.FileLocation
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiParameter
import org.jetbrains.kotlin.name.JvmStandardClassIds
import org.jetbrains.kotlin.psi.KtFunction
import org.jetbrains.uast.UMethod

/**
 * A lamda that given a [CallableItem] will create a list of [ParameterItem]s for it.
 *
 * This is called from within the constructor of the [ParameterItem.containingCallable] and can only
 * access the [CallableItem.name] (to identify callables that have special nullability rules) and
 * store a reference to it in [ParameterItem.containingCallable]. In particularly, it must not
 * access [CallableItem.parameters] as that will not yet have been initialized when this is called.
 */
internal typealias ParameterItemsFactory = (PsiCallableItem) -> List<PsiParameterItem>

internal abstract class PsiCallableItem(
    override val codebase: PsiBasedCodebase,
    val psiMethod: PsiMethod,
    fileLocation: FileLocation = PsiFileLocation(psiMethod),
    // Takes ClassItem as this may be duplicated from a PsiBasedCodebase on the classpath into a
    // TextClassItem.
    modifiers: DefaultModifierList,
    documentationFactory: ItemDocumentationFactory,
    name: String,
    containingClass: ClassItem,
    typeParameterList: TypeParameterList,
    returnType: TypeItem,
    parameterItemsFactory: ParameterItemsFactory,
    throwsTypes: List<ExceptionTypeItem>,
) :
    DefaultCallableItem(
        codebase = codebase,
        fileLocation = fileLocation,
        itemLanguage = psiMethod.itemLanguage,
        modifiers = modifiers,
        documentationFactory = documentationFactory,
        variantSelectorsFactory = ApiVariantSelectors.MUTABLE_FACTORY,
        name = name,
        containingClass = containingClass,
        typeParameterList = typeParameterList,
        returnType = returnType,
        // Down cast needed for parameters. Doing it here saves having to do it in multiple other
        // places.
        parameterItemsFactory = { parameterItemsFactory(it as PsiCallableItem) },
        throwsTypes = throwsTypes,
    ),
    CallableItem,
    PsiItem {

    override fun psi() = psiMethod

    /**
     * Create the [CallableBody] during initialization of this callable to allow it to contain an
     * immutable reference to this object.
     *
     * The leaking of `this` is ok as [PsiCallableBody] does not access any properties of this that
     * may be uninitialized during its initialization.
     */
    override val body: CallableBody = PsiCallableBody(@Suppress("LeakingThis") this)

    override fun shouldExpandOverloads(): Boolean {
        val ktFunction = (psiMethod as? UMethod)?.sourcePsi as? KtFunction ?: return false
        return modifiers.isActual() &&
            psiMethod.hasAnnotation(JvmStandardClassIds.JVM_OVERLOADS_FQ_NAME.asString()) &&
            // It is /technically/ invalid to have actual functions with default values, but
            // some places suppress the compiler error, so we should handle it here too.
            ktFunction.valueParameters.none { it.hasDefaultValue() } &&
            parameters().any { it.hasDefaultValue() }
    }

    companion object {
        /**
         * Create a list of [PsiParameterItem]s.
         *
         * The [codebase] and [psiMethod] parameters are added here, rather than retrieving from
         * [containingCallable]'s [PsiCallableItem.codebase] and [PsiCallableItem.psiMethod]
         * properties respectively, because at the time this is called [containingCallable] is in
         * the process of being initialized and those properties have not yet been initialized.
         */
        internal fun parameterList(
            codebase: PsiBasedCodebase,
            psiMethod: PsiMethod,
            containingCallable: PsiCallableItem,
            enclosingTypeItemFactory: PsiTypeItemFactory,
        ): List<PsiParameterItem> {
            val psiParameters = psiMethod.psiParameters
            val fingerprint = MethodFingerprint(containingCallable.name(), psiParameters.size)
            return psiParameters.mapIndexed { index, parameter ->
                PsiParameterItem.create(
                    codebase,
                    containingCallable,
                    fingerprint,
                    parameter,
                    index,
                    enclosingTypeItemFactory
                )
            }
        }

        internal fun throwsTypes(
            psiMethod: PsiMethod,
            enclosingTypeItemFactory: PsiTypeItemFactory,
        ): List<ExceptionTypeItem> {
            val throwsClassTypes = psiMethod.throwsList.referencedTypes
            if (throwsClassTypes.isEmpty()) {
                return emptyList()
            }

            return throwsClassTypes
                // Convert the PsiType to an ExceptionTypeItem and wrap it in a ThrowableType.
                .map { psiType -> enclosingTypeItemFactory.getExceptionType(PsiTypeInfo(psiType)) }
                // We're sorting the names here even though outputs typically do their own sorting,
                // since for example the MethodItem.sameSignature check wants to do an
                // element-by-element comparison to see if the signature matches, and that should
                // match overrides even if they specify their elements in different orders.
                .sortedWith(ExceptionTypeItem.fullNameComparator)
        }
    }
}

/** Get the [PsiParameter]s for a [PsiMethod]. */
val PsiMethod.psiParameters: List<PsiParameter>
    get() = if (this is UMethod) uastParameters else parameterList.parameters.toList()
