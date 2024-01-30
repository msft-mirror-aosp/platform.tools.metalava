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

import com.android.tools.metalava.model.TypeParameterItem

/**
 * The set of [TypeParameterItem]s that are in scope.
 *
 * This is used to resolve a reference to a type parameter to the appropriate type parameter. They
 * are in order from closest to most distant. e.g. When resolving the type parameters for `method`
 * this will contain the type parameters from `method` then from `Inner`, then from `Outer`, i.e.
 * `[M, X, I, X, O, X]`. That ensures that when searching for a type parameter whose name shadows
 * one from an outer scope, e.g. `X`, that the inner one is used.
 *
 * ```
 *     public class Outer<O, X> {
 *     }
 *
 *     public class Outer.Inner<I, X> {
 *       method public <M, X> M method(O o, I i);
 *     }
 * ```
 */
typealias TypeParameterScope = List<TypeParameterItem>

/** Finds the closest [TypeParameterItem] with the specified name. */
internal fun TypeParameterScope.findTypeParameter(name: String): TypeParameterItem? = firstOrNull {
    it.name() == name
}
