/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.tools.metalava.model.psi

import com.android.tools.metalava.kotlin
import com.android.tools.metalava.model.VisibilityLevel
import kotlin.test.Test
import kotlin.test.assertEquals

class PsiModifierItemTest {
    @Test
    fun `Kotlin implicit internal visibility inheritance`() {
        testCodebase(
            kotlin(
                """
                    open class Base {
                        internal open fun method(): Int = 1
                        internal open val property: Int = 2
                    }

                    class Inherited : Base() {
                        override fun method(): Int = 3
                        override val property = 4
                    }
                """
            )
        ) { codebase ->
            val method = codebase.assumeClass("Inherited").methods()
                .first { it.name().startsWith("method") }
            val property = codebase.assumeClass("Inherited").properties().single()

            assertEquals(VisibilityLevel.INTERNAL, method.modifiers.getVisibilityLevel())
            assertEquals(VisibilityLevel.INTERNAL, property.modifiers.getVisibilityLevel())
        }
    }
}
