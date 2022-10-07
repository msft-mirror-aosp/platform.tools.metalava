/*
 * Copyright (C) 2022 The Android Open Source Project
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

import com.android.tools.metalava.SdkIdentifier

/**
 * A filter of classes, fields and methods that are allowed in and extension SDK, and for each item,
 * what extension SDK it first appeared in. Also, a mapping between SDK name and numerical ID.
 *
 * Internally, the filers are represented as a tree, where each node in the tree matches a part of a
 * package, class or member name. For example, given the patterns
 *
 *   com.example.Foo             -> [A]
 *   com.example.Foo#someMethod  -> [B]
 *   com.example.Bar             -> [A, C]
 *
 * (anything prefixed with com.example.Foo is allowed and part of the A extension, except for
 * com.example.Foo#someMethod which is part of B; anything prefixed with com.example.Bar is part
 * of both A and C), the internal tree looks like
 *
 *   root -> null
 *     com -> null
 *       example -> null
 *         Foo -> [A]
 *           someMethod -> [B]
 *         Bar -> [A, C]
 */
class ApiToExtensionsMap private constructor(
    private val sdkIdentifiers: Set<SdkIdentifier>,
    private val root: Node,
) {
    fun getExtensions(clazz: ApiClass): Set<String> = getExtensions(clazz.name.toDotNotation())

    fun getExtensions(clazz: ApiClass, member: ApiElement): Set<String> =
        getExtensions(clazz.name.toDotNotation() + "#" + member.name.toDotNotation())

    fun getExtensions(what: String): Set<String> {
        val parts = what.split(REGEX_DELIMITERS)

        var lastSeenExtensions = root.extensions
        var node = root.children.findNode(parts[0]) ?: return lastSeenExtensions
        if (node.extensions.isNotEmpty()) {
            lastSeenExtensions = node.extensions
        }

        for (part in parts.stream().skip(1)) {
            node = node.children.findNode(part) ?: break
            if (node.extensions.isNotEmpty()) {
                lastSeenExtensions = node.extensions
            }
        }
        return lastSeenExtensions
    }

    fun getSdkIdentifiers(): Set<SdkIdentifier> = sdkIdentifiers.toSet()

    /**
     * Construct a `from` attribute value
     *
     * `from` is an XML attribute on class, method and fields in the XML generated by ARG_GENERATE_API_LEVELS.
     * It expresses in what SDKs an API exist, and in which version of each SDK it was first introduced;
     * `from` replaces the `since` attribute.
     *
     * The format of `from` is
     *
     * from="ext:version[,ext:version[,...]]
     *
     * where <ext> is the numerical ID of the SDK, and <version> is the version in which the API was introduced.
     *
     * See go/mainline-sdk-api-versions-xml for more information.
     *
     * @param androidSince the version of the Android platform in which this API was introduced, or null if it is not part of the Android platform
     * @param extensions names of the extension SDKs in which this API exists
     * @param extensionsSince the version of the extension SDKs in which this API was initially introduced (same value for all extensions)
     * @return a `from` value suitable for including verbatim in XML
     */
    fun calculateFromAttr(
        androidSince: Int?,
        extensions: Set<String>,
        extensionsSince: Int
    ): String {
        val versions = mutableSetOf<String>()
        if (androidSince != null) {
            versions.add("$ANDROID_PLATFORM_SDK_ID:$androidSince")
        }
        for (ext in extensions) {
            val ident = sdkIdentifiers.find {
                it.name == ext
            } ?: throw IllegalStateException("unknown extension SDK \"$ext\"")
            assert(ident.id != ANDROID_PLATFORM_SDK_ID) // invariant
            versions.add("${ident.id}:$extensionsSince")
        }
        return versions.joinToString(",")
    }

    companion object {
        // Hard-coded ID for the Android platform SDK. Used identically as the extension SDK IDs
        // to express when an API first appeared in an SDK.
        private const val ANDROID_PLATFORM_SDK_ID = 0

        private val REGEX_DELIMITERS = Regex("[.#$]")
        private val REGEX_WHITESPACE = Regex("\\s+")

        /*
         * Create an ApiToExtensionsMap from a list of text based rules.
         *
         * The input is a multi-line string, where each rules is written on the format
         *
         *     <jar-file> <pattern> <ext> [<ext> [...]]
         *
         * <pattern> is either '*', which matches everything, or a 'com.foo.Bar$Inner#member' string
         * (or prefix thereof terminated before . or $), which matches anything with that prefix.
         * Note that arguments and return values of methods are omitted (and there is no way to
         * distinguish overloaded methods).
         *
         * <ext> is the short name of an extension SDK (e.g. T).
         *
         * All fields are separated by whitespace (spaces or tabs).
         *
         * Lines beginning with # are comments and are ignored. Blank lines are ignored.
         *
         * It is an error to specify the same <pattern> twice.
         *
         * A more specific rule has higher precedence than a less specific rule.
         *
         * @param jar jar file to limit lookups to: ignore lines belonging to other jar files
         * @param rules multi-line string of filter patterns as described above
         * @throws IllegalArgumentException if the input is malformed
         */
        fun fromString(jar: String, rules: String): ApiToExtensionsMap {
            val root = Node("<root>")
            val sdkIdentifiers = mutableSetOf<SdkIdentifier>()
            val allSeenExtensions = mutableSetOf<String>()
            val lines = rules.lines().filter {
                it.isNotBlank() && !it.startsWith('#')
            }
            for (line in lines) {
                val all = line.split(REGEX_WHITESPACE, 3)
                if (all.size == 2) {
                    // This line is an SDK declaration on the format
                    // <short-name>  <numerical-id>

                    sdkIdentifiers.add(SdkIdentifier(all[1].toInt(), all[0]))
                } else if (all.size == 3) {
                    // This line is a filter pattern on the format
                    // <jar-name>  <pattern>  <sdk>[ <sdk>[ ...]]

                    if (jar != all[0]) {
                        // this is not the jar file you're looking for
                        continue
                    }
                    val pattern = all[1]
                    val extensions = all[2].split(REGEX_WHITESPACE).toSet()
                    allSeenExtensions.addAll(extensions)

                    if (pattern == "*") {
                        root.extensions = extensions
                        continue
                    }

                    // add each part of the pattern as separate nodes, e.g. if pattern is
                    // com.example.Foo, add nodes, "com" -> "example" -> "Foo"
                    val parts = pattern.split(REGEX_DELIMITERS)
                    var node = root.children.addNode(parts[0])
                    for (name in parts.stream().skip(1)) {
                        node = node.children.addNode(name)
                    }
                    if (node.extensions.isNotEmpty()) {
                        throw IllegalArgumentException("duplicate pattern: $line")
                    }
                    node.extensions = extensions
                } else {
                    throw IllegalArgumentException("bad input: $line")
                }
            }

            // verify: the predefined Android platform SDK ID is not reused as an extension SDK ID
            if (sdkIdentifiers.any { it.id == ANDROID_PLATFORM_SDK_ID }) {
                throw java.lang.IllegalArgumentException("bad SDK definition: the ID $ANDROID_PLATFORM_SDK_ID is reserved for the Android platform SDK")
            }

            // verify: all rules refer to declared SDKs
            val allSdkNames = sdkIdentifiers.map { it.name }.toList()
            for (ext in allSeenExtensions) {
                if (!allSdkNames.contains(ext)) {
                    throw IllegalArgumentException("bad SDK definitions: undefined SDK $ext")
                }
            }

            // verify: no duplicate SDK IDs
            if (sdkIdentifiers.size != sdkIdentifiers.distinctBy { it.id }.size) {
                throw IllegalArgumentException("bad SDK definitions: duplicate SDK IDs")
            }

            // verify: no duplicate SDK names
            if (sdkIdentifiers.size != sdkIdentifiers.distinctBy { it.name }.size) {
                throw IllegalArgumentException("bad SDK definitions: duplicate SDK names")
            }

            return ApiToExtensionsMap(sdkIdentifiers, root)
        }
    }
}

private fun MutableSet<Node>.addNode(name: String): Node {
    findNode(name)?.let {
        return it
    }
    val node = Node(name)
    add(node)
    return node
}

private fun Set<Node>.findNode(breadcrumb: String): Node? = find { it.breadcrumb == breadcrumb }

private fun String.toDotNotation(): String = split('(')[0].replace('/', '.')

private class Node(val breadcrumb: String) {
    var extensions: Set<String> = emptySet()
    val children: MutableSet<Node> = mutableSetOf()
}
