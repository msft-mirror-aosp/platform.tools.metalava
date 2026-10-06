/*
 * Copyright (C) 2026 The Android Open Source Project
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

package com.android.tools.metalava.cli.surface

import com.android.tools.metalava.cli.common.MetalavaSubCommand
import com.android.tools.metalava.cli.common.stdout
import com.github.ajalt.clikt.core.requireObject

/** Subcommand of [MultiSurfaceCommand] which runs operations on a specific API surface. */
class SingleSurfaceCommand() :
    MetalavaSubCommand(
        help = "Performs operations on a single API surface.",
        // False for now while there are no options
        printHelpOnEmptyArgs = false,
    ) {
    /** Options created by [MultiSurfaceCommand]. */
    private val sharedOptions by requireObject<MultiSurfaceCommand.SharedOptions>()

    override fun run() {
        stdout.println("The single-surface command is currently experimental")
    }
}
