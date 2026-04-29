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
import com.android.tools.metalava.model.Codebase
import com.android.tools.metalava.model.Item
import com.android.tools.metalava.model.SelectableItem
import com.android.tools.metalava.reporter.Issues
import com.android.tools.metalava.reporter.Reporter
import java.util.function.Predicate

/**
 * Base class for Device Policy Annotation Handlers.
 *
 * Contains shared utilities for parsing attributes and reporting issues.
 */
abstract class BaseDevicePolicyAnnotationHandler(
    protected val codebase: Codebase,
    protected val reporter: Reporter,
    protected val filterReference: Predicate<SelectableItem>
) {

    /** Processes a policy annotation and returns a documentation string. */
    abstract fun processPolicyAnnotation(annotation: AnnotationItem, item: Item): String

    /** A helper function to format the allow/disallow status of a field. */
    protected fun StringBuilder.appendAllowed(
        name: String,
        allowed: Boolean,
        leadingSpaces: String = "   ",
    ) {
        append("$leadingSpaces<li>$name: ${if (allowed) "Allowed" else "Not allowed"}</li>\n")
    }
}

/** Report missing required fields inside the annotation. */
fun Reporter.reportOnMissingFields(fieldName: String, item: Item) {
    report(
        Issues.INVALID_DEVICE_POLICY_ANNOTATION,
        item,
        "Missing required field '$fieldName' inside $item"
    )
}

/**
 * Proxy class bound to an instance of the `android.processor.devicepolicy.ListResolutionMechanism`
 * annotation class.
 *
 * @see bindTo
 */
data class ListResolutionMechanismProxy(
    val custom: Boolean = false,
    val union: Boolean = false,
) {
    // TODO(b/492421367): Enrich the doc for resolution mechanism.
    fun generateDocs(item: Item): String {
        if (custom) {
            return "custom"
        } else if (union) {
            return "union"
        } else {
            item.codebase.reporter.report(
                Issues.INVALID_DEVICE_POLICY_ANNOTATION,
                item,
                "ListResolutionMechanism must have either 'custom' or 'union' set to true."
            )
            return ""
        }
    }
}
