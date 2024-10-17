/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.tools.metalava.model.api.surface

/** The configured set of [ApiSurface]s. */
sealed interface ApiSurfaces {
    /**
     * The list of all [ApiSurface]s.
     *
     * If [base] is set then it comes first; [main] is always last.
     */
    val all: List<ApiSurface>

    /** The main [ApiSurface]. */
    val main: ApiSurface

    /** The optional base [ApiSurface]. */
    val base: ApiSurface?

    companion object {
        /**
         * Create an [ApiSurfaces] instance.
         *
         * @param needsBase if `false` (the default) then the returned [ApiSurfaces.base] property
         *   is null, otherwise it is an [ApiSurface] that the [ApiSurfaces.main] references in its
         *   [ApiSurface.extends] property.
         */
        fun create(needsBase: Boolean = false): ApiSurfaces = DefaultApiSurfaces(needsBase)
    }

    /** Default implementation of [ApiSurfaces]. */
    private class DefaultApiSurfaces(needsBase: Boolean) : ApiSurfaces {

        override val all: List<DefaultApiSurface>

        override val base: DefaultApiSurface?

        override val main: DefaultApiSurface

        init {
            val surfaceList = mutableListOf<DefaultApiSurface>()

            /**
             * Create an [ApiSurface] with the specified [name] which has an optional [extends].
             *
             * Adds the created [ApiSurface] to [all].
             */
            fun createSurface(name: String, extends: DefaultApiSurface?) =
                DefaultApiSurface(
                        surfaces = this,
                        name = name,
                        extends = extends,
                    )
                    .also { surfaceList.add(it) }

            base =
                if (needsBase)
                    createSurface(
                        "base",
                        extends = null,
                    )
                else null

            main =
                createSurface(
                    "main",
                    extends = base,
                )

            all = surfaceList.toList()
        }
    }

    /** Default implementation of [ApiSurface]. */
    private class DefaultApiSurface(
        override val surfaces: ApiSurfaces,
        override val name: String,
        override val extends: DefaultApiSurface?,
    ) : ApiSurface {
        override fun toString(): String = "ApiSurface($name)"
    }
}
