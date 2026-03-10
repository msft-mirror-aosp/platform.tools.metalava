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
import kotlin.enums.enumEntries
import kotlin.properties.ReadOnlyProperty
import kotlin.reflect.KProperty

/**
 * Information about the different customizable properties in [FileFormat].
 *
 * This implements [ReadOnlyProperty] and has a [provideDelegate] method so that this can be used as
 * a delegate in [Companion] giving each instance access to the name of the delegating property in
 * [Companion], e.g. the [KOTLIN_STYLE_NULLS] instance is given a [KProperty] whose [KProperty.name]
 * is `"KOTLIN_STYLE_NULLS"`.
 */
abstract class CustomizableProperty
internal constructor(
    val defaultable: Boolean = false,
    /** Syntax of command line values. */
    val valueSyntax: String = "",
    /** Help text to use on the command line. */
    val help: String = "",
) : ReadOnlyProperty<CustomizableProperty.Companion, CustomizableProperty> {

    companion object {
        /** List of all [CustomizableProperty]s, populated by [provideDelegate]. */
        private val propertyList = mutableListOf<CustomizableProperty>()

        // The order of values in this is significant as it determines the order of the properties
        // in signature headers. The values in this block are not in alphabetical order because it
        // is important that they are at the start of the signature header.

        val NAME by
            object : StringProperty() {
                override fun setFromString(builder: Builder, value: String) {
                    builder.name = value
                }

                override fun stringFromFormat(format: FileFormat): String? = format.name
            }

        val SURFACE by
            object : StringProperty() {
                override fun setFromString(builder: Builder, value: String) {
                    builder.surface = value
                }

                override fun stringFromFormat(format: FileFormat): String? = format.surface
            }

        /** language=[java|kotlin] */
        val LANGUAGE by
            object : EnumProperty() {
                override fun setFromString(builder: Builder, value: String) {
                    builder.language = enumFromString<Language>(value)
                }

                override fun stringFromFormat(format: FileFormat): String? =
                    format.language?.stringFromEnum()
            }

        // The following values must be in alphabetical order.

        /** add-additional-overrides=[yes|no] */
        val ADD_ADDITIONAL_OVERRIDES by
            object : BooleanProperty(defaultable = true) {
                override fun setFromString(builder: Builder, value: String) {
                    builder.addAdditionalOverrides = yesNo(value)
                }

                override fun stringFromFormat(format: FileFormat): String? =
                    format.specifiedAddAdditionalOverrides?.let { yesNo(it) }
            }

        /** include-default-parameter-values=[yes|no] */
        val INCLUDE_DEFAULT_PARAMETER_VALUES by
            object :
                BooleanProperty(
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
            }

        /** include-type-use-annotations=[yes|no] */
        val INCLUDE_TYPE_USE_ANNOTATIONS by
            object : BooleanProperty() {
                override fun setFromString(builder: Builder, value: String) {
                    builder.includeTypeUseAnnotations = yesNo(value)
                }

                override fun stringFromFormat(format: FileFormat): String =
                    yesNo(format.includeTypeUseAnnotations)
            }

        /** kotlin-name-type-order=[yes|no] */
        val KOTLIN_NAME_TYPE_ORDER by
            object : BooleanProperty() {
                override fun setFromString(builder: Builder, value: String) {
                    builder.kotlinNameTypeOrder = yesNo(value)
                }

                override fun stringFromFormat(format: FileFormat): String =
                    yesNo(format.kotlinNameTypeOrder)
            }

        /** kotlin-style-nulls=[yes|no] */
        val KOTLIN_STYLE_NULLS by
            object :
                BooleanProperty(
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

                override fun stringFromFormat(format: FileFormat): String =
                    yesNo(format.kotlinStyleNulls)
            }

        val MIGRATING by
            object : StringProperty() {
                override fun setFromString(builder: Builder, value: String) {
                    builder.migrating = value
                }

                override fun stringFromFormat(format: FileFormat): String? = format.migrating
            }

        val NORMALIZE_ABSTRACT_MODIFIER by
            object :
                BooleanProperty(
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
            }

        val NORMALIZE_FINAL_MODIFIER by
            object :
                BooleanProperty(
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
            }

        /** overloaded-method-other=[source|signature] */
        val OVERLOADED_METHOD_ORDER by
            object :
                EnumProperty(
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
            }

        val SORT_WHOLE_EXTENDS_LIST by
            object : BooleanProperty(defaultable = true) {
                override fun setFromString(builder: Builder, value: String) {
                    builder.sortWholeExtendsList = yesNo(value)
                }

                override fun stringFromFormat(format: FileFormat): String? =
                    format.specifiedSortWholeExtendsList?.let { yesNo(it) }
            }

        val STRIP_JAVA_LANG_PREFIX by
            object : EnumProperty(defaultable = true) {
                override fun setFromString(builder: Builder, value: String) {
                    builder.stripJavaLangPrefix = enumFromString<StripJavaLangPrefix>(value)
                }

                override fun stringFromFormat(format: FileFormat): String? =
                    format.specifiedStripJavaLangPrefix?.stringFromEnum()
            }

        val TYPE_ARGUMENT_SPACING by
            object :
                EnumProperty(
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
            }

        /** The [List] of all [CustomizableProperty]s. */
        val entries = propertyList.toList()

        /** Map from [CustomizableProperty.propertyName] to [CustomizableProperty]. */
        private val byPropertyName = entries.associateBy { it.propertyName }

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

    /** The property name in the [parseSpecifier] input. */
    lateinit var propertyName: String
        private set

    /** Called to get the value of the delegating property; as this is the value just return it. */
    override fun getValue(thisRef: Companion, property: KProperty<*>) = this

    /**
     * Called once on creation to retrieve the property delegate.
     *
     * Initializes the name and adds a mapping from the name to this and then just returns this as
     * the delegate.
     *
     * @param thisRef the [CustomizableProperty.Companion] object.
     * @param property the property within [CustomizableProperty.Companion] for which this is a
     *   delegate, e.g. [NAME], [KOTLIN_STYLE_NULLS], etc.
     */
    operator fun provideDelegate(thisRef: Companion, property: KProperty<*>): CustomizableProperty {
        // Initialize property name based on the Companion property names.
        propertyName = property.name.lowercase(Locale.US).replace("_", "-")
        propertyList.add(this)
        return this
    }

    /**
     * Set the corresponding property in the supplied [Builder] to the value corresponding to the
     * string representation [value].
     */
    internal abstract fun setFromString(builder: Builder, value: String)

    /**
     * Get the string representation of the corresponding property from the supplied [FileFormat].
     */
    internal abstract fun stringFromFormat(format: FileFormat): String?
}

/** A [CustomizableProperty] whose value is a [String]. */
private abstract class StringProperty : CustomizableProperty()

/** A [CustomizableProperty] whose value is an [Enum]. */
private abstract class EnumProperty(
    defaultable: Boolean = false,
    valueSyntax: String = "",
    help: String = "",
) : CustomizableProperty(defaultable, valueSyntax, help) {

    /** Inline function to map from a string value to an enum value of the required type. */
    inline fun <reified T : Enum<T>> enumFromString(value: String): T {
        val entries = enumEntries<T>()
        return nonInlineEnumFromString(entries, value)
    }

    /**
     * Non-inline portion of the function to map from a string value to an enum value of the
     * required type.
     */
    fun <E : Enum<E>> nonInlineEnumFromString(entries: List<E>, value: String): E {
        return entries.firstOrNull { it.stringFromEnum() == value }
            ?: let {
                val possibilities = entries.possibilitiesList { "'${it.stringFromEnum()}'" }
                throw ApiParseException(
                    "unexpected value for $propertyName, found '$value', expected one of $possibilities"
                )
            }
    }

    /**
     * Given an array of items return a list of possibilities.
     *
     * The last pair of items are separated by " or ", the other pairs are separated by ", ".
     */
    private fun <E> List<E>.possibilitiesList(transform: (E) -> String): String {
        val allButLast = dropLast(1)
        val last = last()
        val options = buildString {
            allButLast.joinTo(this, transform = transform)
            append(" or ")
            append(transform(last))
        }
        return options
    }

    /**
     * Extension function to convert an enum value to an external string.
     *
     * It simply returns the lowercase version of the enum name with `_` replaced with `-`.
     */
    fun <E : Enum<E>> E.stringFromEnum() = name.lowercase(Locale.US).replace("_", "-")
}

/** A [CustomizableProperty] whose value is a [Boolean]. */
private abstract class BooleanProperty(
    defaultable: Boolean = false,
    valueSyntax: String = "",
    help: String = "",
) : CustomizableProperty(defaultable, valueSyntax, help) {
    /** Convert a "yes|no" string into a boolean. */
    fun yesNo(value: String) =
        when (value) {
            "yes" -> true
            "no" -> false
            else ->
                throw ApiParseException(
                    "unexpected value for $propertyName, found '$value', expected one of 'yes' or 'no'"
                )
        }

    /** Convert a boolean into a `yes|no` string. */
    fun yesNo(value: Boolean) = if (value) "yes" else "no"
}
