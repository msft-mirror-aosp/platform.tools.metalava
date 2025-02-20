/*
 * Copyright (C) 2025 The Android Open Source Project
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

package com.android.tools.metalava.testing

import java.io.ByteArrayOutputStream
import java.io.File

object JavacHelper {
    private val jdkPath = getJdkPath()

    private fun getJdkPath(): String {
        val javaHome = System.getProperty("java.home")
        if (javaHome != null) {
            var javaHomeFile = File(javaHome)
            if (File(javaHomeFile, "bin${File.separator}javac").exists()) {
                return javaHome
            } else if (javaHomeFile.name == "jre") {
                javaHomeFile = javaHomeFile.parentFile
                if (File(javaHomeFile, "bin${File.separator}javac").exists()) {
                    return javaHomeFile.path
                }
            }
        }
        return System.getenv("JAVA_HOME") ?: error("Could not get JDK path")
    }

    private fun runCommand(executable: String, args: List<String>) {
        val command = buildList {
            add(executable)
            addAll(args)
        }

        val output = ByteArrayOutputStream()
        val exitCode =
            try {
                val process = ProcessBuilder(command).redirectErrorStream(true).start()
                process.inputStream.copyTo(output)
                process.waitFor()
            } catch (e: Exception) {
                error("Failed to run `$command` (${e.message})")
            }

        if (exitCode != 0) {
            error(
                "Executing `$command` failed with the following output:\n${output.toString(Charsets.UTF_8).prependIndent()}"
            )
        }
    }

    /** Compile the [sources] into [outputDirectory] throwing an exception if it fails. */
    fun compile(outputDirectory: File, sources: List<File>) {
        runCommand(
            "$jdkPath/bin/javac",
            buildList {
                add("-d")
                add(outputDirectory.path)
                sources.mapTo(this) { it.path }
            }
        )
    }
}
