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

package com.android.tools.metalava.doc.annotationhandlers

import com.android.tools.metalava.model.AnnotationItem
import com.android.tools.metalava.model.Item
import com.android.tools.metalava.model.annotation.binding.bindTo

/** Handles @android.processor.devicepolicy.StringPolicyDefinition annotation. */
class StringPolicyAnnotationHandler(
    context: DevicePolicyContext,
) : BaseDevicePolicyAnnotationHandler(context) {

    /** Processes the [StringPolicyDefinitionProxy] and returns the documentation for the policy. */
    override fun processPolicyAnnotation(annotation: AnnotationItem, item: Item): String {
        val proxy = annotation.bindTo<StringPolicyDefinitionProxy>(item)
        return proxy?.generateDocs() ?: ""
    }
}

/**
 * Proxy class bound to an instance of the `android.processor.devicepolicy.StringPolicyDefinition`
 * annotation class.
 *
 * @see bindTo
 */
data class StringPolicyDefinitionProxy(
    val base: PolicyDefinitionProxy,
    val emptyStringAllowed: Boolean,
    val unprintableCharactersAllowed: Boolean,
    val pureWhitespaceAllowed: Boolean,
    val unstrippedStringAllowed: Boolean,
    val maxLength: Int,
) {
    fun generateDocs() = buildString {
        append("\n<p>Policy Type: String</p>\n <ul>\n")
        append(base.generateDocs())
        append(
            "   <li>Empty string: ${if (emptyStringAllowed) "Allowed" else "Not allowed"}</li>\n"
        )
        append(
            "   <li>Unprintable characters: ${if (unprintableCharactersAllowed) "Allowed" else "Not allowed"}</li>\n"
        )
        append(
            "   <li>Pure whitespace: ${if (pureWhitespaceAllowed) "Allowed" else "Not allowed"}</li>\n"
        )
        append(
            "   <li>Unstripped string: ${if (unstrippedStringAllowed) "Allowed" else "Not allowed"}</li>\n"
        )
        append(
            "   <li>Max Length: ${if (maxLength == Integer.MAX_VALUE) "No limit" else maxLength}</li>\n"
        )
        append(" </ul>\n")
    }
}
