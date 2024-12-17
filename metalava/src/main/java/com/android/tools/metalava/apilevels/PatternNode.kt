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

/**
 * A node in a tree of path patterns used to select historical API files.
 *
 * e.g. the nodes of `prebuilts/sdk/%/public/android.jar` would be:
 * 1. The root element.
 * 2. `prebuilts`
 * 3. `sdk`
 * 4. `%` - a wild card representing any numbered directory.
 * 5. `public`
 * 6. `android.jar`
 *
 * Where each node is the child of the preceding node.
 *
 * If two or more patterns had matching nodes, then they will share the nodes. e.g.
 * `prebuilts/sdk/%/system/android.jar` would share the first 4 nodes with above followed by two
 * more nodes `system` and `android.jar`.
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
    protected val children = mutableListOf<PatternNode>()

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
    private fun getExistingOrAdd(child: PatternNode) =
        children.find { it == child } ?: child.also { children.add(child) }

    /**
     * The root [PatternNode].
     *
     * Just acts as a container for other [PatternNode]s.
     */
    private class RootPatternNode : PatternNode() {
        override fun toString() = "<root>"
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
     */
    private data class ApiVersionPatternNode(val pattern: String) : PatternNode() {
        override fun toString() = withDirectorySuffixIfHasChildren(pattern)
    }

    companion object {
        /**
         * Parse a list of [patterns] into a tree of [PatternNode]s.
         *
         * Each pattern in [patterns] must contain a single '%' that is a placeholder for the
         * version number.
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
            val versionIndex = pathPattern.indexOf('%')
            if (versionIndex == -1) error("Pattern '$pathPattern' does not contain '%'")
            else if (pathPattern.indexOf('%', versionIndex + 1) != -1)
                error("Pattern '$pathPattern' contains more than one '%'")

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
                        '%' in namePattern -> {
                            parseParameterizedPattern(namePattern)
                        }
                        else -> FixedNamePatternNode(namePattern)
                    }

                // Find a matching node in the parent adding the new node if no existing node
                // exists. Use the result as the parent for the next node.
                parent = parent.getExistingOrAdd(node)
            }
        }

        /** Parse a parameterized pattern, i.e. one with '%'. */
        private fun parseParameterizedPattern(pattern: String): PatternNode {
            // Generate a regular expression pattern from the name pattern.
            val regexpPattern =
                // Replace the % with `\E(\d+)\Q` and wrap the result inside `\Q...\E`. That ensures
                // that all the text apart from the % will be treated literally in the resulting
                // regular expression.
                """\Q${pattern.replace("%", """\E(\d+)\Q""")}\E"""
                    // Remove any `\Q\E` that are added by the preceding step, e.g. when namePattern
                    // starts or ends with `%`.
                    .replace("""\Q\E""", "")
            return ApiVersionPatternNode(regexpPattern)
        }
    }
}
