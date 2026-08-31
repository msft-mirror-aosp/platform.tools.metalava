/*
 * Copyright (C) 2020 The Android Open Source Project
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
import com.android.tools.metalava.model.MethodItem
import com.android.tools.metalava.model.SelectableItem

/**
 * Filter that will elide exact duplicate methods that are already included in another
 * superclass/interfaces.
 */
class ElidingPredicate(
    private val wrapped: FilterPredicate,

    /** Whether overriding methods essential for compiling the stubs should be elided or not. */
    private val addAdditionalOverrides: Boolean,
) : FilterPredicate {

    // Returning true means we are keeping this item
    // i.e. when this returns false, we are eliding the item
    override fun test(item: SelectableItem): Boolean {
        // This method should be included, but if it's an exact duplicate
        // override then we can elide it.
        return if (item is MethodItem) {
            val duplicateSuper = findDuplicateSuperMethod(item, item)

            val doNotElideForAdditionalOverridePurpose =
                addAdditionalOverrides && item.isRequiredOverridingMethodForTextStub()

            duplicateSuper == null || doNotElideForAdditionalOverridePurpose
        } else {
            true
        }
    }

    /** Search for a super method of [item] in the reference API that has an identical signature. */
    private fun findDuplicateSuperMethod(item: MethodItem, current: MethodItem): MethodItem? {
        val superMethods = current.superMethods()
        for (superMethod in superMethods) {
            // Check if this super method is included in the reference API.
            if (wrapped.test(superMethod)) {
                // If it is in the API and has the exact same signature, we found a duplicate
                // super method that allows this item to be elided.
                if (
                    MethodItem.sameSignature(
                        item,
                        superMethod,
                        addAdditionalOverrides = addAdditionalOverrides,
                    )
                ) {
                    return superMethod
                }
                // If item is abstract and this included super method is concrete, item is
                // explicitly re-abstracting a concrete method. Do not search further up this
                // inheritance path because finding an abstract ancestor (e.g. in a grandparent
                // interface) would incorrectly cause item to be elided.
                if (item.modifiers.isAbstract() && !superMethod.modifiers.isAbstract()) {
                    continue
                }
            }
            // If the super method is not in the API (e.g. from an inaccessible class or interface),
            // or if it did not match, recursively search its super methods for an ancestor in the
            // API.
            val found = findDuplicateSuperMethod(item, superMethod)
            if (found != null) {
                return found
            }
        }
        return null
    }
}
