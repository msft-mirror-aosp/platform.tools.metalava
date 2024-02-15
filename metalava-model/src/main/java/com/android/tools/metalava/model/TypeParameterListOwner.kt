/*
 * Copyright (C) 2018 The Android Open Source Project
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

package com.android.tools.metalava.model

/** Interface common to all [Item]s that can have type parameters. */
sealed interface TypeParameterListOwner {
    /**
     * Any type parameters for the [Item], if there are no parameters then [TypeParameterList.NONE].
     */
    fun typeParameterList() = typeParameterList

    /**
     * Any type parameters for the [Item], if there are no parameters then [TypeParameterList.NONE].
     */
    val typeParameterList: TypeParameterList
}
