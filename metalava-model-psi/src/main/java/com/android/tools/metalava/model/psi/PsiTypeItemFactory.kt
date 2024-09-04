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

package com.android.tools.metalava.model.psi

import com.android.tools.metalava.model.CallableItem
import com.android.tools.metalava.model.ClassItem
import com.android.tools.metalava.model.ClassTypeItem
import com.android.tools.metalava.model.PrimitiveTypeItem
import com.android.tools.metalava.model.ReferenceTypeItem
import com.android.tools.metalava.model.TypeArgumentTypeItem
import com.android.tools.metalava.model.TypeItem
import com.android.tools.metalava.model.TypeModifiers
import com.android.tools.metalava.model.TypeNullability
import com.android.tools.metalava.model.TypeParameterItem
import com.android.tools.metalava.model.TypeParameterScope
import com.android.tools.metalava.model.VariableTypeItem
import com.android.tools.metalava.model.WildcardTypeItem
import com.android.tools.metalava.model.type.ContextNullability
import com.android.tools.metalava.model.type.DefaultTypeItemFactory
import com.android.tools.metalava.model.type.DefaultTypeModifiers
import com.intellij.psi.PsiAnnotation
import com.intellij.psi.PsiArrayType
import com.intellij.psi.PsiClassType
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiEllipsisType
import com.intellij.psi.PsiNameHelper
import com.intellij.psi.PsiPrimitiveType
import com.intellij.psi.PsiType
import com.intellij.psi.PsiTypeParameter
import com.intellij.psi.PsiTypes
import com.intellij.psi.PsiWildcardType
import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.types.KaClassType
import org.jetbrains.kotlin.analysis.api.types.KaFunctionType
import org.jetbrains.kotlin.analysis.api.types.KaTypeMappingMode
import org.jetbrains.kotlin.utils.addToStdlib.ifNotEmpty
import org.jetbrains.uast.kotlin.isKotlin

/**
 * Encapsulates a [PsiType] and an optional context [PsiElement] for use with [PsiTypeItemFactory].
 */
data class PsiTypeInfo(val psiType: PsiType, val context: PsiElement? = null)

/**
 * Creates [PsiTypeItem]s from [PsiType]s and an optional context [PsiElement], encapsulated within
 * [PsiTypeInfo].
 */
