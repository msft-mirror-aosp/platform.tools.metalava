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

import com.android.tools.metalava.cli.common.enumOption
import com.android.tools.metalava.cli.common.newFile
import com.android.tools.metalava.model.FileFormat
import com.android.tools.metalava.model.MethodItem
import com.github.ajalt.clikt.parameters.groups.OptionGroup
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.validate
import com.github.ajalt.clikt.parameters.types.choice

const val ARG_API = "--api"
const val ARG_REMOVED_API = "--removed-api"
const val ARG_API_OVERLOADED_METHOD_ORDER = "--api-overloaded-method-order"
const val ARG_FORMAT = "--format"
const val ARG_OUTPUT_KOTLIN_NULLS = "--output-kotlin-nulls"

enum class OverloadedMethodOrder(val comparator: Comparator<MethodItem>, val help: String) {
    /** Sort overloaded methods according to source order. */
    SOURCE(
        MethodItem.sourceOrderForOverloadedMethodsComparator,
        help =
            """
        preserves the order in which overloaded methods appear in the source files. This means
        that refactorings of the source files which change the order but not the API can cause
        unnecessary changes in the API signature files.
    """
                .trimIndent()
    ),

    /** Sort overloaded methods by their signature. */
    SIGNATURE(
        MethodItem.comparator,
        help =
            """
        sorts overloaded methods by their signature. This means that refactorings of the source
        files which change the order but not the API will have no effect on the API signature
        files.
    """
                .trimIndent()
    )
}

class SignatureOutputOptions :
    OptionGroup(
        name = "Signature File Output",
        help = "Options controlling the signature file output"
    ) {

    /** If set, a file to write an API file to. */
    val apiFile by
        option(
                ARG_API,
                metavar = "<file>",
                help =
                    """
                    Output file into which the API signature will be generated. If this is not
                    specified then no API signature file will be created.
                """
                        .trimIndent()
            )
            .newFile()

    /** If set, a file to write an API file containing APIs that have been removed. */
    val removedApiFile by
        option(
                ARG_REMOVED_API,
                metavar = "<file>",
                help =
                    """
                    Output file into which the API signatures for removed APIs will be generated. If
                    this is not specified then no removed API signature file will be created.
                """
                        .trimIndent()
            )
            .newFile()

    /**
     * Determines how overloaded methods, i.e. methods with the same name, are ordered in signature
     * files.
     */
    val apiOverloadedMethodOrder by
        enumOption(
            help =
                """
                Specifies the order of overloaded methods in signature files.
                Applies to the contents of the files specified on $ARG_API and $ARG_REMOVED_API.
            """
                    .trimIndent(),
            enumValueHelpGetter = { it.help },
            default = OverloadedMethodOrder.SIGNATURE,
        )

    /** The output format version being used */
    val outputFormat by
        option(ARG_FORMAT, help = "Sets the output signature file format to be the given version.")
            .choice(
                "v2" to FileFormat.V2,
                "v3" to FileFormat.V3,
                "v4" to FileFormat.V4,
                "recommended" to FileFormat.recommended,
                "latest" to FileFormat.latest,
            )
            .default(FileFormat.recommended)

    /**
     * Whether nullness annotations should be displayed as ?/!/empty instead of
     * with @NonNull/@Nullable/empty.
     */
    private val outputKotlinStyleNulls by
        option(
                ARG_OUTPUT_KOTLIN_NULLS,
                help =
                    """
        Controls whether nullness annotations should be formatted as in Kotlin (with "?" for 
        nullable types, "" for non nullable types, and "!" for unknown.
        The default is `yes` if $ARG_FORMAT >= v3 and must be `no` (or unspecified) if 
        $ARG_FORMAT < v3."
    """
                        .trimIndent()
            )
            .choice(
                "yes" to true,
                "no" to false,
            )
            .validate {
                require(!it || outputFormat >= FileFormat.V3) {
                    "'$ARG_OUTPUT_KOTLIN_NULLS=yes' requires '$ARG_FORMAT=v3' or higher not " +
                        "'$ARG_FORMAT=${outputFormat.optionValue}"
                }
            }

    /**
     * The default value of [outputKotlinStyleNulls] is dependent upon the [format] value but
     * unfortunately, it is not possible in Clikt to specify a default that depends on another
     * property, so instead the default is implemented here.
     */
    val effectiveOutputKotlinStyleNulls
        get() = outputKotlinStyleNulls ?: (outputFormat.useKotlinStyleNulls())
}
