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

package com.android.tools.metalava.model.type

import com.android.tools.metalava.model.ClassTypeItem
import com.android.tools.metalava.model.DefaultStandaloneTypeItem
import com.android.tools.metalava.model.LambdaTypeItem
import com.android.tools.metalava.model.TypeArgumentTypeItem
import com.android.tools.metalava.model.TypeItem
import com.android.tools.metalava.model.TypeModifiers

internal class DefaultLambdaTypeItem(
    override val isSuspend: Boolean,
    override val receiverType: TypeItem?,
    override val parameterTypes: List<TypeItem>,
    override val returnType: TypeItem,
    private val jvmClassType: ClassTypeItem,
) :
    DefaultStandaloneTypeItem(
        modifiers = jvmClassType.modifiers,
        isValueClassType = jvmClassType.isValueClassType,
    ),
    LambdaTypeItem {

    override fun asJvmClassType(): ClassTypeItem = jvmClassType

    override val qualifiedName: String
        get() = jvmClassType.qualifiedName

    override val arguments: List<TypeArgumentTypeItem>
        get() = jvmClassType.arguments

    override val outerClassType: ClassTypeItem?
        get() = jvmClassType.outerClassType

    override val className: String
        get() = jvmClassType.className

    override fun substitute(
        modifiers: TypeModifiers,
        receiverType: TypeItem?,
        parameterTypes: List<TypeItem>,
        returnType: TypeItem,
        jvmClassType: ClassTypeItem,
    ): LambdaTypeItem {
        val newJvmClassType = jvmClassType.substitute(modifiers = modifiers)
        return if (
            receiverType !== this.receiverType ||
                parameterTypes !== this.parameterTypes ||
                returnType !== this.returnType ||
                newJvmClassType !== this.jvmClassType
        ) {
            DefaultLambdaTypeItem(
                isSuspend = isSuspend,
                receiverType = receiverType,
                parameterTypes = parameterTypes,
                returnType = returnType,
                jvmClassType = newJvmClassType,
            )
        } else this
    }

    override fun equalsImpl(other: DefaultStandaloneTypeItem): Boolean {
        if (other !is ClassTypeItem) return false
        return qualifiedName == other.qualifiedName &&
            outerClassType == other.outerClassType &&
            arguments == other.arguments
    }

    override fun hashCodeImpl(): Int {
        var result = qualifiedName.hashCode()
        result = 31 * result + (outerClassType?.hashCode() ?: 0)
        result = 31 * result + arguments.hashCode()
        return result
    }
}
