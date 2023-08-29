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

package com.android.tools.metalava.model

import java.io.File
import java.nio.file.Path

/**
 * Encapsulates location information in a source file.
 *
 * There are two forms of location encapsulated here. The location in the source file, represented
 * by the [path] and [line] properties and the baseline key used to track known issues.
 *
 * The source location is optional as it is not always available. An unavailable source location is
 * indicated by a null [path]. Even when the [path] is available the [line] may be unknown, which is
 * indicated by a non-positive value.
 *
 * The baseline key identifies an API element, for the purposes of tracking known issues. It does
 * not use the source location as that can vary too much and the baseline key needs to identify the
 * same API component over a long period of time. It makes sense for it to be part of this as it is
 * always used together with the source location. The baseline key allows known issues to be
 * identified and filtered out and the source location is used when reporting new issues.
 */
data class Location(
    /** The absolute path to the location, is null when no source location is available. */
    val path: Path?,
    /** The line number, may be non-positive indicating that it could not be found. */
    val line: Int,
    /** The baseline key that identifies the API element. */
    val baselineKey: BaselineKey,
) {
    companion object {
        val unknownBaselineKey = getBaselineKeyForElementId("?")

        fun unknownLocationWithBaselineKey(
            baselineKey: BaselineKey = unknownBaselineKey
        ): Location {
            return Location(null, 0, baselineKey)
        }

        val unknownLocationAndBaselineKey = Location(null, 0, unknownBaselineKey)

        fun forFile(file: File?): Location {
            file ?: return unknownLocationAndBaselineKey
            return Location(file.toPath(), 0, getBaselineKeyForFile(file))
        }

        /** Gat a [BaselineKey] for the supplied file. */
        fun getBaselineKeyForFile(file: File): BaselineKey {
            val path = file.toPath()
            return PathBaselineKey(path)
        }

        /**
         * Gat a [BaselineKey] that for the supplied element id.
         *
         * An element id is something that can uniquely identify an API element over a long period
         * of time, e.g. a class name, class name plus method signature.
         */
        fun getBaselineKeyForElementId(elementId: String): BaselineKey {
            return ElementIdBaselineKey(elementId)
        }

        fun getBaselineKeyForItem(item: Item): BaselineKey {
            val elementId = getElementIdForItem(item)
            return getBaselineKeyForElementId(elementId)
        }

        private fun getElementIdForItem(item: Item): String {
            return when (item) {
                is ClassItem -> item.qualifiedName()
                is MethodItem ->
                    item.containingClass().qualifiedName() +
                        "#" +
                        item.name() +
                        "(" +
                        item.parameters().joinToString { it.type().toSimpleType() } +
                        ")"
                is FieldItem -> item.containingClass().qualifiedName() + "#" + item.name()
                is PackageItem -> item.qualifiedName()
                is ParameterItem ->
                    getElementIdForItem(item.containingMethod()) +
                        " parameter #" +
                        item.parameterIndex
                else -> item.describe(false)
            }
        }
    }
}

/** Key that can be used to identify an API component for use in the baseline. */
sealed interface BaselineKey {
    /**
     * Get the element id for this key.
     *
     * @param pathTransformer if the key contains a path then it will be passed to this to transform
     *   it into a form suitable for its use.
     */
    fun elementId(pathTransformer: (String) -> String = { it }): String
}

/**
 * A [BaselineKey] for an element id (which is simply a string that identifies a specific API
 * element).
 */
private data class ElementIdBaselineKey(val elementId: String) : BaselineKey {
    override fun elementId(pathTransformer: (String) -> String): String {
        return elementId
    }
}

/** A [BaselineKey] for a [Path]. */
private data class PathBaselineKey(val path: Path) : BaselineKey {
    override fun elementId(pathTransformer: (String) -> String): String {
        return pathTransformer(path.toString())
    }
}
