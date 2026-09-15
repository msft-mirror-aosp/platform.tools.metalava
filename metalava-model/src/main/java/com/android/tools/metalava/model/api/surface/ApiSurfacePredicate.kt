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

    /** [ApiVariantType]s for removed-only APIs. */
    private val removedOnlyVariantTypes = listOf(ApiVariantType.REMOVED)

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
        val inclusionMask =
            computeInclusionMask(
                apiSurface.surfaces,
                apiSurface.includedSurfaces,
                variantTypes,
            )

        return ItemApiVariantsPredicate(apiSurface.surfaces, inclusionMask)
    }

    /**
     * Base class for [FilterPredicate]s that match items based on an [inclusionMask] of
     * [ApiVariant]s.
     */
    abstract class ApiVariantsPredicate(
        private val apiSurfaces: ApiSurfaces,
        protected val inclusionMask: Int,
    ) : FilterPredicate {
        override fun toString() =
            "${javaClass.simpleName}(${ApiVariantSet(inclusionMask).formatFor(apiSurfaces)})"
    }

    /**
     * A [FilterPredicate] that matches an item if it belongs to at least one [ApiVariant] matching
     * [inclusionMask].
     */
    private class ItemApiVariantsPredicate(
        apiSurfaces: ApiSurfaces,
        inclusionMask: Int,
    ) : ApiVariantsPredicate(apiSurfaces, inclusionMask) {
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
        val variantTypes = if (forRemoved) removedOnlyVariantTypes else coreOnlyVariantTypes
        val inclusionMask =
            computeInclusionMask(
                apiSurface.surfaces,
                setOf(apiSurface),
                variantTypes,
            )

        return DeltaVariantsPredicate(apiSurface.surfaces, inclusionMask)
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
    private class DeltaVariantsPredicate(
        apiSurfaces: ApiSurfaces,
        inclusionMask: Int,
    ) : ApiVariantsPredicate(apiSurfaces, inclusionMask) {
        override fun test(t: SelectableItem) =
            t.selectedApi.itemApiVariants.bits and inclusionMask != 0 ||
                t.selectedApi.superClassApiVariants.bits and inclusionMask != 0
    }

    /**
     * A [FilterPredicate] that determines whether an item should be traversed when visiting an API
     * surface delta matching [inclusionMask].
     *
     * Matches an item if:
     * * The item itself belongs to a matching variant via [SelectedApi.itemApiVariants].
     * * The item is a class whose contents belong to a matching variant via
     *   [SelectedApi.contentApiVariants]. This ensures that base classes containing delta members
     *   are traversed rather than skipped, allowing visitors to reach those delta members.
     * * The item is a class whose super class belongs to a matching variant via
     *   [SelectedApi.superClassApiVariants]. This ensures that classes extending a super class in
     *   this delta surface are traversed to accurately reveal the inheritance hierarchy.
     */
    private class TraversalPredicate(apiSurfaces: ApiSurfaces, inclusionMask: Int) :
        ApiVariantsPredicate(apiSurfaces, inclusionMask) {
        override fun test(t: SelectableItem) =
            t.selectedApi.run {
                itemApiVariants.bits and inclusionMask != 0 ||
                    contentApiVariants.bits and inclusionMask != 0 ||
                    superClassApiVariants.bits and inclusionMask != 0
            }
    }

    /**
     * Return [ApiFilters] for traversing and analyzing items belonging to the [apiSurface] delta
     * for the given [apiType].
     *
     * The returned filters:
     * - [ApiFilters.reference]: matches types referenced across the entire API surface hierarchy
     *   (including base surfaces that [apiSurface] extends).
     * - [ApiFilters.emit]: matches items that belong to the [apiSurface] delta and are marked for
     *   emission (without eliding method overrides).
     * - [ApiFilters.traversal]: matches items in the delta, classes containing delta members (via
     *   [SelectedApi.contentApiVariants]), and classes extending delta classes (via
     *   [SelectedApi.superClassApiVariants]), allowing visitors to traverse into base classes that
     *   contain delta members while skipping unrelated items.
     */
    fun forSurfaceFilters(apiType: ApiType, apiSurface: ApiSurface): ApiFilters {
        // Items in this API surface can reference types or paired methods across the whole API
        // surface hierarchy (including base surfaces that this surface extends).
        val reference = referenceFilter(apiType, apiSurface)

        // Create a mask matching the specific variant for this API surface delta.
        val variantType =
            if (apiType == ApiType.REMOVED) ApiVariantType.REMOVED else ApiVariantType.CORE
        val variants = listOf(apiSurface.variantFor(variantType))
        val emitMask = apiSurface.surfaces.createVariantSet(variants).bits

        // Emitted items must belong to this delta (or have a superclass in the delta) and be
        // marked for emission.
        val emit = EMITTED_ONLY.and(DeltaVariantsPredicate(apiSurface.surfaces, emitMask))

        // Traversal includes items in the delta as well as base classes whose contents belong to
        // the delta (via contentApiVariants) so that visitors can visit delta members within
        // base classes.
        val traversal = EMITTED_ONLY.and(TraversalPredicate(apiSurface.surfaces, emitMask))
        return ApiFilters(
            reference = reference,
            emit = emit,
            traversal = traversal,
        )
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
            ApiType.CORE ->
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

    /**
     * Compute the bitmask for the [ApiVariantSet] containing the [variantTypes] of each
     * [ApiSurface] in [includedSurfaces].
     */
    private fun computeInclusionMask(
        apiSurfaces: ApiSurfaces,
        includedSurfaces: Collection<ApiSurface>,
        variantTypes: List<ApiVariantType>,
    ): Int {
        val variants = buildList {
            for (surface in includedSurfaces) {
                for (variantType in variantTypes) {
                    add(surface.variantFor(variantType))
                }
            }
        }

        return apiSurfaces.createVariantSet(variants).bits
    }
}
