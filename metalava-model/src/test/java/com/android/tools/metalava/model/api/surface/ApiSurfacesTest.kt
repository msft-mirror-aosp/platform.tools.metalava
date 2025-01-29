/*
 * Copyright (C) 2025 The Android Open Source Project
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

import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import org.junit.Test

class ApiSurfacesTest {
    /**
     * Check for basic self-consistency.
     *
     * This does not place any requirements on whether [ApiSurfaces.base] is `null` or not just that
     * whatever it is that it is consistent.
     */
    private fun ApiSurfaces.assertSelfConsistent() {
        assertTrue(main.isMain, "main is main")
        if (base == null) {
            assertNull(main.extends, "main does not extend anything")
        } else {
            assertSame(base, main.extends, "main extends base")
            assertFalse(base!!.isMain, "base is not main")
        }
    }

    @Test
    fun `Test without base`() {
        val apiSurfaces = ApiSurfaces.create(needsBase = false)
        apiSurfaces.assertSelfConsistent()
        assertNull(apiSurfaces.base, "base not expected")
    }

    @Test
    fun `Test with base`() {
        val apiSurfaces = ApiSurfaces.create(needsBase = true)
        apiSurfaces.assertSelfConsistent()
        assertNotNull(apiSurfaces.base, "base is expected")
    }
}
