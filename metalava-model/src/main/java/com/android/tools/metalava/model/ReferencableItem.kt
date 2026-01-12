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

package com.android.tools.metalava.model

import com.android.tools.metalava.model.scope.NameClassification

/**
 * Effectively forms a union type of all referencable [Item]s that extend this.
 *
 * Currently, the union contains:
 * * [PackageItem]
 * * [ClassItem]
 * * [TypeParameterItem]
 * * [FieldItem]
 */
sealed interface ReferencableItem

/** Provides details about an invalid reference and why it could not be resolved. */
data class InvalidReferencableItem(
    /** The qualified name of the reference that could not be resolved. */
    private val unresolvedReferenceName: String,
    /** The [NameClassification] of [unresolvedNameClassification]. */
    private val unresolvedNameClassification: NameClassification,
    /** The name of the scope where an issue was encountered. */
    private val failingScopeName: String,
    /** The simple name of the item being searched for in [failingScopeName]. */
    private val failingSimpleName: String,
    /** The [NameClassification] of [failingSimpleName]. */
    private val failingSimpleNameClassification: NameClassification,
    /** The reason for the failure. */
    private val reason: Reason,
) : ReferencableItem {
    val message
        get(): String = reason.run { format() }

    enum class Reason {
        NOT_FOUND {
            override fun InvalidReferencableItem.format() =
                if (unresolvedReferenceName == failingSimpleName) {
                    "Could not resolve ${failingSimpleNameClassification.describeName(failingSimpleName)} in '$failingScopeName'"
                } else {
                    "Could not resolve ${unresolvedNameClassification.describeName(unresolvedReferenceName)} as could not find ${failingSimpleNameClassification.describeName(failingSimpleName)} in '$failingScopeName'"
                }
        },
        ;

        abstract fun InvalidReferencableItem.format(): String
    }
}
