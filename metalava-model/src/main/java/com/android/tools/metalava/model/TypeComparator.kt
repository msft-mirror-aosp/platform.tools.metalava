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

package com.android.tools.metalava.model

/** Compares [TypeItem] instances for equality according to specific criteria. */
sealed interface TypeComparator {
    /** Compare [type1] and [type2] for equality. */
    fun compare(type1: TypeItem?, type2: TypeItem?): Boolean

    /** Compute a hash code for [type] that is consistent with [compare]. */
    fun hash(type: TypeItem?): Int

    /** Provides the basic implementation of [TypeComparator] that others extend. */
    sealed class Base : TypeComparator {
        override fun compare(type1: TypeItem?, type2: TypeItem?): Boolean {
            if (type1 === type2) return true
            if (type1 == null || type2 == null) return false

            if (!compareModifiers(type1.modifiers, type2.modifiers)) return false

            return compareStructure(type1, type2)
        }

        override fun hash(type: TypeItem?): Int {
            if (type == null) return 0

            var result = hashStructure(type)
            result = 31 * result + hashModifiers(type.modifiers)
            return result
        }

        /**
         * Compare [modifiers1] and [modifiers2].
         *
         * The default compares both nullability and type-use annotations, matching the most
         * complete check across subclasses ([IDENTICAL] and [STRICT]). Subclasses that ignore
         * annotations or nullability override this method.
         */
        protected open fun compareModifiers(
            modifiers1: TypeModifiers,
            modifiers2: TypeModifiers,
        ): Boolean {
            return modifiers1.nullability == modifiers2.nullability &&
                modifiers1.annotations == modifiers2.annotations
        }

        /**
         * Hash [modifiers].
         *
         * The default hashes both nullability and type-use annotations, matching the most complete
         * check across subclasses ([IDENTICAL] and [STRICT]). Subclasses that ignore annotations or
         * nullability override this method.
         */
        protected open fun hashModifiers(modifiers: TypeModifiers): Int {
            var result = modifiers.nullability.hashCode()
            result = 31 * result + modifiers.annotations.hashCode()
            return result
        }

        /**
         * Compare [param1] and [param2].
         *
         * The default compares type parameters by equality ([param1] == [param2]), matching most
         * subclasses ([STRICT], [NULLABILITY_AWARE], [IGNORE_NULLABILITY], [ERASED], and
         * [FLATTENED_WILDCARDS]). Only [IDENTICAL] overrides this to compare by identity.
         */
        protected open fun compareTypeParameters(
            param1: TypeParameterItem,
            param2: TypeParameterItem,
        ): Boolean = param1 == param2

        /**
         * Hash [param].
         *
         * The default hashes type parameters by equality ([param].hashCode()), matching most
         * subclasses ([STRICT], [NULLABILITY_AWARE], [IGNORE_NULLABILITY], [ERASED], and
         * [FLATTENED_WILDCARDS]). Only [IDENTICAL] overrides this to hash by identity.
         */
        protected open fun hashTypeParameter(param: TypeParameterItem): Int = param.hashCode()

        /** Compare the structural elements of [type1] and [type2]. */
        private fun compareStructure(type1: TypeItem, type2: TypeItem): Boolean {
            return when (type1) {
                is PrimitiveTypeItem -> {
                    type2 is PrimitiveTypeItem && type1.kind == type2.kind
                }
                is ArrayTypeItem -> {
                    type2 is ArrayTypeItem &&
                        type1.isVarargs == type2.isVarargs &&
                        compare(type1.componentType, type2.componentType)
                }
                is ClassTypeItem -> {
                    type2 is ClassTypeItem &&
                        type1.qualifiedName == type2.qualifiedName &&
                        type1.arguments.size == type2.arguments.size &&
                        type1.arguments.zip(type2.arguments).all { (a1, a2) -> compare(a1, a2) } &&
                        compare(type1.outerClassType, type2.outerClassType)
                }
                is VariableTypeItem -> {
                    type2 is VariableTypeItem &&
                        compareTypeParameters(type1.asTypeParameter, type2.asTypeParameter)
                }
                is WildcardTypeItem -> {
                    type2 is WildcardTypeItem &&
                        compare(type1.extendsBound, type2.extendsBound) &&
                        compare(type1.superBound, type2.superBound)
                }
                else -> false
            }
        }

        /** Hash the structural elements of [type]. */
        private fun hashStructure(type: TypeItem): Int {
            return when (type) {
                is PrimitiveTypeItem -> type.kind.hashCode()
                is ArrayTypeItem -> {
                    var result = type.isVarargs.hashCode()
                    result = 31 * result + hash(type.componentType)
                    result
                }
                is ClassTypeItem -> {
                    var result = type.qualifiedName.hashCode()
                    result = 31 * result + hash(type.outerClassType)
                    result =
                        31 * result + type.arguments.fold(1) { acc, arg -> 31 * acc + hash(arg) }
                    result
                }
                is VariableTypeItem -> hashTypeParameter(type.asTypeParameter)
                is WildcardTypeItem -> {
                    var result = hash(type.extendsBound)
                    result = 31 * result + hash(type.superBound)
                    result
                }
                else -> 0
            }
        }
    }

    /**
     * [TypeComparator] that compares structure (including type parameter identity), nullability,
     * and type-use annotations.
     */
    data object IDENTICAL : Base() {
        override fun compareTypeParameters(
            param1: TypeParameterItem,
            param2: TypeParameterItem,
        ): Boolean = param1 === param2

        override fun hashTypeParameter(param: TypeParameterItem): Int =
            System.identityHashCode(param)
    }

    /**
     * [TypeComparator] that compares structure (including type parameter equality), nullability,
     * and type-use annotations.
     */
    data object STRICT : Base()
}

/** Compare this [TypeItem] to [other] using [comparator]. */
fun TypeItem?.equalTo(
    other: TypeItem?,
    comparator: TypeComparator = TypeComparator.IDENTICAL,
): Boolean = comparator.compare(this, other)
