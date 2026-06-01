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

package com.android.tools.metalava.model.api.surface

import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runners.MethodSorters

@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class ValueApiVariantSetTest {
    private val apiSurfaces = ApiSurfaces.create(needsBase = true)

    private val main = apiSurfaces.main
    private val base = apiSurfaces.base!!

    private val mainCore = main.variantFor(ApiVariantType.CORE)
    private val mainRemoved = main.variantFor(ApiVariantType.REMOVED)
    private val baseRemoved = base.variantFor(ApiVariantType.REMOVED)
    private val baseDocOnly = base.variantFor(ApiVariantType.DOC_ONLY)

    private fun ValueApiVariantSet.format() = formatFor(apiSurfaces)

    @Test
    fun `Test empty variant set`() {
        val variantSet = ValueApiVariantSet.EMPTY

        assertTrue(variantSet.isEmpty(), "isEmpty")
        assertFalse(variantSet.isNotEmpty(), "isNotEmpty")

        for (variant in apiSurfaces.variants) {
            assertFalse(variantSet.contains(variant), "contains($variant)")
        }

        assertEquals("ApiVariantSet[]", variantSet.format(), "empty set")
    }

    @Test
    fun `Test plus and minus variant`() {
        var variantSet = ValueApiVariantSet.EMPTY

        variantSet += mainCore

        assertTrue(variantSet.isNotEmpty(), "isNotEmpty")
        assertEquals("ApiVariantSet[main(C)]", variantSet.format())
        assertTrue(mainCore in variantSet, "main(C) should contain main(CORE)")
        assertFalse(baseRemoved in variantSet, "main(C) should not contain base(REMOVED)")
        assertTrue(variantSet.containsAny(main), "main(C) should contain something from main")
        assertFalse(variantSet.containsAny(base), "main(C) should not contain anything from base")

        variantSet += baseRemoved

        assertEquals("ApiVariantSet[base(R),main(C)]", variantSet.format())
        assertTrue(mainCore in variantSet, "base(R),main(C) should contain main(CORE)")
        assertTrue(baseRemoved in variantSet, "base(R),main(C) should contain base(REMOVED)")
        assertTrue(
            variantSet.containsAny(main),
            "base(R),main(C) should contain something from main"
        )
        assertTrue(
            variantSet.containsAny(base),
            "base(R),main(C) should contain something from base"
        )

        variantSet -= mainCore

        assertEquals("ApiVariantSet[base(R)]", variantSet.format())
        assertFalse(mainCore in variantSet, "base(R) should not contain main(CORE)")
        assertTrue(baseRemoved in variantSet, "base(R) should contain base(REMOVED)")
        assertFalse(variantSet.containsAny(main), "base(R) should not contain anything from main")
        assertTrue(variantSet.containsAny(base), "base(R) should contain something from base")

        variantSet -= baseRemoved
        assertTrue(variantSet.isEmpty(), "isEmpty")
    }

    @Test
    fun `Test plus and minus set`() {
        val set1 = apiSurfaces.createVariantSet(mainCore, mainRemoved, baseDocOnly)
        val set2 = apiSurfaces.createVariantSet(mainCore, baseRemoved)

        assertEquals(
            "ApiVariantSet[base(RD),main(CR)]",
            (set1 + set2).format(),
            message = "set1 + set2"
        )
        assertEquals(
            "ApiVariantSet[base(RD),main(CR)]",
            (set2 + set1).format(),
            message = "set2 + set1"
        )

        assertEquals(
            "ApiVariantSet[base(D),main(R)]",
            (set1 - set2).format(),
            message = "set1 - set2"
        )
        assertEquals("ApiVariantSet[base(R)]", (set2 - set1).format(), message = "set2 - set1")
    }
}
