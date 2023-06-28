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

package com.android.tools.metalava

import com.android.tools.metalava.model.text.TextCodebase
import java.io.File

/** File conversion tasks */
internal data class ConvertFile(
    val fromApiFile: File,
    val outputFile: File,
    val baseApiFile: File? = null,
    val strip: Boolean = false
)

/** Perform the file conversion described by the [ConvertFile] on which this is called. */
internal fun ConvertFile.process() {
    val signatureApi = SignatureFileLoader.load(fromApiFile)

    val apiType = ApiType.ALL
    val apiEmit = apiType.getEmitFilter()
    val strip = strip
    val apiReference = if (strip) apiType.getEmitFilter() else apiType.getReferenceFilter()
    val baseFile = baseApiFile

    val outputApi =
        if (baseFile != null) {
            // Convert base on a diff
            val baseApi = SignatureFileLoader.load(baseFile)
            TextCodebase.computeDelta(baseFile, baseApi, signatureApi)
        } else {
            signatureApi
        }

    // See JDiff's XMLToAPI#nameAPI
    val apiName = outputFile.nameWithoutExtension.replace(' ', '_')
    createReportFile(outputApi, outputFile, "JDiff File") { printWriter ->
        JDiffXmlWriter(
            printWriter,
            apiEmit,
            apiReference,
            signatureApi.preFiltered && !strip,
            apiName
        )
    }
}
