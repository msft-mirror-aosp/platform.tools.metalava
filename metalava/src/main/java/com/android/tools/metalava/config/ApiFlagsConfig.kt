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

package com.android.tools.metalava.config

import com.android.tools.metalava.config.ApiFlagActionConfig.Mutability
import com.android.tools.metalava.config.ApiFlagActionConfig.Status
import com.fasterxml.jackson.annotation.JsonValue
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty

data class ApiFlagsConfig(
    @field:JacksonXmlProperty(localName = "unknown-flags", namespace = CONFIG_NAMESPACE)
    val unknownFlags: UnknownApiFlagsConfig? = null,
    @field:JacksonXmlProperty(localName = "api-flag", namespace = CONFIG_NAMESPACE)
    val flags: List<ApiFlagConfig> = emptyList(),
) : CombinableConfig<ApiFlagsConfig> {

    /** Combine with another [ApiFlagsConfig] by concatenating the [flags]s. */
    override fun combineWith(other: ApiFlagsConfig) =
        ApiFlagsConfig(
            unknownFlags =
                when {
                    unknownFlags == null -> other.unknownFlags
                    other.unknownFlags == null -> unknownFlags
                    else ->
                        error("Cannot combine unknownFlag $unknownFlags and ${other.unknownFlags}")
                },
            flags = flags + other.flags
        )

    /** Validate this object, i.e. check to make sure that the contained objects are consistent. */
    fun validate() {}

    /** Write this [ApiFlagsConfig] to [writer]. */
    internal fun writeTo(writer: ConfigWriter) {
        writer.element("api-flags") {
            unknownFlags?.writeTo(this)
            for (flag in flags) {
                flag.writeTo(this)
            }
        }
    }
}

interface ApiFlagActionConfig {
    /**
     * Whether the flag can be mutated during the lifetime of the platform for which the API is
     * being built.
     */
    val mutability: Mutability

    /**
     * The status of the flag.
     *
     * If the flag is [Mutability.MUTABLE] then this could change over the lifetime of the platform
     * for which the API is being built.
     */
    val status: Status

    enum class Mutability {
        MUTABLE,
        IMMUTABLE,
        ;

        val configFileValue: String = name.lowercase()

        /** Name to use when serializing and deserializing this [Mutability] instance. */
        @JsonValue fun forJackson() = configFileValue
    }

    enum class Status {
        ENABLED,
        DISABLED,
        ;

        val configFileValue: String = name.lowercase()

        /** Name to use when serializing and deserializing this [Status] instance. */
        @JsonValue fun forJackson() = configFileValue
    }
}

data class UnknownApiFlagsConfig(
    @field:JacksonXmlProperty(isAttribute = true) override val mutability: Mutability,
    @field:JacksonXmlProperty(isAttribute = true) override val status: Status,
) : ApiFlagActionConfig {
    /** Write this [UnknownApiFlagsConfig] to [writer]. */
    internal fun writeTo(writer: ConfigWriter) {
        writer.element(
            "unknown-flags",
            attributes = {
                attribute("mutability", mutability.configFileValue)
                attribute("status", status.configFileValue)
            },
        )
    }
}

data class ApiFlagConfig(
    /** The flag package name. */
    @field:JacksonXmlProperty(isAttribute = true, localName = "package") val pkg: String,

    /** The flag name, within [pkg]. */
    @field:JacksonXmlProperty(isAttribute = true) val name: String,
    @field:JacksonXmlProperty(isAttribute = true) override val mutability: Mutability,
    @field:JacksonXmlProperty(isAttribute = true) override val status: Status,

    /** Whether the flag is exported */
    @field:JacksonXmlProperty(isAttribute = true, localName = "is-exported")
    val isExported: Boolean,
) : ApiFlagActionConfig {
    /** Write this [ApiFlagConfig] to [writer]. */
    internal fun writeTo(writer: ConfigWriter) {
        writer.element(
            "api-flag",
            attributes = {
                attribute("package", pkg)
                attribute("name", name)
                attribute("mutability", mutability.configFileValue)
                attribute("status", status.configFileValue)
                attribute("is-exported", isExported)
            },
        )
    }
}
