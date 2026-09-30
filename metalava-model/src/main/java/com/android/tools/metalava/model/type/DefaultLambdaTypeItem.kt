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
    DefaultClassTypeItem(
        modifiers = jvmClassType.modifiers,
        qualifiedName = jvmClassType.qualifiedName,
        arguments = jvmClassType.arguments,
        outerClassType = jvmClassType.outerClassType,
        isValueClassType = jvmClassType.isValueClassType,
    ),
    LambdaTypeItem {

    override fun asJvmClassType(): ClassTypeItem = jvmClassType

    override fun substitute(
        modifiers: TypeModifiers,
        outerClassType: ClassTypeItem?,
        arguments: List<TypeArgumentTypeItem>,
    ): LambdaTypeItem =
        if (requiresNewInstance(modifiers, outerClassType, arguments))
            DefaultLambdaTypeItem(
                isSuspend = isSuspend,
                receiverType = receiverType,
                parameterTypes = parameterTypes,
                returnType = returnType,
                jvmClassType =
                    jvmClassType.substitute(
                        modifiers,
                        outerClassType,
                        arguments,
                    ),
            )
        else this
}
