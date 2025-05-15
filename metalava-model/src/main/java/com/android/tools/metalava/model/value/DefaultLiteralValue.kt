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

/** Base class for all [LiteralValue] implementations. */
internal sealed class DefaultLiteralValue<U : Any> : DefaultValue(), LiteralValue<U>

internal sealed class DefaultPrimitiveValue<U : Any> : DefaultLiteralValue<U>(), PrimitiveValue<U>

internal sealed class DefaultNumericValue<U : Number>(
    /**
     * True if the original value of this from the source, was specified as an integer, e.g. `3`
     * instead of `3.0`, or `3.0f` or `3L`. This is used to tweak formatting to match legacy
     * behavior.
     */
    private val wasOriginallySpecifiedAsInt: Boolean = false,
) : DefaultPrimitiveValue<U>() {

    /**
     * If the [configuration] has [ValueStringConfiguration.treatAsIntIfOriginallySpecifiedAsInt]
     * set to `true` and [wasOriginallySpecifiedAsInt] is also `true` then this will append
     * [underlyingValue] as if it was an `int`, otherwise it will invoke [appendNumericValueTo] to
     * append the value as normal.
     */
    final override fun appendValueStringTo(
        builder: StringBuilder,
        configuration: ValueStringConfiguration
    ) {
        if (configuration.treatAsIntIfOriginallySpecifiedAsInt && wasOriginallySpecifiedAsInt) {
            val intValue = underlyingValue.toInt()
            builder.append(intValue)
            return
        }

        // Append the default numeric value to builder.
        appendNumericValueTo(builder, configuration)
    }

    internal open fun appendNumericValueTo(
        builder: StringBuilder,
        configuration: ValueStringConfiguration
    ) {
        builder.append(underlyingValue)
    }

    override fun appendLegacyStateTo(builder: StringBuilder) {
        if (wasOriginallySpecifiedAsInt) builder.append(",asInt")
    }
}

internal class DefaultBooleanValue(override val underlyingValue: Boolean) :
    DefaultPrimitiveValue<Boolean>(), BooleanValue

internal class DefaultByteValue(override val underlyingValue: Byte) :
    DefaultNumericValue<Byte>(), ByteValue

internal class DefaultCharValue(override val underlyingValue: Char) :
    DefaultPrimitiveValue<Char>(), CharValue

internal class DefaultDoubleValue(
    override val underlyingValue: Double,
    wasOriginallySpecifiedAsInt: Boolean = false,
) : DefaultNumericValue<Double>(wasOriginallySpecifiedAsInt), DoubleValue {

    override fun appendNumericValueTo(
        builder: StringBuilder,
        configuration: ValueStringConfiguration
    ) {
        configuration.specialValues[this]?.let {
            builder.append(it)
            return
        }

        builder.append(underlyingValue)
    }
}

internal class DefaultFloatValue(
    override val underlyingValue: Float,
    wasOriginallySpecifiedAsInt: Boolean = false,
    private val nonLiteralInSource: Boolean = false,
) : DefaultNumericValue<Float>(wasOriginallySpecifiedAsInt), FloatValue {

    override fun appendNumericValueTo(
        builder: StringBuilder,
        configuration: ValueStringConfiguration
    ) {
        configuration.specialValues[this]?.let {
            builder.append(it)
            return
        }

        // If it was not a literal then use the non-literal suffix. This is mutually
        // exclusive with it being specified as an int so it does not matter which one is
        // performed first.
        val suffix = if (nonLiteralInSource) configuration.nonLiteralFloatSuffix else 'f'
        builder.append(underlyingValue).append(suffix)
    }

    override fun appendLegacyStateTo(builder: StringBuilder) {
        super<DefaultNumericValue>.appendLegacyStateTo(builder)
        if (nonLiteralInSource) builder.append(",nonLiteral")
    }
}

internal class DefaultIntValue(
    override val underlyingValue: Int,
    private val nonLiteralInSource: Boolean = false,
) : DefaultNumericValue<Int>(), IntValue {

    override fun appendNumericValueTo(
        builder: StringBuilder,
        configuration: ValueStringConfiguration
    ) {
        if (nonLiteralInSource) {
            when (configuration.nonLiteralIntFormat) {
                IntFormat.HEXADECIMAL -> {
                    builder.append("0x").append(Integer.toHexString(underlyingValue))
                    return
                }
                else -> {}
            }
        }

        builder.append(underlyingValue)
    }

    override fun appendLegacyStateTo(builder: StringBuilder) {
        if (nonLiteralInSource) builder.append(",nonLiteral")
    }
}

internal class DefaultLongValue(
    override val underlyingValue: Long,
    wasOriginallySpecifiedAsInt: Boolean = false,
) : DefaultNumericValue<Long>(wasOriginallySpecifiedAsInt), LongValue {

    override fun appendNumericValueTo(
        builder: StringBuilder,
        configuration: ValueStringConfiguration
    ) {
        builder.append(underlyingValue).append('L')
    }
}

internal class DefaultShortValue(override val underlyingValue: Short) :
    DefaultNumericValue<Short>(), ShortValue

internal class DefaultStringValue(override val underlyingValue: String) :
    DefaultLiteralValue<String>(), StringValue
