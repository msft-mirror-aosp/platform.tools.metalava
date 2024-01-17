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

package com.android.tools.metalava.cli.compatibility

import com.android.tools.metalava.ApiType
import com.android.tools.metalava.SignatureFileCache
import com.android.tools.metalava.cli.common.allowStructuredOptionName
import com.android.tools.metalava.cli.common.existingFile
import com.android.tools.metalava.model.Codebase
import com.github.ajalt.clikt.parameters.groups.OptionGroup
import com.github.ajalt.clikt.parameters.options.option
import java.io.File

const val ARG_CHECK_COMPATIBILITY_API_RELEASED = "--check-compatibility:api:released"
const val ARG_CHECK_COMPATIBILITY_REMOVED_RELEASED = "--check-compatibility:removed:released"
const val ARG_CHECK_COMPATIBILITY_BASE_API = "--check-compatibility:base"
const val ARG_ERROR_MESSAGE_CHECK_COMPATIBILITY_RELEASED = "--error-message:compatibility:released"

/** The name of the group, can be used in help text to refer to the options in this group. */
const val COMPATIBILITY_CHECK_GROUP = "Compatibility Checks"

class CompatibilityCheckOptions :
    OptionGroup(
        name = COMPATIBILITY_CHECK_GROUP,
        help =
            """
                Options controlling which, if any, compatibility checks are performed against a
                previously released API.
            """
                .trimIndent(),
    ) {

    internal val baseApiForCompatCheck: File? by
        option(
                ARG_CHECK_COMPATIBILITY_BASE_API,
                help =
                    """
                        When performing a compat check, use the provided signature file as a base
                        api, which is treated as part of the API being checked. This allows us to
                        compute the full API surface from a partial API surface (e.g. the current
                         @SystemApi txt file), which allows us to recognize when an API is moved
                         from the partial API to the base API and avoid incorrectly flagging this
                     """
                        .trimIndent(),
            )
            .existingFile()
            .allowStructuredOptionName()

    private val checkReleasedApi: File? by
        option(
                ARG_CHECK_COMPATIBILITY_API_RELEASED,
                help =
                    """
                        Check compatibility of the previously released API.
                    """
                        .trimIndent(),
            )
            .existingFile()
            .allowStructuredOptionName()

    private val checkReleasedRemoved: File? by
        option(
                ARG_CHECK_COMPATIBILITY_REMOVED_RELEASED,
                help =
                    """
                        Check compatibility of the previously released but since removed APIs.
                    """
                        .trimIndent(),
            )
            .existingFile()
            .allowStructuredOptionName()

    /**
     * If set, metalava will show this error message when "check-compatibility:*:released" fails.
     * (i.e. [ARG_CHECK_COMPATIBILITY_API_RELEASED] and [ARG_CHECK_COMPATIBILITY_REMOVED_RELEASED])
     */
    internal val errorMessage: String? by
        option(
                ARG_ERROR_MESSAGE_CHECK_COMPATIBILITY_RELEASED,
                help =
                    """
                        If set, this is output when errors are detected in
                        $ARG_CHECK_COMPATIBILITY_API_RELEASED or
                        $ARG_CHECK_COMPATIBILITY_REMOVED_RELEASED.
                    """
                        .trimIndent(),
                metavar = "<message>",
            )
            .allowStructuredOptionName()

    /**
     * Request for compatibility checks. [file] represents the signature file to be checked.
     * [apiType] represents which part of the API should be checked.
     */
    data class CheckRequest(val file: File, val apiType: ApiType) {
        override fun toString(): String {
            return "--check-compatibility:${apiType.flagName}:released $file"
        }
    }

    val compatibilityChecks: List<CheckRequest> by lazy {
        buildList {
            checkReleasedApi?.let { add(CheckRequest(it, ApiType.PUBLIC_API)) }
            checkReleasedRemoved?.let { add(CheckRequest(it, ApiType.REMOVED)) }
        }
    }

    fun previouslyReleasedCodebases(signatureFileCache: SignatureFileCache): List<Codebase> =
        listOfNotNull(checkReleasedApi, checkReleasedRemoved).map { signatureFileCache.load(it) }
}
