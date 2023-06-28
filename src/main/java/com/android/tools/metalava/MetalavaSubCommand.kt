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

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.context

/** Base class for all sub-commands of [MetalavaCommand]. */
abstract class MetalavaSubCommand(help: String) : CliktCommand(help = help) {

    init {
        context {
            // Set the localization for this command.
            localization = MetalavaLocalization()

            // Set the help option names to the standard metalava help options.
            helpOptionNames = setOf("-h", "--help", "-?")

            // Override the help formatter to use metalava styling of the help.
            helpFormatter =
                MetalavaHelpFormatter(
                    // Retrieve the terminal from the CommonOptions that is made available by the
                    // containing MetalavaCommand.
                    { currentContext.findObject<CommonOptions>()?.terminal ?: plainTerminal },
                    localization,
                )
        }
    }
}
