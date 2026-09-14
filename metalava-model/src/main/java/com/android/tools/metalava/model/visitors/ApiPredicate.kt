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

import com.android.tools.metalava.model.FilterPredicate
import com.android.tools.metalava.model.SelectableItem
import com.android.tools.metalava.model.api.surface.ApiSurface
import com.android.tools.metalava.model.api.surface.ApiSurfacePredicate

/**
 * Predicate that decides if the given member should be considered part of an API surface area.
 *
 * It only matches items that are in the [ApiSurfacePredicate.Config.apiSurface]. If that extends
 * another [ApiSurface] then this will not match items that are part of the extended [ApiSurface]
 * (except for classes whose superclass is in [ApiSurfacePredicate.Config.apiSurface], which are
 * included to reveal the class hierarchy).
 */
class ApiPredicate(
    /**
     * Set what the value of [SelectableItem.removed] must be equal to in order for a member to
     * match.
     *
     * This is typically useful when generating "removed.txt", when you only want to match members
     * that have actually been removed.
     */
    matchRemoved: Boolean = false,

    /** Configuration that may be provided by command line options. */
    config: ApiSurfacePredicate.Config,
) : FilterPredicate {
    /**
     * Predicate that only matches items belonging to [ApiSurfacePredicate.Config.apiSurface] for
     * delta generation.
     */
    private val surfacePredicate = ApiSurfacePredicate.forDelta(config.apiSurface, matchRemoved)

    override fun test(item: SelectableItem): Boolean {
        // Check whether this item belongs to the target API surface delta. This excludes items
        // that only belong to contributing base surfaces or are docOnly.
        return surfacePredicate.test(item)
    }
}
