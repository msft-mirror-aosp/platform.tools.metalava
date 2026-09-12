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

package com.android.tools.metalava.model.visitors

import com.android.tools.metalava.model.ClassItem
import com.android.tools.metalava.model.FilterPredicate
import com.android.tools.metalava.model.SelectableItem
import com.android.tools.metalava.model.api.surface.ApiSurface
import com.android.tools.metalava.model.api.surface.ApiSurfacePredicate
import com.android.tools.metalava.model.api.surface.ApiSurfaces

/**
 * Predicate that decides if the given member should be considered part of an API surface area.
 *
 * It only matches items that are in the [Config.apiSurface]. If that extends another [ApiSurface]
 * then this will not match items that are part of the extended [ApiSurface] (except for classes
 * whose superclass is in [Config.apiSurface], which are included to reveal the class hierarchy).
 */
class ApiPredicate(
    /**
     * Set what the value of [SelectableItem.removed] must be equal to in order for a member to
     * match.
     *
     * This is typically useful when generating "removed.txt", when you only want to match members
     * that have actually been removed.
     */
    private val matchRemoved: Boolean = false,

    /** Configuration that may be provided by command line options. */
    config: Config,
) : FilterPredicate {
    /** Predicate that only matches items belonging to [Config.apiSurface] for delta generation. */
    private val surfacePredicate = ApiSurfacePredicate.forDelta(config.apiSurface, matchRemoved)

    /** True if [Config.apiSurface] is a delta surface, i.e. it extends another [ApiSurface]. */
    private val isDeltaSurface = config.apiSurface.extends != null

    /**
     * Contains configuration for [ApiPredicate] that can, or at least could, come from command line
     * options.
     */
    data class Config(
        /** The [ApiSurface] that this predicate is for. */
        val apiSurface: ApiSurface = ApiSurfaces.DEFAULT.main,

        /**
         * Whether overriding methods essential for compiling the stubs should be considered as APIs
         * or not.
         */
        val addAdditionalOverrides: Boolean = false,
    )

    override fun test(item: SelectableItem): Boolean {
        val itemSelectors = item.variantSelectors

        // If the item or any of its containing classes are inaccessible then ignore it.
        if (!itemSelectors.accessible) return false

        val hidden = itemSelectors.hidden
        if (hidden) return false

        // If this surface is a delta surface extending another surface and a class's superclass
        // is part of this surface's delta, the class itself must be emitted in this surface's
        // signature file as well (even if the class is not part of this delta) to accurately
        // reveal the class hierarchy (i.e. that it extends this superclass), which was concealed
        // in the base API surface.
        //
        // This only applies when [isDeltaSurface] is true, because in a base/root surface (like
        // public) the hierarchy was never concealed, and every unhidden class's superclass (such
        // as java.lang.Object) would otherwise match surfacePredicate and cause all classes to be
        // included.
        //
        // Using surfacePredicate ensures the superclass belongs directly to this surface's delta
        // rather than a contributing base surface. Only the class definition is marked visible;
        // its members are tested separately and will not be included in the delta.
        if (
            isDeltaSurface &&
                item is ClassItem &&
                item.superClass()?.let { surfacePredicate.test(it) } == true
        ) {
            return itemSelectors.removed == matchRemoved
        }

        // Check whether this item belongs to the target API surface delta. This excludes items
        // that only belong to contributing base surfaces or are docOnly.
        return surfacePredicate.test(item)
    }
}
