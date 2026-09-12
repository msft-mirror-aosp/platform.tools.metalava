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

package com.android.tools.metalava.model.api.surface

import com.android.tools.metalava.model.EMITTED_ONLY
import com.android.tools.metalava.model.FilterPredicate
import com.android.tools.metalava.model.SelectableItem
import com.android.tools.metalava.model.visitors.ApiVisitor

/** Factory for creating [FilterPredicate] instances based on [ApiSurface]s and [ApiVariant]s. */
object ApiSurfacePredicate {
    /** Singleton instance of [WholeApiPredicate]. */
    private val WHOLE_API_PREDICATE: FilterPredicate = WholeApiPredicate()

    /**
     * Return a [FilterPredicate] that matches any item that belongs to at least one [ApiVariant]
     * across the whole API surface.
     *
     * Only matches items for which [SelectableItem.emit] is true, filtering out non-emittable items
     * such as external classpath dependencies (e.g. `java.lang.Object`) that are not part of the
     * emitted API even if they have been assigned API variants during traversal.
     */
    fun wholeApi() = WHOLE_API_PREDICATE

    /**
     * A [FilterPredicate] that matches an item if it belongs to at least one [ApiVariant] across
     * all [ApiSurface]s.
     */
    private class WholeApiPredicate : FilterPredicate {
        override fun test(t: SelectableItem) = t.selectedApi.itemApiVariants.isNotEmpty()
    }

    /** [ApiVariantType]s for core-only APIs. */
    private val coreOnlyVariantTypes = listOf(ApiVariantType.CORE)

    /** [ApiVariantType]s for core APIs and doc-only APIs. */
    private val corePlusDocOnlyVariantTypes = listOf(ApiVariantType.CORE, ApiVariantType.DOC_ONLY)

    /** [ApiVariantType]s for core APIs and removed APIs. */
    private val corePlusRemovedVariantTypes = listOf(ApiVariantType.CORE, ApiVariantType.REMOVED)

    /**
     * Return a [FilterPredicate] that matches any item that belongs to the core [ApiVariant] of
     * [apiSurface] or any surface that it includes.
     */
    fun wholeCoreApi(apiSurface: ApiSurface) = wholeApiForVariants(apiSurface, coreOnlyVariantTypes)

    /**
     * Return a [FilterPredicate] that matches any item that belongs to the core [ApiVariant] of
     * [apiSurface] or any surface that it includes.
     *
     * Only matches items for which [SelectableItem.emit] is true, filtering out non-emittable items
     * such as external classpath dependencies (e.g. `java.lang.Object`) that are not part of the
     * emitted API even if they have been assigned API variants during traversal.
     */
    fun wholeCoreEmittableApi(apiSurface: ApiSurface): FilterPredicate =
        EMITTED_ONLY.and(wholeCoreApi(apiSurface))

    /**
     * Return a [FilterPredicate] that matches any item that belongs to the core or removed
     * [ApiVariant] of [apiSurface] or any surface that it includes.
     */
    fun wholeCoreAndRemovedApi(apiSurface: ApiSurface) =
        wholeApiForVariants(apiSurface, corePlusRemovedVariantTypes)

    /**
     * Return a [FilterPredicate] that matches any item that belongs to any of [variantTypes] of
     * [apiSurface] or any surface that it includes.
     */
    private fun wholeApiForVariants(
        apiSurface: ApiSurface,
        variantTypes: List<ApiVariantType>,
    ): FilterPredicate {
        val surfacesToInclude = apiSurface.includedSurfaces

        val variants = buildList {
            for (surface in surfacesToInclude) {
                for (variantType in variantTypes) {
                    add(surface.variantFor(variantType))
                }
            }
        }

        val inclusionMask = apiSurface.surfaces.createVariantSet(variants).bits

        return ItemApiVariantsPredicate(inclusionMask)
    }

    /**
     * A [FilterPredicate] that matches an item if it belongs to at least one [ApiVariant] matching
     * [inclusionMask].
     */
    private class ItemApiVariantsPredicate(private val inclusionMask: Int) : FilterPredicate {
        override fun test(t: SelectableItem) =
            t.selectedApi.itemApiVariants.bits and inclusionMask != 0
    }

    /**
     * Return a [FilterPredicate] for stub generation that matches any item that belongs to the core
     * [ApiVariant] (and optionally [ApiVariantType.DOC_ONLY] if [includeDocOnly] is `true`) of
     * [apiSurface] or any surface that it includes.
     */
    fun forStubs(
        apiSurface: ApiSurface,
        includeDocOnly: Boolean,
    ): FilterPredicate {
        val variantTypes = if (includeDocOnly) corePlusDocOnlyVariantTypes else coreOnlyVariantTypes
        return wholeApiForVariants(apiSurface, variantTypes)
    }

    /**
     * Return a [FilterPredicate] for matching only that part of the whole API that belongs to the
     * [apiSurface].
     *
     * If [forRemoved] is true then it will only match variants of type [ApiVariantType.REMOVED]
     * else it will only match variants of type [ApiVariantType.CORE].
     *
     * Unlike [forStubs], this only matches items in [apiSurface] itself, not any surface that it
     * extends.
     *
     * Currently, this only works with subclasses of [ApiVisitor] as it relies on its support for
     * visiting classes that either match the predicate or where one of its members does.
     *
     * TODO(b/512093496): Make it work with ApiSurfaceVisitor.
     */
    fun forDelta(
        apiSurface: ApiSurface,
        forRemoved: Boolean,
    ): FilterPredicate {
        val variantType = if (forRemoved) ApiVariantType.REMOVED else ApiVariantType.CORE
        val variants = listOf(apiSurface.variantFor(variantType))

        val inclusionMask = apiSurface.surfaces.createVariantSet(variants).bits

        return ItemApiVariantsPredicate(inclusionMask)
    }
}
