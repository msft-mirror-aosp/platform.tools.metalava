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

package com.android.tools.metalava.model.testsuite.multiplatform

import com.android.tools.metalava.model.testing.FilterAction.EXCLUDE
import com.android.tools.metalava.model.testing.FilterByProvider
import com.android.tools.metalava.model.testsuite.BaseModelTest
import com.android.tools.metalava.testing.createAndroidModuleDescription
import com.android.tools.metalava.testing.createCommonModuleDescription
import com.android.tools.metalava.testing.createNativeModuleDescription
import com.android.tools.metalava.testing.createProjectDescription
import com.android.tools.metalava.testing.kotlin
import com.google.common.truth.Truth.assertThat
import org.junit.Test

@FilterByProvider("psi", "k1", action = EXCLUDE)
class CommonMultiplatformCodebaseTest : BaseModelTest() {
    @Test
    fun `Test multiplatform codebase with single source set`() {
        val androidSource =
            kotlin(
                "androidMain/src/test/pkg/Foo.kt",
                """
                package test.pkg
                class Foo
                """
            )
        runMultiplatformCodebaseTest(
            inputSet(androidSource),
            projectDescription =
                createProjectDescription(
                    createAndroidModuleDescription(arrayOf(androidSource), dependsOn = emptyList())
                )
        ) {
            assertThat(multiplatformCodebase.sourceSets).containsExactly("androidMain")
        }
    }

    @Test
    fun `Test multiplatform codebase with multiple source sets`() {
        val commonSource =
            kotlin(
                "common/src/test/pkg/Foo.kt",
                """
                package test.pkg
                expect class F00
                """
            )
        val androidSource =
            kotlin(
                "androidMain/src/test/pkg/Foo_android.kt",
                """
                package test.pkg
                actual class Foo
                """
            )
        val nativeSource =
            kotlin(
                "nativeMain/src/test/pkg/Foo_native.kt",
                """
                package test.pkg
                actual class Foo
                """
            )

        runMultiplatformCodebaseTest(
            inputSet(commonSource, androidSource, nativeSource),
            projectDescription =
                createProjectDescription(
                    createCommonModuleDescription(arrayOf(commonSource)),
                    createAndroidModuleDescription(arrayOf(androidSource)),
                    createNativeModuleDescription(arrayOf(nativeSource)),
                )
        ) {
            assertThat(multiplatformCodebase.sourceSets)
                .containsExactly("commonMain", "androidMain", "nativeMain")
        }
    }
}
