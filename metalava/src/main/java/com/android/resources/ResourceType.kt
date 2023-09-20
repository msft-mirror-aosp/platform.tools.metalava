/*
 * Copyright (C) 2007 The Android Open Source Project
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
// Copied from tools/base/layoutlib-api
package com.android.resources

/**
 * Enum representing a type of compiled resource.
 *
 * See `ResourceType` in aapt2/Resource.h.
 *
 * Although the [displayName] and [alternateXmlNames] are currently unused they were kept for a few
 * reasons:
 * 1. It's not clear that they will not be needed in metalava.
 * 2. It makes it easier to copy new resource types from the original when needed.
 * 3. It has little maintenance cost.
 */
enum class ResourceType(
    /** Returns the resource type name, as used by XML files. */
    private val xmlName: String,
    /** Returns a translated display name for the resource type. */
    @Suppress("unused") private val displayName: String,
    private val kind: Kind = Kind.REAL,
    @Suppress("unused") private val alternateXmlNames: List<String> = emptyList(),
) {
    ANIM("anim", "Animation"),
    ANIMATOR("animator", "Animator"),
    ARRAY("array", "Array", "string-array", "integer-array"),
    ATTR("attr", "Attr"),
    BOOL("bool", "Boolean"),
    COLOR("color", "Color"),
    DIMEN("dimen", "Dimension"),
    DRAWABLE("drawable", "Drawable"),
    FONT("font", "Font"),
    FRACTION("fraction", "Fraction"),
    ID("id", "ID"),
    INTEGER("integer", "Integer"),
    INTERPOLATOR("interpolator", "Interpolator"),
    LAYOUT("layout", "Layout"),
    MENU("menu", "Menu"),
    MIPMAP("mipmap", "Mip Map"),
    NAVIGATION("navigation", "Navigation"),
    PLURALS("plurals", "Plurals"),
    RAW("raw", "Raw"),
    STRING("string", "String"),
    STYLE("style", "Style"),
    STYLEABLE("styleable", "Styleable", Kind.STYLEABLE),
    TRANSITION("transition", "Transition"),
    XML("xml", "XML"),

    /**
     * This is not actually used. Only there because they get parsed and since we want to detect new
     * resource type, we need to have this one exist.
     */
    PUBLIC("public", "Public visibility modifier", Kind.SYNTHETIC),

    /**
     * This type is used for elements dynamically generated by the parsing of aapt:attr nodes. The
     * "aapt:attr" allow to inline resources as part of a different resource, for example, a
     * drawable as part of a layout. When the parser encounters one of these nodes, it will generate
     * a synthetic _aaptattr reference.
     */
    AAPT("_aapt", "Aapt Attribute", Kind.SYNTHETIC),

    /**
     * This tag is used for marking a resource overlayable, i.e. that it can be overlaid at runtime
     * by RROs (Runtime Resource Overlays). This is a new feature supported starting Android 10.
     * This tag (and the content following it in that node) does not define a resource.
     */
    OVERLAYABLE("overlayable", "Overlayable tag", Kind.SYNTHETIC),

    /** Represents item tags inside a style definition. */
    STYLE_ITEM("item", "Style Item", Kind.SYNTHETIC),

    /**
     * Not an actual resource type from AAPT. Used to provide sample data values in the tools
     * namespace
     */
    SAMPLE_DATA("sample", "Sample data", Kind.SYNTHETIC),

    /**
     * Not a real resource, but a way of defining a resource reference that will be replaced with
     * its actual value during linking. Does not exist at runtime, nor does it appear in the R
     * class. Only present in raw and flat resources.
     */
    MACRO("macro", "Macro resource replacement", Kind.SYNTHETIC);

    private enum class Kind {
        /** These types are used both in the R and as XML tag names. */
        REAL,

        /**
         * Styleables are handled by aapt but don't end up in the resource table. They have an R
         * inner class (called `styleable`), are declared in XML (using `declare-styleable`) but
         * cannot be referenced from XML.
         */
        STYLEABLE,

        /**
         * Other types that are not known to aapt, but are used by tools to represent some
         * information in the resources system.
         */
        SYNTHETIC
    }

    constructor(
        xmlName: String,
        displayName: String,
        vararg alternateXmlNames: String,
    ) : this(
        xmlName = xmlName,
        displayName = displayName,
        kind = Kind.REAL,
        alternateXmlNames = alternateXmlNames.toList()
    )

    override fun toString(): String {
        // Unfortunately we still have code that relies on toString() returning the aapt name.
        return xmlName
    }

    companion object {
        private val CLASS_NAMES: Map<String, ResourceType> = buildMap {
            put(STYLEABLE.xmlName, STYLEABLE)
            put(AAPT.xmlName, AAPT)
            for (type in values()) {
                if (type.kind != Kind.REAL || type == STYLEABLE) {
                    continue
                }
                put(type.xmlName, type)
            }
        }

        /**
         * Returns the enum by its name as it appears in the R class.
         *
         * @param className name of the inner class of the R class, e.g. "string" or "styleable".
         */
        fun fromClassName(className: String): ResourceType? {
            return CLASS_NAMES[className]
        }
    }
}
