/*
 * Copyright (C) 2017 The Android Open Source Project
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

import java.io.PrintWriter

/** Represents an API element, e.g. class, method or field. */
open class ApiElement : Comparable<ApiElement> {
    /** Returns the name of the API element. */
    val name: String
    /** The Android API level of this ApiElement. */
    /** The Android platform SDK version this API was first introduced in. */
    var since = 0
        private set
    /** The extension version of this ApiElement. */
    /** The Android extension SDK version this API was first introduced in. */
    var sinceExtension = NEVER
        private set

    /**
     * The SDKs and their versions this API was first introduced in.
     *
     * The value is a comma-separated list of &lt;int&gt;:&lt;int&gt; values, where the first
     * &lt;int&gt; is the integer ID of an SDK, and the second &lt;int&gt; the version of that SDK,
     * in which this API first appeared.
     *
     * This field is a super-set of mSince, and if non-null/non-empty, should be preferred.
     */
    var sdks: String? = null
        private set

    var mainlineModule: String? = null
        private set

    /**
     * The API level this element was deprecated in, should only be used if [isDeprecated] is true.
     */
    var deprecatedIn = 0
        private set

    var lastPresentIn = 0
        private set

    /**
     * @param name the name of the API element
     * @param version an API version for which the API element existed, or -1 if the class does not
     *   yet exist in the Android SDK (only in extension SDKs)
     * @param deprecated whether the API element was deprecated in the API version in question
     */
    internal constructor(name: String, version: Int, deprecated: Boolean = false) {
        this.name = name
        since = version
        lastPresentIn = version
        if (deprecated) {
            deprecatedIn = version
        }
    }

    /** @param name the name of the API element */
    internal constructor(name: String) {
        this.name = name
    }

    /**
     * Checks if this API element was introduced not later than another API element.
     *
     * @param other the API element to compare to
     * @return true if this API element was introduced not later than `other`
     */
    fun introducedNotLaterThan(other: ApiElement?): Boolean {
        return since <= other!!.since
    }

    /**
     * Updates the API element with information for a specific API version.
     *
     * @param version an API version for which the API element existed
     * @param deprecated whether the API element was deprecated in the API version in question
     */
    fun update(version: Int, deprecated: Boolean) {
        assert(version > 0)
        if (since > version) {
            since = version
        }
        if (lastPresentIn < version) {
            lastPresentIn = version
        }
        if (deprecated) {
            // If it was not previously deprecated or was deprecated in a later version than this
            // one then deprecate it in this version.
            if (deprecatedIn == 0 || deprecatedIn > version) {
                deprecatedIn = version
            }
        } else {
            // If it was previously deprecated and was deprecated in an earlier version than this
            // one then treat it as being undeprecated.
            if (deprecatedIn != 0 && deprecatedIn < version) {
                deprecatedIn = 0
            }
        }
    }

    /**
     * Updates the API element with information for a specific API version.
     *
     * @param version an API version for which the API element existed
     */
    fun update(version: Int) {
        update(version, isDeprecated)
    }

    /**
     * Analogous to update(), but for extensions sdk versions.
     *
     * @param version an extension SDK version for which the API element existed
     */
    fun updateExtension(version: Int) {
        assert(version > 0)
        if (sinceExtension > version) {
            sinceExtension = version
        }
    }

    fun updateSdks(sdks: String?) {
        this.sdks = sdks
    }

    fun updateMainlineModule(module: String?) {
        mainlineModule = module
    }

    val isDeprecated: Boolean
        /** Checks whether the API element is deprecated or not. */
        get() = deprecatedIn != 0

    /**
     * Prints an XML representation of the element to a writer terminated by a line break.
     * Attributes with values matching the parent API element are omitted.
     *
     * @param tag the tag of the XML element
     * @param parentElement the parent API element
     * @param indent the whitespace prefix to insert before the XML element
     * @param writer the writer to which the XML element will be written.
     */
    open fun print(tag: String?, parentElement: ApiElement, indent: String, writer: PrintWriter) {
        print(tag, true, parentElement, indent, writer)
    }

