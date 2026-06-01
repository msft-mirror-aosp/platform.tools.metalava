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
value class ValueApiVariantSet(internal val bits: Int) {
    /** Return true if this contains no [ApiVariant]s. */
    fun isEmpty() = bits == 0

    /** Return true if this contains at least one [ApiVariant]. */
    fun isNotEmpty() = bits != 0

    operator fun contains(variant: ApiVariant) = (bits and variant.bitMask) != 0

    /** True if this set contains any of the variants from [surface]. */
    fun containsAny(surface: ApiSurface) = containsAny(surface.variantSet.value)

    /** True if this set contains any of the variants from [variantSet]. */
    fun containsAny(variantSet: ValueApiVariantSet) = (bits and variantSet.bits) != 0

    /** Return the result of adding [variant] to this [ValueApiVariantSet]. */
    operator fun plus(variant: ApiVariant) = ValueApiVariantSet(bits or variant.bitMask)

    /**
     * Return the result of adding all [ApiVariant]s in [other] [ValueApiVariantSet] to this
     * [ValueApiVariantSet].
     */
    operator fun plus(other: ValueApiVariantSet) = ValueApiVariantSet(bits or other.bits)

    /** Return the result of removing [variant] from this [ValueApiVariantSet]. */
    operator fun minus(variant: ApiVariant) = ValueApiVariantSet(bits and variant.bitMask.inv())

    /**
     * Return the result of removing all [ApiVariant]s in [other] [ValueApiVariantSet] from this
     * [ValueApiVariantSet].
     */
    operator fun minus(other: ValueApiVariantSet) = ValueApiVariantSet(bits and other.bits.inv())

    /** Represent the values as binary number starting with a `0b` prefix. */
    override fun toString() = "0b${Integer.toBinaryString(bits)}"

    /** Format this for [apiSurfaces]. */
    fun formatFor(apiSurfaces: ApiSurfaces) = buildString {
        append("ApiVariantSet[")
        var separator = ""
        for (apiSurface in apiSurfaces.all) {
            // If this set does not contain any variants from the ApiSurface then ignore it.
            if (!this@ValueApiVariantSet.containsAny(apiSurface.variantSet.value)) continue
            append(separator)
            separator = ","
            append(apiSurface.name)
            append("(")
            for (variant in apiSurface.variants) {
                if (variant in this@ValueApiVariantSet) append(variant.type.shortCode)
            }
            append(")")
        }
        append("]")
    }

    companion object {
        /** The empty [ValueApiVariantSet]. */
        val EMPTY = ValueApiVariantSet(0)
    }
}

/**
 * The base set of [ApiVariant]s.
 *
 * Provides common query only functionality for [ApiVariantSet] and [MutableApiVariantSet].
 */
sealed class BaseApiVariantSet(internal val apiSurfaces: ApiSurfaces) {
    internal abstract val value: ValueApiVariantSet

    fun isEmpty() = value.isEmpty()

    fun isNotEmpty() = value.isNotEmpty()

    operator fun contains(variant: ApiVariant) = value.contains(variant)

    /** True if this set contains any of the variants from [surface]. */
    fun containsAny(surface: ApiSurface) = containsAny(surface.variantSet)

    /** True if this set contains any of the variants from [variantSet]. */
    fun containsAny(variantSet: ApiVariantSet): Boolean {
        require(apiSurfaces === variantSet.apiSurfaces) {
            "Mismatch between ApiSurfaces, this set is for $apiSurfaces, other set is for ${variantSet.apiSurfaces}"
        }
        return value.containsAny(variantSet.value)
    }

    /**
     * Return the union of this [BaseApiVariantSet] with the [other] [BaseApiVariantSet].
     *
     * If this is a [MutableApiVariantSet] then this will modify and return this. If this is
     * [ApiVariantSet] then it will create a [MutableApiVariantSet] copy and then modify and return
     * it.
     */
    abstract fun unionWith(other: BaseApiVariantSet): BaseApiVariantSet

    /**
     * Get a [MutableApiVariantSet] from this.
     *
     * This will return the object on which it is called if that is already mutable, otherwise it
     * will create a separate mutable copy of this.
     */
    abstract fun toMutable(): MutableApiVariantSet

    /**
     * Get an immutable [ApiVariantSet] from this.
     *
     * This will return the object on which it is called if that is already immutable, otherwise it
     * will create a separate immutable copy of this.
     */
    abstract fun toImmutable(): ApiVariantSet

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is BaseApiVariantSet) return false

        if (apiSurfaces != other.apiSurfaces) return false
        if (value != other.value) return false

        return true
    }

    override fun hashCode(): Int {
        var result = apiSurfaces.hashCode()
        result = 31 * result + value.hashCode()
        return result
    }

    override fun toString() = value.formatFor(apiSurfaces)
}

/** An immutable set of [ApiVariant]s. */
class ApiVariantSet(apiSurfaces: ApiSurfaces, override val value: ValueApiVariantSet) :
    BaseApiVariantSet(apiSurfaces) {

    override fun unionWith(other: BaseApiVariantSet): BaseApiVariantSet =
        if (value + other.value == value) this
        else if (value.isEmpty()) other else toMutable().apply { unionWith(other) }

    override fun toMutable() = MutableApiVariantSet(apiSurfaces, value)

    override fun toImmutable() = this

    companion object {
        internal fun emptySet(apiSurfaces: ApiSurfaces) =
            ApiVariantSet(
                apiSurfaces,
                ValueApiVariantSet.EMPTY,
            )

        /**
         * Build an [ApiVariantSet].
         *
         * Creates a [MutableApiVariantSet], calls [lambda] to modify it and then calls
         * [MutableApiVariantSet.toImmutable] to return an immutable [ApiVariantSet].
         *
         * @param apiSurfaces the [ApiSurfaces] whose [ApiVariant]s it will contain.
         * @param lambda the lambda that will be passed a [MutableApiVariantSet] to modify.
         */
        fun build(apiSurfaces: ApiSurfaces, lambda: MutableApiVariantSet.() -> Unit) =
            MutableApiVariantSet(apiSurfaces).apply(lambda).toImmutable()
    }
}

/** A mutable set of [ApiVariant]s. */
class MutableApiVariantSet
internal constructor(
    apiSurfaces: ApiSurfaces,
    override var value: ValueApiVariantSet = ValueApiVariantSet.EMPTY,
) : BaseApiVariantSet(apiSurfaces) {

    override fun unionWith(other: BaseApiVariantSet) = this.apply { value += other.value }

    override fun toMutable() = this

    override fun toImmutable() =
        if (value.isEmpty()) apiSurfaces.emptyVariantSet else ApiVariantSet(apiSurfaces, value)

    /**
     * Add [variant] to this set.
     *
     * This has no effect if it is already a member.
     */
    fun add(variant: ApiVariant) {
        value += variant
    }

    /**
     * Remove [variant] from this set.
     *
     * This has no effect if it was not a member.
     */
    fun remove(variant: ApiVariant) {
        value -= variant
    }

    /** Clear the set. */
    fun clear() {
        value = ValueApiVariantSet.EMPTY
    }

    companion object {

        /** Create a [MutableApiVariantSet] for [apiSurfaces]. */
        fun setOf(apiSurfaces: ApiSurfaces): MutableApiVariantSet {
            // Make sure all the variant bits can fit into an Int.
            if (apiSurfaces.variants.count() > 30)
                error("Too many API variants to store in the set")
            return MutableApiVariantSet(apiSurfaces, ValueApiVariantSet.EMPTY)
        }
    }
}
