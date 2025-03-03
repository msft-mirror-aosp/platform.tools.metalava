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

package com.android.tools.metalava

import com.android.tools.metalava.model.ANDROID_FLAGGED_API
import com.android.tools.metalava.model.ANDROID_SYSTEM_API
import com.android.tools.metalava.model.api.flags.ApiFlag
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class ApiFlagsCreatorTest {
    @Test
    fun `Test empty --revert-annotation value list`() {
        val apiFlags = ApiFlagsCreator.createFromRevertAnnotations(emptyList())
        assertNull(apiFlags)
    }

    @Test
    fun `Test invalid --revert-annotation value`() {
        val exception =
            assertThrows(IllegalStateException::class.java) {
                ApiFlagsCreator.createFromRevertAnnotations(listOf(ANDROID_SYSTEM_API))
            }
        assertEquals(
            "Unexpected --revert-annotation: android.annotation.SystemApi",
            exception.message
        )
    }

    @Test
    fun `Test revert all`() {
        val apiFlags = ApiFlagsCreator.createFromRevertAnnotations(listOf(ANDROID_FLAGGED_API))
        assertNotNull(apiFlags)
        assertEquals(emptyMap(), apiFlags.byQualifiedName)
    }

    @Test
    fun `Test finalize some, revert others`() {
        val flagName = "test.pkg.flags.flag_name"
        val apiFlags =
            ApiFlagsCreator.createFromRevertAnnotations(
                listOf(ANDROID_FLAGGED_API, """!$ANDROID_FLAGGED_API("$flagName")""")
            )
        assertNotNull(apiFlags)
        assertEquals(ApiFlag.FINALIZE_FLAGGED_API, apiFlags[flagName])
        assertEquals(ApiFlag.REVERT_FLAGGED_API, apiFlags["test.pkg.flags.other_flag_name"])
    }
}
