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

package com.android.tools.metalava.model.item

import com.android.tools.metalava.model.ClassItem
import com.android.tools.metalava.model.Codebase
import com.android.tools.metalava.model.Item

/**
 * A factory that will create a [DefaultCodebase] for a specific [CodebaseAssembler].
 *
 * An implementation of this must not try and access any [CodebaseAssembler] functions as it will
 * not be fully initialized at the time this is called.
 */
typealias DefaultCodebaseFactory = (CodebaseAssembler) -> DefaultCodebase

/**
 * A [CodebaseAssembler] is responsible for providing a [Codebase] with access to classes which are
 * present in the underlying model but not yet present in the [Codebase].
 *
 * Although, the interface is simple, the implementation will do a vast amount of the work of
 * mapping an underlying model's representation of the API to a [Codebase], if not all of it.
 */
interface CodebaseAssembler {
    /** Factory for creating appropriate [Item] subclasses for the [Codebase] this is assembling. */
    val itemFactory: DefaultItemFactory

    /**
     * A [ClassItem] with [qualifiedName] could not be found in the associated [Codebase] so look in
     * the underlying model's set of classes to see if one could be found there. If it could then
     * create a [ClassItem] representation of it and return that, otherwise return null.
     */
    fun createClassFromUnderlyingModel(qualifiedName: String): ClassItem?

    /**
     * Overrideable hook, called from [DefaultCodebase.registerClass] for each new
     * [DefaultClassItem].
     */
    fun newClassRegistered(classItem: DefaultClassItem) {}
}
