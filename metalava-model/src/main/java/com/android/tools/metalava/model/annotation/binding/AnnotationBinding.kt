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

package com.android.tools.metalava.model.annotation.binding

import com.android.tools.metalava.model.AnnotationItem
import com.android.tools.metalava.model.Item
import com.android.tools.metalava.model.annotation.AnnotationDefaults
import com.android.tools.metalava.model.value.Value
import com.android.tools.metalava.model.value.asBoolean
import com.android.tools.metalava.model.value.asInt
import com.android.tools.metalava.model.value.asString
import com.android.tools.metalava.reporter.Issues
import kotlin.reflect.KClass
import kotlin.reflect.KFunction
import kotlin.reflect.KParameter
import kotlin.reflect.KType

/**
 * Binder that can create an instance of [kClass] from an [AnnotationItem] that uses the
 * [annotationDefaults].
 */
internal class AnnotationBinding<T : Any>(
    private val kClass: KClass<T>,
    private val annotationDefaults: AnnotationDefaults?,
) : AnnotationBindingFactory<T> {
    /**
     * The [KFunction] representing the constructor that will be used to create the instance of
     * [kClass].
     */
    private val bindingConstructor = kClass.selectBindingConstructor()

    /**
     * Map from attribute name to the [ParameterBinder] that will bind that attributes values to a
     * parameter in [bindingConstructor].
     */
    private val bindersByName =
        bindingConstructor.parameters
            .mapNotNull { parameter ->
                val name =
                    parameter.name
                        ?: error(
                            "internal error: ${kClass.qualifiedName}: Cannot get name for parameter ${parameter.index}"
                        )
                val type = parameter.type

                val nullableConverter = converterForType(type) ?: return@mapNotNull null

                ParameterBinder(
                    name,
                    parameter,
                    nullableConverter,
                )
            }
            .associateBy { it.name }

    override fun createInstanceFrom(
        annotationItem: AnnotationItem,
        item: Item,
    ): T? {
        val context = ConverterContext(annotationItem, item)

        // Construct a map from KParameter to the value of the parameter's type to pass for that
        // parameter to the bindingConstructor.
        val argsByName = buildMap {
            // Iterate over the annotation attributes, converting their values to the appropriate
            // type for the parameter.
            for (attribute in annotationItem.attributes) {
                val name = attribute.name
                val binder = bindersByName[name] ?: continue
                val value = attribute.value
                binder.bindToValue(context, this, value)
            }
        }

        // If an error was encountered during binding then return `null`.
        if (context.doNotInstantiate) {
            return null
        }

        // Construct an instance passing in parameters by name. While great care is taken to avoid
        // it this could throw an exception if there is some inconsistency between the annotation
        // definition/use and the binding constructor parameters. If the exception could only be
        // caused by errors within Metalava then it would be acceptable to have it abort processing
        // but this could possibly be caused by changes to sources too. Aborting processing in that
        // case would not be helpful so this catches it and reports it, returning `null` in the
        // process.
        return try {
            bindingConstructor.callBy(argsByName)
        } catch (e: Exception) {
            item.codebase.reporter.report(
                Issues.INVALID_ANNOTATION_BINDING,
                item,
                "internal error: could not bind ${kClass.qualifiedName} to ${annotationItem.qualifiedName}: ${e.message}",
                location = annotationItem.fileLocation
            )
            null
        }
    }

    /** Binds a specific [parameter] to the value of an attribute [name]. */
    private class ParameterBinder(
        /** The parameter name, provided here as [KParameter.name] is nullable. */
        val name: String,

        /** The [KParameter] to bound. */
        private val parameter: KParameter,

        /** The [ValueConverter] that may produce a `null` value. */
        private val nullableConverter: ValueConverter<*>,
    ) {
        /** Convert this [Value] within [context] to a value appropriate for [parameter]. */
        private fun Value.convert(context: ConverterContext) = context.nullableConverter(this)

        /**
         * Bind this [parameter] in [map] to the result of converting [value] to the parameter's
         * type using [context].
         */
        fun bindToValue(
            context: ConverterContext,
            map: MutableMap<KParameter, Any?>,
            value: Value
        ) {
            val any = value.convert(context)
            map[parameter] = any
            if (any == null) {
                context.reportInvalidAttributeValue(name, value, parameter.type)
            }
        }
    }

    companion object {
        /**
         * Select the binding constructor to use on this [KClass].
         *
         * If [KClass] only has a single constructor that is returned. Otherwise, the constructor
         * that is annotated with [BindingConstructor] is returned.
         */
        private fun <T : Any> KClass<T>.selectBindingConstructor(): KFunction<T> {
            val constructors = constructors
            return when (constructors.size) {
                0 ->
                    error(
                        "Cannot create an instance of ${this.qualifiedName} as it has no constructors"
                    )
                1 -> constructors.single()
                else -> {
                    val annotated =
                        constructors.filter { constructor ->
                            constructor.annotations.any { annotation ->
                                annotation is BindingConstructor
                            }
                        }
                    when (annotated.size) {
                        0 ->
                            error(
                                "Found multiple constructors in ${this.qualifiedName}; please annotate one with @BindingConstructor"
                            )
                        1 -> annotated.single()
                        else ->
                            error(
                                "Found multiple constructors in ${this.qualifiedName} that are annotated with @BindingConstructor, please annotate only one"
                            )
                    }
                }
            }
        }

        /** Create a [ValueConverter] to type [T], using [conversion]. */
        private inline fun <reified T : Any> MutableMap<KClass<*>, ValueConverter<*>>
            .registerValueConverter(
            noinline conversion: ValueConverter<T>,
        ) = conversion.also { put(T::class, it) }

        /** Map from [KClass] to the [ValueConverter]. */
        private val valueConvertersByKClass = buildMap {
            // Converter from a [Value] to a [Boolean].
            registerValueConverter { value -> value.asBoolean() }

            // Converter from a [Value] to an [Int].
            registerValueConverter { value -> value.asInt() }

            // Converter from a [Value] to a [String].
            registerValueConverter { value -> value.asString() }
        }

        /** Get the [ValueConverter] to use for converting [Value]s to [type]. */
        private fun converterForType(type: KType): (ValueConverter<*>)? {
            val classifier = type.classifier
            val kClass = classifier as? KClass<*> ?: return null

            // Try one of the built-in conversions first.
            valueConvertersByKClass[kClass]?.let {
                return it
            }

            return null
        }
    }
}

/** Lambda for converting [Value] to a value of type [T] using the [ConverterContext]. */
internal typealias ValueConverter<T> = ConverterContext.(Value) -> T?

/** Contextual information provided to [ValueConverter]s. */
internal class ConverterContext(
    /** The [AnnotationItem] being converted, may be nested. */
    val annotation: AnnotationItem,

    /** The [Item] to which the [annotation] belongs. */
    val item: Item,
) {
    /** Tracks whether an error was encountered which would prevent instantiation. */
    var doNotInstantiate = false
        private set

    /** Report that attribute [name]'s [value] cannot be converted to [type]. */
    fun reportInvalidAttributeValue(name: String, value: Value, type: KType) {
        item.codebase.reporter.report(
            Issues.INVALID_ANNOTATION_BINDING,
            item,
            "Attribute '$name' is invalid: `${value.toValueString()}` cannot be converted to $type",
            location = annotation.fileLocation
        )

        // There was some inconsistency encountered between the source annotation instance and the
        // bound class so do not instantiate an instance.
        doNotInstantiate = true
    }
}
