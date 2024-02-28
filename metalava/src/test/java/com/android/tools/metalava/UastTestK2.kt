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
import org.junit.Ignore
import org.junit.Test

@FilterByProvider("psi", "k1", action = EXCLUDE)
class UastTestK2 : UastTestBase() {

    @Test
    fun `Test RequiresOptIn and OptIn -- K2`() {
        `Test RequiresOptIn and OptIn`()
    }

    @Test
    fun `renamed via @JvmName -- K2`() {
        `renamed via @JvmName`()
    }

    @Test
    fun `Kotlin Reified Methods -- K2`() {
        `Kotlin Reified Methods`()
    }

    @Test
    fun `Annotation on parameters of data class synthetic copy -- K2`() {
        `Annotation on parameters of data class synthetic copy`()
    }

    @Test
    fun `declarations with value class in its signature -- K2`() {
        `declarations with value class in its signature`()
    }

    @Test
    fun `non-last vararg type -- K2`() {
        `non-last vararg type`()
    }

    @Test
    fun `implements Comparator -- K2`() {
        `implements Comparator`()
    }

    @Test
    fun `constant in file-level annotation -- K2`() {
        `constant in file-level annotation`()
    }

    @Test
    fun `final modifier in enum members -- K2`() {
        `final modifier in enum members`()
    }

    @Test
    fun `lateinit var as mutable bare field -- K2`() {
        `lateinit var as mutable bare field`()
    }

    @Test
    fun `Upper bound wildcards -- enum members -- K2`() {
        `Upper bound wildcards -- enum members`()
    }

    @Test
    fun `Upper bound wildcards -- type alias -- K2`() {
        `Upper bound wildcards -- type alias`()
    }

    @Test
    fun `Upper bound wildcards -- extension function type -- K2`() {
        `Upper bound wildcards -- extension function type`()
    }

    @Test
    fun `boxed type argument as method return type -- K2`() {
        `boxed type argument as method return type`()
    }

    @Test
    fun `setter returns this with type cast -- K2`() {
        `setter returns this with type cast`()
    }

    @Test
    fun `suspend fun in interface -- K2`() {
        `suspend fun in interface`()
    }

    @Test
    fun `nullable return type via type alias -- K2`() {
        `nullable return type via type alias`()
    }

    @Test
    fun `IntDef with constant in companion object -- K2`() {
        `IntDef with constant in companion object`()
    }

    @Test
    fun `APIs before and after @Deprecated(HIDDEN) on properties or accessors -- K2`() {
        `APIs before and after @Deprecated(HIDDEN) on properties or accessors`()
    }

    @Test
    fun `actual typealias -- without value class -- K2`() {
        `actual typealias -- without value class`()
    }

    @Test
    fun `actual typealias -- without common split -- K2`() {
        `actual typealias -- without common split`()
    }

    @Ignore("b/324521456: need to set kotlin-stdlib-common for common module")
    @Test
    fun `actual typealias -- K2`() {
        `actual typealias`()
    }
}
