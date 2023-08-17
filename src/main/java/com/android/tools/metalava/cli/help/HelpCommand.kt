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

package com.android.tools.metalava.cli.help

import com.android.tools.metalava.ARG_STUB_PACKAGES
import com.android.tools.metalava.cli.common.MetalavaHelpFormatter
import com.android.tools.metalava.cli.common.stdout
import com.android.tools.metalava.cli.common.terminal
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.context
import com.github.ajalt.clikt.core.subcommands
import com.github.ajalt.clikt.output.Localization

class HelpCommand :
    CliktCommand(
        help = "Provides help for general metalava concepts",
        invokeWithoutSubcommand = true,
    ) {

    init {
        context {
            localization =
                object : Localization {
                    override fun commandsTitle(): String {
                        return "Concepts"
                    }

                    override fun commandMetavar(): String {
                        return "<concept>..."
                    }
                }

            helpFormatter = MetalavaHelpFormatter(this@HelpCommand::terminal, localization)

            // Help options make no sense on a help command.
            helpOptionNames = emptySet()
        }
        subcommands(
            IssuesCommand(),
            packageFilterHelp,
        )
    }

    override fun run() {
        if (currentContext.invokedSubcommand == null) {
            stdout.println(getFormattedHelp())
        }
    }
}

private val packageFilterHelp =
    SimpleHelpCommand(
        name = "package-filters",
        help =
            """
Explains the syntax and behavior of package filters used in options like $ARG_STUB_PACKAGES.

A package filter is specified as a sequence of package matchers, separated by `:`. A matcher
consists of an option leading `+` or `-` following by a pattern. If `-` is specified then it will
exclude all packages that match the pattern, otherwise (i.e. with `+` or without either) it will
include all packages that match the pattern. If a package is matched by multiple matchers then the
last one wins.

Patterns can be one of the following:

`*` - match every package.

`<package>` - an exact match, e.g. `foo` will only match `foo` and `foo.bar` will only match
`foo.bar`.

`<package>*` - a prefix match, e.g. `foo*` will match `foo` and `foobar` and `foo.bar`.

`<package>.*` - a recursive match, will match `<package>` and any nested packages, e.g. `foo.*`
will match `foo` and `foo.bar` and `foo.bar.baz` but not `foobar`.
            """
                .trimIndent()
    )
