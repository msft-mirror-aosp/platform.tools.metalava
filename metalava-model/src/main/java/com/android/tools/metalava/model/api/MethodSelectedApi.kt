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

import com.android.tools.metalava.model.MethodItem
import com.android.tools.metalava.model.api.surface.ApiSurface
import com.android.tools.metalava.model.api.surface.ApiVariantSet
import com.android.tools.metalava.model.api.surface.ApiVariantType

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
            }
        }
    }
}
