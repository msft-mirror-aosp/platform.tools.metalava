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
import com.android.tools.metalava.model.TypeParameterScope
import com.android.tools.metalava.model.type.TypeItemFactory

internal class TextTypeItemFactory(
    private val typeParser: TextTypeParser,
) : TypeItemFactory<String, TextTypeItem> {

    override fun getBoundsType(underlyingType: String, typeParameterScope: TypeParameterScope) =
        typeParser.obtainTypeFromString(underlyingType, typeParameterScope) as BoundsTypeItem

    override fun getGeneralType(underlyingType: String, typeParameterScope: TypeParameterScope) =
        typeParser.obtainTypeFromString(underlyingType, typeParameterScope)

    override fun getInterfaceType(underlyingType: String, typeParameterScope: TypeParameterScope) =
        typeParser.getSuperType(underlyingType, typeParameterScope)

    override fun getSuperClassType(underlyingType: String, typeParameterScope: TypeParameterScope) =
        typeParser.getSuperType(underlyingType, typeParameterScope)
}
