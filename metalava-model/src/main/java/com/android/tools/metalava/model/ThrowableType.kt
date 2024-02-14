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
sealed interface ThrowableType {
    /**
     * The underlying [TypeParameterItem], if available; must only be called if `this` is a
     * [VariableTypeItem].
     */
    val typeParameterItem: TypeParameterItem

    val erasedClass: ClassItem?

    /** A description of the `ThrowableType`, suitable for use in reports. */
    fun description(): String

    /** The full name of the underlying class. */
    fun fullName(): String

    fun toTypeString(): String

    /** A wrapper of [ExceptionTypeItem] that implements [ThrowableType]. */
    private abstract class ThrowableExceptionTypeItem(val exceptionTypeItem: ExceptionTypeItem) :
        ThrowableType {

        private val fullName =
            when (exceptionTypeItem) {
                is ClassTypeItem -> bestGuessAtFullName(exceptionTypeItem.qualifiedName)
                is VariableTypeItem -> exceptionTypeItem.name
            }

        override val typeParameterItem: TypeParameterItem
            get() =
                (exceptionTypeItem as? VariableTypeItem)?.asTypeParameter
                    ?: error("Cannot access `typeParameterItem` on $this")

        override fun toTypeString() = exceptionTypeItem.toTypeString()

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as ThrowableExceptionTypeItem

            if (exceptionTypeItem != other.exceptionTypeItem) return false

            return true
        }

        override fun hashCode(): Int {
            return exceptionTypeItem.hashCode()
        }

        override fun toString() = exceptionTypeItem.toString()
    }

    /**
     * Temporarily extend [ThrowableExceptionTypeItem] to implement [ClassTypeItem] by delegation to
     * aid in replacing [ThrowableType] with [ExceptionTypeItem].
     */
    private class ThrowableClassTypeItem(classTypeItem: ClassTypeItem) :
        ThrowableExceptionTypeItem(classTypeItem), ClassTypeItem by classTypeItem

    /**
     * Temporarily extend [ThrowableExceptionTypeItem] to implement [VariableTypeItem] by delegation
     * to aid in replacing [ThrowableType] with [ExceptionTypeItem].
     */
    private class ThrowableVariableTypeItem(variableTypeItem: VariableTypeItem) :
        ThrowableExceptionTypeItem(variableTypeItem), VariableTypeItem by variableTypeItem

    companion object {
        /** Get a [ThrowableType] wrapper around an [ExceptionTypeItem] */
        fun ofExceptionType(exceptionType: ExceptionTypeItem): ThrowableType =
            when (exceptionType) {
                is ClassTypeItem -> ThrowableClassTypeItem(exceptionType)
                is VariableTypeItem -> ThrowableVariableTypeItem(exceptionType)
            }

        /** A partial ordering over [ThrowableType] comparing [ThrowableType.fullName]. */
        val fullNameComparator: Comparator<ThrowableType> = Comparator.comparing { it.fullName() }
    }
}
