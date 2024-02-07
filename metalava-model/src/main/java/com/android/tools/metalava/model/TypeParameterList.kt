/*
 * Copyright (C) 2018 The Android Open Source Project
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
 * Represents a type parameter list. For example, in class<S, T extends List<String>> the type
 * parameter list is <S, T extends List<String>> and has type parameters named S and T, and type
 * parameter T has bounds List<String>.
 */
interface TypeParameterList {
    /**
     * Returns source representation of this type parameter, using fully qualified names (possibly
     * with java.lang. removed if requested via options)
     */
    override fun toString(): String

    /** Returns the type parameters, if any */
    @MetalavaApi fun typeParameters(): List<TypeParameterItem>

    /** Returns the number of type parameters */
    fun typeParameterCount() = typeParameters().size

    companion object {
        /** Type parameter list when there are no type parameters */
        val NONE =
            object : TypeParameterList {
                override fun toString(): String = ""

                override fun typeParameters(): List<TypeParameterItem> = emptyList()

                override fun typeParameterCount(): Int = 0
            }
    }
}

abstract class DefaultTypeParameterList : TypeParameterList {
    private val toString by lazy {
        buildString {
            if (typeParameters().isNotEmpty()) {
                append("<")
                var first = true
                for (param in typeParameters()) {
                    if (!first) {
                        append(", ")
                    }
                    first = false
                    append(param.toSource())
                }
                append(">")
            }
        }
    }

    override fun toString(): String {
        return toString
    }

    companion object {
        /**
         * Create a [TypeParameterList] from model specific parameter and bounds information.
         *
         * @param inputParams a map from the model specific parameter [P] to the corresponding model
         *   specific bounds [B].
         * @param paramFactory a function that will create a [TypeParameterItem] from the model
         *   specified parameter [P] and then register, so it can be found by [boundsFactory].
         * @param boundsSetter a function that will create a list of [BoundTypeItem] from the model
         *   specific bounds and store it in [TypeParameterItem.typeBounds].
         * @param P the model specific type parameter type.
         * @param B the model specific bounds type.
         */
        fun <I : TypeParameterItem, P, B> createListOfTypeParameterItems(
            inputParams: Map<P, B>,
            paramFactory: (P, B) -> I,
            boundsSetter: (I, B) -> List<BoundsTypeItem>,
        ): List<TypeParameterItem> {

            // First, create a Map from [TypeParameterItem] to model specific bounds. Using the
            // [paramFactory] to convert the model specific parameter to a [TypeParameterItem].
            val typeParameterItemToBounds =
                inputParams.map { (param, bounds) -> paramFactory(param, bounds) to bounds }.toMap()

            // Secondly, create and set the bounds in the [TypeParameterItem].
            for ((typeParameter, bounds) in typeParameterItemToBounds) {
                val boundsTypeItem = boundsSetter(typeParameter, bounds)
                if (typeParameter.typeBounds() !== boundsTypeItem)
                    error("boundsSetter did not set bounds")
            }

            // Create a List<TypeParameterItem> from the keys.
            return typeParameterItemToBounds.keys.toList()
        }
    }
}
