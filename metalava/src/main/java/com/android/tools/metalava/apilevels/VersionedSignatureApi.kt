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

import com.android.tools.metalava.cli.common.SignatureFileLoader
import com.android.tools.metalava.model.Codebase
import com.android.tools.metalava.model.text.SignatureFile
import java.io.File

/**
 * Encapsulates an [ApiVersion] and associated API definition, currently represented by a single
 * [file] that contains an API signature.
 *
 * Also, includes a [signatureFileLoader] that is used to load a [Codebase] from [file].
 */
class VersionedSignatureApi(
    private val signatureFileLoader: SignatureFileLoader,
    private val file: File,
    val apiVersion: ApiVersion,
) {
    /** Load the API into a [Codebase] using the [signatureFileLoader]. */
    fun load() = signatureFileLoader.load(SignatureFile.fromFiles(file))
}
