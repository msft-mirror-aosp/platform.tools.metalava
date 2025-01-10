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

package com.android.tools.metalava.apilevels

import java.io.File

/**
 * A node in a tree of path patterns used to select historical API files.
 *
 * e.g. the nodes of `prebuilts/sdk/{version:level}/public/android.jar` would be:
 * 1. The root element.
 * 2. `prebuilts`
 * 3. `sdk`
 * 4. `{version:level}` - a wild card representing any numbered directory.
 * 5. `public`
 * 6. `android.jar`
 *
 * Where each node is the child of the preceding node.
 *
 * If two or more patterns had matching nodes, then they will share the nodes. e.g.
 * `prebuilts/sdk/{version:level}/system/android.jar` would share the first 4 nodes with above
 * followed by two more nodes `system` and `android.jar`.
 *
 * These will be used to either find matching files in the file system by scanning through matching
 * directories or determine whether a specific file path that is passed in matches the pattern. In
 * either case this will also be used to extract information from the path, e.g. the api version.
 */
sealed class PatternNode {
    /**
     * List of all the children of this node.
     *
     * Nodes are added in the order in which they should be checked.
     */
    private val children = mutableListOf<PatternNode>()

    /**
     * Dump the contents of this node and return as a string.
     *
     * Useful for debugging and testing.
     */
    internal fun dump(): String {
        return buildString { dumpTo(this, "") }
    }

    /** Dump the contents of this node to the [builder] using [indent]. */
    private fun dumpTo(builder: StringBuilder, indent: String) {
        builder.apply {
            append(indent)
            append(this@PatternNode.toString())
            append("\n")
            for (child in children) {
                child.dumpTo(this, "$indent  ")
            }
        }
    }

    /**
     * Get [text] plus a `/` suffix if this has any children as in that case it is assumed to match
     * a directory.
     */
    protected fun withDirectorySuffixIfHasChildren(text: String) =
        text + if (children.isEmpty()) "" else "/"

    /**
     * Get an existing child node that matches [child] or if none exist add [child] and return it.
     */
    private fun getExistingOrAdd(child: PatternNode): PatternNode {
        // The child node must be new without any children of its own.
        require(child.children.isEmpty()) { "Cannot add $child as it has children of its own" }
        return children.find { it == child } ?: child.also { children.add(child) }
    }

    /**
     * Provides access to the files that are to be scanned.
     *
     * Callers that want to limit the scanning to only some files can provide a custom
     * implementation of this.
     */
    internal interface FileProvider {
        /**
         * Resolve [name] relative to [base] and if the resulting file exists then return it,
         * otherwise return null.
         */
        fun resolve(base: File, name: String): File?

        /** Return a sequence of the files in [dir], or null if [dir] is not a directory. */
        fun listFiles(dir: File): Sequence<File>?
    }

    /** Provides access to all files in the whole file system. */
    internal open class WholeFileSystemProvider : FileProvider {
        override fun resolve(base: File, name: String): File? {
            val file = base.resolve(name)
            return if (file.exists()) file else null
        }

        override fun listFiles(dir: File): Sequence<File>? {
            return dir.listFiles()?.asSequence()
        }
    }

    /**
     * A [FileProvider] that limits access to a supplied list of [File]s.
     *
     * @param files The list of [File]s to which this will provide access.
     */
    internal class LimitedFileSystemProvider(files: List<File>) : WholeFileSystemProvider() {
        /**
         * Map from [File] to the list of [File]s it contains (or an empty list for [File]s that
         * have no contents).
         */
        private val fileToContents =
            buildMap<File, MutableList<File>> {
                for (file in files) {
                    // Remember the file.
                    computeIfAbsent(file) { mutableListOf() }

                    // Add the file to its parent file's contents. Repeat for its parent file.
                    var f: File = file
                    while (true) {
                        val parent = f.parentFile ?: break
                        val contents = computeIfAbsent(parent) { mutableListOf() }
                        contents.add(f)
                        f = parent
                    }
                }
            }

