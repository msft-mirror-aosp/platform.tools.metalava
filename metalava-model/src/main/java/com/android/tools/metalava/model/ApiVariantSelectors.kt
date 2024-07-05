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

package com.android.tools.metalava.model

/** A factory that will create an [ApiVariantSelectors] for a specific [Item]. */
typealias ApiVariantSelectorsFactory = (Item) -> ApiVariantSelectors

/** Contains properties that select which, if any, variant of an API an [Item] belongs in. */
sealed interface ApiVariantSelectors {
    /**
     * Indicates whether the item was explicitly hidden in the source, e.g. via an `@hide` javadoc
     * tag in its [Item.documentation], or a hide annotation directly on the [Item].
     */
    val originallyHidden: Boolean

    /**
     * Indicates whether the [Item] should be hidden, i.e. should not be included in ANY API surface
     * variant.
     *
     * Initially set to [originallyHidden] but updated due to inheritance.
     */
    var hidden: Boolean

    companion object {
        /**
         * An [ApiVariantSelectors] factory that will always return an immutable
         * [ApiVariantSelectors]. It will return `false` for all the properties and throw an error
         * on any attempt to set a property.
         */
        val IMMUTABLE_FACTORY: ApiVariantSelectorsFactory = { Immutable }
    }

    /**
     * An immutable [ApiVariantSelectors] that will return `false` for all the properties and fail
     * on any attempt to set the `var` properties.
     */
    @Suppress("ConvertObjectToDataObject") // Requires language level 1.9
    private object Immutable : ApiVariantSelectors {

        override val originallyHidden: Boolean
            get() = false

        override var hidden: Boolean
            get() = false
            set(value) {
                error("Cannot set `hidden` to $value")
            }

        override fun toString() = "Immutable"
    }
}
