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

package com.android.tools.metalava.testing

import com.android.tools.lint.checks.infrastructure.TestFile
import com.android.tools.metalava.testing.TemporaryFolderOwner.Companion.createTestRule
import java.io.File
import org.junit.Rule
import org.junit.rules.TemporaryFolder

/** Provides helper functions for a test class that has a [TemporaryFolder] rule. */
interface TemporaryFolderOwner {
    /**
     * Provides access to temporary files.
     *
     * Must be defined as follows in subclasses:
     * ```
     *     @get:Rule final override val temporaryFolder = createTestRule()
     * ```
     */
    val temporaryFolder: CustomTemporaryFolder

    /**
     * Given an array of [TestFile] get a folder called "project" (creating it if it is empty),
     * write the files to the folder and then return the folder.
     */
    fun createProjectDir(files: Array<TestFile>): File {
        val dir = getOrCreateFolder("project")
        files.createFiles(dir)
        return dir
    }

    /**
     * Get a folder with a path [relative] to the root.
     *
     * Use an existing folder, or create a new one if necessary. It is an error if a file exists but
     * is not a directory.
     */
    fun getOrCreateFolder(relative: String = ""): File {
        val dir = temporaryFolder.root.resolve(relative)
        // If the directory exists and is a directory then use it, otherwise drop through to create
        // a new one. If the directory exists but is not a directory then attempting to create a new
        // one will report an issue.
        return if (dir.isDirectory) {
            dir
        } else {
            temporaryFolder.newFolder(relative)
        }
    }

    /**
     * Get a file with a path [relative] to the root.
     *
     * Use an existing file, or create an empty new one if necessary. It is an error if a file
     * exists but is not a normal file.
     */
    fun getOrCreateFile(relative: String = ""): File {
        val file = temporaryFolder.root.resolve(relative)
        // If the file exists and is a normal file then use it, otherwise drop through to create
        // a new one. If the file exists but is not a normal file then attempting to create a new
        // one will report an issue.
        return if (file.isFile) {
            file
        } else {
            file.parentFile.mkdirs()
            temporaryFolder.newFile(relative)
        }
    }

    /** Create a file (and containing directory if necessary) with a path [relative] to the root. */
    fun newFile(relative: String = ""): File {
        val file = temporaryFolder.root.resolve(relative)
        file.parentFile.mkdirs()
        return temporaryFolder.newFile(relative)
    }

    /**
     * Build a file structure in the directory [relative] to the root.
     *
     * Creates the directory first, if needed. Then creates a [DirectoryBuilder] for the directory
     * and then invokes [body] on it to populate the directory.
     */
    fun buildFileStructure(relative: String = "", body: DirectoryBuilder.() -> Unit): File {
        val dir = getOrCreateFolder(relative)
        dir.buildFileStructure(body)
        return dir
    }

    /**
     * Replaces temporary folders that can change from one test run to the next with fixed labels to
     * make it easier to test expectations.
     *
     * @see CustomTemporaryFolder.cleanupString
     */
    fun cleanupString(
        string: String,
        project: File? = null,
    ) = temporaryFolder.cleanupString(string, project)

    /**
     * Replaces temporary folders that can change from one test run to the next with fixed labels to
     * make it easier to test expectations.
     *
     * @see CustomTemporaryFolder.replaceFileWithSymbol
     */
    fun replaceFileWithSymbol(
        string: String,
        fileToSymbol: Map<File, String> = emptyMap(),
    ) = temporaryFolder.replaceFileWithSymbol(string, fileToSymbol)

    /** Create a [File] from this [TestFile] in the root directory of the [temporaryFolder]. */
    fun TestFile.toFile() = createFile(temporaryFolder.root)

    companion object {
        /** Create the [TemporaryFolder]. */
        fun createTestRule(): CustomTemporaryFolder = CustomTemporaryFolder()
    }
}

/** Base class for implementers of [TemporaryFolderOwner]. */
open class BaseTemporaryFolderOwner : TemporaryFolderOwner {
    @get:Rule final override val temporaryFolder = createTestRule()
}

/**
 * Custom extension of [TemporaryFolder] that keeps track of mappings from temporary files to a
 * label that is used in [TemporaryFolderOwner.cleanupString].
 */
class CustomTemporaryFolder : TemporaryFolder() {
    /**
     * Replaces temporary folders that can change from one test run to the next with fixed labels to
     * make it easier to test expectations.
     *
     * First, if [project] is provided, this will replace any usages of its [File.getPath] or
     * [File.getCanonicalPath] with `TESTROOT`.
     *
     * Finally, it will replace [TemporaryFolder.getRoot] with `TESTROOT`.
     */
    fun cleanupString(
        string: String,
        project: File?,
    ) =
        if (project == null) {
            replaceFileWithSymbol(string, emptyMap())
        } else {
            replaceFileWithSymbol(string, mapOf(project to "TESTROOT"))
        }

    /**
     * Replaces temporary folders that can change from one test run to the next with fixed labels to
     * make it easier to test expectations.
     *
     * First, for each [Map.Entry] in [fileToSymbol] it will replace any usages of its
     * [Map.Entry.key]'s [File.getPath] or [File.getCanonicalPath] with its [Map.Entry.value].
     *
     * Finally, it will replace [TemporaryFolder.getRoot] with `TESTROOT`.
     */
    internal fun replaceFileWithSymbol(
        string: String,
        fileToSymbol: Map<File, String>,
    ): String {
        var s = string

        for ((file, symbol) in fileToSymbol) {
            s = s.replace(file.path, symbol)
            s = s.replace(file.canonicalPath, symbol)
        }

        s = s.replace(root.path, "TESTROOT")

        s = s.trim()

        return s
    }
}
