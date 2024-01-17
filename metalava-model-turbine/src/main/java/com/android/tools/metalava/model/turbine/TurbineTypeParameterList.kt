/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.tools.metalava.model.turbine

import com.android.tools.metalava.model.Codebase
import com.android.tools.metalava.model.TypeParameterList

internal class TurbineTypeParameterList(
    val codebase: Codebase,
) : TypeParameterList {

    internal lateinit var typeParameters: List<TurbineTypeParameterItem>

    override fun toString(): String = TODO("b/295800205")

    override fun typeParameterNames(): List<String> = TODO("b/295800205")

    override fun typeParameters(): List<TurbineTypeParameterItem> = typeParameters
}
