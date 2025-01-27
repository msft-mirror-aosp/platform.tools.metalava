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

import java.io.File

/**
 * Supports updating [Api] with information from the version of the API that is defined in [jar].
 *
 * The [updater] is responsible for updating the [Api].
 */
class VersionedJarApi(
    val jar: File,
    private val updater: ApiHistoryUpdater,
) : VersionedApi {
    override val apiVersion
        get() = updater.apiVersion

    override fun updateApi(api: Api) {
        api.readJar(jar, updater)
    }

    override fun forExtension() = updater.forExtension()

    override fun toString() = "VersionedJarApi(jar=$jar, updater=$updater)"
}
