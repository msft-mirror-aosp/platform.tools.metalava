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

/** A factory that will create an [ApiVariantSelectors] for a specific [SelectableItem]. */
typealias ApiVariantSelectorsFactory = (SelectableItem) -> ApiVariantSelectors

/** Contains properties that select which, if any, variant of an API an [Item] belongs in. */
sealed class ApiVariantSelectors {
    /**
     * Indicates whether the item was explicitly hidden in the source, e.g. via an `@hide` javadoc
     * tag in its [SelectableItem.documentation], or a hide annotation directly on the [Item].
     */
    abstract val originallyHidden: Boolean

    /**
     * Indicates whether children of an [Item] should be hidden, i.e. should not be included in ANY
     * API surface variant.
     *
     * Initially set to [originallyHidden] but updated due to inheritance.
     */
    internal abstract val inheritableHidden: Boolean

    /**
     * Indicates whether the [Item] should be hidden, i.e. should not be included in ANY API surface
     * variant.
     *
     * Initially set to [inheritableHidden] but updated due to show annotations.
     */
    abstract val hidden: Boolean

    /**
     * Indicates whether the [Item] should be in the removed API surface variant.
     *
     * Initially set to `true` if the [SelectableItem.documentation] contains `@removed` but updated
     * due to inheritance.
     */
    abstract val removed: Boolean

    /** Determines whether this item will be shown as part of the API or not. */
    abstract val showability: Showability

    /** Create a duplicate of this for the specified [Item]. */
    abstract fun duplicate(item: Item): ApiVariantSelectors

    /**
     * Update the mutable properties of this by inheriting state from the parent selectors, if
     * available.
     */
    abstract fun inheritInto()

    companion object {
        /**
         * An [ApiVariantSelectors] factory that will always return an immutable
         * [ApiVariantSelectors]. It will return `false` for all the properties and throw an error
         * on any attempt to set a property, or if [ApiVariantSelectors.inheritInto] is called.
         */
        val IMMUTABLE_FACTORY: ApiVariantSelectorsFactory = { Immutable }

        /**
         * An [ApiVariantSelectors] factory that will return a new, mutable, [ApiVariantSelectors]
         * for each [SelectableItem].
         */
        val MUTABLE_FACTORY: ApiVariantSelectorsFactory = { Immutable }
    }

    /**
     * An immutable [ApiVariantSelectors] that will return `false` for all the properties and fail
     * on any attempt to set the `var` properties.
     *
     * The implementation of [ApiVariantSelectors] properties return values that will prevent
     * [SelectableItem]s from being hidden in any way. Attempting to mutate them may result in an
     * error being thrown.
     */
    private object Immutable : ApiVariantSelectors() {
        override val originallyHidden: Boolean
            get() = false

        override val inheritableHidden: Boolean
            get() = false

        override val hidden: Boolean
            get() = false

        override var removed: Boolean
            get() = false
            set(value) {
                error("Cannot set `removed` to $value")
            }

        override val showability: Showability
            get() = Showability.NO_EFFECT

        override fun duplicate(item: Item): ApiVariantSelectors = this

        override fun inheritInto() = error("Cannot inheritInto() $this")

        override fun toString() = "Immutable"
    }
}