        override fun resolve(base: File, name: String): File? {
            val file = super.resolve(base, name)
            return if (file in fileToContents) file else null
        }

        override fun listFiles(dir: File): Sequence<File>? {
            if (!dir.isDirectory) return null
            return fileToContents[dir]?.asSequence()
        }
    }

    /** Configuration provided when scanning. */
    internal data class ScanConfig(
        /** The root directory from which the scanning will be performed. */
        val dir: File,

        /**
         * An optional range which, if specified, will limit the versions that will be returned.
         * This is provided when scanning, instead of just filtering afterward, to save time when
         * scanning by ignoring version directories that are not in the range.
         */
        val apiVersionRange: ClosedRange<ApiVersion>? = null,

        /** Provides access to [File]s. */
        val fileProvider: FileProvider = WholeFileSystemProvider(),
    )

    /**
     * Scan the [ScanConfig.dir] using this pattern node as the guide.
     *
     * Returns a list of [MatchedPatternFile] objects, in version order (from the lowest to the
     * highest), If multiple matching files have the same version then only the first version will
     * be used.
     */
    internal fun scan(config: ScanConfig): List<MatchedPatternFile> {
        val dir = config.dir
        val start = PatternFileState(file = dir)
        return scan(config, start)
            // Ignore all but the first of each version.
            .distinctBy { it.version }
            // Sort them from the lowest version to the highest version.
            .sortedBy { it.version }
            // Convert the sequence into a list.
            .toList()
    }

    /**
     * Scan the [PatternFileState.file] using this pattern node as the guide to find the matching
     * files.
     *
     * This returns the result as a [Sequence] of [MatchedPatternFile] which have each been
     * populated with information extracted from matching [File]s.
     *
     * The basic idea is that the [PatternNode] will guide the scanning by using information within
     * the [PatternNode] hierarchy to limit scanning to only those directories that could possibly
     * match the patterns from which the [PatternNode] hierarchy was created.
     *
     * Each implementation of this consumes a [PatternFileState] (whose [PatternFileState.file] is
     * the directory to scan) and then applies its own rules to select [File]s that match. It then
     * creates copies of [state] for each [File] (possibly updating other properties too). Those new
     * [PatternFileState]s are either passed to [children] for further scanning, or if this is a
     * leaf node then they are converted into a sequence of [MatchedPatternFile]s that are returned
     * to the caller.
     */
    internal abstract fun scan(
        config: ScanConfig,
        state: PatternFileState
    ): Sequence<MatchedPatternFile>

    /**
     * Pass the [PatternFileState] on for further scanning or return [MatchedPatternFile]s if no
     * further scanning is necessary.
     *
     * If [children] is empty then this just returns a [Sequence] containing the
     * [MatchedPatternFile] created from [state]. Otherwise, this passes [state] to each of the
     * [children] to scan, and flattens the resulting [Sequence]s of [MatchedPatternFile]s and
     * returns that.
     */
    internal fun scanChildrenOrReturnMatching(
        config: ScanConfig,
        state: PatternFileState,
    ): Sequence<MatchedPatternFile> =
        if (children.isEmpty())
            sequenceOf(
                // Convert the PatternFileState into MatchedPatternFile objects relative to dir.
                state.matchedPatternFile(config.dir),
            )
        else children.asSequence().flatMap { it.scan(config, state) }

    /**
     * Used by [getExistingOrAdd] to allow duplicate nodes to be ignored.
     *
     * This must not include [children] in the check as in [getExistingOrAdd] the existing
     * [PatternNode]s being compared are likely to have a non-empty [children] list but the new
     * [PatternNode] will have an empty [children] list.
     */
    abstract override fun equals(other: Any?): Boolean

    /** Not currently used but should be implemented consistent with [equals]. */
    abstract override fun hashCode(): Int

    /**
     * The root [PatternNode].
     *
     * Just acts as a container for other [PatternNode]s.
     */
    private class RootPatternNode : PatternNode() {
        override fun toString() = "<root>"

        override fun scan(
            config: ScanConfig,
            state: PatternFileState
        ): Sequence<MatchedPatternFile> {
            return scanChildrenOrReturnMatching(config, state)
        }

