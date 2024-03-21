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

package com.android.tools.metalava.model.text

import com.android.tools.metalava.model.BoundsTypeItem
import com.android.tools.metalava.model.ClassTypeItem
import com.android.tools.metalava.model.ExceptionTypeItem
import com.android.tools.metalava.model.JAVA_LANG_ANNOTATION
import com.android.tools.metalava.model.JAVA_LANG_ENUM
import com.android.tools.metalava.model.JAVA_LANG_OBJECT
import com.android.tools.metalava.model.TypeItem
import com.android.tools.metalava.model.TypeParameterItem
import com.android.tools.metalava.model.TypeParameterScope
import com.android.tools.metalava.model.type.ContextNullability
import com.android.tools.metalava.model.type.TypeItemFactory

internal class TextTypeItemFactory(
    private val codebase: TextCodebase,
    private val typeParser: TextTypeParser,
    override val typeParameterScope: TypeParameterScope = TypeParameterScope.empty,
) : TypeItemFactory<String, TextTypeItemFactory> {

    /** A [JAVA_LANG_ANNOTATION] suitable for use as a super type. */
    val superAnnotationType
        get() = getInterfaceType(JAVA_LANG_ANNOTATION)

    /** A [JAVA_LANG_ENUM] suitable for use as a super type. */
    val superEnumType
        get() = getSuperClassType(JAVA_LANG_ENUM)

    /** A [JAVA_LANG_OBJECT] suitable for use as a super type. */
    val superObjectType
        get() = getSuperClassType(JAVA_LANG_OBJECT)

    override fun nestedFactory(
        scopeDescription: String,
        typeParameters: List<TypeParameterItem>
    ): TextTypeItemFactory {
        val scope = typeParameterScope.nestedScope(scopeDescription, typeParameters)
        return if (scope === typeParameterScope) this
        else TextTypeItemFactory(codebase, typeParser, scope)
    }

    override fun getBoundsType(underlyingType: String) =
        typeParser.obtainTypeFromString(underlyingType, typeParameterScope) as BoundsTypeItem

    override fun getExceptionType(underlyingType: String) =
        (typeParser.obtainTypeFromString(underlyingType, typeParameterScope) as ExceptionTypeItem)
            .also { exceptionTypeItem ->
                if (exceptionTypeItem is ClassTypeItem) {
                    codebase.requireStubKindFor(exceptionTypeItem, StubKind.THROWABLE)
                }
            }

    override fun getGeneralType(underlyingType: String): TypeItem =
        typeParser.obtainTypeFromString(underlyingType, typeParameterScope)

    private fun getSuperType(underlyingType: String): ClassTypeItem =
        typeParser.obtainTypeFromString(
            underlyingType,
            typeParameterScope,
            ContextNullability.forceNonNull,
        ) as ClassTypeItem

    override fun getInterfaceType(underlyingType: String) =
        getSuperType(underlyingType).also { classTypeItem ->
            codebase.requireStubKindFor(classTypeItem, StubKind.INTERFACE)
        }

    override fun getSuperClassType(underlyingType: String) = getSuperType(underlyingType)
}
