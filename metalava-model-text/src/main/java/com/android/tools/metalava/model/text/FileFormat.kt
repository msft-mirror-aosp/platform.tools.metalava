/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.tools.metalava.model.text

import java.util.Locale

/** File formats that metalava can emit APIs to */
enum class FileFormat(
    val signatureFileFormatDefaults: SignatureFileFormat,
) {
    V1(SignatureFileFormat.V1),
    V2(SignatureFileFormat.V2),
    V3(SignatureFileFormat.V3),
    V4(SignatureFileFormat.V4);

    /** The value to use in a command line option. */
    val optionValue: String = name.lowercase(Locale.US)

    fun useKotlinStyleNulls(): Boolean {
        return signatureFileFormatDefaults.kotlinStyleNulls
    }

    fun header(): String? = signatureFileFormatDefaults.header()

    companion object {
        /** The latest signature file version, equivalent to --format=latest */
        val latest = values().maxOrNull()!!
    }
}
