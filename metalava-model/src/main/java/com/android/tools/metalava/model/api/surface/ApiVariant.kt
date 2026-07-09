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

/** An API variant of [type] for [surface] */
class ApiVariant(
    /** The [ApiSurface] of which this is a variant. */
    val surface: ApiSurface,

    /** The type of this variant. */
    val type: ApiVariantType,

    /**
     * The list of all [ApiVariant]s belonging to the owning [ApiSurfaces].
     *
     * This must add itself to it.
     */
    allVariants: MutableList<ApiVariant>,
) {
    /**
     * Bit mask for this, used within [ApiVariantSet].
     *
     * This must be unique across all [ApiVariant]s within `allVariants` so it computes the bit
     * based on the current size of `allVariants` and then adds itself to the list ensuring that the
     * next [ApiVariant] will use a different bit.
     */
    internal val bitMask: Int = 1 shl allVariants.size.also { allVariants.add(this) }

    override fun toString(): String {
        return "${surface.name}(${type.name})"
    }
}

/**
 * An immutable set of [ApiVariant]s.
 *
 * As this is a value class it is treated as its primitive [bits]. That makes it more efficient in
 * terms of storage and processing than if it was an object. However, due to the limitation of the
 * `value class` mechanism that means it cannot contain a reference to the [ApiSurfaces] for which
 * this applied. Instead, it relies on the owner tracking the [ApiSurfaces] separately.
 */
@JvmInline
value class ApiVariantSet(internal val bits: Int) {
    /** Return true if this contains no [ApiVariant]s. */
    fun isEmpty() = bits == 0

    /** Return true if this contains at least one [ApiVariant]. */
    fun isNotEmpty() = bits != 0

    operator fun contains(variant: ApiVariant) = (bits and variant.bitMask) != 0

    /** True if this set contains any of the variants from [surface]. */
    fun containsAny(surface: ApiSurface) = containsAny(surface.variantSet)

    /** True if this set contains any of the variants from [variantSet]. */
    fun containsAny(variantSet: ApiVariantSet) = (bits and variantSet.bits) != 0

    /** Return the result of adding [variant] to this [ApiVariantSet]. */
    operator fun plus(variant: ApiVariant) = ApiVariantSet(bits or variant.bitMask)

    /**
     * Return the result of adding all [ApiVariant]s in [other] [ApiVariantSet] to this
     * [ApiVariantSet].
     */
    operator fun plus(other: ApiVariantSet) = ApiVariantSet(bits or other.bits)

    /** Return the result of removing [variant] from this [ApiVariantSet]. */
    operator fun minus(variant: ApiVariant) = ApiVariantSet(bits and variant.bitMask.inv())

    /**
     * Return the result of removing all [ApiVariant]s in [other] [ApiVariantSet] from this
     * [ApiVariantSet].
     */
    operator fun minus(other: ApiVariantSet) = ApiVariantSet(bits and other.bits.inv())

    /** Return the intersection of this [ApiVariantSet] with the [other] [ApiVariantSet]. */
    fun intersectionWith(other: ApiVariantSet): ApiVariantSet = ApiVariantSet(bits and other.bits)

    /** Represent the values as binary number starting with a `0b` prefix. */
    override fun toString() = "0b${Integer.toBinaryString(bits)}"

    /** Format this for [apiSurfaces]. */
    fun formatFor(apiSurfaces: ApiSurfaces) = buildString {
        append("ApiVariantSet[")
        var separator = ""
        for (apiSurface in apiSurfaces.all) {
            // If this set does not contain any variants from the ApiSurface then ignore it.
            if (!this@ApiVariantSet.containsAny(apiSurface.variantSet)) continue
            append(separator)
            separator = ","
            append(apiSurface.name)
            append("(")
            for (variant in apiSurface.variants) {
                if (variant in this@ApiVariantSet) append(variant.type.shortCode)
            }
            append(")")
        }
        append("]")
    }

    companion object {
        /** The empty [ApiVariantSet]. */
        val EMPTY = ApiVariantSet(0)
    }
}
