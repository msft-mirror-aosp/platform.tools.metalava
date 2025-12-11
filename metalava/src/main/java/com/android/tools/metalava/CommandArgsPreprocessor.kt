/*
 * Copyright (C) 2020 The Android Open Source Project
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

import com.android.tools.metalava.cli.common.ExecutionEnvironment
import com.intellij.util.execution.ParametersListUtil

/**
 * Preprocess command line arguments.
 * 1. Prepend/append {@code ENV_VAR_METALAVA_PREPEND_ARGS} and {@code ENV_VAR_METALAVA_PREPEND_ARGS}
 */
internal fun preprocessArgv(
    executionEnvironment: ExecutionEnvironment,
    args: Array<String>
): Array<String> {
    return if (!executionEnvironment.isUnderTest()) {
        val prepend = envVarToArgs(ENV_VAR_METALAVA_PREPEND_ARGS)
        val append = envVarToArgs(ENV_VAR_METALAVA_APPEND_ARGS)
        if (prepend.isEmpty() && append.isEmpty()) {
            args
        } else {
            prepend + args + append
        }
    } else {
        args
    }
}

/**
 * Given an environment variable name pointing to a shell argument string, returns the parsed
 * argument strings (or empty array if not set)
 */
private fun envVarToArgs(varName: String): Array<String> {
    val value = System.getenv(varName) ?: return emptyArray()
    return ParametersListUtil.parse(value).toTypedArray()
}
