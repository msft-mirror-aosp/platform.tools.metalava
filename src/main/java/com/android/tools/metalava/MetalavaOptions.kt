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

import com.github.ajalt.clikt.completion.CompletionCandidates
import com.github.ajalt.clikt.parameters.arguments.ProcessedArgument
import com.github.ajalt.clikt.parameters.arguments.RawArgument
import com.github.ajalt.clikt.parameters.arguments.convert
import com.github.ajalt.clikt.parameters.options.NullableOption
import com.github.ajalt.clikt.parameters.options.RawOption
import com.github.ajalt.clikt.parameters.options.convert
import java.io.File

// This contains extensions methods for creating custom Clikt options.

/** Convert the option to a [File] that represents an existing file. */
fun RawOption.existingFile(): NullableOption<File, File> {
    return fileConversion(::stringToExistingFile)
}

/** Convert the argument to a [File] that represents an existing file. */
fun RawArgument.existingFile(): ProcessedArgument<File, File> {
    return fileConversion(::stringToExistingFile)
}

/** Convert the argument to a [File] that represents an existing directory. */
fun RawArgument.existingDir(): ProcessedArgument<File, File> {
    return fileConversion(::stringToExistingDir)
}

/** Convert the argument to a [File] that represents a new file. */
fun RawArgument.newFile(): ProcessedArgument<File, File> {
    return fileConversion(::stringToNewFile)
}

/** Convert the option to a [File] using the supplied conversion function.. */
private fun RawOption.fileConversion(conversion: (String) -> File): NullableOption<File, File> {
    return convert({ localization.pathMetavar() }, CompletionCandidates.Path) { str ->
        try {
            conversion(str)
        } catch (e: DriverException) {
            e.message?.let { fail(it) } ?: throw e
        }
    }
}

/** Convert the argument to a [File] using the supplied conversion function. */
fun RawArgument.fileConversion(conversion: (String) -> File): ProcessedArgument<File, File> {
    return convert(CompletionCandidates.Path) { str ->
        try {
            conversion(str)
        } catch (e: DriverException) {
            e.message?.let { fail(it) } ?: throw e
        }
    }
}

/**
 * Converts a path to a [File] that represents the absolute path, with the following special
 * behavior:
 * - "~" will be expanded into the home directory path.
 * - If the given path starts with "@", it'll be converted into "@" + [file's absolute path]
 *
 * Note, unlike the other "stringToXxx" methods, this method won't register the given path to
 * [FileReadSandbox].
 */
internal fun fileForPathInner(path: String): File {
    // java.io.File doesn't automatically handle ~/ -> home directory expansion.
    // This isn't necessary when metalava is run via the command line driver
    // (since shells will perform this expansion) but when metalava is run
    // directly, not from a shell.
    if (path.startsWith("~/")) {
        val home = System.getProperty("user.home") ?: return File(path)
        return File(home + path.substring(1))
    } else if (path.startsWith("@")) {
        return File("@" + File(path.substring(1)).absolutePath)
    }

    return File(path).absoluteFile
}

/**
 * Convert a string representing an existing directory to a [File].
 *
 * This will fail if:
 * * The file is not a regular directory.
 */
internal fun stringToExistingDir(value: String): File {
    val file = fileForPathInner(value)
    if (!file.isDirectory) {
        throw DriverException("$file is not a directory")
    }
    return FileReadSandbox.allowAccess(file)
}

/**
 * Convert a string representing an existing file to a [File].
 *
 * This will fail if:
 * * The file is not a regular file.
 */
internal fun stringToExistingFile(value: String): File {
    val file = fileForPathInner(value)
    if (!file.isFile) {
        throw DriverException("$file is not a file")
    }
    return FileReadSandbox.allowAccess(file)
}

/**
 * Convert a string representing a new file to a [File].
 *
 * This will fail if:
 * * the file is a directory.
 * * the file exists and cannot be deleted.
 * * the parent directory does not exist, and cannot be created.
 */
internal fun stringToNewFile(value: String): File {
    val output = fileForPathInner(value)

    if (output.exists()) {
        if (output.isDirectory) {
            throw DriverException("$output is a directory")
        }
        val deleted = output.delete()
        if (!deleted) {
            throw DriverException("Could not delete previous version of $output")
        }
    } else if (output.parentFile != null && !output.parentFile.exists()) {
        val ok = output.parentFile.mkdirs()
        if (!ok) {
            throw DriverException("Could not create ${output.parentFile}")
        }
    }

    return FileReadSandbox.allowAccess(output)
}
