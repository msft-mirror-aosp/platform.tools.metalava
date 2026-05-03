/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.tools.metalava.model.api

import com.android.tools.metalava.model.AnnotationContext
import com.android.tools.metalava.model.AnnotationItem
import com.android.tools.metalava.model.value.ArrayElementValue
import com.android.tools.metalava.model.value.ArrayValue
import com.android.tools.metalava.model.value.Value
import java.util.TreeMap

internal class AnnotationMatcher
private constructor(
    private val qualifiedNameToEntries: Map<String, List<Entry>>,
) {
    /**
     * Returns a sorted set of fully qualified annotation names that may be matched by this matcher.
     * Note that this matcher might incorporate parameters but this function strips them.
     */
    val annotationNames: Set<String>
        get() = qualifiedNameToEntries.keys

    /** Checks whether an annotation is matched by this. */
    fun matches(annotation: AnnotationItem): Boolean {
        val qualifiedName = annotation.qualifiedName
        // If the annotation name is not in the map of annotation names that can be matched then
        // this can never match so return immediately rather than generating an entry for the
        // annotation.
        if (qualifiedName !in qualifiedNameToEntries) {
            return false
        }

        // If there are no entries for the annotation's class then return immediately.
        val entries = qualifiedNameToEntries[annotation.qualifiedName] ?: return false

        // Get an Entry from the annotation.
        val wrapper = fromAnnotationItem(annotation)
        return entries.any { entry -> entry.annotationsMatch(wrapper) }
    }

    /**
     * An [Entry] for annotations having a certain [qualifiedName] and possibly certain
     * [attributes].
     *
     * An [Entry] does not have a Codebase like an [AnnotationItem] does.
     */
    private class Entry(
        val qualifiedName: String,
        val attributes: Map<String, Value>,
    ) {
        fun annotationsMatch(
            existingAnnotation: Entry,
        ): Boolean {
            // The annotation must have an attribute for each attribute in the matcher.
            if (attributes.size > existingAnnotation.attributes.size) {
                return false
            }

            // The annotation must have the same value as every matcher attribute.
            val annotationAttributes = existingAnnotation.attributes
            return attributes.all { (attributeName, matcherValue) ->
                matcherValue == annotationAttributes[attributeName]
            }
        }
    }

    companion object {
        /**
         * Create an [AnnotationMatcher] from a list of [annotationPatterns] each of which is an
         * annotation that can match an annotation based on its qualified name and/or attribute
         * values.
         */
        fun create(annotationPatterns: List<String>): AnnotationMatcher {
            val entries = annotationPatterns.map { fromOption(it) }
            val map = entries.groupByTo(TreeMap()) { it.qualifiedName }
            return AnnotationMatcher(map)
        }

        /** Normalize this [Value] to simplify comparison. */
        private fun Value.normalizeValue(): Value =
            when (this) {
                is ArrayValue -> {
                    val size = elements.size
                    when (size) {
                        0 -> this
                        // Replace an array containing a single value with the normalized value.
                        1 -> elements[0]
                        // Normalize the elements of the array.
                        else -> Value.createArrayValue(elements)
                    }
                }
                is ArrayElementValue -> this
            }

        private fun fromOption(text: String): Entry {
            val annotationItem =
                AnnotationItem.createFromSource(
                    // Use the NoOpAnnotationManager whose `normalizeInputName(...)` method will not
                    // reject any annotations so createFromSource(...) will never return null.
                    AnnotationContext.DEFAULT_RESOLVE_NULL,
                    "@$text"
                ) ?: error("Could not construct annotation from `$text`")

            return fromAnnotationItem(annotationItem)
        }

        private fun fromAnnotationItem(annotationItem: AnnotationItem): Entry {
            val qualifiedName = annotationItem.qualifiedName

            // Create a map from attribute name to normalized value.
            val attributes =
                annotationItem.attributes.associateBy({ it.name }) { it.value.normalizeValue() }

            // Merge in any default values.
            val withDefaults =
                annotationItem.annotationContext
                    .defaultsForAnnotationClass(annotationItem.qualifiedName)
                    .apply(attributes)

            return Entry(qualifiedName, withDefaults)
        }
    }
}
