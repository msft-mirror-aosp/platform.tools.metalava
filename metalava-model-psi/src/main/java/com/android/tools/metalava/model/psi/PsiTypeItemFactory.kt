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

import com.android.tools.metalava.model.BoundsTypeItem
import com.android.tools.metalava.model.ExceptionTypeItem
import com.android.tools.metalava.model.TypeParameterItem
import com.android.tools.metalava.model.TypeUse
import com.android.tools.metalava.model.type.TypeItemFactory
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiType
import org.jetbrains.uast.kotlin.isKotlin

/**
 * Encapsulates a [PsiType] and an optional context [PsiElement] for use with [PsiTypeItemFactory].
 */
data class PsiTypeInfo(val psiType: PsiType, val context: PsiElement? = null)

/**
 * Creates [PsiTypeItem]s from [PsiType]s and an optional context [PsiElement], encapsulated within
 * [PsiTypeInfo].
 */
internal class PsiTypeItemFactory(val codebase: PsiBasedCodebase) :
    TypeItemFactory<PsiTypeInfo, PsiTypeItemFactory> {

    override val typeParameterScope
        get() = error("unsupported")

    override fun nestedFactory(scopeDescription: String, typeParameters: List<TypeParameterItem>) =
        error("unsupported")

    override fun getBoundsType(underlyingType: PsiTypeInfo) =
        getType(underlyingType) as BoundsTypeItem

    override fun getExceptionType(underlyingType: PsiTypeInfo) =
        getType(underlyingType) as ExceptionTypeItem

    override fun getGeneralType(underlyingType: PsiTypeInfo) = getType(underlyingType)

    override fun getInterfaceType(underlyingType: PsiTypeInfo) =
        getSuperType(underlyingType.psiType)

    override fun getSuperClassType(underlyingType: PsiTypeInfo) =
        getSuperType(underlyingType.psiType)

    /**
     * Creates a [PsiClassTypeItem] that is suitable for use as a super type, e.g. in an `extends`
     * or `implements` list.
     */
    private fun getSuperType(psiType: PsiType): PsiClassTypeItem {
        return getType(psiType, typeUse = TypeUse.SUPER_TYPE) as PsiClassTypeItem
    }

    private fun getType(psiTypeInfo: PsiTypeInfo, typeUse: TypeUse = TypeUse.GENERAL): PsiTypeItem {
        return getType(psiTypeInfo.psiType, psiTypeInfo.context, typeUse)
    }

    /**
     * Returns a [PsiTypeItem] representing the [psiType]. The [context] is used to get nullability
     * information for Kotlin types.
     */
    internal fun getType(
        psiType: PsiType,
        context: PsiElement? = null,
        typeUse: TypeUse = TypeUse.GENERAL
    ): PsiTypeItem {
        val kotlinTypeInfo =
            if (context != null && isKotlin(context)) {
                KotlinTypeInfo.fromContext(context)
            } else {
                null
            }

        // Note: We do *not* cache these; it turns out that storing PsiType instances
        // in a map is bad for performance; it has a very expensive equals operation
        // for some type comparisons (and we sometimes end up with unexpected results,
        // e.g. where we fetch an "equals" type from the map but its representation
        // is slightly different to what was intended
        return PsiTypeItem.create(codebase, psiType, kotlinTypeInfo, typeUse)
    }

    /** Get a [PsiClassTypeItem] to represent the [PsiClassItem]. */
    fun getClassTypeForClass(psiClassItem: PsiClassItem): PsiClassTypeItem {
        // Create a PsiType for the class. Specifies `PsiSubstitutor.EMPTY` so that if the class
        // has any type parameters then the PsiType will include references to those parameters.
        val psiTypeWithTypeParametersIfAny = codebase.getClassType(psiClassItem.psiClass)
        return PsiTypeItem.create(codebase, psiTypeWithTypeParametersIfAny, null)
            as PsiClassTypeItem
    }
}
