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

package com.android.tools.metalava.model.testsuite

import com.android.tools.metalava.model.ModelOptions
import java.util.Locale

/** Interface that needs to be implemented by tests that need access to [ModelProviderTestInfo]. */
interface ModelProviderAwareTest {

    var modelProviderTestInfo: ModelProviderTestInfo

    /** The [ModelSuiteRunner] that this test must use. */
    val runner
        get() = modelProviderTestInfo.runner

    /**
     * The [InputFormat] of the test files that should be processed by this test. It must ignore all
     * other [InputFormat]s.
     */
    val inputFormat
        get() = modelProviderTestInfo.inputFormat

    /** Encapsulates all the configuration for the [BaseModelTest] */
    data class ModelProviderTestInfo(
        /** The [ModelSuiteRunner] to use. */
        val runner: ModelSuiteRunner,
        val inputFormat: InputFormat,
        val modelOptions: ModelOptions = ModelOptions.empty,
    ) {
        /** Override this to return the string that will be used in the test name. */
        override fun toString(): String = buildString {
            append(runner)
            append(",")
            append(inputFormat.name.lowercase(Locale.US))
            if (modelOptions != ModelOptions.empty) {
                append(",")
                append(modelOptions)
            }
        }
    }
}
