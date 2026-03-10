/*
 * Copyright (C) 2026 The Android Open Source Project
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

import com.android.tools.metalava.model.StripJavaLangPrefix
import com.android.tools.metalava.model.text.FileFormat.Builder
import com.android.tools.metalava.model.text.FileFormat.Companion.parseSpecifier
import com.android.tools.metalava.model.text.FileFormat.Language
import com.android.tools.metalava.model.text.FileFormat.OverloadedMethodOrder
import com.android.tools.metalava.model.text.FileFormat.TypeArgumentSpacing
import java.util.Locale

/** Information about the different customizable properties in [FileFormat]. */
enum class CustomizableProperty(
    val defaultable: Boolean = false,
    /** Syntax of command line values. */
    val valueSyntax: String = "",
    /** Help text to use on the command line. */
    val help: String = "",
) {
    // The order of values in this is significant as it determines the order of the properties
    // in signature headers. The values in this block are not in alphabetical order because it
    // is important that they are at the start of the signature header.

    NAME {
        override fun setFromString(builder: Builder, value: String) {
            builder.name = value
        }

        override fun stringFromFormat(format: FileFormat): String? = format.name
    },
    SURFACE {
        override fun setFromString(builder: Builder, value: String) {
            builder.surface = value
        }

        override fun stringFromFormat(format: FileFormat): String? = format.surface
    },

    /** language=[java|kotlin] */
    LANGUAGE {
        override fun setFromString(builder: Builder, value: String) {
            builder.language = enumFromString<Language>(value)
        }

        override fun stringFromFormat(format: FileFormat): String? =
            format.language?.stringFromEnum()
    },

    // The following values must be in alphabetical order.

    /** add-additional-overrides=[yes|no] */
    ADD_ADDITIONAL_OVERRIDES(defaultable = true) {
        override fun setFromString(builder: Builder, value: String) {
            builder.addAdditionalOverrides = yesNo(value)
        }

        override fun stringFromFormat(format: FileFormat): String? =
            format.specifiedAddAdditionalOverrides?.let { yesNo(it) }
    },
    /** include-default-parameter-values=[yes|no] */
    INCLUDE_DEFAULT_PARAMETER_VALUES(
        valueSyntax = "yes|no",
        help =
            """
                    If `no` then the signature file will not include any information about default
                    parameter values. If `yes` then it will use the pseudo modifier `optional` to
                    indicate a parameter that has a default value.
                """,
    ) {
        override fun setFromString(builder: Builder, value: String) {
            builder.includeDefaultParameterValues = yesNo(value)
        }

        override fun stringFromFormat(format: FileFormat): String =
            yesNo(format.includeDefaultParameterValues)
    },
    /** include-type-use-annotations=[yes|no] */
    INCLUDE_TYPE_USE_ANNOTATIONS {
        override fun setFromString(builder: Builder, value: String) {
            builder.includeTypeUseAnnotations = yesNo(value)
        }

        override fun stringFromFormat(format: FileFormat): String =
            yesNo(format.includeTypeUseAnnotations)
    },
    /** kotlin-name-type-order=[yes|no] */
    KOTLIN_NAME_TYPE_ORDER {
        override fun setFromString(builder: Builder, value: String) {
            builder.kotlinNameTypeOrder = yesNo(value)
        }

        override fun stringFromFormat(format: FileFormat): String =
            yesNo(format.kotlinNameTypeOrder)
    },
    /** kotlin-style-nulls=[yes|no] */
    KOTLIN_STYLE_NULLS(
        valueSyntax = "yes|no",
        help =
            """
                    If `no` then the signature file will use `@Nullable` and `@NonNull` annotations
                    to indicate that the annotated item accepts `null` and does not accept `null`
                    respectively and neither indicates that it's not defined.

                    If `yes` then the signature file will use a type suffix of `?`, no type suffix
                    and a type suffix of `!` to indicate the that the type accepts `null`, does not
                    accept `null` or it's not defined respectively.
                """,
    ) {
        override fun setFromString(builder: Builder, value: String) {
            builder.kotlinStyleNulls = yesNo(value)
        }

        override fun stringFromFormat(format: FileFormat): String = yesNo(format.kotlinStyleNulls)
    },
    MIGRATING {
        override fun setFromString(builder: Builder, value: String) {
            builder.migrating = value
        }

        override fun stringFromFormat(format: FileFormat): String? = format.migrating
    },
    NORMALIZE_ABSTRACT_MODIFIER(
        defaultable = true,
        valueSyntax = "yes|no",
        help =
            """
                    Specifies how the `abstract` modifier is handled on `abstract` methods. If this
                    is `yes` and the method's containing class does not allow `abstract` then the
                    `abstract` modifier is not written out, otherwise it is.
                """,
    ) {
        override fun setFromString(builder: Builder, value: String) {
            builder.normalizeAbstractModifier = yesNo(value)
        }

        override fun stringFromFormat(format: FileFormat): String? =
            format.specifiedNormalizeAbstractModifier?.let { yesNo(it) }
    },
    NORMALIZE_FINAL_MODIFIER(
        defaultable = true,
        valueSyntax = "yes|no",
        help =
            """
                    Specifies how the `final` modifier is handled on `final` methods. If this is
                    `yes` and the method's containing class is `final` then the `final` modifier is
                    not written out, otherwise it is.
                """,
    ) {
        override fun setFromString(builder: Builder, value: String) {
            builder.normalizeFinalModifier = yesNo(value)
        }

        override fun stringFromFormat(format: FileFormat): String? =
            format.specifiedNormalizeFinalModifier?.let { yesNo(it) }
    },
    /** overloaded-method-other=[source|signature] */
    OVERLOADED_METHOD_ORDER(
        defaultable = true,
        valueSyntax = "source|signature",
        help =
            """
                    Specifies the order of overloaded methods in signature files. Applies to the
                    contents of the files specified on `--api` and `--removed-api`.

                    `source` - preserves the order in which overloaded methods appear in the source
                    files. This means that refactorings of the source files which change the order
                    but not the API can cause unnecessary changes in the API signature files.

                    `signature` (default) - sorts overloaded methods by their signature. This means
                    that refactorings of the source files which change the order but not the API
                    will have no effect on the API signature files.
                """,
    ) {
        override fun setFromString(builder: Builder, value: String) {
            builder.overloadedMethodOrder = enumFromString<OverloadedMethodOrder>(value)
        }

        override fun stringFromFormat(format: FileFormat): String? =
            format.specifiedOverloadedMethodOrder?.stringFromEnum()
    },
    SORT_WHOLE_EXTENDS_LIST(defaultable = true) {
        override fun setFromString(builder: Builder, value: String) {
            builder.sortWholeExtendsList = yesNo(value)
        }

        override fun stringFromFormat(format: FileFormat): String? =
            format.specifiedSortWholeExtendsList?.let { yesNo(it) }
    },
    STRIP_JAVA_LANG_PREFIX(defaultable = true) {
        override fun setFromString(builder: Builder, value: String) {
            builder.stripJavaLangPrefix = enumFromString<StripJavaLangPrefix>(value)
        }

        override fun stringFromFormat(format: FileFormat): String? =
            format.specifiedStripJavaLangPrefix?.stringFromEnum()
    },
    TYPE_ARGUMENT_SPACING(
        defaultable = true,
        valueSyntax = "legacy|none|space",
        help =
            """
                    Specifies the spacing between the type arguments of a generic type. e.g.
                    `Map<String, Integer>`. The default is `legacy`.

                    `legacy` - adds no spaces between type arguments except those used in the bounds
                    of a type parameter. e.g. `Map<String,Integer>` will have no space except in
                    `class Foo<M extends Map<String, Integer>`.

                    `none` - adds no spaces between any type arguments.

                    `space` - adds a single space between every type argument.

                    Note: This does not affect the spacing of type parameters in a type parameter
                    list, e.g. `interface Map<K, V>`. They always have a space separator.
                """,
    ) {
        override fun setFromString(builder: Builder, value: String) {
            builder.typeArgumentSpacing = enumFromString<TypeArgumentSpacing>(value)
        }

        override fun stringFromFormat(format: FileFormat): String? =
            format.specifiedTypeArgumentSpacing?.stringFromEnum()
    },
    ;

    /** The property name in the [parseSpecifier] input. */
    val propertyName: String = name.lowercase(Locale.US).replace("_", "-")

    /**
     * Set the corresponding property in the supplied [Builder] to the value corresponding to the
     * string representation [value].
     */
    internal abstract fun setFromString(builder: Builder, value: String)

    /**
     * Get the string representation of the corresponding property from the supplied [FileFormat].
     */
    internal abstract fun stringFromFormat(format: FileFormat): String?

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
        val byPropertyName = entries.associateBy { it.propertyName }

        /**
         * Get the [CustomizableProperty] by name, throwing an [ApiParseException] if it could not
         * be found.
         *
         * @param name the name of the property.
         * @param defaultableOnly if `true` then only [CustomizableProperty.defaultable] properties
         *   are allowed.
         */
        fun getByName(name: String, defaultableOnly: Boolean): CustomizableProperty =
            byPropertyName[name]?.let { if (!defaultableOnly || it.defaultable) it else null }
                ?: let {
                    val possibilities =
                        byPropertyName
                            .filter { (_, property) -> !defaultableOnly || property.defaultable }
                            .keys
                            .sorted()
                            .joinToString("', '")
                    throw ApiParseException(
                        "unknown format property name `$name`, expected one of '$possibilities'"
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
