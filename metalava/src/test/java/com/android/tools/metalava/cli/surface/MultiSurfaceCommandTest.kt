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

import com.android.tools.metalava.cli.common.BaseCommandTest
import com.github.ajalt.clikt.core.subcommands
import org.junit.Test

/**
 * Tests for [MultiSurfaceCommand] and [SingleSurfaceCommand] (which is run in the context of a
 * [MultiSurfaceCommand]).
 */
class MultiSurfaceCommandTest :
    BaseCommandTest<MultiSurfaceCommand>({
        // `single-surface` can only be run as a subcommand of `multi-surface`
        MultiSurfaceCommand().subcommands(SingleSurfaceCommand())
    }) {
    @Test
    fun `Test multi-surface help`() {
        commandTest {
            args += listOf("multi-surface", "--help")

            expectedStdout =
                """
                Usage: metalava multi-surface [options] <sub-command>? ...

                  Command that sets up state to run operations for multiple API surfaces.

                  Should be run with one or more `single-surface` subcommands to process the API surfaces.

                Options:
                  -h, -?, --help                             Show this message and exit

                Sub-commands:
                  single-surface                             Performs operations on a single API surface.
                """
                    .trimIndent()
        }
    }

    @Test
    fun `Test single-surface help`() {
        commandTest {
            args += listOf("multi-surface", "single-surface", "--help")

            expectedStdout =
                """
                The multi-surface command is currently experimental

                Usage: metalava multi-surface single-surface [options]

                  Performs operations on a single API surface.

                Options:
                  -h, -?, --help                             Show this message and exit
                """
                    .trimIndent()
        }
    }

    @Test
    fun `Test running multiple single-surface commands`() {
        commandTest {
            args += listOf("multi-surface", "single-surface", "single-surface")
            expectedStdout =
                """
                The multi-surface command is currently experimental
                The single-surface command is currently experimental
                The single-surface command is currently experimental
                """
                    .trimIndent()
        }
    }
}
