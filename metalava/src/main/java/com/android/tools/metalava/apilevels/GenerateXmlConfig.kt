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
import com.android.tools.metalava.model.Codebase
import java.io.File

/** Properties for the [ApiGenerator.generateXml] method that come from comment line options. */
data class GenerateXmlConfig(
    /**
     * A map from [ApiVersion] to associated jar file for historical APIs versions, i.e. APIs that
     * have previously been released.
     */
    val versionToJar: Map<ApiVersion, File>,

    /** A version that has not yet been finalized. */
    val notFinalizedSdkVersion: ApiVersion,

    /**
     * The version to use for the current sources [Codebase], or null if the [Codebase] should not
     * be included in the API history.
     */
    val codebaseSdkVersion: ApiVersion?,

    /** The API levels file that will be generated. */
    val outputFile: File,

    /** The [ApiPrinter] to use to write the API versions to [outputFile]. */
    val printer: ApiPrinter,

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
