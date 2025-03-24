/*
 * Copyright (C) 2025 The Android Open Source Project
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

package com.android.tools.metalava.model.value

import com.android.tools.metalava.model.Codebase
import com.android.tools.metalava.model.javaEscapeString

/** Represents a value in a [Codebase]. */
sealed interface Value {
    /** The kind of this [Value]. */
    val kind: ValueKind

    /**
     * Create a snapshot for this suitable for use in [targetCodebase].
     *
     * This is needed as some [Value]s will reference items in the [Codebase].
     */
    fun snapshot(targetCodebase: Codebase) = this

    /** A string representation of the value. */
    fun toValueString(
        configuration: ValueStringConfiguration = ValueStringConfiguration.DEFAULT
    ): String

    /**
     * Whether this value is equal to [other].
     *
     * This is implemented on each sub-interface of [Value] instead of [equals] because interfaces
     * are not allowed to implement [equals].
     */
    fun equalToValue(other: Value): Boolean

    /**
     * Hashcode for the type.
     *
     * This is implemented on each sub-interface of [Value] instead of [hashCode] because interfaces
     * are not allowed to implement [hashCode].
     */
    fun hashCodeForValue(): Int

    /**
     * Companion object implements [ValueFactory] to allow factory methods to be accessed for
     * testing purposes using the object, e.g. [Value.createLiteralValue].
     */
    companion object : ValueFactory
}

/** Configuration options for how to represent a value as a string. */
class ValueStringConfiguration {
    companion object {
        /** Default configuration. */
        val DEFAULT = ValueStringConfiguration()
    }
}

/** Enumeration of the different types of [ValueKind]. */
enum class ValueKind {
    BOOLEAN,
    BYTE,
    CHAR,
    DOUBLE,
    FLOAT,
    INT,
    LONG,
    SHORT,
    STRING,
    ;

    override fun toString() = super.toString().lowercase()
}

/** A [Value] that can be used in a constant field as defined by JLS 15.28. */
sealed interface ConstantValue : Value

/**
 * A [Value] that encapsulates an [underlyingValue] that can be either a primitive or a String.
 *
 * There is one sub-interface of this for each possible [LiteralValue]. The reason for doing that
 * rather than having a single [LiteralValue] containing an [Any] is it will provide type safety.
 */
sealed interface LiteralValue<T : Any> : ConstantValue {
    /**
     * The underlying value.
     *
     * Will be a primitive type's object wrapper (e.g. [java.lang.Integer]) or a [String].
     */
    val underlyingValue: T

    /**
     * Default implementation just returns the underlying value's standard [String.toString] value.
     */
    override fun toValueString(configuration: ValueStringConfiguration) = underlyingValue.toString()
}

/** A [LiteralValue] that is of a primitive type. */
sealed interface PrimitiveValue<T : Any> : LiteralValue<T>

/** A [Value] that encapsulates a [Boolean]. */
sealed interface BooleanValue : PrimitiveValue<Boolean> {
    override val kind: ValueKind
        get() = ValueKind.BOOLEAN

    override fun equalToValue(other: Value) =
        other is BooleanValue && underlyingValue == other.underlyingValue

    override fun hashCodeForValue() = underlyingValue.hashCode()
}

/** A [Value] that encapsulates a [Byte]. */
sealed interface ByteValue : PrimitiveValue<Byte> {
    override val kind: ValueKind
        get() = ValueKind.BYTE

    override fun equalToValue(other: Value) =
        other is ByteValue && underlyingValue == other.underlyingValue

    override fun hashCodeForValue() = underlyingValue.hashCode()
}

/** A [Value] that encapsulates a [Char]. */
sealed interface CharValue : PrimitiveValue<Char> {
    override val kind: ValueKind
        get() = ValueKind.CHAR

    override fun equalToValue(other: Value) =
        other is CharValue && underlyingValue == other.underlyingValue

    override fun hashCodeForValue() = underlyingValue.hashCode()

    override fun toValueString(configuration: ValueStringConfiguration): String {
        val escaped = javaEscapeString(underlyingValue.toString())
        return "'$escaped'"
    }
}

/** A [Value] that encapsulates a [Double]. */
sealed interface DoubleValue : PrimitiveValue<Double> {
    override val kind: ValueKind
        get() = ValueKind.DOUBLE

    override fun equalToValue(other: Value) =
        other is DoubleValue && underlyingValue == other.underlyingValue

    override fun hashCodeForValue() = underlyingValue.hashCode()
}

/** A [Value] that encapsulates a [Float]. */
sealed interface FloatValue : PrimitiveValue<Float> {
    override val kind: ValueKind
        get() = ValueKind.FLOAT

    override fun equalToValue(other: Value) =
        other is FloatValue && underlyingValue == other.underlyingValue

    override fun hashCodeForValue() = underlyingValue.hashCode()

    override fun toValueString(configuration: ValueStringConfiguration) =
        // No `f` suffix is needed on special values.
        if (underlyingValue.isNaN() || underlyingValue.isInfinite()) underlyingValue.toString()
        else "${underlyingValue}f"
}

/** A [Value] that encapsulates a [Int]. */
sealed interface IntValue : PrimitiveValue<Int> {
    override val kind: ValueKind
        get() = ValueKind.INT

    override fun equalToValue(other: Value) =
        other is IntValue && underlyingValue == other.underlyingValue

    override fun hashCodeForValue() = underlyingValue.hashCode()
}

/** A [Value] that encapsulates a [Long]. */
sealed interface LongValue : PrimitiveValue<Long> {
    override val kind: ValueKind
        get() = ValueKind.LONG

    override fun equalToValue(other: Value) =
        other is LongValue && underlyingValue == other.underlyingValue

    override fun hashCodeForValue() = underlyingValue.hashCode()

    override fun toValueString(configuration: ValueStringConfiguration) = "${underlyingValue}L"
}

/** A [Value] that encapsulates a [Short]. */
sealed interface ShortValue : PrimitiveValue<Short> {
    override val kind: ValueKind
        get() = ValueKind.SHORT

    override fun equalToValue(other: Value) =
        other is ShortValue && underlyingValue == other.underlyingValue

    override fun hashCodeForValue() = underlyingValue.hashCode()
}

/** A [Value] that encapsulates a [String]. */
sealed interface StringValue : LiteralValue<String> {
    override val kind: ValueKind
        get() = ValueKind.STRING

    override fun equalToValue(other: Value) =
        other is StringValue && underlyingValue == other.underlyingValue

    override fun hashCodeForValue() = underlyingValue.hashCode()

    override fun toValueString(configuration: ValueStringConfiguration): String {
        val escaped = javaEscapeString(underlyingValue)
        return "\"$escaped\""
    }
}

/** Base implementation of [Value]. */
internal sealed class DefaultValue : Value {
    override fun equals(other: Any?): Boolean {
        if (other !is Value) return false
        return equalToValue(other)
    }

    override fun hashCode(): Int = hashCodeForValue()

    override fun toString() = "${javaClass.simpleName}(${toValueString()})"
}