internal class PsiTypeItemFactory(
    private val assembler: PsiCodebaseAssembler,
    typeParameterScope: TypeParameterScope
) : DefaultTypeItemFactory<PsiTypeInfo, PsiTypeItemFactory>(typeParameterScope) {

    private val codebase = assembler.codebase

    /** Construct a [PsiTypeItemFactory] suitable for creating types within [classItem]. */
    fun from(classItem: ClassItem): PsiTypeItemFactory {
        val scope = TypeParameterScope.from(classItem)
        return if (scope.isEmpty()) this else PsiTypeItemFactory(assembler, scope)
    }

    /** Construct a [PsiTypeItemFactory] suitable for creating types within [callableItem]. */
    fun from(callableItem: CallableItem): PsiTypeItemFactory {
        val scope = TypeParameterScope.from(callableItem)
        return if (scope.isEmpty()) this else PsiTypeItemFactory(assembler, scope)
    }

    override fun self() = this

    override fun createNestedFactory(scope: TypeParameterScope) =
        PsiTypeItemFactory(assembler, scope)

    override fun getType(
        underlyingType: PsiTypeInfo,
        contextNullability: ContextNullability,
        // The isVarArg is unused here as that information is encoded in the [PsiType] using the
        // [PsiEllipsisType] extension of [PsiArrayType].
        isVarArg: Boolean,
    ): PsiTypeItem {
        return getType(underlyingType.psiType, underlyingType.context, contextNullability)
    }

    /**
     * Returns a [PsiTypeItem] representing the [psiType]. The [context] is used to get nullability
     * information for Kotlin types.
     */
    internal fun getType(
        psiType: PsiType,
        context: PsiElement? = null,
        contextNullability: ContextNullability = ContextNullability.none,
    ): PsiTypeItem {
        val kotlinTypeInfo =
            if (context != null && isKotlin(context)) {
                KotlinTypeInfo.fromContext(context)
            } else {
                null
            }

        // Note: We do *not* cache these; it turns out that storing PsiType instances
        // in a map is bad for performance; it has a very expensive equals operation
        // for some type comparisons (and we sometimes end up with unexpected results,
        // e.g. where we fetch an "equals" type from the map but its representation
        // is slightly different to what was intended
        return createTypeItem(psiType, kotlinTypeInfo, contextNullability)
    }

    /** Get a [PsiClassTypeItem] to represent the [PsiClassItem]. */
    fun getClassTypeForClass(psiClassItem: PsiClassItem): PsiClassTypeItem {
        // Create a PsiType for the class. Specifies `PsiSubstitutor.EMPTY` so that if the class
        // has any type parameters then the PsiType will include references to those parameters.
        val psiTypeWithTypeParametersIfAny = assembler.getClassType(psiClassItem.psiClass)
        // Create a PsiTypeItemFactory that will correctly resolve any references to the class's
        // type parameters.
        val classTypeItemFactory = from(psiClassItem)
        return classTypeItemFactory.createTypeItem(
            psiTypeWithTypeParametersIfAny,
            KotlinTypeInfo.fromContext(psiClassItem.psiClass),
            contextNullability = ContextNullability.forceNonNull,
            creatingClassTypeForClass = true,
        ) as PsiClassTypeItem
    }

    /** Get a [VariableTypeItem] to represent [PsiTypeParameterItem]. */
    fun getVariableTypeForTypeParameter(
        psiTypeParameterItem: PsiTypeParameterItem
    ): VariableTypeItem {
        val psiTypeParameter = psiTypeParameterItem.psi()
        val psiType = assembler.getClassType(psiTypeParameter)
        return createVariableTypeItem(
            psiType,
            null,
            psiTypeParameterItem,
            ContextNullability.forceUndefined,
        )
    }

    // PsiTypeItem factory methods

    /** Creates modifiers based on the annotations of the [type]. */
    private fun createTypeModifiers(
        type: PsiType,
        kotlinType: KotlinTypeInfo?,
        contextNullability: ContextNullability,
    ): TypeModifiers {
        val typeAnnotations =
            type.annotations.mapNotNull { anno ->
                // SLC adds JetBrain nullness annotation on types.
                if (anno.isJetBrainNullnessAnnotation) null
                else PsiAnnotationItem.create(codebase, anno)
            }
        // Compute the nullability, factoring in any context nullability, kotlin types and
        // type annotations.
        val nullability = contextNullability.compute(kotlinType?.nullability(), typeAnnotations)
        return DefaultTypeModifiers.create(typeAnnotations, nullability)
    }

    private val PsiAnnotation.isJetBrainNotNull: Boolean
        get() {
            return qualifiedName == org.jetbrains.annotations.NotNull::class.qualifiedName
        }

    private val PsiAnnotation.isJetBrainNullable: Boolean
        get() {
            return qualifiedName == org.jetbrains.annotations.Nullable::class.qualifiedName
        }

    private val PsiAnnotation.isJetBrainNullnessAnnotation: Boolean
        get() {
            return isJetBrainNotNull || isJetBrainNullable
        }

    /** Create a [PsiTypeItem]. */
    private fun createTypeItem(
        psiType: PsiType,
        kotlinType: KotlinTypeInfo?,
        contextNullability: ContextNullability = ContextNullability.none,
        creatingClassTypeForClass: Boolean = false,
    ): PsiTypeItem {
        return when (psiType) {
            is PsiPrimitiveType ->
                createPrimitiveTypeItem(
                    psiType = psiType,
                    kotlinType = kotlinType,
                )
            is PsiArrayType ->
                createArrayTypeItem(
                    psiType = psiType,
                    kotlinType = kotlinType,
                    contextNullability = contextNullability,
                )
            is PsiClassType -> {
                val typeParameterItem =
                    when (val psiClass = psiType.resolve()) {
                        // If the type resolves to a PsiTypeParameter then the TypeParameterItem
                        // must exist.
                        is PsiTypeParameter -> {
                            val name = psiClass.simpleName
                            typeParameterScope.getTypeParameter(name)
                        }
                        // If the type could not be resolved then the TypeParameterItem might
                        // exist.
                        null ->
                            psiType.className?.let { name ->
                                typeParameterScope.findTypeParameter(name)
                            }
                        // Else it is not a TypeParameterItem.
                        else -> null
                    }

                if (typeParameterItem != null) {
                    // The type parameters of a class type for the class definition don't have
                    // defined nullability (their bounds might).
                    val correctedContextNullability =
                        if (creatingClassTypeForClass) {
                            ContextNullability.forceUndefined
                        } else {
                            contextNullability
                        }
                    createVariableTypeItem(
                        psiType = psiType,
                        kotlinType = kotlinType,
                        typeParameterItem = typeParameterItem,
                        contextNullability = correctedContextNullability,
                    )
                } else {
                    if (kotlinType?.kaType is KaFunctionType) {
                        createLambdaTypeItem(
                            psiType = psiType,
                            kotlinType = kotlinType,
                            contextNullability = contextNullability,
                        )
                    } else {
                        createClassTypeItem(
                            psiType = psiType,
                            kotlinType = kotlinType,
                            contextNullability = contextNullability,
                            creatingClassTypeForClass = creatingClassTypeForClass,
                        )
                    }
                }
            }
            is PsiWildcardType ->
                createWildcardTypeItem(
                    psiType = psiType,
                    kotlinType = kotlinType,
                )
            // There are other [PsiType]s, but none can appear in API surfaces.
            else ->
                throw IllegalStateException(
                    "Invalid type in API surface: $psiType${
                    if (kotlinType != null) {
                        " in file " + kotlinType.context.containingFile.name
                    } else ""
                }"
                )
        }
    }

    /** Create a [PsiPrimitiveTypeItem]. */
    private fun createPrimitiveTypeItem(
        psiType: PsiPrimitiveType,
        kotlinType: KotlinTypeInfo?,
    ) =
        PsiPrimitiveTypeItem(
            psiType = psiType,
            kind = getKind(psiType),
            modifiers = createTypeModifiers(psiType, kotlinType, ContextNullability.forceNonNull),
        )

    /** Get the [PrimitiveTypeItem.Primitive] enum from the [PsiPrimitiveType]. */
    private fun getKind(type: PsiPrimitiveType): PrimitiveTypeItem.Primitive {
        return when (type) {
            PsiTypes.booleanType() -> PrimitiveTypeItem.Primitive.BOOLEAN
            PsiTypes.byteType() -> PrimitiveTypeItem.Primitive.BYTE
            PsiTypes.charType() -> PrimitiveTypeItem.Primitive.CHAR
            PsiTypes.doubleType() -> PrimitiveTypeItem.Primitive.DOUBLE
            PsiTypes.floatType() -> PrimitiveTypeItem.Primitive.FLOAT
            PsiTypes.intType() -> PrimitiveTypeItem.Primitive.INT
            PsiTypes.longType() -> PrimitiveTypeItem.Primitive.LONG
            PsiTypes.shortType() -> PrimitiveTypeItem.Primitive.SHORT
            PsiTypes.voidType() -> PrimitiveTypeItem.Primitive.VOID
            else ->
                throw java.lang.IllegalStateException(
                    "Invalid primitive type in API surface: $type"
                )
        }
    }

    /** Create a [PsiArrayTypeItem]. */
    private fun createArrayTypeItem(
        psiType: PsiArrayType,
        kotlinType: KotlinTypeInfo?,
        contextNullability: ContextNullability,
    ) =
        PsiArrayTypeItem(
            psiType = psiType,
            componentType =
                createTypeItem(
                    psiType.componentType,
                    kotlinType?.forArrayComponentType(),
                    //  Pass in the [ContextNullability.forComponentType] just in case this is the
                    // return type of an annotation method, or in other words the type of an
                    // annotation attribute.
                    contextNullability.forComponentType(),
                ),
            isVarargs = psiType is PsiEllipsisType,
            modifiers = createTypeModifiers(psiType, kotlinType, contextNullability),
        )

    /** Create a [PsiClassTypeItem]. */
    private fun createClassTypeItem(
        psiType: PsiClassType,
        kotlinType: KotlinTypeInfo?,
        contextNullability: ContextNullability,
        creatingClassTypeForClass: Boolean = false,
    ): PsiClassTypeItem {
        val qualifiedName = psiType.computeQualifiedName()
        return PsiClassTypeItem(
            codebase = codebase,
            psiType = psiType,
            qualifiedName = qualifiedName,
            arguments =
                computeTypeArguments(
                    psiType,
                    kotlinType,
                    creatingClassTypeForClass,
                ),
            outerClassType =
                computeOuterClass(
                    psiType,
                    kotlinType,
                    creatingClassTypeForClass = true,
                ),
            // This should be able to use `psiType.name`, but that sometimes returns null.
            className = ClassTypeItem.computeClassName(qualifiedName),
            modifiers = createTypeModifiers(psiType, kotlinType, contextNullability),
        )
    }

    /** Compute the [PsiClassTypeItem.arguments]. */
    private fun computeTypeArguments(
        psiType: PsiClassType,
        kotlinType: KotlinTypeInfo?,
        creatingClassTypeForClass: Boolean = false,
    ): List<TypeArgumentTypeItem> {
        val psiParameters =
            psiType.parameters.toList().ifEmpty {
                // Sometimes, when a PsiClassType's arguments are empty it is not because there
                // are no arguments but due to a bug in Psi somewhere. Check to see if the
                // kotlin type info has a different set of type arguments and if it has then use
                // that to fix the type, otherwise just assume it should be empty.
                kotlinType?.kaType?.let { ktType ->
                    (ktType as? KaClassType)?.typeArguments?.ifNotEmpty {
                        fixUpPsiTypeMissingTypeArguments(psiType, kotlinType)
                    }
                }
                    ?: emptyList()
            }

        return psiParameters.mapIndexed { i, param ->
            val forTypeArgument = kotlinType?.forTypeArgument(i)
            createTypeItem(
                param,
                forTypeArgument,
                creatingClassTypeForClass = creatingClassTypeForClass
            )
                as TypeArgumentTypeItem
        }
    }

    /**
     * Fix up a [PsiClassType] that is missing type arguments.
     *
     * This seems to happen in a very limited situation. The example that currently fails, but there
     * may be more, appears to be due to an impedance mismatch between Kotlin collections and Java
     * collections.
     *
     * Assume the following Kotlin and Java classes from the standard libraries:
     * ```
     * package kotlin.collections
     * public interface MutableCollection<E> : Collection<E>, MutableIterable<E> {
     *     ...
     *     public fun addAll(elements: Collection<E>): Boolean
     *     public fun containsAll(elements: Collection<E>): Boolean
     *     public fun removeAll(elements: Collection<E>): Boolean
     *     public fun retainAll(elements: Collection<E>): Boolean
     *     ...
     * }
     *
     * package java.util;
     * public interface Collection<E> extends Iterable<E> {
     *     boolean addAll(Collection<? extends E> c);
     *     boolean containsAll(Collection<?> c);
     *     boolean removeAll(Collection<?> c);
     *     boolean retainAll(Collection<?> c);
     * }
     * ```
     *
     * The given the following class this function is called for the types of the parameters of the
     * `removeAll`, `retainAll` and `containsAll` methods but not for the `addAll` method.
     *
     * ```
     * abstract class Foo<Z> : MutableCollection<Z> {
     *     override fun addAll(elements: Collection<Z>): Boolean = true
     *     override fun containsAll(elements: Collection<Z>): Boolean = true
     *     override fun removeAll(elements: Collection<Z>): Boolean = true
     *     override fun retainAll(elements: Collection<Z>): Boolean = true
     * }
     * ```
     *
     * Metalava and/or the underlying Psi model, appears to treat the `MutableCollection` in `Foo`
     * as if it was a `java.util.Collection`, even though it is referring to
     * `kotlin.collections.Collection`. So, both `Foo` and `MutableCollection` are reported as
     * extending `java.util.Collection`.
     *
     * So, you have the following two methods (mapped into Java classes):
     *
     * From `java.util.Collection` itself:
     * ```
     *      boolean containsAll(java.util.Collection<?> c);
     * ```
     *
     * And from `kotlin.collections.MutableCollection`:
     * ```
     *     public fun containsAll(elements: java.util.Collection<E>): Boolean
     * ```
     *
     * But, strictly speaking that is not allowed for a couple of reasons:
     * 1. `java.util.Collection` is not covariant because it is mutable. However,
     *    `kotlin.collections.Collection` is covariant because it immutable.
     * 2. `Collection<Z>` is more restrictive than `Collection<?>`. Java will let you try and remove
     *    a collection of `Number` from a collection of `String` even though it is meaningless.
     *    Kotlin's approach is more correct but only possible because its `Collection` is immutable.
     *
     * The [kotlinType] seems to have handled that issue reasonably well producing a type of
     * `java.util.Collection<? extends Z>`. Unfortunately, when that is converted to a `PsiType` the
     * `PsiType` for `Z` does not resolve to a `PsiTypeParameter`.
     *
     * The wildcard is correct.
     */
    @OptIn(KaExperimentalApi::class)
    private fun fixUpPsiTypeMissingTypeArguments(
        psiType: PsiClassType,
        kotlinType: KotlinTypeInfo
    ): List<PsiType> {
        if (kotlinType.analysisSession == null || kotlinType.kaType == null) return emptyList()

        val kaType = kotlinType.kaType as KaClassType

        // Restrict this fix to the known issue.
        val className = psiType.className
        if (className != "Collection") {
            return emptyList()
        }

        // Convert the KtType to PsiType.
        //
        // Convert the whole type rather than extracting the type parameters and converting them
        // separately because the result depends on the parameterized class, i.e.
        // `java.util.Collection` in this case. Also, type arguments can be wildcards but
        // wildcards cannot exist on their own. It will probably be relying on undefined
        // behavior to try and convert a wildcard on their own.
        val psiTypeFromKotlin =
            kotlinType.analysisSession.run {
                // Use the default mode so that the resulting psiType is
                // `java.util.Collection<? extends Z>`.
                val mode = KaTypeMappingMode.DEFAULT
                kaType.asPsiType(kotlinType.context, false, mode = mode)
            } as? PsiClassType
        return psiTypeFromKotlin?.parameters?.toList() ?: emptyList()
    }

    /** Compute the [PsiClassTypeItem.outerClassType]. */
    private fun computeOuterClass(
        psiType: PsiClassType,
        kotlinType: KotlinTypeInfo?,
        creatingClassTypeForClass: Boolean = false,
    ): PsiClassTypeItem? {
        // TODO(b/300081840): this drops annotations on the outer class
        return PsiNameHelper.getOuterClassReference(psiType.canonicalText).let { outerClassName ->
            // [PsiNameHelper.getOuterClassReference] returns an empty string if there is no
            // outer class reference. If the type is not a nested type, it returns the package
            // name (e.g. for "java.lang.String" it returns "java.lang").
            if (outerClassName == "" || assembler.findPsiPackage(outerClassName) != null) {
                null
            } else {
                val psiOuterClassType =
                    assembler.createPsiType(
                        outerClassName,
                        // The context psi element allows variable types to be resolved (with no
                        // context, they would be interpreted as class types). The [psiContext]
                        // works in most cases, but is null when creating a type directly from a
                        // class declaration, so the resolved [psiType] provides context then.
                        psiType.psiContext ?: psiType.resolve()
                    )
                createTypeItem(
                    psiOuterClassType,
                    kotlinType?.forOuterClass(),
                    // An outer class reference can't be null.
                    contextNullability = ContextNullability.forceNonNull,
                    creatingClassTypeForClass = creatingClassTypeForClass,
                )
                    as PsiClassTypeItem
            }
        }
    }

    /** Support mapping from boxed types back to their primitive type. */
    private val boxedToPsiPrimitiveType =
        mapOf(
            "java.lang.Byte" to PsiTypes.byteType(),
            "java.lang.Double" to PsiTypes.doubleType(),
            "java.lang.Float" to PsiTypes.floatType(),
            "java.lang.Integer" to PsiTypes.intType(),
            "java.lang.Long" to PsiTypes.longType(),
            "java.lang.Short" to PsiTypes.shortType(),
            "java.lang.Boolean" to PsiTypes.booleanType(),
            // This is not strictly speaking a boxed -> unboxed mapping, but it fits in nicely
            // with the others.
            "kotlin.Unit" to PsiTypes.voidType(),
        )

    /** If the type item is not nullable and is a boxed type then map it to the unboxed type. */
    private fun unboxTypeWherePossible(typeItem: TypeItem): TypeItem {
        if (
            typeItem is ClassTypeItem && typeItem.modifiers.nullability == TypeNullability.NONNULL
        ) {
            boxedToPsiPrimitiveType[typeItem.qualifiedName]?.let { psiPrimitiveType ->
                return createPrimitiveTypeItem(psiPrimitiveType, null)
            }
        }
        return typeItem
    }

    /** An input parameter of type X is represented as a "? super X" in the `Function<X>` class. */
    private fun unwrapInputType(typeItem: TypeItem): TypeItem {
        return unboxTypeWherePossible((typeItem as? WildcardTypeItem)?.superBound ?: typeItem)
    }

    /**
     * The return type of type X can be represented as a "? extends X" in the `Function<X>` class.
     */
    private fun unwrapOutputType(typeItem: TypeItem): TypeItem {
        return unboxTypeWherePossible((typeItem as? WildcardTypeItem)?.extendsBound ?: typeItem)
    }

    /**
     * Create a [PsiLambdaTypeItem].
     *
     * Extends a [PsiClassTypeItem] and then deconstructs the type arguments of Kotlin `Function<N>`
     * to extract the receiver, input and output types. This makes heavy use of the
     * [KotlinTypeInfo.kaType] property of [kotlinType] which must be a [KtFunctionalType]. That has
     * the information necessary to determine which of the Kotlin `Function<N>` class's type
     * arguments are the receiver (if any) and which are input parameters. The last type argument is
     * always the return type.
     */
    private fun createLambdaTypeItem(
        psiType: PsiClassType,
        kotlinType: KotlinTypeInfo,
        contextNullability: ContextNullability,
    ): PsiLambdaTypeItem {
        val qualifiedName = psiType.computeQualifiedName()

        val kaType = kotlinType.kaType as KaFunctionType

        val isSuspend = kaType.isSuspend

        val actualKotlinType =
            kotlinType.copy(
                overrideTypeArguments =
                    // Compute a set of [KtType]s corresponding to the type arguments in the
                    // underlying `kotlin.jvm.functions.Function*`.
                    buildList {
                        // The optional lambda receiver is the first type argument.
                        kaType.receiverType?.let { add(kotlinType.copy(kaType = it)) }
                        // The lambda's explicit parameters appear next.
                        kaType.parameterTypes.mapTo(this) { kotlinType.copy(kaType = it) }
                        // A `suspend` lambda is transformed by Kotlin in the same way that a
                        // `suspend` function is, i.e. an additional continuation parameter is added
                        // at the end of the explicit parameters that encapsulates the return type
                        // and the return type is changed to `Any?`.
                        if (isSuspend) {
                            // Create a KotlinTypeInfo for the continuation parameter that
                            // encapsulates the actual return type.
                            add(kotlinType.forSyntheticContinuationParameter(kaType.returnType))
                            // Add the `Any?` for the return type.
                            add(kotlinType.nullableAny())
                        } else {
                            // As it is not a `suspend` lambda add the return type last.
                            add(kotlinType.copy(kaType = kaType.returnType))
                        }
                    }
            )

        // Get the type arguments for the kotlin.jvm.functions.Function<X> class.
        val typeArguments = computeTypeArguments(psiType, actualKotlinType)

        // If the function has a receiver then it is the first type argument.
        var firstParameterTypeIndex = 0
        val receiverType =
            if (kaType.hasReceiver) {
                // The first parameter type is now the second type argument.
                firstParameterTypeIndex = 1
                unwrapInputType(typeArguments[0])
            } else {
                null
            }

        // The last type argument is always the return type.
        val returnType = unwrapOutputType(typeArguments.last())
        val lastParameterTypeIndex = typeArguments.size - 1

        // Get the parameter types, excluding the optional receiver and the return type.
        val parameterTypes =
            typeArguments
                .subList(firstParameterTypeIndex, lastParameterTypeIndex)
                .map { unwrapInputType(it) }
                .toList()

        return PsiLambdaTypeItem(
            codebase = codebase,
            psiType = psiType,
            qualifiedName = qualifiedName,
            arguments = typeArguments,
            outerClassType = computeOuterClass(psiType, actualKotlinType),
            // This should be able to use `psiType.name`, but that sometimes returns null.
            className = ClassTypeItem.computeClassName(qualifiedName),
            modifiers = createTypeModifiers(psiType, actualKotlinType, contextNullability),
            isSuspend = isSuspend,
            receiverType = receiverType,
            parameterTypes = parameterTypes,
            returnType = returnType,
        )
    }

    /** Create a [PsiVariableTypeItem]. */
    private fun createVariableTypeItem(
        psiType: PsiClassType,
        kotlinType: KotlinTypeInfo?,
        typeParameterItem: TypeParameterItem,
        contextNullability: ContextNullability,
    ) =
        PsiVariableTypeItem(
            psiType = psiType,
            modifiers = createTypeModifiers(psiType, kotlinType, contextNullability),
            asTypeParameter = typeParameterItem,
        )

    /** Create a [PsiWildcardTypeItem]. */
    private fun createWildcardTypeItem(
        psiType: PsiWildcardType,
        kotlinType: KotlinTypeInfo?,
    ) =
        PsiWildcardTypeItem(
            psiType = psiType,
            extendsBound =
                createBound(
                    psiType.extendsBound,
                    // The kotlinType only applies to an explicit bound, not an implicit bound, so
                    // only pass it through if this has an explicit `extends` bound.
                    kotlinType.takeIf { psiType.isExtends },
                    // If this is a Kotlin wildcard type with an implicit Object extends bound, the
                    // Object bound should be nullable, not platform nullness like in Java.
                    contextNullability =
                        if (kotlinType != null && !psiType.isExtends) {
                            ContextNullability(TypeNullability.NULLABLE)
                        } else {
                            ContextNullability.none
                        }
                ),
            superBound =
                createBound(
                    psiType.superBound,
                    // The kotlinType only applies to an explicit bound, not an implicit bound, so
                    // only pass it through if this has an explicit `super` bound.
                    kotlinType.takeIf { psiType.isSuper },
                ),
            modifiers = createTypeModifiers(psiType, kotlinType, ContextNullability.forceUndefined),
        )

    /**
     * Create a [PsiWildcardTypeItem.extendsBound] or [PsiWildcardTypeItem.superBound].
     *
     * If a [PsiWildcardType] doesn't have a bound, the bound is represented as the null [PsiType]
     * instead of just `null`.
     */
    private fun createBound(
        bound: PsiType,
        kotlinType: KotlinTypeInfo?,
        contextNullability: ContextNullability = ContextNullability.none
    ): ReferenceTypeItem? {
        return if (bound == PsiTypes.nullType()) {
            null
        } else {
            // Use the same Kotlin type, because the wildcard isn't its own level in the KtType.
            createTypeItem(bound, kotlinType, contextNullability) as ReferenceTypeItem
        }
    }
}

/** Compute the qualified name for a [PsiClassType].. */
internal fun PsiClassType.computeQualifiedName(): String {
    // It should be possible to do `psiType.rawType().canonicalText` instead, but this does not
    // always work if psi is unable to resolve the reference.
    // See https://youtrack.jetbrains.com/issue/KTIJ-27093 for more details.
    return PsiNameHelper.getQualifiedClassName(canonicalText, true)
}
