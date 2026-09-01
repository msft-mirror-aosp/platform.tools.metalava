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

import com.android.tools.metalava.model.AnnotationItem
import com.android.tools.metalava.model.ClassItem
import com.android.tools.metalava.model.FilterPredicate
import com.android.tools.metalava.model.MemberItem
import com.android.tools.metalava.model.MethodItem
import com.android.tools.metalava.model.SelectableItem

/**
 * Predicate that decides if the given member should be considered part of an API surface area. To
 * make the most accurate decision, it searches for signals on the member, all containing classes,
 * and all containing packages.
 */
class ApiPredicate(
    /**
     * Set if the value of [MemberItem.removed] should be ignored. That is, this predicate will
     * assume that all encountered members match the "removed" requirement.
     *
     * This is typically useful when generating "removed.txt", when it's okay to reference both
     * current and removed APIs.
     */
    private val ignoreRemoved: Boolean = false,

    /**
     * Set what the value of [MemberItem.removed] must be equal to in order for a member to match.
     *
     * This is typically useful when generating "removed.txt", when you only want to match members
     * that have actually been removed.
     */
    private val matchRemoved: Boolean = false,

    /** Whether we should include doc-only items */
    private val includeDocOnly: Boolean = false,

    /** Whether to include "for stub purposes" APIs. See [AnnotationItem.isShowForStubPurposes] */
    private val includeApisForStubPurposes: Boolean = true,

    /** Configuration that may be provided by command line options. */
    private val config: Config,
) : FilterPredicate {

    /**
     * Contains configuration for [ApiPredicate] that can, or at least could, come from command line
     * options.
     */
    data class Config(
        /**
         * Set if the value of [MemberItem.hasShowAnnotation] should be ignored. That is, this
         * predicate will assume that all encountered members match the "shown" requirement.
         *
         * This is typically useful when generating "current.txt", when no
         * [Options.allShowAnnotations] have been defined.
         */
        val ignoreShown: Boolean = true,

        /**
         * Whether overriding methods essential for compiling the stubs should be considered as APIs
         * or not.
         */
        val addAdditionalOverrides: Boolean = false,
    )

    override fun test(item: SelectableItem): Boolean {
        // non-class, i.e., (literally) member declaration w/o emit flag, e.g., due to `expect`
        // Some [ClassItem], e.g., JvmInline, java.lang.* classes, may not set the emit flag.
        if (item !is ClassItem && !item.emit) {
            return false
        }

        val visibleForAdditionalOverridePurpose =
            if (config.addAdditionalOverrides) {
                item is MethodItem && item.isRequiredOverridingMethodForTextStub()
            } else {
                false
            }

        val itemSelectors = item.variantSelectors

        // If the item or any of its containing classes are inaccessible then ignore it.
        if (!itemSelectors.accessible) return false

        var hidden = itemSelectors.hidden && !visibleForAdditionalOverridePurpose
        if (hidden) return false

        if (!includeApisForStubPurposes && item.includeOnlyForStubPurposes()) {
            return false
        }

        // If a class item's parent class is an api-only annotation marked class,
        // the item should be marked visible as well, in order to provide
        // information about the correct class hierarchy that was concealed for
        // less restricted APIs.
        // Only the class definition is marked visible, and class attributes are
        // not affected.
        if (
            item is ClassItem &&
                item.superClass()?.let {
                    it.hasShowAnnotation() && !it.includeOnlyForStubPurposes()
                } == true
        ) {
            return itemSelectors.removed == matchRemoved
        }

        // If docOnly items are not included and this item is docOnly then ignore it.
        if (!includeDocOnly && itemSelectors.docOnly) return false

        // If removed status is not ignored and this item's status does not match what is required
        // then ignore this item.
        if (!ignoreRemoved && itemSelectors.removed != matchRemoved) return false

        if (!config.ignoreShown && !hasShowAnnotation(item)) {
            return false
        }

        // If any containing class is hidden then ignore this item.
        if (item.anyContainingClass { it.hidden }) {
            return false
        }

        return true
    }

    /**
     * Check if any containing class of this item matches [predicate], traversing from the innermost
     * containing class out to the top-level class.
     */
    private inline fun SelectableItem.anyContainingClass(
        predicate: (ClassItem) -> Boolean,
    ): Boolean {
        var cls = containingClass()
        while (cls != null) {
            if (predicate(cls)) return true
            cls = cls.containingClass()
        }
        return false
    }

    /** Check if this item or any of its containing classes or packages has a show annotation. */
    private fun hasShowAnnotation(item: SelectableItem): Boolean {
        if (item.hasShowAnnotation()) return true

        if (item.anyContainingClass { it.hasShowAnnotation() }) return true

        // Traverse up the package hierarchy to check if this item belongs to a shown package.
        var showPackage = item.containingPackage()
        while (showPackage != null) {
            // If an intermediate package is hidden, it prevents any show annotations on its
            // parent packages from propagating down to this item.
            if (showPackage.hidden) {
                break
            }
            if (showPackage.hasShowAnnotation()) return true
            showPackage = showPackage.containingPackage()
        }

        return false
    }
}
