/*
 * Copyright (C) 2026 The Android Open Source Project
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

package com.android.tools.metalava.model.api

import com.android.tools.metalava.model.ClassOrigin
import com.android.tools.metalava.model.FilterPredicate
import com.android.tools.metalava.model.MethodItem
import com.android.tools.metalava.model.api.surface.ApiSurface
import com.android.tools.metalava.model.api.surface.ApiSurfacePredicate
import com.android.tools.metalava.model.api.surface.ApiVariantSet
import com.android.tools.metalava.model.api.surface.ApiVariantType
import com.android.tools.metalava.model.visitors.ApiType
import com.android.tools.metalava.reporter.Issues

/**
 * Selected API class for methods, ensuring record component getter methods inherit the parent
 * class's API variants.
 */
internal class MethodSelectedApi(
    selectedApiUpdater: SelectedApiUpdater,
    item: MethodItem,
) : MemberSelectedApi<MethodItem>(selectedApiUpdater, item) {

    override fun itemSpecificInitialization() {
        // Record components are not separately selectable so need special initialization.
        if (item.isRecordComponentGetter) {
            initializeRecordComponent(item.recordComponentRelationship!!)
            return
        }

        super.itemSpecificInitialization()

        // If the parent is not part of any API then the method cannot be either.
        if (parent.itemApiVariants.isEmpty()) return

        // Unlike classes and fields, methods implicitly inherit API surface membership from the
        // methods they override (e.g. an unannotated or @Hide method implementing a public
        // interface method). If this method did not directly specify any API variants, find the
        // widest API surface inherited from overridden super methods.
        if (itemApiVariants.isEmpty()) {
            val apiSurfaces = selectedApiUpdater.apiSurfaces
            val parentSurface = parent.itemApiVariants.widestSurfaceFor(apiSurfaces) ?: return
            var maxSuperSurface: ApiSurface? = null
            var maxSuperVariants = ApiVariantSet.EMPTY

            for (superMethod in item.superMethods()) {
                val superVariants = superMethod.selectedApi.itemApiVariants
                val superSurface = superVariants.narrowestSurfaceFor(apiSurfaces) ?: continue

                // Do not inherit removed or doconly status from overridden methods.
                val superCoreVariant = superSurface.variantFor(ApiVariantType.CORE)
                if (superCoreVariant !in superVariants) {
                    continue
                }

                // A method can only inherit API variants from a super method in a narrower API
                // surface than the containing class.
                if (superSurface >= parentSurface) {
                    continue
                }

                // Find the widest API surface among the super methods.
                if (maxSuperSurface == null || superSurface > maxSuperSurface) {
                    maxSuperSurface = superSurface
                    maxSuperVariants = superVariants
                }

                // Stop searching if maxSuperSurface is at least as wide as the main surface being
                // generated.
                if (maxSuperSurface >= apiSurfaces.main) {
                    break
                }
            }

            // Adopt the API variants from the super method in the widest surface found.
            if (maxSuperSurface != null) {
                itemApiVariants = maxSuperVariants
            } else {
                checkHidingApiMethodOverride()
            }
        }
    }

    /**
     * Checks whether a method attempts to hide an override of a method that is already part of the
     * API.
     *
     * It is an error to attempt to hide a method in a class if the method overrides an API method
     * in a superclass or interface, unless hiding from a narrower API surface (e.g. a public class
     * hiding a system API override).
     */
    private fun checkHidingApiMethodOverride() {
        if (revert) return
        val reporter = item.codebase.reporter
        if (reporter.isSuppressed(Issues.HIDING_API_METHOD_OVERRIDE)) return

        val apiSurfaces = selectedApiUpdater.apiSurfaces
        val filterReference = ApiSurfacePredicate.wholeCoreApi(apiSurfaces.main)
        val removedFilterPredicate =
            ApiSurfacePredicate.apiFilters(
                    ApiType.REMOVED,
                    ApiSurfacePredicate.Config(apiSurface = apiSurfaces.main),
                )
                .emit

        // Check to see if the method overrides an API method, if so report an issue.
        if (
            !reportIfOverridingApiMethod(filterReference) { method, overriddenMethod ->
                "Attempting to hide ${method.describe()} which overrides ${overriddenMethod.describe()} which is already part of the API"
            }
        ) {
            // Check to see if the method overrides a method that was previously part of the API
            // but has since been removed.
            reportIfOverridingApiMethod(removedFilterPredicate) { method, overriddenMethod ->
                "Attempting to hide ${method.describe()} which overrides ${overriddenMethod.describe()} which was part of the API but has now been removed"
            }
        }
    }

    /**
     * Report an [Issues.HIDING_API_METHOD_OVERRIDE] issue if this method overrides a method that
     * matches [predicate].
     *
     * If the overridden method is from the class path then do not report an error as it may not be
     * possible to determine if a method in a jar matches a specific API version.
     *
     * Do not report an error if a final class hides a protected method from its superclass, as
     * there is no way to call a method of a final class through a protected method of the
     * superclass.
     */
    private inline fun reportIfOverridingApiMethod(
        predicate: FilterPredicate,
        reportMessageProvider: (MethodItem, MethodItem) -> String,
    ): Boolean =
        item
            .findPredicateSuperMethod(predicate)
            ?.takeIf { overriddenMethod ->
                overriddenMethod.origin != ClassOrigin.CLASS_PATH &&
                    !(item.containingClass().modifiers.isFinal() &&
                        overriddenMethod.modifiers.isProtected())
            }
            ?.also { overriddenMethod ->
                item.codebase.reporter.report(
                    Issues.HIDING_API_METHOD_OVERRIDE,
                    item,
                    reportMessageProvider(item, overriddenMethod),
                )
            } != null
}
