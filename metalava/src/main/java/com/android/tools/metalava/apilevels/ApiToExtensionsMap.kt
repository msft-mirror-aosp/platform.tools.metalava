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
import javax.xml.parsers.SAXParserFactory
import org.xml.sax.Attributes
import org.xml.sax.helpers.DefaultHandler

/**
 * The base of dessert release independent SDKs.
 *
 * A dessert release independent SDK is one which is not coupled to the Android dessert release
 * numbering. Any SDK greater than or equal to this is not comparable to either each other, or to
 * the Android dessert release. e.g. `1000000` is not the same as, later than, or earlier than
 * SDK 31. Similarly, `1000001` is not the same as, later than, or earlier then `1000000`.
 */
private const val DESSERT_RELEASE_INDEPENDENT_SDK_BASE = 1000000

/**
 * A filter of classes, fields and methods that are allowed in and extension SDK, and for each item,
 * what extension SDK it first appeared in. Also, a mapping between SDK name and numerical ID.
 *
 * Internally, the filters are represented as a tree, where each node in the tree matches a part of
 * a package, class or member name. For example, given the patterns
 *
 * ```
 * com.example.Foo -> [A] com.example.Foo#someMethod -> [B] com.example.Bar -> [A, C]
 * ```
 *
 * (anything prefixed with com.example.Foo is allowed and part of the A extension, except for
 * com.example.Foo#someMethod which is part of B; anything prefixed with com.example.Bar is part of
 * both A and C), the internal tree looks like
 *
 * ```
 * root -> null com -> null example -> null Foo -> [A] someMethod -> [B] Bar -> [A, C]
 * ```
 */
