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

package com.android.tools.metalava.cli.common

import com.github.ajalt.clikt.output.HelpFormatter.ParameterHelp
import com.github.ajalt.clikt.output.Localization

/** Extends [MetalavaHelpFormatter] to append information about the legacy flags. */
internal class LegacyHelpFormatter(
    terminalSupplier: () -> Terminal,
    localization: Localization,
    private val helpListTransform: (List<ParameterHelp>) -> List<ParameterHelp>,
    /** Provider for additional non-Clikt usage information. */
    private val nonCliktUsageProvider: (Terminal, Int) -> String,
) : MetalavaHelpFormatter(terminalSupplier, localization) {
    override fun formatHelp(
        prolog: String,
        epilog: String,
        parameters: List<ParameterHelp>,
        programName: String
    ): String {
        val extendedEpilog = "```${nonCliktUsageProvider(terminal, width)}```$epilog"
        val transformedParameters = helpListTransform(parameters)
        return super.formatHelp(prolog, extendedEpilog, transformedParameters, programName)
    }
}
