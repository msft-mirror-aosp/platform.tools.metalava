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

/** Represents a value in a [Codebase]. */
sealed interface Value {
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
}

/** Configuration options for how to represent a value as a string. */
class ValueStringConfiguration {
    companion object {
        /** Default configuration. */
        val DEFAULT = ValueStringConfiguration()
    }
}

/** A [Value] that can be used in a constant field as defined by JLS 15.28. */
sealed interface ConstantValue : Value

/** Base implementation of [Value]. */
internal sealed class DefaultValue : Value {
    override fun equals(other: Any?): Boolean {
        if (other !is Value) return false
        return equalToValue(other)
    }

    override fun hashCode(): Int = hashCodeForValue()

    override fun toString() = "${javaClass.simpleName}(${toValueString()})"
}