class ApiToExtensionsMap
private constructor(
    private val sdkIdentifiers: Set<SdkIdentifier>,
    private val root: Node,
) {
    fun isEmpty(): Boolean = root.children.isEmpty() && root.extensions.isEmpty()

    fun getExtensions(clazz: ApiClass): List<String> = getExtensions(clazz.name.toDotNotation())

    fun getExtensions(clazz: ApiClass, member: ApiElement): List<String> =
        getExtensions(clazz.name.toDotNotation() + "#" + member.name.toDotNotation())

    fun getExtensions(what: String): List<String> {
        // Special case: getExtensionVersion is not part of an extension
        val sdkExtensions = "android.os.ext.SdkExtensions"
        if (what == sdkExtensions || what == "$sdkExtensions#getExtensionVersion") {
            return listOf()
        }

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
     * Construct a `sdks` attribute value
     *
     * `sdks` is an XML attribute on class, method and fields in the XML generated by
     * ARG_GENERATE_API_LEVELS. It expresses in what SDKs an API exist, and in which version of each
     * SDK it was first introduced; `sdks` replaces the `since` attribute.
     *
     * The format of `sdks` is
     *
     * ```
     * sdks="ext:version[,ext:version[,...]]
     * ```
     *
     * where <ext> is the numerical ID of the SDK, and <version> is the version in which the API was
     * introduced.
     *
     * The returned string is guaranteed to be one of
     * - list of (extensions,finalized_version) pairs + ANDROID_SDK:finalized_dessert
     * - list of (extensions,finalized_version) pairs
     * - ANDROID_SDK:finalized_dessert
     * - ANDROID_SDK:next_dessert_int (for symbols not finalized anywhere)
     *
     * See go/mainline-sdk-api-versions-xml for more information.
     *
     * @param androidSince Android dessert version in which this symbol was finalized, or
     *   notFinalizedValue if this symbol has not been finalized in an Android dessert
     * @param notFinalizedValue value used together with the Android SDK ID to indicate that this
     *   symbol has not been finalized at all
     * @param extensions names of the SDK extensions in which this symbol has been finalized; may be
     *   non-empty even if extensionsSince is [ApiElement.NEVER].
     * @param extensionsSince the version of the SDK extensions in which this API was initially
     *   introduced (same value for all SDK extensions), or [ApiElement.NEVER] if this symbol has
     *   not been finalized in any SDK extension (regardless of the [extensions] argument)
     * @return an `sdks` value suitable for including verbatim in XML
     */
    fun calculateSdksAttr(
        androidSince: SdkVersion,
        notFinalizedValue: SdkVersion,
        extensions: List<String>,
        extensionsSince: ExtVersion
    ): String {
        // Special case: symbol not finalized anywhere -> "ANDROID_SDK:next_dessert_int"
        if (androidSince == notFinalizedValue && extensionsSince == ApiElement.NEVER) {
            return "$ANDROID_PLATFORM_SDK_ID:$notFinalizedValue"
        }

        val versions = mutableSetOf<String>()
        // Only include SDK extensions if the symbol has been finalized in at least one
        if (extensionsSince != ApiElement.NEVER) {
            for (ext in extensions) {
                val ident =
                    sdkIdentifiers.find { it.shortname == ext }
                        ?: throw IllegalStateException("unknown extension SDK \"$ext\"")
                assert(ident.id != ANDROID_PLATFORM_SDK_ID) // invariant
                if (ident.id >= DESSERT_RELEASE_INDEPENDENT_SDK_BASE || ident.id <= androidSince) {
                    versions.add("${ident.id}:$extensionsSince")
                }
            }
        }

        // Only include the Android SDK in `sdks` if
        // - the symbol has been finalized in an Android dessert, and
        // - the symbol has been finalized in at least one SDK extension
        if (androidSince != notFinalizedValue && versions.isNotEmpty()) {
            versions.add("$ANDROID_PLATFORM_SDK_ID:$androidSince")
        }
        return versions.joinToString(",")
    }

    companion object {
        // Hard-coded ID for the Android platform SDK. Used identically as the extension SDK IDs
        // to express when an API first appeared in an SDK.
        const val ANDROID_PLATFORM_SDK_ID = 0

        private val REGEX_DELIMITERS = Regex("[.#$]")

        /**
         * Create an ApiToExtensionsMap from a list of text based rules.
         *
         * The input is XML:
         *
         *     <?xml version="1.0" encoding="utf-8"?>
         *     <sdk-extensions-info version="1">
         *         <sdk name="<name>" shortname="<short-name>" id="<int>" reference="<constant>" />
         *         <symbol jar="<jar>" pattern="<pattern>" sdks="<sdks>" />
         *     </sdk-extensions-info>
         *
         * The <sdk> and <symbol> tags may be repeated.
         * - <name> is a long name for the SDK, e.g. "R Extensions".
         * - <short-name> is a short name for the SDK, e.g. "R-ext".
         * - <id> is the numerical identifier for the SDK, e.g. 30. It is an error to use the
         *   Android SDK ID (0).
         * - <jar> is the jar file symbol belongs to, named after the jar file in
         *   prebuilts/sdk/extensions/<int>/public, e.g. "framework-sdkextensions".
         * - <constant> is a Java symbol that can be passed to `SdkExtensions.getExtensionVersion`
         *   to look up the version of the corresponding SDK, e.g.
         *   "android/os/Build$VERSION_CODES$R"
         * - <pattern> is either '*', which matches everything, or a 'com.foo.Bar$Inner#member'
         *   string (or prefix thereof terminated before . or $), which matches anything with that
         *   prefix. Note that arguments and return values of methods are omitted (and there is no
         *   way to distinguish overloaded methods).
         * - <sdks> is a comma separated list of SDKs in which the symbol defined by <jar> and
         *   <pattern> appears; the list items are <name> attributes of SDKs defined in the XML.
         *
         * It is an error to specify the same <jar> and <pattern> pair twice.
         *
         * A more specific <symbol> rule has higher precedence than a less specific rule.
         *
         * @param filterByJar jar file to limit lookups to: ignore symbols not present in this jar
         *   file
         * @param xml XML as described above
         * @throws IllegalArgumentException if the XML is malformed
         */
        fun fromXml(filterByJar: String, xml: String): ApiToExtensionsMap {
            val root = Node("<root>")
            val sdkIdentifiers = mutableSetOf<SdkIdentifier>()
            val allSeenExtensions = mutableSetOf<String>()

            val parser = SAXParserFactory.newDefaultInstance().newSAXParser()
            try {
                parser.parse(
                    xml.byteInputStream(),
                    object : DefaultHandler() {
                        override fun startElement(
                            uri: String,
                            localName: String,
                            qualifiedName: String,
                            attributes: Attributes
                        ) {
                            when (qualifiedName) {
                                "sdk" -> {
                                    val id = attributes.getIntOrThrow(qualifiedName, "id")
                                    val shortname =
                                        attributes.getStringOrThrow(qualifiedName, "shortname")
                                    val name = attributes.getStringOrThrow(qualifiedName, "name")
                                    val reference =
                                        attributes.getStringOrThrow(qualifiedName, "reference")
                                    sdkIdentifiers.add(
                                        SdkIdentifier(id, shortname, name, reference)
                                    )
                                }
                                "symbol" -> {
                                    val jar = attributes.getStringOrThrow(qualifiedName, "jar")
                                    if (jar != filterByJar) {
                                        return
                                    }
                                    val sdks =
                                        attributes
                                            .getStringOrThrow(qualifiedName, "sdks")
                                            .split(',')
                                    if (sdks != sdks.distinct()) {
                                        throw IllegalArgumentException(
                                            "symbol lists the same SDK multiple times: '$sdks'"
                                        )
                                    }
                                    allSeenExtensions.addAll(sdks)
                                    val pattern =
                                        attributes.getStringOrThrow(qualifiedName, "pattern")
                                    if (pattern == "*") {
                                        root.extensions = sdks
                                        return
                                    }
                                    // add each part of the pattern as separate nodes, e.g. if
                                    // pattern is
                                    // com.example.Foo, add nodes, "com" -> "example" -> "Foo"
                                    val parts = pattern.split(REGEX_DELIMITERS)
                                    var node = root.children.addNode(parts[0])
                                    for (name in parts.stream().skip(1)) {
                                        node = node.children.addNode(name)
                                    }
                                    if (node.extensions.isNotEmpty()) {
                                        throw IllegalArgumentException(
                                            "duplicate pattern: $pattern"
                                        )
                                    }
                                    node.extensions = sdks
                                }
                            }
                        }
                    }
                )
            } catch (e: Throwable) {
                throw IllegalArgumentException("failed to parse xml", e)
            }

            // verify: the predefined Android platform SDK ID is not reused as an extension SDK ID
            if (sdkIdentifiers.any { it.id == ANDROID_PLATFORM_SDK_ID }) {
                throw IllegalArgumentException(
                    "bad SDK definition: the ID $ANDROID_PLATFORM_SDK_ID is reserved for the Android platform SDK"
                )
            }

            // verify: all rules refer to declared SDKs
            val allSdkNames = sdkIdentifiers.map { it.shortname }.toList()
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
            if (sdkIdentifiers.size != sdkIdentifiers.distinctBy { it.shortname }.size) {
                throw IllegalArgumentException("bad SDK definitions: duplicate SDK short names")
            }

            // verify: no duplicate SDK names
            if (sdkIdentifiers.size != sdkIdentifiers.distinctBy { it.name }.size) {
                throw IllegalArgumentException("bad SDK definitions: duplicate SDK names")
            }

            // verify: no duplicate SDK references
            if (sdkIdentifiers.size != sdkIdentifiers.distinctBy { it.reference }.size) {
                throw IllegalArgumentException("bad SDK definitions: duplicate SDK references")
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

private fun Attributes.getStringOrThrow(tag: String, attr: String): String =
    getValue(attr) ?: throw IllegalArgumentException("<$tag>: missing attribute: $attr")

private fun Attributes.getIntOrThrow(tag: String, attr: String): Int =
    getStringOrThrow(tag, attr).toIntOrNull()
        ?: throw IllegalArgumentException("<$tag>: attribute $attr: not an integer")

private fun Set<Node>.findNode(breadcrumb: String): Node? = find { it.breadcrumb == breadcrumb }

private fun String.toDotNotation(): String = split('(')[0].replace('/', '.')

private class Node(val breadcrumb: String) {
    var extensions: List<String> = emptyList()
    val children: MutableSet<Node> = mutableSetOf()
}
