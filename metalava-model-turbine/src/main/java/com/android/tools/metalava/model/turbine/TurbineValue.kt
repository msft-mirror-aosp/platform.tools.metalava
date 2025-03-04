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

package com.android.tools.metalava.model.turbine

import com.google.turbine.model.Const
import com.google.turbine.model.Const.ArrayInitValue
import com.google.turbine.model.Const.Kind
import com.google.turbine.model.Const.Value
import com.google.turbine.model.TurbineConstantTypeKind
import com.google.turbine.tree.Tree.ArrayInit
import com.google.turbine.tree.Tree.Expression

/**
 * A representation of a value in Turbine.
 *
 * A value could be:
 * * A literal constant, e.g. `"string"`, or `3.4`.
 * * An enum constant, e.g. `RetentionPolicy.CLASS`.
 * * A class literal.
 * * A constant field.
 * * An array of one of the above types.
 *
 * They can be used as constant field values and annotation attribute values, including default
 * values.
 *
 * It consists of two parts.
 * * [const] - this is the constant value and has been evaluated by Turbine.
 * * [expr] - the optional source representation of the value. This is `null` when the value is
 *   obtained from a binary file, e.g. the value of an annotation attribute of an annotation on a
 *   class loaded from the class path.
 *
 * The model needs information from both so this encapsulates them together to make them easier to
 * use and provide a convenient place for code that manipulate them.
 */
internal class TurbineValue(
    /** The constant object representing the annotation value. */
    val const: Const,

    /** An optional [Expression] that might provide additional context for value extraction. */
    val expr: Expression?,
) {
    /**
     * Get the source representation of this value suitable for use when writing a method's default
     * value.
     */
    fun getSourceForMethodDefault(): String {
        return when (const.kind()) {
            Kind.PRIMITIVE -> {
                when ((const as Value).constantTypeKind()) {
                    TurbineConstantTypeKind.FLOAT -> {
                        val value = (const as Const.FloatValue).value()
                        when {
                            value == Float.POSITIVE_INFINITY -> "java.lang.Float.POSITIVE_INFINITY"
                            value == Float.NEGATIVE_INFINITY -> "java.lang.Float.NEGATIVE_INFINITY"
                            else -> value.toString() + "f"
                        }
                    }
                    TurbineConstantTypeKind.DOUBLE -> {
                        val value = (const as Const.DoubleValue).value()
                        when {
                            value == Double.POSITIVE_INFINITY ->
                                "java.lang.Double.POSITIVE_INFINITY"
                            value == Double.NEGATIVE_INFINITY ->
                                "java.lang.Double.NEGATIVE_INFINITY"
                            else -> const.toString()
                        }
                    }
                    TurbineConstantTypeKind.BYTE -> const.getValue().toString()
                    else -> const.toString()
                }
            }
            Kind.ARRAY -> {
                const as ArrayInitValue
                // This is case where defined type is array type but default value is
                // single non-array element
                // For e.g. char[] letter() default 'a';
                if (const.elements().count() == 1 && expr != null && expr !is ArrayInit) {
                    TurbineValue(const.elements().single(), expr).getSourceForMethodDefault()
                } else const.underlyingValue.toString()
            }
            Kind.CLASS_LITERAL -> "${const.underlyingValue}.class"
            else -> const.underlyingValue.toString()
        }
    }
}