        /** Root nodes are unique. */
        override fun equals(other: Any?): Boolean {
            return this === other
        }

        /** Root nodes are unique. */
        override fun hashCode(): Int {
            return System.identityHashCode(this)
        }
    }

    /**
     * Matches a fixed file called [name].
     *
     * e.g. if [name] is `foo` then when scanning/matching directory `bar`, this will scan/match
     * `bar/foo`.
     */
    private data class FixedNamePatternNode(
        /** The fixed name of the file that this matches. */
        val name: String,
    ) : PatternNode() {
        override fun toString() = withDirectorySuffixIfHasChildren(name)

        /** Check to see if there i */
        override fun scan(
            config: ScanConfig,
            state: PatternFileState
        ): Sequence<MatchedPatternFile> {
            // Resolve this against the file in [properties] to get a new file. If that file does
            // not exist then ignore it by returning an empty sequence.
            val newFile = config.fileProvider.resolve(state.file, name) ?: return emptySequence()

            // Create a new set of properties by copying the original properties, replacing the file
            // with the new file.
            val newProperties = state.copy(file = newFile)

            // Pass the properties on to the next nodes in the scanning, or return if this is the
            // last node.
            return scanChildrenOrReturnMatching(config, newProperties)
        }
    }

    /**
     * Matches any file name containing an API version number.
     *
     * [pattern] the regular expression pattern that will match the file name and whose 1st group
     * will contain the API version number.
     *
     * e.g. if [pattern] is `android-(\d+)` then when scanning/matching directory `bar`, this will
     * scan/match any file in that directory called `android-<version>`, e.g. `bar/android-1`,
     * `bar/android-2`, etc.
     *
     * This is a data class as it needs to implement [equals] and [hashCode] so that instances can
     * be de-duped by [PatternNode.getExistingOrAdd].
     */
    private data class ApiVersionPatternNode(val pattern: String) : PatternNode() {
        override fun toString() = withDirectorySuffixIfHasChildren(pattern)

        private val regex = Regex(pattern)

        override fun scan(
            config: ScanConfig,
            state: PatternFileState
        ): Sequence<MatchedPatternFile> {
            val contents = config.fileProvider.listFiles(state.file) ?: return emptySequence()
            return contents.flatMap { file ->
                // Match the regex against the file name, if it does not match then ignore this
                // file and all its contents by returning an empty sequence.
                val name = file.name
                val matcher = regex.matchEntire(name) ?: return@flatMap emptySequence()

                // Extract the API version from the file name and make sure that if a range is
                // specified that it is within the range. If it is not then ignore this file and
                // all its contents by returning an empty sequence. This relies on the [pattern]
                // using the first group to match the API version.
                val level = matcher.groups[1]!!.value.toInt()
                val version = ApiVersion.fromLevel(level)
                config.apiVersionRange?.let { apiVersionRange ->
                    if (version !in apiVersionRange) return@flatMap emptySequence()
                }

                // Create a new set of properties with the file and extracted version and then
                // pass them on to the next node in the scanning, or return if this is the last
                // node.
                val newProperties = state.copy(file = file, version = version)
                scanChildrenOrReturnMatching(config, newProperties)
            }
        }
    }

