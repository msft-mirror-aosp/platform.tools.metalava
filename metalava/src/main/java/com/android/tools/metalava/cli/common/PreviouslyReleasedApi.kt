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

package com.android.tools.metalava.cli.common

import com.android.SdkConstants
import com.android.tools.metalava.model.Codebase
import com.android.tools.metalava.model.text.SignatureFile
import java.io.File

/** A previously released API. */
sealed interface PreviouslyReleasedApi {
    /** The set of files defining the previously released API. */
    val files: List<File>

    /** Load the files into a list of [Codebase]s. */
    fun load(
        jarLoader: (File) -> Codebase,
        signatureLoader: (SignatureFile) -> Codebase,
    ): List<Codebase>

    companion object {
        /**
         * Create an optional [PreviouslyReleasedApi] instance from the list of [files] passed to
         * the option [optionName].
         *
         * If [files] is empty then this returns `null`. If [files] contains a mixture of jar and
         * non-jar files (assumed to be signature files) then it is an error. Otherwise, this will
         * return a [JarBasedApi] or [SignatureBasedApi] for a list of jar files and a list of
         * signature files respectively.
         */
        internal fun optionalPreviouslyReleasedApi(optionName: String, files: List<File>) =
            if (files.isEmpty()) null
            else {
                // Partition the files into jar and non-jar files, the latter are assumed to be
                // signature files.
                val (jarFiles, signatureFiles) =
                    files.partition { it.path.endsWith(SdkConstants.DOT_JAR) }
                when {
                    jarFiles.isEmpty() -> SignatureBasedApi.fromFiles(signatureFiles)
                    signatureFiles.isEmpty() -> JarBasedApi(jarFiles)
                    else ->
                        throw IllegalStateException(
                            "$optionName: Cannot mix jar files (e.g. ${jarFiles.first()}) and signature files (e.g. ${signatureFiles.first()})"
                        )
                }
            }
    }
}

/** A previously released API defined by jar files. */
data class JarBasedApi(override val files: List<File>) : PreviouslyReleasedApi {
    override fun load(
        jarLoader: (File) -> Codebase,
        signatureLoader: (SignatureFile) -> Codebase,
    ) = files.map { jarLoader(it) }
}

/**
 * A previously released API defined by signature files.
 *
 * If a single file is provided then it may be a full API or a delta on another API. If multiple
 * files are provided then they are expected to be provided in order from the narrowest API to the
 * widest API, where all but the first files are deltas on the preceding file.
 */
data class SignatureBasedApi(val signatureFiles: List<SignatureFile>) : PreviouslyReleasedApi {

    override val files: List<File> = signatureFiles.map { it.file }

    override fun load(
        jarLoader: (File) -> Codebase,
        signatureLoader: (SignatureFile) -> Codebase,
    ) = signatureFiles.map { signatureLoader(it) }

    companion object {
        fun fromFiles(files: List<File>): SignatureBasedApi {
            val lastIndex = files.size - 1
            return SignatureBasedApi(
                files.mapIndexed { index, file ->
                    SignatureFile(
                        file,
                        // The last file is assumed to be for the current API surface.
                        forCurrentApiSurface = index == lastIndex,
                    )
                }
            )
        }
    }
}
