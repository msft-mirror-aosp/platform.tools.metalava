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

/** Version of an SDK, e.g. Android or AndroidX. */
typealias SdkVersion = Int

/** The lowest [SdkVersion], used as the default value when higher versions override lower ones. */
const val SDK_VERSION_LOWEST: SdkVersion = 0

/** The highest [SdkVersion], used as the default value when lower versions override higher ones. */
const val SDK_VERSION_HIGHEST: SdkVersion = Int.MAX_VALUE

/** Get the [SdkVersion] for [level]. */
fun sdkVersionFromLevel(level: Int): SdkVersion = level

/** Version of an SDK extension. */
typealias ExtVersion = Int

/** The highest [ExtVersion], used as the default value when lower versions override higher ones. */
const val EXT_VERSION_HIGHEST: ExtVersion = Int.MAX_VALUE

/** Get the [ExtVersion] for [level]. */
fun extVersionFromLevel(level: Int): ExtVersion = level

/**
 * Make sure that this is a valid version.
 *
 * This is intended to be called on [SdkVersion] and [ExtVersion].
 */
val Int.isValid
    get() = this > 0
