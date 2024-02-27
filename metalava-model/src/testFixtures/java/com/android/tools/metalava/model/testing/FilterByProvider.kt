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

package com.android.tools.metalava.model.testing

/**
 * Controls whether a specific class or any of its subclasses will be run for a particular provider.
 *
 * When specified on a class it will determine whether the class or any of its subclasses will be
 * run for the named [provider] unless overridden by a closer annotation on a subclass. Multiple
 * annotations can be applied to each class and the first one that matches a specific provider will
 * win.
 *
 * By default, tests will be run against all providers that are available unless [action] is set to
 * [FilterAction.EXCLUDE].
 */
@Target(AnnotationTarget.CLASS)
@Repeatable
annotation class FilterByProvider(
    val provider: String,
    val action: FilterAction,
) {
    enum class FilterAction {
        /** The test is run for the matching provider. */
        INCLUDE,

        /** The test is not for the matching provider. */
        EXCLUDE,
    }
}
