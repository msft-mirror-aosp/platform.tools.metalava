/*
 * Copyright (C) 2023 The Android Open Source Project
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

import com.android.tools.metalava.model.testing.FilterAction.EXCLUDE
import com.android.tools.metalava.model.testing.FilterByProvider
import org.junit.Test

@FilterByProvider("psi", "k2", action = EXCLUDE)
class UastTestK1 : UastTestBase() {

    @Test
    fun `Test RequiresOptIn and OptIn -- K1`() {
        `Test RequiresOptIn and OptIn`()
    }

    @Test
    fun `renamed via @JvmName -- K1`() {
        `renamed via @JvmName`()
    }

    @Test
    fun `Kotlin Reified Methods -- K1`() {
        `Kotlin Reified Methods`()
    }

    @Test
    fun `Annotation on parameters of data class synthetic copy -- K1`() {
        `Annotation on parameters of data class synthetic copy`()
    }

    @Test
    fun `declarations with value class in its signature -- K1`() {
        `declarations with value class in its signature`()
    }

    @Test
    fun `non-last vararg type -- K1`() {
        `non-last vararg type`()
    }

    @Test
    fun `implements Comparator -- K1`() {
        `implements Comparator`()
    }

    @Test
    fun `constant in file-level annotation -- K1`() {
        `constant in file-level annotation`()
    }

    @Test
    fun `final modifier in enum members -- K1`() {
        `final modifier in enum members`()
    }

    @Test
    fun `lateinit var as mutable bare field -- K1`() {
        `lateinit var as mutable bare field`()
    }

    @Test
    fun `Upper bound wildcards -- enum members -- K1`() {
        `Upper bound wildcards -- enum members`()
    }

    @Test
    fun `Upper bound wildcards -- type alias -- K1`() {
        `Upper bound wildcards -- type alias`()
    }

    @Test
    fun `Upper bound wildcards -- extension function type -- K1`() {
        `Upper bound wildcards -- extension function type`()
    }

    @Test
    fun `boxed type argument as method return type -- K1`() {
        `boxed type argument as method return type`()
    }

    @Test
    fun `setter returns this with type cast -- K1`() {
        `setter returns this with type cast`()
    }

    @Test
    fun `suspend fun in interface -- K1`() {
        `suspend fun in interface`()
    }

    @Test
    fun `nullable return type via type alias -- K1`() {
        `nullable return type via type alias`()
    }

    @Test
    fun `IntDef with constant in companion object -- K1`() {
        `IntDef with constant in companion object`()
    }

    @Test
    fun `APIs before and after @Deprecated(HIDDEN) on properties or accessors -- K1`() {
        `APIs before and after @Deprecated(HIDDEN) on properties or accessors`()
    }

    @Test
    fun `actual typealias -- without value class -- K1`() {
        `actual typealias -- without value class`()
    }

    @Test
    fun `actual typealias -- without common split -- K1`() {
        `actual typealias -- without common split`()
    }

    @Test
    fun `actual typealias -- K1`() {
        `actual typealias`()
    }
}
