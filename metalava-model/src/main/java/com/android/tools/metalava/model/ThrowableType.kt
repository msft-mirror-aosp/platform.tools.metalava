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

package com.android.tools.metalava.model

/**
 * Represents a type which can be used in a `throws` declaration, e.g. a non-generic class, or a
 * reference to a type parameter that extends [java.lang.Throwable] or one of its subclasses.
 *
 * This is currently an alias for [ClassItem] but it will eventually be migrated to a completely
 * separate type.
 */
interface ThrowableType {
    /** True if [classItem] is a [TypeParameterItem]. */
    val isTypeParameter: Boolean

    /**
     * The underlying [ClassItem], if available; must only be called if [isTypeParameter] is
     * `false`.
     */
    val classItem: ClassItem

    /**
     * The underlying [TypeParameterItem], if available; must only be called if [isTypeParameter] is
     * `true`.
     */
    val typeParameterItem: TypeParameterItem

    /**
     * The optional [ClassItem] that is a subclass of [java.lang.Throwable].
     *
     * When the underlying [classItem] is a [TypeParameterItem] this will return the erased type
     * class, if available, or `null` otherwise. When the underlying [classItem] is not a
     * [TypeParameterItem] then this will just return [classItem].
     */
    val throwableClass: ClassItem?

    /** A description of the `ThrowableType`, suitable for use in reports. */
    fun description(): String

    /** The full name of the underlying [classItem]. */
    fun fullName(): String

    /** The fully qualified name, will be the simple name of a [TypeParameterItem]. */
    fun qualifiedName(): String

    /** A wrapper of [ClassItem] that implements [ThrowableType]. */
    private class ThrowableClassItem(override val classItem: ClassItem) : ThrowableType {

        /* This is never a type parameter. */
        override val isTypeParameter
            get() = false

        override val typeParameterItem: TypeParameterItem
            get() = error("Cannot access `typeParameterItem` on $this")

        /** The [classItem] is a subclass of [java.lang.Throwable] */
        override val throwableClass: ClassItem
            get() = classItem

        override fun description() = qualifiedName()

        override fun fullName() = classItem.fullName()

        override fun qualifiedName() = classItem.qualifiedName()

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as ThrowableClassItem

            if (classItem != other.classItem) return false

            return true
        }

        override fun hashCode(): Int {
            return classItem.hashCode()
        }

        override fun toString() = classItem.toString()
    }

    /** A wrapper of [TypeParameterItem] that implements [ThrowableType]. */
    private class ThrowableTypeParameterItem(override val typeParameterItem: TypeParameterItem) :
        ThrowableType {

        /** This is always a type parameter. */
        override val isTypeParameter
            get() = true

        /** The [typeParameterItem] has no underlying [ClassItem]. */
        override val classItem: ClassItem
            get() = error("Cannot access classItem of $this")

        /** The [throwableClass] is the lower bounds of [typeParameterItem]. */
        override val throwableClass: ClassItem?
            get() = typeParameterItem.typeBounds().firstNotNullOfOrNull { it.asClass() }

        override fun description() =
            "${typeParameterItem.simpleName()} (extends ${throwableClass?.qualifiedName() ?: "unknown throwable"})}"

        /** A TypeParameterItem name is not prefixed by a containing class. */
        override fun fullName() = typeParameterItem.simpleName()

        /** A TypeParameterItem name is not qualified by the package. */
        override fun qualifiedName() = typeParameterItem.simpleName()

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as ThrowableTypeParameterItem

            if (typeParameterItem != other.typeParameterItem) return false

            return true
        }

        override fun hashCode(): Int {
            return typeParameterItem.hashCode()
        }

        override fun toString() = typeParameterItem.toString()
    }

    companion object {
        /** Get a [ThrowableType] wrapper around [ClassItem] */
        fun ofClass(classItem: ClassItem): ThrowableType =
            if (classItem is TypeParameterItem) error("Must not call this with a TypeParameterItem")
            else ThrowableClassItem(classItem)

        /** Get a [ThrowableType] wrapper around [TypeParameterItem] */
        fun ofTypeParameter(classItem: TypeParameterItem): ThrowableType =
            ThrowableTypeParameterItem(classItem)

        /** A partial ordering over [ThrowableType] comparing [ThrowableType.fullName]. */
        val fullNameComparator: Comparator<ThrowableType> = Comparator.comparing { it.fullName() }
    }
}
