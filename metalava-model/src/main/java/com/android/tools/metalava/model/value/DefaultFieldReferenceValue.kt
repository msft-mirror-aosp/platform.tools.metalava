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

import com.android.tools.metalava.model.ClassResolver
import com.android.tools.metalava.model.Codebase
import com.android.tools.metalava.model.FieldItem
import com.android.tools.metalava.model.value.Value.Companion.toString
import java.util.Optional
import kotlin.jvm.optionals.getOrNull

/** Base class for [FieldReferenceValue] implementations. */
internal abstract class BaseFieldReferenceValue(
    private val classResolver: ClassResolver,
    override val qualifiedClassName: String,
    override val fieldName: String,
) : DefaultValue(), FieldReferenceValue {

    private lateinit var optionalFieldItem: Optional<FieldItem>

    override fun resolve(): FieldItem? {
        if (!::optionalFieldItem.isInitialized) {
            if (qualifiedClassName == "") {
                optionalFieldItem = Optional.empty()
            } else {
                optionalFieldItem =
                    Optional.ofNullable(
                        classResolver
                            .resolveClass(qualifiedClassName)
                            ?.findField(
                                fieldName,
                                includeSuperClasses = true,
                                includeInterfaces = true,
                            )
                    )
            }
        }
        return optionalFieldItem.getOrNull()
    }
}

internal class DefaultFieldReferenceValue(
    classResolver: ClassResolver,
    qualifiedClassName: String,
    fieldName: String,

    /**
     * The optional constant value of this field.
     *
     * Is `null` if the field does not reference a constant value.
     *
     * Note: This is NOT used in [equals], [hashCode] or [toString]. That is because this may be
     * provided lazily and accessing it may have side effects but those methods are not expected to
     * have side effects.
     */
    private val constantValue: ConstantValue? = null,
) : BaseFieldReferenceValue(classResolver, qualifiedClassName, fieldName) {

    /**
     * Implement this here rather than in [FieldReferenceValue] as it needs to access
     * [constantValue] which is an implementation detail.
     */
    override fun snapshot(targetCodebase: Codebase) =
        Value.createFieldReferenceValue(
            targetCodebase,
            qualifiedClassName,
            fieldName,
            constantValue,
        )

    /** The [constantValue], if present, may be a [LiteralValue]. */
    override fun asLiteralValue() = constantValue?.asLiteralValue()
}
