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

import com.android.tools.metalava.model.ClassResolver
import com.android.tools.metalava.model.ClassTypeItem
import com.android.tools.metalava.model.LambdaTypeItem
import com.android.tools.metalava.model.TypeArgumentTypeItem
import com.android.tools.metalava.model.TypeItem
import com.android.tools.metalava.model.TypeModifiers

class DefaultLambdaTypeItem(
    classResolver: ClassResolver,
    modifiers: TypeModifiers,
    qualifiedName: String,
    arguments: List<TypeArgumentTypeItem>,
    outerClassType: ClassTypeItem?,
    override val isSuspend: Boolean,
    override val receiverType: TypeItem?,
    override val parameterTypes: List<TypeItem>,
    override val returnType: TypeItem,
) :
    DefaultClassTypeItem(
        classResolver = classResolver,
        modifiers = modifiers,
        qualifiedName = qualifiedName,
        arguments = arguments,
        outerClassType = outerClassType,
    ),
    LambdaTypeItem {

    @Deprecated(
        "implementation detail of this class",
        replaceWith = ReplaceWith("substitute(modifiers, outerClassType, arguments)"),
    )
    override fun duplicate(
        modifiers: TypeModifiers,
        outerClassType: ClassTypeItem?,
        arguments: List<TypeArgumentTypeItem>
    ): LambdaTypeItem {
        return DefaultLambdaTypeItem(
            classResolver = classResolver,
            qualifiedName = qualifiedName,
            arguments = arguments,
            outerClassType = outerClassType,
            modifiers = modifiers,
            isSuspend = isSuspend,
            receiverType = receiverType,
            parameterTypes = parameterTypes,
            returnType = returnType,
        )
    }
}
