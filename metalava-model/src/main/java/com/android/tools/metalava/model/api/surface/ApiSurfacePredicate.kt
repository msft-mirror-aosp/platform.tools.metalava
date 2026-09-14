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
import com.android.tools.metalava.model.api.SelectedApi
import com.android.tools.metalava.model.visitors.ApiFilters
import com.android.tools.metalava.model.visitors.ApiType
import com.android.tools.metalava.model.visitors.ApiVisitor
import com.android.tools.metalava.model.visitors.ElidingPredicate
import com.android.tools.metalava.model.visitors.MatchOverridingMethodPredicate

/** Factory for creating [FilterPredicate] instances based on [ApiSurface]s and [ApiVariant]s. */
object ApiSurfacePredicate {
    /**
     * Contains configuration for predicates that can, or at least could, come from command line
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
     * [apiSurface] delta.
     *
     * If [forRemoved] is true then it will only match variants of type [ApiVariantType.REMOVED]
     * else it will only match variants of type [ApiVariantType.CORE].
     *
     * Unlike [forStubs], this only matches items in [apiSurface] itself, not any surface that it
     * extends. In addition to matching items that directly belong to [apiSurface], it also matches
     * classes that inherit variants from a super class belonging to [apiSurface] via
     * [SelectedApi.superClassApiVariants].
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

        return DeltaVariantsPredicate(inclusionMask)
    }

    /**
     * A [FilterPredicate] that matches an item if it belongs to at least one [ApiVariant] matching
     * [inclusionMask].
     *
     * Matches an item if:
     * * The item itself belongs to a matching variant via [SelectedApi.itemApiVariants].
     * * The item is a class whose super class belongs to a matching variant via
     *   [SelectedApi.superClassApiVariants]. This ensures that classes extending a super class in
     *   this delta surface are included in signature files to accurately reveal the inheritance
     *   hierarchy.
     */
    private class DeltaVariantsPredicate(private val inclusionMask: Int) : FilterPredicate {
        override fun test(t: SelectableItem) =
            t.selectedApi.itemApiVariants.bits and inclusionMask != 0 ||
                t.selectedApi.superClassApiVariants.bits and inclusionMask != 0
    }

    /**
     * Return a [FilterPredicate] that matches items belonging to the [apiSurface] delta for the
     * given [apiType] and marked for emission.
     *
     * Does not elide matching method overrides.
     */
    fun nonElidingFilter(apiType: ApiType, apiSurface: ApiSurface): FilterPredicate =
        // Only items marked for emission should appear in the signature file.
        EMITTED_ONLY.and(
            forDelta(
                apiSurface = apiSurface,
                forRemoved = apiType == ApiType.REMOVED,
            )
        )

    /**
     * Return a [FilterPredicate] for emitting API items for the given [apiType] using the
     * configuration in [apiPredicateConfig].
     *
     * Unlike [nonElidingFilter], this will elide method overrides that match the overridden method,
     * unless configured to include additional overrides via [Config.addAdditionalOverrides].
     */
    fun emitFilter(apiType: ApiType, apiPredicateConfig: Config): FilterPredicate {
        val nonElidingFilter =
            MatchOverridingMethodPredicate(nonElidingFilter(apiType, apiPredicateConfig.apiSurface))
        val referenceFilter = referenceFilter(apiType, apiPredicateConfig.apiSurface)
        return nonElidingFilter.and(elidingPredicate(referenceFilter, apiPredicateConfig))
    }

    /**
     * Return a [FilterPredicate] matching types that can be referenced by APIs of the given
     * [apiType] across [apiSurface] and any surface that it extends.
     */
    fun referenceFilter(apiType: ApiType, apiSurface: ApiSurface): FilterPredicate =
        when (apiType) {
            ApiType.PUBLIC_API ->
                // Emitted APIs can reference types (such as superclasses, interfaces, parameter
                // types, or thrown exceptions) that belong to any API surface extended by the
                // target surface, so references must match across the whole API surface.
                wholeCoreApi(apiSurface)
            ApiType.REMOVED ->
                // References in removed APIs can refer to types across the whole API surface.
                wholeCoreAndRemovedApi(apiSurface)
        }

    /**
     * Create an [ElidingPredicate] that wraps [wrappedPredicate] and uses information from the
     * [apiPredicateConfig].
     */
    private fun elidingPredicate(
        wrappedPredicate: FilterPredicate,
        apiPredicateConfig: Config,
    ) =
        ElidingPredicate(
            wrappedPredicate,
            addAdditionalOverrides = apiPredicateConfig.addAdditionalOverrides,
        )

    /**
     * Return the [ApiFilters] for [apiType] using information from [apiPredicateConfig] to
     * customize their behavior.
     *
     * The returned [ApiFilters.emit] will elide method overrides that match the overridden method.
     */
    fun apiFilters(apiType: ApiType, apiPredicateConfig: Config) =
        ApiFilters(
            reference = referenceFilter(apiType, apiPredicateConfig.apiSurface),
            emit = emitFilter(apiType, apiPredicateConfig),
        )

    /**
     * Return the [ApiFilters] for [apiType] using information from [apiPredicateConfig] to
     * customize their behavior.
     *
     * The returned [ApiFilters.emit] will NOT elide method overrides that match the overridden
     * method.
     */
    fun nonElidingApiFilters(apiType: ApiType, apiPredicateConfig: Config) =
        ApiFilters(
            reference = referenceFilter(apiType, apiPredicateConfig.apiSurface),
            emit = nonElidingFilter(apiType, apiPredicateConfig.apiSurface),
        )
}
