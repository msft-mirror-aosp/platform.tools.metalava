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

import com.android.tools.metalava.apilevels.ApiGenerator.SdkExtensionsArguments
import java.io.File

/** Properties for the [ApiGenerator.generateXml] method that come from comment line options. */
data class GenerateXmlConfig(
    /**
     * A map from [ApiVersion] to associated jar file for historical APIs versions, i.e. APIs that
     * have previously been released.
     */
    val versionToJar: Map<ApiVersion, File>,

    /**
     * The current version.
     *
     * If there is no corresponding element in [versionToJar] for this then the API defined in the
     * sources will be added to the API levels file for this API level unless
     * [isDeveloperPreviewBuild] is `true`.
     */
    val currentSdkVersion: ApiVersion,

    /**
     * True if the [currentSdkVersion] level is for a developer preview build.
     *
     * If this is `true` then the API defined in the sources will be added to the API levels file
     * with an API level of [currentSdkVersion]` - 1`.
     */
    val isDeveloperPreviewBuild: Boolean,

    /** The API levels file that will be generated. */
    val outputFile: File,

    /**
     * Optional SDK extensions arguments.
     *
     * If provided then SDK extension information will be included in the API levels file.
     */
    val sdkExtensionsArguments: SdkExtensionsArguments?,

    /**
     * If `true` then any references to undefined classes will be removed from super class and
     * interface lists; otherwise any such references will be treated as an error.
     *
     * An undefined class is one that is not defined within any of the API versions loaded.
     */
    val removeMissingClasses: Boolean,
)
