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

package com.android.tools.metalava.apilevels

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class SdkVersionTest {
    @Test
    fun `Test sorting`() {
        val versions =
            listOf(
                SdkVersion.fromString("10.0"),
                SdkVersion.fromString("10.0.1"),
                SdkVersion.fromString("10"),
                SdkVersion.fromString("10.0.1-beta"),
                SdkVersion.fromString("1"),
                SdkVersion.fromString("10.0.1-alpha"),
                SdkVersion.fromString("5"),
            )

        assertThat(versions.sorted())
            .isEqualTo(
                listOf(
                    SdkVersion.fromString("1"),
                    SdkVersion.fromString("5"),
                    SdkVersion.fromString("10"),
                    SdkVersion.fromString("10.0"),
                    SdkVersion.fromString("10.0.1-alpha"),
                    SdkVersion.fromString("10.0.1-beta"),
                    SdkVersion.fromString("10.0.1"),
                )
            )
    }
}