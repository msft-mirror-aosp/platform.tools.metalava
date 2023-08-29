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

package com.android.tools.metalava.model.text

import com.android.tools.metalava.model.MethodItem
import java.io.LineNumberReader
import java.io.Reader
import java.util.Locale

/**
 * Encapsulates all the information related to the format of a signature file.
 *
 * Some of these will be initialized from the version specific defaults and some will be overridden
 * on the command line.
 */
data class FileFormat(
    val version: Version,
    val specifiedOverloadedMethodOrder: OverloadedMethodOrder? = null,
    val kotlinStyleNulls: Boolean,
    /**
     * If non-null then it indicates that the file format is being used to migrate a signature file
     * to fix a bug that causes a change in the signature file contents but not a change in version.
     * e.g. This would be used when migrating a 2.0 file format that currently uses source order for
     * overloaded methods (using a command line parameter to override the default order of
     * signature) to a 2.0 file that uses signature order.
     *
     * This should be used to provide an explanation as to what is being migrated and why. It should
     * be relatively concise, e.g. something like:
     * ```
     * "See <short-url> for details"
     * ```
     *
     * This value cannot use `,` (because it is a separator between properties in [specifier]) or
     * `\n` (because it is the terminator of the signature format line).
     */
    val migrating: String? = null,
    val conciseDefaultValues: Boolean,
    val specifiedAddAdditionalOverrides: Boolean? = null,
) {
    init {
        if (migrating != null && "[,\n]".toRegex().find(migrating) != null) {
            throw IllegalStateException(
                """invalid value for property 'migrating': '$migrating' contains at least one invalid character from the set {',', '\n'}"""
            )
        }
    }

    // This defaults to SIGNATURE but can be overridden on the command line.
    val overloadedMethodOrder
        get() = specifiedOverloadedMethodOrder ?: OverloadedMethodOrder.SIGNATURE

    // This defaults to false but can be overridden on the command line.
    val addAdditionalOverrides
        get() = specifiedAddAdditionalOverrides ?: false

    /** The base version of the file format. */
    enum class Version(
        /** The version number of this as a string, e.g. "3.0". */
        internal val versionNumber: String,

        /** Indicates whether the version supports properties fully or just for migrating. */
        internal val propertySupport: PropertySupport = PropertySupport.FOR_MIGRATING_ONLY,

        /**
         * Factory used to create a [FileFormat] instance encapsulating the defaults of this
         * version.
         */
        factory: (Version) -> FileFormat,
    ) {
        V2(
            versionNumber = "2.0",
            factory = { version ->
                FileFormat(
                    version = version,
                    kotlinStyleNulls = false,
                    conciseDefaultValues = false,
                )
            }
        ),
        V3(
            versionNumber = "3.0",
            factory = { version ->
                V2.defaults.copy(
                    version = version,
                    // This adds kotlinStyleNulls = true
                    kotlinStyleNulls = true,
                )
            }
        ),
        V4(
            versionNumber = "4.0",
            factory = { version ->
                V3.defaults.copy(
                    version = version,
                    // This adds conciseDefaultValues = true
                    conciseDefaultValues = true,
                )
            }
        ),
        V5(
            versionNumber = "5.0",
            // This adds full property support.
            propertySupport = PropertySupport.FULL,
            factory = { version ->
                V4.defaults.copy(
                    version = version,
                    // This does not add any property defaults, just full property support.
                )
            }
        );

        /**
         * The defaults associated with this version.
         *
         * It is initialized via a factory to break the cycle where the [Version] constructor
         * depends on the [FileFormat] constructor and vice versa.
         */
        val defaults = factory(this)
    }

    internal enum class PropertySupport {
        /**
         * The version only supports properties being temporarily specified in the signature file to
         * aid migration.
         */
        FOR_MIGRATING_ONLY,

        /**
         * The version supports properties fully, both for migration and permanent customization in
         * the signature file.
         */
        FULL
    }

    enum class OverloadedMethodOrder(val comparator: Comparator<MethodItem>) {
        /** Sort overloaded methods according to source order. */
        SOURCE(MethodItem.sourceOrderForOverloadedMethodsComparator),

        /** Sort overloaded methods by their signature. */
        SIGNATURE(MethodItem.comparator)
    }

    /**
     * Apply some optional overrides, provided from the command line, to this format, returning a
     * new format.
     *
     * @param overloadedMethodOrder If non-null then override the [overloadedMethodOrder] property.
     * @return a format with the overrides applied.
     */
    fun applyOptionalCommandLineSuppliedOverrides(
        overloadedMethodOrder: OverloadedMethodOrder? = null,
    ): FileFormat {
        // Only apply the overloadedMethodOrder command line override to the format if it has not
        // already been specified.
        val effectiveOverloadedMethodOrder =
            this.specifiedOverloadedMethodOrder ?: overloadedMethodOrder

        return copy(
            specifiedOverloadedMethodOrder = effectiveOverloadedMethodOrder,
        )
    }

    /**
     * Get the header for the signature file that corresponds to this format.
     *
     * This always starts with the signature format prefix, and the version number.
     *
     * For versions < 5.0 this also includes some optional properties added on the same line as, and
     * just after, the version number to aid with migrating signature files to fix bugs.
     *
     * For version >= 5.0 this also includes some optional properties specified after the version
     * line with one property per line, prefixed with [PROPERTY_LINE_PREFIX].
     */
    fun header(): String {
        val supportsPropertiesFully = version.propertySupport == PropertySupport.FULL
        // Only include the full specifier in the header when explicitly migrating and when the
        // format version does not support properties. Otherwise, just include the version.
        val specifier =
            if (migrating != null && !supportsPropertiesFully) specifier()
            else version.versionNumber

        // Only add properties when the version supports them fully.
        val properties = if (supportsPropertiesFully) properties() else ""

        return "$SIGNATURE_FORMAT_PREFIX$specifier\n$properties"
    }

    /**
     * Get the specifier for this format.
     *
     * It starts with the version number followed by an optional `:` followed by at least one comma
     * separate `property=value` pair. This can be used either on the command line for the
     * `--format` option or, if version < 5.0 after the signature format prefix in the header of a
     * signature file.
     */
    fun specifier(): String {
        return buildString {
            append(version.versionNumber)

            var separator = VERSION_PROPERTIES_SEPARATOR
            iterateOverOverridingProperties { property, value ->
                append(separator)
                separator = ","
                append(property)
                append("=")
                append(value)
            }
        }
    }

    /**
     * Get the properties for this version.
     *
     * This is only called when [Version.propertySupport] is true.
     *
     * This produces a possibly empty, multi-line string containing one `property=value` pair per
     * line, prefixed by [PROPERTY_LINE_PREFIX].
     */
    private fun properties() = buildString {
        iterateOverOverridingProperties { property, value ->
            append(PROPERTY_LINE_PREFIX)
            append(property)
            append("=")
            append(value)
            append("\n")
        }
    }

    /**
     * Iterate over all the properties of this format which have different values to the values in
     * this format's [Version.defaults], invoking the [consumer] with each property, value pair.
     */
    private fun iterateOverOverridingProperties(consumer: (String, String) -> Unit) {
        val defaults = version.defaults
        if (this@FileFormat != defaults) {
            OverrideableProperty.values().forEach { prop ->
                val thisValue = prop.stringFromFormat(this@FileFormat)
                val defaultValue = prop.stringFromFormat(defaults)
                if (thisValue != defaultValue) {
                    consumer(prop.propertyName, thisValue)
                }
            }
        }
    }

    companion object {
        private val allDefaults = Version.values().map { it.defaults }.toList()

        private val versionByNumber = Version.values().associateBy { it.versionNumber }

        // The defaults associated with version 2.0.
        val V2 = Version.V2.defaults

        // The defaults associated with version 3.0.
        val V3 = Version.V3.defaults

        // The defaults associated with version 4.0.
        val V4 = Version.V4.defaults

        // The defaults associated with version 5.0.
        val V5 = Version.V5.defaults

        // The defaults associated with the latest version.
        val LATEST = allDefaults.last()

        const val SIGNATURE_FORMAT_PREFIX = "// Signature format: "

        /**
         * The size of the buffer and read ahead limit.
         *
         * Should be big enough to handle any first package line, even one with lots of annotations.
         */
        private const val BUFFER_SIZE = 1024

        /**
         * Parse the start of the contents provided by [reader] to obtain the [FileFormat]
         *
         * @return the [FileFormat] or null if the reader was blank.
         */
        fun parseHeader(filename: String, reader: Reader): FileFormat? {
            val lineNumberReader =
                if (reader is LineNumberReader) reader else LineNumberReader(reader, BUFFER_SIZE)

            try {
                return parseHeader(lineNumberReader)
            } catch (cause: ApiParseException) {
                // Wrap the exception and add contextual information to help user identify and fix
                // the problem. This is done here instead of when throwing the exception as the
                // original thrower does not have that context.
                throw ApiParseException(
                    "Signature format error - ${cause.message}",
                    filename,
                    lineNumberReader.lineNumber,
                    cause,
                )
            }
        }

        /**
         * Parse the start of the contents provided by [reader] to obtain the [FileFormat]
         *
         * This consumes only the content that makes up the header. So, the rest of the file
         * contents can be read from the reader.
         *
         * @return the [FileFormat] or null if the reader was blank.
         */
        private fun parseHeader(reader: LineNumberReader): FileFormat? {
            // This reads the minimal amount to determine whether this is likely to be a
            // signature file.
            val prefixLength = SIGNATURE_FORMAT_PREFIX.length
            val buffer = CharArray(prefixLength)
            val prefix =
                reader.read(buffer, 0, prefixLength).let { count ->
                    if (count == -1) {
                        // An empty file.
                        return null
                    }
                    String(buffer, 0, count)
                }

            if (prefix != SIGNATURE_FORMAT_PREFIX) {
                // If the prefix is blank then either the whole file is blank in which case it is
                // handled specially, or the file is not blank and is not a signature file in which
                // case it is an error.
                if (prefix.isBlank()) {
                    var line = reader.readLine()
                    while (line != null && line.isBlank()) {
                        line = reader.readLine()
                    }
                    // If the line is null then te whole file is blank which is handled specially.
                    if (line == null) {
                        return null
                    }
                }

                // An error occurred as the prefix did not match. A valid prefix must appear on a
                // single line so just in case what was read contains multiple lines trim it down to
                // a single line for error reporting. The LineNumberReader has translated non-unix
                // newline characters into `\n` so this is safe.
                val firstLine = prefix.substringBefore("\n")
                // As the error is going to be reported for the first line, even though possibly
                // multiple lines have been read set the line number to 1.
                reader.lineNumber = 1
                throw ApiParseException(
                    "invalid prefix, found '$firstLine', expected '$SIGNATURE_FORMAT_PREFIX'"
                )
            }

            val specifier = reader.readLine()
            val format = parseSpecifier(specifier = specifier, migratingAllowed = true)

            if (format.version.propertySupport == PropertySupport.FULL) {
                return parseProperties(reader, format)
            }

            return format
        }

        private const val VERSION_PROPERTIES_SEPARATOR = ":"

        /**
         * Apply some optional additional properties from a string.
         *
         * This parses the string into property/value pairs, makes sure that they are valid
         * properties and values and then returns a new copy of this with its values overridden by
         * the values from the properties string.
         *
         * @param migratingAllowed indicates whether the `migrating` property is allowed in the
         *   specifier.
         * @param extraVersions extra versions to add to the error message if a version is not
         *   recommended but otherwise ignored. This allows the caller to handle some additional
         *   versions first but still report a helpful message.
         */
        fun parseSpecifier(
            specifier: String,
            migratingAllowed: Boolean,
            extraVersions: Set<String> = emptySet(),
        ): FileFormat {
            val specifierParts = specifier.split(VERSION_PROPERTIES_SEPARATOR, limit = 2)
            val versionNumber = specifierParts[0]
            val version = getVersionFromNumber(versionNumber, extraVersions)
            val versionDefaults = version.defaults
            if (specifierParts.size == 1) {
                return versionDefaults
            }

            if (version.propertySupport == PropertySupport.FULL) {
                throw ApiParseException(
                    "invalid specifier, '$specifier' version $versionNumber does not support properties on the version line"
                )
            }

            val properties = specifierParts[1]

            val builder = Builder(versionDefaults)
            properties.trim().split(",").forEach { parsePropertyAssignment(builder, it) }
            val format = builder.build()

            // If after applying all the properties the format matches its version defaults then
            // there is nothing else to do.
            if (format == versionDefaults) {
                return format
            }

            if (migratingAllowed) {
                // At the moment if migrating is allowed and the version defaults have been
                // overridden then `migrating` is mandatory as no existing version supports
                // overriding properties except for migrating.
                if (format.migrating == null) {
                    throw ApiParseException(
                        "invalid format specifier: '$specifier' - must provide a 'migrating' property when customizing version $versionNumber"
                    )
                }
            } else if (format.migrating != null) {
                throw ApiParseException(
                    "invalid format specifier: '$specifier' - must not contain a 'migrating' property"
                )
            }

            return format
        }

        /**
         * Get the [Version] from the number.
         *
         * @param versionNumber the version number as a string.
         * @param extraVersions extra versions to add to the error message if a version is not
         *   supported but otherwise ignored. This allows the caller to handle some additional
         *   versions first but still report a helpful message.
         */
        private fun getVersionFromNumber(
            versionNumber: String,
            extraVersions: Set<String> = emptySet(),
        ): Version =
            versionByNumber[versionNumber]
                ?: let {
                    val allVersions = versionByNumber.keys + extraVersions
                    val possibilities = allVersions.joinToString { "'$it'" }
                    throw ApiParseException(
                        "invalid version, found '$versionNumber', expected one of $possibilities"
                    )
                }

        /**
         * Parse a property assignment of the form `property=value`, updating the appropriate
         * property in [builder], or throwing an exception if there was a problem.
         */
        private fun parsePropertyAssignment(
            builder: Builder,
            assignment: String,
        ) {
            val propertyParts = assignment.split("=")
            if (propertyParts.size != 2) {
                throw ApiParseException("expected <property>=<value> but found '$assignment'")
            }
            val name = propertyParts[0]
            val value = propertyParts[1]
            val overrideable = OverrideableProperty.getByName(name)
            overrideable.setFromString(builder, value)
        }

        private const val PROPERTY_LINE_PREFIX = "// - "

        /**
         * Parse property pairs, one per line, each of which must be prefixed with
         * [PROPERTY_LINE_PREFIX], apply them to the supplied [format] and returning the result.
         */
        private fun parseProperties(reader: LineNumberReader, format: FileFormat): FileFormat {
            val builder = Builder(format)
            do {
                reader.mark(1024)
                val line = reader.readLine() ?: break
                if (line.startsWith("package ")) {
                    reader.reset()
                    break
                }

                val remainder = line.removePrefix(PROPERTY_LINE_PREFIX)
                if (remainder == line) {
                    throw ApiParseException(
                        "invalid property prefix, expected '$PROPERTY_LINE_PREFIX', found '$line'"
                    )
                }

                parsePropertyAssignment(builder, remainder)
            } while (true)

            return builder.build()
        }
    }

    /** A builder for [FileFormat] that applies some optional values to a base [FileFormat]. */
    private class Builder(private val base: FileFormat) {
        var addAdditionalOverrides: Boolean? = null
        var conciseDefaultValues: Boolean? = null
        var kotlinStyleNulls: Boolean? = null
        var migrating: String? = null
        var overloadedMethodOrder: OverloadedMethodOrder? = null

        fun build(): FileFormat =
            base.copy(
                conciseDefaultValues = conciseDefaultValues ?: base.conciseDefaultValues,
                kotlinStyleNulls = kotlinStyleNulls ?: base.kotlinStyleNulls,
                migrating = migrating ?: base.migrating,
                specifiedAddAdditionalOverrides = addAdditionalOverrides
                        ?: base.specifiedAddAdditionalOverrides,
                specifiedOverloadedMethodOrder = overloadedMethodOrder
                        ?: base.specifiedOverloadedMethodOrder,
            )
    }

    /** Information about the different overrideable properties in [FileFormat]. */
    private enum class OverrideableProperty {
        /** add-additional-overrides=[yes|no] */
        ADD_ADDITIONAL_OVERRIDES {
            override fun setFromString(builder: Builder, value: String) {
                builder.addAdditionalOverrides = yesNo(value)
            }

            override fun stringFromFormat(format: FileFormat): String =
                yesNo(format.addAdditionalOverrides)
        },
        /** concise-default-values=[yes|no] */
        CONCISE_DEFAULT_VALUES {
            override fun setFromString(builder: Builder, value: String) {
                builder.conciseDefaultValues = yesNo(value)
            }

            override fun stringFromFormat(format: FileFormat): String =
                yesNo(format.conciseDefaultValues)
        },
        /** kotlin-style-nulls=[yes|no] */
        KOTLIN_STYLE_NULLS {
            override fun setFromString(builder: Builder, value: String) {
                builder.kotlinStyleNulls = yesNo(value)
            }

            override fun stringFromFormat(format: FileFormat): String =
                yesNo(format.kotlinStyleNulls)
        },
        MIGRATING {
            override fun setFromString(builder: Builder, value: String) {
                builder.migrating = value
            }

            override fun stringFromFormat(format: FileFormat): String = format.migrating ?: ""
        },
        /** overloaded-method-other=[source|signature] */
        OVERLOADED_METHOD_ORDER {
            override fun setFromString(builder: Builder, value: String) {
                builder.overloadedMethodOrder = enumFromString<OverloadedMethodOrder>(value)
            }

            override fun stringFromFormat(format: FileFormat): String =
                format.overloadedMethodOrder.stringFromEnum()
        };

        /** The property name in the [parseSpecifier] input. */
        val propertyName: String = name.lowercase(Locale.US).replace("_", "-")

        /**
         * Set the corresponding property in the supplied [Builder] to the value corresponding to
         * the string representation [value].
         */
        abstract fun setFromString(builder: Builder, value: String)

        /**
         * Get the string representation of the corresponding property from the supplied
         * [FileFormat].
         */
        abstract fun stringFromFormat(format: FileFormat): String

        /** Inline function to map from a string value to an enum value of the required type. */
        inline fun <reified T : Enum<T>> enumFromString(value: String): T {
            val enumValues = enumValues<T>()
            return nonInlineEnumFromString(enumValues, value)
        }

        /**
         * Non-inline portion of the function to map from a string value to an enum value of the
         * required type.
         */
        fun <T : Enum<T>> nonInlineEnumFromString(enumValues: Array<T>, value: String): T {
            return enumValues.firstOrNull { it.stringFromEnum() == value }
                ?: let {
                    val possibilities = enumValues.possibilitiesList { "'${it.stringFromEnum()}'" }
                    throw ApiParseException(
                        "unexpected value for $propertyName, found '$value', expected one of $possibilities"
                    )
                }
        }

        /**
         * Extension function to convert an enum value to an external string.
         *
         * It simply returns the lowercase version of the enum name with `_` replaced with `-`.
         */
        fun <T : Enum<T>> T.stringFromEnum(): String {
            return name.lowercase(Locale.US).replace("_", "-")
        }

        /**
         * Intermediate enum used to map from string to [Boolean]
         *
         * The instances are not used directly but are used via [YesNo.values].
         */
        enum class YesNo(val b: Boolean) {
            @Suppress("UNUSED") YES(true),
            @Suppress("UNUSED") NO(false)
        }

        /** Convert a "yes|no" string into a boolean. */
        fun yesNo(value: String): Boolean {
            return enumFromString<YesNo>(value).b
        }

        /** Convert a boolean into a `yes|no` string. */
        fun yesNo(value: Boolean): String = if (value) "yes" else "no"

        companion object {
            val byPropertyName = values().associateBy { it.propertyName }

            fun getByName(name: String): OverrideableProperty =
                byPropertyName[name]
                    ?: throw ApiParseException(
                        "unknown format property name `$name`, expected one of '${byPropertyName.keys.joinToString("', '")}'"
                    )
        }
    }
}

/**
 * Given an array of items return a list of possibilities.
 *
 * The last pair of items are separated by " or ", the other pairs are separated by ", ".
 */
fun <T> Array<T>.possibilitiesList(transform: (T) -> String): String {
    val allButLast = dropLast(1)
    val last = last()
    val options = buildString {
        allButLast.joinTo(this, transform = transform)
        append(" or ")
        append(transform(last))
    }
    return options
}
