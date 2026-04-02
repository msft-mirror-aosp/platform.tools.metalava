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

package com.android.tools.metalava.model.annotation.binding

import kotlin.test.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class AnnotationBindingTest {
    @Test
    fun `Test no constructors`() {
        val exception =
            assertThrows(IllegalStateException::class.java) {
                AnnotationBinding(Runnable::class, null)
            }

        assertEquals(
            "Cannot create an instance of java.lang.Runnable as it has no constructors",
            exception.message
        )
    }

    @Suppress("unused")
    class MultipleConstructors(s: String) {
        constructor() : this("")
    }

    @Test
    fun `Test multiple constructors, none annotated`() {
        val exception =
            assertThrows(IllegalStateException::class.java) {
                AnnotationBinding(MultipleConstructors::class, null)
            }

        assertEquals(
            "Found multiple constructors in com.android.tools.metalava.model.annotation.binding.AnnotationBindingTest.MultipleConstructors; please annotate one with @BindingConstructor",
            exception.message
        )
    }

    @Suppress("unused")
    class MultipleAnnotatedConstructors @BindingConstructor constructor(s: String) {
        @BindingConstructor constructor() : this("")
    }

    @Test
    fun `Test multiple constructors, multiple annotated`() {
        val exception =
            assertThrows(IllegalStateException::class.java) {
                AnnotationBinding(MultipleAnnotatedConstructors::class, null)
            }

        assertEquals(
            "Found multiple constructors in com.android.tools.metalava.model.annotation.binding.AnnotationBindingTest.MultipleAnnotatedConstructors that are annotated with @BindingConstructor, please annotate only one",
            exception.message
        )
    }

    @Suppress("unused")
    class OneAnnotatedConstructors(s: String) {
        @BindingConstructor constructor() : this("")
    }

    @Test
    fun `Test multiple constructors, one annotated`() {
        AnnotationBinding(OneAnnotatedConstructors::class, null)
    }
}
