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

import com.android.tools.metalava.reporter.Issues
import com.android.tools.metalava.reporter.Report
import com.android.tools.metalava.reporter.ReportFormatter
import com.android.tools.metalava.reporter.Severity

/** Formats a [Report] for output. */
internal class TerminalReportFormatter
private constructor(
    /** Whether output should be colorized */
    val terminal: Terminal = plainTerminal,
) : ReportFormatter {

    override fun format(report: Report): String {
        return buildString {
            val (severity, relativePath, line, message, id) = report
            append(terminal.attributes(bold = true))
            relativePath?.let {
                append(it)
                if (line > 0) append(":").append(line)
                append(": ")
            }
            when (severity) {
                Severity.INFO -> {
                    append(terminal.attributes(foreground = TerminalColor.CYAN))
                    append("info: ")
                }
                Severity.WARNING,
                Severity.WARNING_ERROR_WHEN_NEW -> {
                    append(terminal.attributes(foreground = TerminalColor.YELLOW))
                    append("warning: ")
                }
                Severity.ERROR -> {
                    append(terminal.attributes(foreground = TerminalColor.RED))
                    append("error: ")
                }
                Severity.INHERIT,
                Severity.HIDDEN -> {}
            }
            append(terminal.reset())
            append(message)
            append(severity.messageSuffix)
            id?.let<Issues.Issue, Unit> { append(" [").append(it.name).append("]") }
        }
    }

    companion object {
        fun forTerminal(terminal: Terminal): ReportFormatter = TerminalReportFormatter(terminal)
    }
}
