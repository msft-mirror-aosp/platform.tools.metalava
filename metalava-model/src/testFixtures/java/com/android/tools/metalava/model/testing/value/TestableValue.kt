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

package com.android.tools.metalava.model.testing.value

import com.android.tools.metalava.model.value.ArrayValue
import com.android.tools.metalava.model.value.LiteralValue
import com.android.tools.metalava.model.value.Value
import com.android.tools.metalava.model.value.ValueKind
import com.android.tools.metalava.model.value.ValueProviderException
import java.util.EnumSet
import org.junit.AssumptionViolatedException

/** Create a [LiteralValue] from the [underlyingValue]. */
fun literalValue(underlyingValue: Any) = Value.createLiteralValue(null, underlyingValue)

/** Create an [ArrayValue] containing [literals]. */
fun arrayValueFromAny(vararg literals: Any) =
    Value.createArrayValue(literals.map { literalValue(it) })

/**
 * The set of [ValueKind]s that are fully supported across models and so will be tested rigorously,
 * i.e. will not ignore [ValueProviderException]
 *
 * As each additional [ValueKind] is supported across the models they will be added here to ensure
 * that there are no regressions.
 */
private val fullySupportedValueKinds = EnumSet.noneOf(ValueKind::class.java)

/**
 * Run a test on this [Value] ignoring any [ValueProviderException]s if its [Value.kind] is not
 * fully supported across model implementations.
 */
fun Value?.runValueTest(body: (Value) -> Unit) {
    this ?: return

    // Check whether this kind is fully supported.
    val fullySupported = kind in fullySupportedValueKinds

    // ValueProviderExceptions are not treated as test failures if the value kind is not fully
    // supported to avoid having to keep updating baseline files while expanding Value support
    // across the models.
    // TODO(b/354633349): Stop ignoring exceptions.
    try {
        body(this)
    } catch (e: ValueProviderException) {
        if (fullySupported) {
            throw e
        } else {
            throw AssumptionViolatedException("Ignoring exception thrown while retrieving value", e)
        }
    }
}
