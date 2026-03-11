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
class CustomizableProperty<T>
private constructor(
    val defaultable: Boolean = false,
    val defaultValue: T,
    /** Syntax of command line values. */
    val valueSyntax: String = "",
    /** Help text to use on the command line. */
    val help: String = "",
    internal val getter: FileFormat.() -> T?,
    private val valueToString: (T & Any).() -> String,
    private val stringToValue: FromString.() -> T,
) : ReadOnlyProperty<CustomizableProperty.Companion, CustomizableProperty<T>> {

    companion object {
        /** List of all [CustomizableProperty]s, populated by [provideDelegate]. */
        private val propertyList = mutableListOf<CustomizableProperty<*>>()

        /** Factory method for a [Boolean] [CustomizableProperty] */
        private fun booleanProperty(
            defaultable: Boolean = false,
            help: String = "",
            getter: FileFormat.() -> Boolean?,
        ) =
            CustomizableProperty(
                defaultable,
                defaultValue = false,
                "yes|no",
                help,
                getter,
                valueToString = { booleanToYesNo() },
                stringToValue = { yesNoToBoolean() }
            )

        /** Convert a "yes|no" string into a boolean. */
        private fun FromString.yesNoToBoolean() =
            when (string) {
                "yes" -> true
                "no" -> false
                else ->
                    throw ApiParseException(
                        "unexpected value for $propertyName, found '$string', expected one of 'yes' or 'no'"
                    )
            }

        /** Convert a boolean into a `yes|no` string. */
        private fun Boolean.booleanToYesNo() = if (this) "yes" else "no"

        /** Factory method for an [E] [CustomizableProperty] */
        private inline fun <reified E : Enum<E>> enumProperty(
            defaultable: Boolean = false,
            defaultValue: E,
            valueSyntax: String = "",
            help: String = "",
            noinline getter: FileFormat.() -> E?,
        ): CustomizableProperty<E> {
            val entries = enumEntries<E>()
            return CustomizableProperty(
                defaultable,
                defaultValue,
                valueSyntax,
                help,
                getter,
                valueToString = { stringFromEnum() },
                stringToValue = { enumFromString(entries) },
            )
        }

        /** Factory method for an [E] [CustomizableProperty] */
        private inline fun <reified E : Enum<E>> optionalEnumProperty(
            defaultable: Boolean = false,
            defaultValue: E?,
            valueSyntax: String = "",
            help: String = "",
            noinline getter: FileFormat.() -> E?,
        ): CustomizableProperty<E?> {
            val entries = enumEntries<E>()
            return CustomizableProperty(
                defaultable,
                defaultValue,
                valueSyntax,
                help,
                getter,
                valueToString = { stringFromEnum() },
                stringToValue = { enumFromString(entries) },
            )
        }

        /** Map from a string value to an enum value of the required type. */
        private fun <E : Enum<E>> FromString.enumFromString(entries: List<E>): E {
            return entries.firstOrNull { it.stringFromEnum() == string }
                ?: let {
                    val possibilities = entries.possibilitiesList { "'${it.stringFromEnum()}'" }
                    throw ApiParseException(
                        "unexpected value for $propertyName, found '$string', expected one of $possibilities"
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

        /** Factory method for an optional [String] [CustomizableProperty] */
        private fun optionalStringProperty(
            getter: FileFormat.() -> String?,
        ) =
            CustomizableProperty(
                defaultValue = null,
                getter = getter,
                valueToString = { this },
                stringToValue = { string },
            )

        // The order of values in this is significant as it determines the order of the properties
        // in signature headers. The values in this block are not in alphabetical order because it
        // is important that they are at the start of the signature header.

        val NAME by
            optionalStringProperty(
                getter = { name },
            )

        val SURFACE by
            optionalStringProperty(
                getter = { surface },
            )

        /** language=[java|kotlin] */
        val LANGUAGE by
            optionalEnumProperty<Language>(
                defaultValue = null,
                getter = { language },
            )

        // The following values must be in alphabetical order.

        /** add-additional-overrides=[yes|no] */
        val ADD_ADDITIONAL_OVERRIDES by
            booleanProperty(
                defaultable = true,
                getter = { specifiedAddAdditionalOverrides },
            )

        /** include-default-parameter-values=[yes|no] */
        val INCLUDE_DEFAULT_PARAMETER_VALUES by
            booleanProperty(
                help =
                    """
                    If `no` then the signature file will not include any information about default
                    parameter values. If `yes` then it will use the pseudo modifier `optional` to
                    indicate a parameter that has a default value.
                """,
                getter = { includeDefaultParameterValues },
            )

        /** include-type-use-annotations=[yes|no] */
        val INCLUDE_TYPE_USE_ANNOTATIONS by
            booleanProperty(
                getter = { includeTypeUseAnnotations },
            )

        /** kotlin-name-type-order=[yes|no] */
        val KOTLIN_NAME_TYPE_ORDER by
            booleanProperty(
                getter = { kotlinNameTypeOrder },
            )

        /** kotlin-style-nulls=[yes|no] */
        val KOTLIN_STYLE_NULLS by
            booleanProperty(
                help =
                    """
                    If `no` then the signature file will use `@Nullable` and `@NonNull` annotations
                    to indicate that the annotated item accepts `null` and does not accept `null`
                    respectively and neither indicates that it's not defined.

                    If `yes` then the signature file will use a type suffix of `?`, no type suffix
                    and a type suffix of `!` to indicate the that the type accepts `null`, does not
                    accept `null` or it's not defined respectively.
                """,
                getter = { kotlinStyleNulls },
            )

        val MIGRATING by
            optionalStringProperty(
                getter = { migrating },
            )

        val NORMALIZE_ABSTRACT_MODIFIER by
            booleanProperty(
                defaultable = true,
                help =
                    """
                    Specifies how the `abstract` modifier is handled on `abstract` methods. If this
                    is `yes` and the method's containing class does not allow `abstract` then the
                    `abstract` modifier is not written out, otherwise it is.
                """,
                getter = { specifiedNormalizeAbstractModifier },
            )

        val NORMALIZE_FINAL_MODIFIER by
            booleanProperty(
                defaultable = true,
                help =
                    """
                    Specifies how the `final` modifier is handled on `final` methods. If this is
                    `yes` and the method's containing class is `final` then the `final` modifier is
                    not written out, otherwise it is.
                """,
                getter = { specifiedNormalizeFinalModifier },
            )

        /** overloaded-method-other=[source|signature] */
        val OVERLOADED_METHOD_ORDER by
            enumProperty<OverloadedMethodOrder>(
                defaultable = true,
                defaultValue = OverloadedMethodOrder.SIGNATURE,
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
                getter = { specifiedOverloadedMethodOrder },
            )

        val SORT_WHOLE_EXTENDS_LIST by
            booleanProperty(
                defaultable = true,
                getter = { specifiedSortWholeExtendsList },
            )

        val STRIP_JAVA_LANG_PREFIX by
            enumProperty<StripJavaLangPrefix>(
                defaultable = true,
                defaultValue = StripJavaLangPrefix.LEGACY,
                getter = { specifiedStripJavaLangPrefix },
            )

        val TYPE_ARGUMENT_SPACING by
            enumProperty<TypeArgumentSpacing>(
                defaultable = true,
                defaultValue = TypeArgumentSpacing.LEGACY,
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
                getter = { specifiedTypeArgumentSpacing },
            )

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
        fun getByName(name: String, defaultableOnly: Boolean): CustomizableProperty<*> =
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
    operator fun provideDelegate(
        thisRef: Companion,
        property: KProperty<*>
    ): CustomizableProperty<T> {
        // Initialize property name based on the Companion property names.
        propertyName = property.name.lowercase(Locale.US).replace("_", "-")
        propertyList.add(this)
        return this
    }

    /** Context for converting a string to a value. */
    internal class FromString(val propertyName: String, val string: String)

    /**
     * Set the corresponding property in the supplied [Builder] to the value corresponding to the
     * string representation [string].
     */
    internal fun setFromString(builder: Builder, string: String) {
        val value = FromString(propertyName, string).stringToValue()
        builder[this] = value
    }

    /**
     * Get the string representation of the corresponding property from the supplied [FileFormat].
     */
    internal fun stringFromFormat(format: FileFormat): String? = format[this]?.valueToString()

    /** Get the string representation of this property from the supplied [BasePropertyMap]. */
    internal fun stringFromPropertyMap(map: BasePropertyMap): String? = map[this]?.valueToString()

    /** Get [defaultValue] as a string. */
    fun defaultValueAsString() = defaultValue?.valueToString()
}

/**
 * Base of [PropertyMap] and [MutablePropertyMap].
 *
 * Provides behavior common to both.
 */
abstract class BasePropertyMap : Iterable<CustomizableProperty<*>> {
    /**
     * Get the value of [property] as [T]
     *
     * This will NOT apply any defaults.
     */
    abstract operator fun <T> get(property: CustomizableProperty<T>): T?

    abstract override fun iterator(): Iterator<CustomizableProperty<*>>

    override fun toString() = buildString {
        append('{')
        var separator = ""
        for (property in this@BasePropertyMap) {
            val valueAsString = property.stringFromPropertyMap(this@BasePropertyMap) ?: continue
            append(separator)
            append(property.propertyName)
            append('=')
            append(valueAsString)
            separator = ", "
        }
        append('}')
    }
}

/** A type safe map from [CustomizableProperty] to a value of the appropriate type. */
class PropertyMap
internal constructor(
    /**
     * Map from [CustomizableProperty] to `Any?`.
     *
     * This is actually type safe as it can only be populated by [MutablePropertyMap.set] and that
     * ensures that only values compatible with the [CustomizableProperty] key are stored with it.
     */
    private val map: Map<CustomizableProperty<*>, Any?> = emptyMap(),
) : BasePropertyMap() {
    override fun iterator() = map.keys.iterator()

    @Suppress("UNCHECKED_CAST")
    override operator fun <T> get(property: CustomizableProperty<T>): T? = map[property] as T?

    /** Convert this to a [MutablePropertyMap]. */
    fun toMutableMap() = MutablePropertyMap(map.toMutableMap())

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as PropertyMap

        return map == other.map
    }

    override fun hashCode() = map.hashCode()

    companion object {
        internal val EMPTY = PropertyMap(emptyMap())
    }
}

/** An empty [PropertyMap]. */
fun emptyPropertyMap() = PropertyMap.EMPTY

/** A type safe mutable map from [CustomizableProperty] to a value of the appropriate type. */
class MutablePropertyMap
@PublishedApi
internal constructor(
    /**
     * Map from [CustomizableProperty] to `Any?`.
     *
     * This is actually type safe as it can only be populated by [MutablePropertyMap.set] and that
     * ensures that only values compatible with the [CustomizableProperty] key are stored with it.
     */
    private val map: MutableMap<CustomizableProperty<*>, Any?> = mutableMapOf(),
) : BasePropertyMap() {
    override fun iterator() = map.keys.iterator()

    @Suppress("UNCHECKED_CAST")
    override operator fun <T> get(property: CustomizableProperty<T>): T? = map[property] as T?

    /** Set [property] to [value]. */
    operator fun <T> set(property: CustomizableProperty<T>, value: T) {
        map[property] = value
    }

    /** Convert this to an immutable [PropertyMap]. */
    fun toMap() = PropertyMap(map.toMap())

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as MutablePropertyMap

        return map == other.map
    }

    override fun hashCode() = map.hashCode()
}

/** Create a [MutablePropertyMap]. */
fun mutablePropertyMap() = MutablePropertyMap()

/**
 * Build a [PropertyMap] by applying [builder] to a [MutablePropertyMap] and returning
 * [MutablePropertyMap.toMap].
 */
inline fun buildPropertyMap(builder: MutablePropertyMap.() -> Unit) =
    MutablePropertyMap().apply(builder).toMap()