    /**
     * Prints an XML representation of the element to a writer terminated by a line break.
     * Attributes with values matching the parent API element are omitted.
     *
     * @param tag the tag of the XML element
     * @param closeTag if true the XML element is terminated by "/>", otherwise the closing tag of
     *   the element is not printed
     * @param parentElement the parent API element
     * @param indent the whitespace prefix to insert before the XML element
     * @param writer the writer to which the XML element will be written.
     * @see printClosingTag
     */
    fun print(
        tag: String?,
        closeTag: Boolean,
        parentElement: ApiElement,
        indent: String?,
        writer: PrintWriter
    ) {
        writer.print(indent)
        writer.print('<')
        writer.print(tag)
        writer.print(" name=\"")
        writer.print(encodeAttribute(name))
        if (!isEmpty(mainlineModule) && !isEmpty(sdks)) {
            writer.print("\" module=\"")
            writer.print(encodeAttribute(mainlineModule!!))
        }
        if (since > parentElement.since) {
            writer.print("\" since=\"")
            writer.print(since)
        }
        if (!isEmpty(sdks) && sdks != parentElement.sdks) {
            writer.print("\" sdks=\"")
            writer.print(sdks)
        }
        if (deprecatedIn != 0 && deprecatedIn != parentElement.deprecatedIn) {
            writer.print("\" deprecated=\"")
            writer.print(deprecatedIn)
        }
        if (lastPresentIn < parentElement.lastPresentIn) {
            writer.print("\" removed=\"")
            writer.print(lastPresentIn + 1)
        }
        writer.print('"')
        if (closeTag) {
            writer.print('/')
        }
        writer.println('>')
    }

    private fun isEmpty(s: String?): Boolean {
        return s.isNullOrEmpty()
    }

    /**
     * Prints homogeneous XML elements to a writer. Each element is printed on a separate line.
     * Attributes with values matching the parent API element are omitted.
     *
     * @param elements the elements to print
     * @param tag the tag of the XML elements
     * @param indent the whitespace prefix to insert before each XML element
     * @param writer the writer to which the XML elements will be written.
     */
    fun print(elements: Collection<ApiElement>, tag: String?, indent: String, writer: PrintWriter) {
        for (element in elements.sorted()) {
            element.print(tag, this, indent, writer)
        }
    }

    override fun compareTo(other: ApiElement): Int {
        return name.compareTo(other.name)
    }

    companion object {
        const val NEVER = Int.MAX_VALUE

        /**
         * Prints a closing tag of an XML element terminated by a line break.
         *
         * @param tag the tag of the element
         * @param indent the whitespace prefix to insert before the closing tag
         * @param writer the writer to which the XML element will be written.
         */
        fun printClosingTag(tag: String?, indent: String?, writer: PrintWriter) {
            writer.print(indent)
            writer.print("</")
            writer.print(tag)
            writer.println('>')
        }

        private fun encodeAttribute(attribute: String): String {
            return buildString {
                val n = attribute.length
                // &, ", ' and < are illegal in attributes; see
                // http://www.w3.org/TR/REC-xml/#NT-AttValue
                // (' legal in a " string and " is legal in a ' string but here we'll stay on the
                // safe
                // side).
                for (i in 0 until n) {
                    when (val c = attribute[i]) {
                        '"' -> {
                            append("&quot;") // $NON-NLS-1$
                        }
                        '<' -> {
                            append("&lt;") // $NON-NLS-1$
                        }
                        '\'' -> {
                            append("&apos;") // $NON-NLS-1$
                        }
                        '&' -> {
                            append("&amp;") // $NON-NLS-1$
                        }
                        else -> {
                            append(c)
                        }
                    }
                }
            }
        }
    }
}
