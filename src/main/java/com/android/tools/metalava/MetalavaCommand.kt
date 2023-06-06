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
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.multiple
import java.io.PrintWriter

/** Main metalava command. */
class MetalavaCommand(
    private val stdout: PrintWriter,
    private val stderr: PrintWriter,
) :
    CliktCommand(
        // Gather all the options and arguments into a list so that they can be passed to Options().
        treatUnknownOptionsAsArgs = true,
    ) {
    init {
        context {
            console = MetalavaConsole(stdout, stderr)

            // Disable help so that Options can print it instead.
            helpOptionNames = emptySet()
        }
    }

    /** Property into which all the arguments (and unknown options) are gathered. */
    private val flags by argument().multiple()

    /** Process the command. */
    fun process(args: Array<String>) {
        parse(args)
    }

    /**
     * Perform this command's actions.
     *
     * This is called after the command line parameters are parsed.
     */
    override fun run() {
        val remainingArgs = flags.toTypedArray()
        options = Options(remainingArgs, stdout, stderr)

        maybeActivateSandbox()

        processFlags()
    }
}