    companion object {
        /**
         * Parse a list of [patterns] into a tree of [PatternNode]s.
         *
         * Each pattern in [patterns] must contain a single `{version:level}` that is a placeholder
         * for the version number.
         */
        fun parsePatterns(patterns: List<String>): PatternNode {
            val root = RootPatternNode()
            for (pattern in patterns) {
                addPattern(root, pattern)
            }
            return root
        }

        /**
         * Add a new pattern [pathPattern] to [root], where [pathPattern] consists of name patterns
         * separated by `/`.
         *
         * Creates a [PatternNode] for each name pattern in the supplied [pathPattern] inserting it
         * into the [root], reusing existing [PatternNode]s where possible.
         */
        private fun addPattern(root: PatternNode, pathPattern: String) {
            // The list of nodes used for the pattern.
            val nodes = mutableListOf<PatternNode>()

            var parent = root
            // Split the pattern using `/` and iterate over each of the parts adding them into the
            // tree structure.
            for (namePattern in pathPattern.split("/")) {
                // Create a node for the pattern.
                val node =
                    when {
                        // Handle when the path is absolute and starts with a /
                        namePattern == "" -> {
                            FixedNamePatternNode("/")
                        }
                        '{' in namePattern -> {
                            parseParameterizedPattern(pathPattern, namePattern)
                        }
                        else -> FixedNamePatternNode(namePattern)
                    }

                nodes.add(node)

                // Find a matching node in the parent adding the new node if no existing node
                // exists. Use the result as the parent for the next node.
                parent = parent.getExistingOrAdd(node)
            }

            // Check to make sure that exactly one of the nodes will match an API version.
            val count = nodes.count { it is ApiVersionPatternNode }
            when {
                count == 0 -> error("Pattern '$pathPattern' does not contain {version:level}")
                count > 1 -> error("Pattern '$pathPattern' contains more than one {version:level}")
            }
        }

        /** [Regex] to find placeholders in a pattern. */
        private val PLACEHOLDER_REGEX = Regex("""\{[^}]+}""")

        private const val PLACEHOLDER_VERSION_LEVEL = "{version:level}"

        /** Parse a parameterized pattern, i.e. one with a placeholder like '{version:level}'. */
        private fun parseParameterizedPattern(pathPattern: String, pattern: String): PatternNode {
            val regexBuilder = StringBuilder()
            var literalStart = 0

            /**
             * Quote any literal text found between the start of the pattern or last placeholder and
             * the [firstNonLiteral].
             */
            fun quoteLiteralText(firstNonLiteral: Int) {
                if (firstNonLiteral > literalStart) {
                    regexBuilder.append(
                        Regex.escape(pattern.substring(literalStart, firstNonLiteral))
                    )
                }
            }

            // Convert the pattern into a regular expression, quoting any literal text and replacing
            // placeholders with an appropriate regular expression.
            for (matchResult in PLACEHOLDER_REGEX.findAll(pattern)) {
                // Quote any literal text found between the start of the pattern or last
                // placeholder and this match.
                quoteLiteralText(matchResult.range.first)

                // The next block of literal text (if any) will start after the match.
                literalStart = matchResult.range.last + 1

                when (val placeholder = matchResult.value) {
                    PLACEHOLDER_VERSION_LEVEL -> {
                        // The level is just one or more digits.
                        regexBuilder.append("""(\d+)""")
                    }
                    else ->
                        error(
                            "Pattern '$pathPattern' contains an unknown placeholder '$placeholder'"
                        )
                }
            }

            // Quote any literal text found at the end of the pattern after the last placeholder.
            quoteLiteralText(pattern.length)

            return ApiVersionPatternNode(regexBuilder.toString())
        }
    }
}

/**
 * Encapsulates the information accrued about a specific [file] that matches a pattern during
 * scanning.
 */
internal data class PatternFileState(
    /**
     * The [File] that has been matched so far.
     *
     * This could be a directory, e.g. `prebuilts/sdk` after matching
     */
    val file: File,

    /** The optional [ApiVersion] that was extracted from the path. */
    val version: ApiVersion? = null,
) {
    /**
     * Construct a [MatchedPatternFile] from this.
     *
     * This must only be called when this has been matched by a leaf [PatternNode] and so is
     * guaranteed to have had [version] set to a non-null value.
     */
    fun matchedPatternFile(dir: File) =
        if (version == null) error("matching pattern could not extract version from $file")
        else MatchedPatternFile(file.relativeTo(dir), version)
}

/** Represents a [File] that matches a pattern encapsulate in a hierarchy of [PatternNode]s. */
data class MatchedPatternFile(
    /**
     * The matched [File].
     *
     * This is relative to the directory supplied in [PatternNode.ScanConfig.dir].
     */
    val file: File,

    /** The [ApiVersion] extracted from the [File] path. */
    val version: ApiVersion,
)
