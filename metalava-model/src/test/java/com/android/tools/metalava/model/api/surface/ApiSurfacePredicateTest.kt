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

import com.android.tools.metalava.model.visitors.ApiType
import kotlin.test.assertEquals
import org.junit.Test

/** Tests for [ApiSurfacePredicate]. */
class ApiSurfacePredicateTest {
    private val apiSurfaces = ApiSurfaces.create(needsBase = true)
    private val main = apiSurfaces.main
    private val base = apiSurfaces.base!!

    @Test
    fun `Test wholeCoreApi`() {
        assertEquals(
            "ItemApiVariantsPredicate(ApiVariantSet[base(C)])",
            ApiSurfacePredicate.wholeCoreApi(base).toString(),
        )
        assertEquals(
            "ItemApiVariantsPredicate(ApiVariantSet[base(C),main(C)])",
            ApiSurfacePredicate.wholeCoreApi(main).toString(),
        )
    }

    @Test
    fun `Test wholeCoreAndRemovedApi`() {
        assertEquals(
            "ItemApiVariantsPredicate(ApiVariantSet[base(CR)])",
            ApiSurfacePredicate.wholeCoreAndRemovedApi(base).toString(),
        )
        assertEquals(
            "ItemApiVariantsPredicate(ApiVariantSet[base(CR),main(CR)])",
            ApiSurfacePredicate.wholeCoreAndRemovedApi(main).toString(),
        )
    }

    @Test
    fun `Test forStubs`() {
        assertEquals(
            """
                ApiFilters(
                    emit =
                        AndPredicate(
                            EmittedOnlyPredicate
                            ItemOrSuperMethodApiVariantsPredicate(ApiVariantSet[base(C)])
                        )
                    reference =
                        ItemApiVariantsPredicate(ApiVariantSet[base(C)])
                )
            """
                .trimIndent(),
            ApiSurfacePredicate.forStubs(base, includeDocOnly = false).toString(),
        )
        assertEquals(
            """
                ApiFilters(
                    emit =
                        AndPredicate(
                            EmittedOnlyPredicate
                            ItemOrSuperMethodApiVariantsPredicate(ApiVariantSet[base(CD)])
                        )
                    reference =
                        ItemApiVariantsPredicate(ApiVariantSet[base(CD)])
                )
            """
                .trimIndent(),
            ApiSurfacePredicate.forStubs(base, includeDocOnly = true).toString(),
        )
        assertEquals(
            """
                ApiFilters(
                    emit =
                        AndPredicate(
                            EmittedOnlyPredicate
                            ItemOrSuperMethodApiVariantsPredicate(ApiVariantSet[base(C),main(C)])
                        )
                    reference =
                        ItemApiVariantsPredicate(ApiVariantSet[base(C),main(C)])
                )
            """
                .trimIndent(),
            ApiSurfacePredicate.forStubs(main, includeDocOnly = false).toString(),
        )
        assertEquals(
            """
                ApiFilters(
                    emit =
                        AndPredicate(
                            EmittedOnlyPredicate
                            ItemOrSuperMethodApiVariantsPredicate(ApiVariantSet[base(CD),main(CD)])
                        )
                    reference =
                        ItemApiVariantsPredicate(ApiVariantSet[base(CD),main(CD)])
                )
            """
                .trimIndent(),
            ApiSurfacePredicate.forStubs(main, includeDocOnly = true).toString(),
        )
    }

    @Test
    fun `Test forDelta`() {
        assertEquals(
            "DeltaVariantsPredicate(ApiVariantSet[base(C)])",
            ApiSurfacePredicate.forDelta(base, forRemoved = false).toString(),
        )
        assertEquals(
            "DeltaVariantsPredicate(ApiVariantSet[base(R)])",
            ApiSurfacePredicate.forDelta(base, forRemoved = true).toString(),
        )
        assertEquals(
            "DeltaVariantsPredicate(ApiVariantSet[main(C)])",
            ApiSurfacePredicate.forDelta(main, forRemoved = false).toString(),
        )
        assertEquals(
            "DeltaVariantsPredicate(ApiVariantSet[main(R)])",
            ApiSurfacePredicate.forDelta(main, forRemoved = true).toString(),
        )
    }

    @Test
    fun `Test referenceFilter`() {
        assertEquals(
            "ItemApiVariantsPredicate(ApiVariantSet[base(C)])",
            ApiSurfacePredicate.referenceFilter(ApiType.CORE, base).toString(),
        )
        assertEquals(
            "ItemApiVariantsPredicate(ApiVariantSet[base(CR)])",
            ApiSurfacePredicate.referenceFilter(ApiType.REMOVED, base).toString(),
        )
        assertEquals(
            "ItemApiVariantsPredicate(ApiVariantSet[base(C),main(C)])",
            ApiSurfacePredicate.referenceFilter(ApiType.CORE, main).toString(),
        )
        assertEquals(
            "ItemApiVariantsPredicate(ApiVariantSet[base(CR),main(CR)])",
            ApiSurfacePredicate.referenceFilter(ApiType.REMOVED, main).toString(),
        )
    }

    @Test
    fun `Test forSurfaceFilters`() {
        assertEquals(
            """
                ApiFilters(
                    traversal =
                        AndPredicate(
                            EmittedOnlyPredicate
                            TraversalPredicate(ApiVariantSet[base(C)])
                        )
                    emit =
                        AndPredicate(
                            EmittedOnlyPredicate
                            DeltaVariantsPredicate(ApiVariantSet[base(C)])
                        )
                    reference =
                        ItemApiVariantsPredicate(ApiVariantSet[base(C)])
                )
            """
                .trimIndent(),
            ApiSurfacePredicate.forSurfaceFilters(ApiType.CORE, base).toString(),
        )
        assertEquals(
            """
                ApiFilters(
                    traversal =
                        AndPredicate(
                            EmittedOnlyPredicate
                            TraversalPredicate(ApiVariantSet[base(R)])
                        )
                    emit =
                        AndPredicate(
                            EmittedOnlyPredicate
                            DeltaVariantsPredicate(ApiVariantSet[base(R)])
                        )
                    reference =
                        ItemApiVariantsPredicate(ApiVariantSet[base(CR)])
                )
            """
                .trimIndent(),
            ApiSurfacePredicate.forSurfaceFilters(ApiType.REMOVED, base).toString(),
        )
        assertEquals(
            """
                ApiFilters(
                    traversal =
                        AndPredicate(
                            EmittedOnlyPredicate
                            TraversalPredicate(ApiVariantSet[main(C)])
                        )
                    emit =
                        AndPredicate(
                            EmittedOnlyPredicate
                            DeltaVariantsPredicate(ApiVariantSet[main(C)])
                        )
                    reference =
                        ItemApiVariantsPredicate(ApiVariantSet[base(C),main(C)])
                )
            """
                .trimIndent(),
            ApiSurfacePredicate.forSurfaceFilters(ApiType.CORE, main).toString(),
        )
        assertEquals(
            """
                ApiFilters(
                    traversal =
                        AndPredicate(
                            EmittedOnlyPredicate
                            TraversalPredicate(ApiVariantSet[main(R)])
                        )
                    emit =
                        AndPredicate(
                            EmittedOnlyPredicate
                            DeltaVariantsPredicate(ApiVariantSet[main(R)])
                        )
                    reference =
                        ItemApiVariantsPredicate(ApiVariantSet[base(CR),main(CR)])
                )
            """
                .trimIndent(),
            ApiSurfacePredicate.forSurfaceFilters(ApiType.REMOVED, main).toString(),
        )
    }
}
