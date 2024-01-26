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
typealias ThrowableType = ClassItem

/** The [ClassItem]; must only be called if [ClassItem.isTypeParameter] is `false`. */
val ThrowableType.classItem: ClassItem
    get() = if (this is TypeParameterItem) error("Cannot access classItem of $this") else this

/** The [TypeParameterItem]; must only be called if [ClassItem.isTypeParameter] is `true`. */
val ThrowableType.typeParameterItem: TypeParameterItem
    get() =
        if (this is TypeParameterItem) this else error("Cannot access `typeParameterItem` on $this")

/**
 * The optional [ClassItem] that is a subclass of [java.lang.Throwable].
 *
 * When `this` is a [TypeParameterItem] this will just return the erased type class, if available,
 * or `null` otherwise. When `this` is not a [TypeParameterItem] then this will just return `this`.
 */
val ThrowableType.throwableClass: ClassItem?
    get() =
        if (this is TypeParameterItem) typeBounds().firstNotNullOfOrNull { it.asClass() } else this

/** A description of the `ThrowableType`, suitable for use in reports. */
fun ThrowableType.description() =
    if (this is TypeParameterItem)
        "${simpleName()} (extends ${throwableClass?.qualifiedName() ?: "unknown throwable"})}"
    else qualifiedName()

/** Get a [ThrowableType] from a [ClassItem] */
fun ClassItem.Companion.ofClass(classItem: ClassItem): ThrowableType =
    if (classItem is TypeParameterItem) error("Must not call this with a TypeParameterItem")
    else classItem

/** Get a [ThrowableType] from a [TypeParameterItem] */
fun ClassItem.Companion.ofTypeParameter(typeParameterItem: TypeParameterItem): ThrowableType =
    typeParameterItem
