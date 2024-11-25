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

/** Properties for the [ApiGenerator.generateJson] method that come from comment line options. */
data class GenerateJsonConfig(
    /** A list of API signature files, ordered from the oldest API version to newest. */
    val pastApiVersions: List<File>,

    /**
     * The names of the API versions, ordered starting from version 1. This should include the names
     * of all the [pastApiVersions], then the name of the current API version.
     */
    val apiVersionNames: List<String>,

    /** The api versions file that will be generated. */
    val outputFile: File,
)
