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
     * Indicates whether children of an [Item] should be hidden, i.e. should not be included in ANY
     * API surface variant.
     *
     * Initially set to [originallyHidden] but updated due to inheritance.
     */
    var inheritableHidden: Boolean

    /**
     * Indicates whether the [Item] should be hidden, i.e. should not be included in ANY API surface
     * variant.
     *
     * Initially set to [inheritableHidden] but updated due to show annotations.
     */
    var hidden: Boolean

    /**
     * Indicates whether the [Item] should be included in the doc only API surface variant.
     *
     * Initially set to `true` if the [Item.documentation] contains `@doconly` but updated due to
     * inheritance.
     */
    var docOnly: Boolean

    /**
     * Indicates whether the [Item] should be in the removed API surface variant.
     *
     * Initially set to `true` if the [Item.documentation] contains `@removed` but updated due to
     * inheritance.
     */
    var removed: Boolean

    /** Determines whether this item will be shown as part of the API or not. */
    val showability: Showability

    /** Create a duplicate of this for the specified [Item]. */
    fun duplicate(item: Item): ApiVariantSelectors

    /**
     * Update the mutable properties of this by inheriting state from the parent selectors, if
     * available.
     */
    fun inheritInto()

    companion object {
        /**
         * An [ApiVariantSelectors] factory that will always return an immutable
         * [ApiVariantSelectors]. It will return `false` for all the properties and throw an error
         * on any attempt to set a property.
         */
        val IMMUTABLE_FACTORY: ApiVariantSelectorsFactory = { Immutable }

        /**
         * An [ApiVariantSelectors] factory that will return a new, mutable, [ApiVariantSelectors]
         * for each [Item].
         */
        val MUTABLE_FACTORY: ApiVariantSelectorsFactory = { Mutable(it) }
    }

    /**
     * An immutable [ApiVariantSelectors] that will return `false` for all the properties and fail
     * on any attempt to set the `var` properties.
     */
    @Suppress("ConvertObjectToDataObject") // Requires language level 1.9
    private object Immutable : ApiVariantSelectors {

        override val originallyHidden: Boolean
            get() = false

        override var inheritableHidden: Boolean
            get() = false
            set(value) {
                error("Cannot set `inheritableHidden` to $value")
            }

        override var hidden: Boolean
            get() = false
            set(value) {
                error("Cannot set `hidden` to $value")
            }

        override var docOnly: Boolean
            get() = false
            set(value) {
                error("Cannot set `docOnly` to $value")
            }

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

    /**
     * A mutable [ApiVariantSelectors].
     *
     * [originallyHidden] will be `true` if it's [item]'s documentation contains one of `@hide`,
     * `@pending` or `@suppress` or its [Item] has a hide annotation associated with it.
     *
     * Unless [hidden] is written before reading then it will default to `true` if
     * [originallyHidden] is `true` and it does not have any show annotations.
     *
     * [docOnly] will be initialized to `true` if it's [item]'s documentation contains `@doconly`.
     *
     * [removed] will be initialized to `true` if it's [item]'s documentation contains `@removed`.
     *
     * This uses bits in [propertyHasBeenSetBits] and [propertyValueBits] to handle lazy
     * initialization and store the value. The main purpose of using bit masks is not primarily
     * performance or to reduce storage (although keeping that down is a factor) but rather to
     * support lazy initialization with optional setters without duplicating lots of complicated
     * code.
     */
    private class Mutable(private val item: Item) : ApiVariantSelectors {

        /**
         * Contains a bit for each lazy boolean property indicating whether it has been set, either
         * implicitly during initialization or explicitly via its setter.
         *
         * If a bit is set in here then the corresponding bit in [propertyValueBits] contains the
         * value.
         */
        private var propertyHasBeenSetBits = 0

        /**
         * Contains a bit for each lazy boolean property indicating its value.
         *
         * A bit in here represents the value of the property if and only if the corresponding bit
         * has been set in [propertyHasBeenSetBits]. Otherwise, the value of the bit is undefined.
         */
        private var propertyValueBits = 0

        /**
         * Get the value of a property from [propertyValueBits], initializing it if it has not yet
         * been set.
         *
         * @param propertyBitMask the bit mask in [propertyHasBeenSetBits] and [propertyValueBits]
         *   which indicates whether the associated property's value has been set and if so what its
         *   value is.
         * @param initialValueProvider a lambda which returns the initial value of the property if
         *   it has not yet been set.
         */
        private inline fun lazyGet(propertyBitMask: Int, initialValueProvider: () -> Boolean) =
            // Check to see if the property has been set first before accessing the value.
            if ((propertyHasBeenSetBits and propertyBitMask) == 0) {
                // The property has not been set so get the initial value and store it.
                val result = initialValueProvider()
                // Record that the property has been set.
                propertyHasBeenSetBits = propertyHasBeenSetBits or propertyBitMask
                // Record the value.
                if (result) propertyValueBits = propertyValueBits or propertyBitMask
                // Return the result.
                result
            } else {
                // The property has been set so get its value.
                (propertyValueBits and propertyBitMask) != 0
            }

        /**
         * Set the value of a property in [propertyValueBits], skipping initializing it that has not
         * already been done.
         *
         * @param propertyBitMask the bit mask in [propertyHasBeenSetBits] and [propertyValueBits]
         *   which indicates whether the associated property's value has been set and if so what its
         *   value is.
         * @param value the new value of the property.
         */
        private fun lazySet(propertyBitMask: Int, value: Boolean) {
            // Record that the property has been set.
            propertyHasBeenSetBits = propertyHasBeenSetBits or propertyBitMask
            if (value) {
                // The value is true so set the bit.
                propertyValueBits = propertyValueBits or propertyBitMask
            } else {
                // The value is false so clear the bit.
                propertyValueBits = propertyValueBits and propertyBitMask.inv()
            }
        }

        override val originallyHidden: Boolean
            get() =
                lazyGet(ORIGINALLY_HIDDEN_BIT_MASK) {
                    item.documentation.isHidden || item.hasHideAnnotation()
                }

        override var inheritableHidden: Boolean
            get() =
                lazyGet(INHERITABLE_HIDDEN_BIT_MASK) {
                    originallyHidden && !item.hasShowAnnotation()
                }
            set(value) {
                lazySet(INHERITABLE_HIDDEN_BIT_MASK, value)
            }

        override var hidden: Boolean
            get() = lazyGet(HIDDEN_BIT_MASK) { inheritableHidden }
            set(value) {
                lazySet(HIDDEN_BIT_MASK, value)
            }

        override var docOnly: Boolean
            get() = lazyGet(DOCONLY_BIT_MASK) { item.documentation.isDocOnly }
            set(value) {
                lazySet(DOCONLY_BIT_MASK, value)
            }

        override var removed: Boolean
            get() = lazyGet(REMOVED_BIT_MASK) { item.documentation.isRemoved }
            set(value) {
                lazySet(REMOVED_BIT_MASK, value)
            }

        /** Cache of [showability]. */
        private lateinit var _showability: Showability

        override val showability: Showability
            get() =
                if (::_showability.isInitialized) _showability
                else {
                    _showability = item.codebase.annotationManager.getShowabilityForItem(item)
                    _showability
                }

        override fun duplicate(item: Item): ApiVariantSelectors = Mutable(item)

        override fun inheritInto() {
            // PackageItem behaves quite differently to the other Item types so do it first.
            if (item is PackageItem) {
                showability.let { showability ->
                    when {
                        showability.show() -> inheritableHidden = false
                        showability.hide() -> inheritableHidden = true
                    }
                }
                val containingPackageSelectors =
                    item.containingPackage()?.variantSelectors ?: return
                if (containingPackageSelectors.inheritableHidden) {
                    inheritableHidden = true
                }
                if (containingPackageSelectors.docOnly) {
                    docOnly = true
                }
                return
            }

            // Inheritance is only done on a few Item types, ignore the rest.
            if (item !is ClassItem && item !is CallableItem && item !is FieldItem) return

            if (showability.show()) {
                inheritableHidden = false

                if (item is ClassItem) {
                    // Make containing package non-hidden if it contains a show-annotation class.
                    val containingPackageSelectors = item.containingPackage().variantSelectors
                    // Only unhide the package, do not affect anything that might inherit from that
                    // package.
                    containingPackageSelectors.hidden = false
                }
            } else if (showability.hide()) {
                inheritableHidden = true
            } else {
                val containingClassSelectors = item.containingClass()?.variantSelectors
                if (containingClassSelectors != null) {
                    // FieldItem does not inherit hidden status from its containing class.
                    if (item !is FieldItem && containingClassSelectors.inheritableHidden) {
                        inheritableHidden = true
                    } else if (
                        containingClassSelectors.originallyHidden &&
                            containingClassSelectors.showability.showNonRecursive()
                    ) {
                        // This is a member in a class that was hidden but then unhidden; but it was
                        // unhidden by a non-recursive (single) show annotation, so don't inherit
                        // the show annotation into this item.
                        inheritableHidden = true
                    }
                    if (containingClassSelectors.docOnly) {
                        docOnly = true
                    }
                    if (containingClassSelectors.removed) {
                        removed = true
                    }
                } else if (item is ClassItem) {
                    // This will only be executed for top level classes, i.e. containing class is
                    // null. They inherit their properties from the containing package.
                    val containingPackageSelectors = item.containingPackage().variantSelectors
                    if (containingPackageSelectors.inheritableHidden) {
                        inheritableHidden = true
                    }
                    if (containingPackageSelectors.docOnly) {
                        docOnly = true
                    }
                    if (containingPackageSelectors.removed) {
                        removed = true
                    }
                }
            }
        }

        override fun toString(): String {
            return buildString {
                append(item.describe())
                append(" {")
                for ((bitPosition, propertyName) in propertyNamePerBit.withIndex()) {
                    append(propertyName)
                    append("=")
                    if ((propertyHasBeenSetBits and bitPosition) == 0) {
                        append("<not-set>")
                    } else if ((propertyValueBits and bitPosition) == 0) {
                        append("false")
                    } else {
                        append("true")
                    }
                    append(",")
                }
                append("showability=")
                if (::_showability.isInitialized) {
                    append(_showability)
                } else {
                    append("<not-set>")
                }
                append("}")
            }
        }

        companion object {
            // `originallyHidden` related constants
            private const val ORIGINALLY_HIDDEN_BIT_POSITION: Int = 0
            private const val ORIGINALLY_HIDDEN_BIT_MASK: Int = 1 shl ORIGINALLY_HIDDEN_BIT_POSITION

            // `inheritableHidden` related constants
            private const val INHERITABLE_HIDDEN_BIT_POSITION: Int =
                ORIGINALLY_HIDDEN_BIT_POSITION + 1
            private const val INHERITABLE_HIDDEN_BIT_MASK: Int =
                1 shl INHERITABLE_HIDDEN_BIT_POSITION

            // `hidden` related constants
            private const val HIDDEN_BIT_POSITION: Int = INHERITABLE_HIDDEN_BIT_POSITION + 1
            private const val HIDDEN_BIT_MASK: Int = 1 shl HIDDEN_BIT_POSITION

            // `docOnly` related constants
            private const val DOCONLY_BIT_POSITION: Int = HIDDEN_BIT_POSITION + 1
            private const val DOCONLY_BIT_MASK: Int = 1 shl DOCONLY_BIT_POSITION

            // `removed` related constants
            private const val REMOVED_BIT_POSITION: Int = DOCONLY_BIT_POSITION + 1
            private const val REMOVED_BIT_MASK: Int = 1 shl REMOVED_BIT_POSITION

            /** The count of the number of bits used. */
            private const val COUNT_BITS_USED = REMOVED_BIT_POSITION + 1

            /** Map from bit to the associated property name, used in toString() */
            private val propertyNamePerBit =
                Array(COUNT_BITS_USED) { "" }
                    .also { array ->
                        array[ORIGINALLY_HIDDEN_BIT_POSITION] = "originallyHidden"
                        array[INHERITABLE_HIDDEN_BIT_POSITION] = "inheritableHidden"
                        array[HIDDEN_BIT_POSITION] = "hidden"
                        array[DOCONLY_BIT_POSITION] = "docOnly"
                        array[REMOVED_BIT_POSITION] = "removed"
                    }
        }
    }
}
