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

import com.android.tools.metalava.model.BoundsTypeItem
import com.android.tools.metalava.model.ClassItem
import com.android.tools.metalava.model.ClassTypeItem
import com.android.tools.metalava.model.ExceptionTypeItem
import com.android.tools.metalava.model.MethodItem
import com.android.tools.metalava.model.PrimitiveTypeItem
import com.android.tools.metalava.model.ReferenceTypeItem
import com.android.tools.metalava.model.TypeArgumentTypeItem
import com.android.tools.metalava.model.TypeModifiers
import com.android.tools.metalava.model.TypeNullability
import com.android.tools.metalava.model.TypeParameterItem
import com.android.tools.metalava.model.TypeParameterScope
import com.android.tools.metalava.model.TypeUse
import com.android.tools.metalava.model.VariableTypeItem
import com.android.tools.metalava.model.type.DefaultTypeModifiers
import com.android.tools.metalava.model.type.TypeItemFactory
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
import org.jetbrains.kotlin.analysis.api.types.KtNonErrorClassType
import org.jetbrains.kotlin.analysis.api.types.KtTypeMappingMode
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
    val codebase: PsiBasedCodebase,
    override val typeParameterScope: TypeParameterScope
) : TypeItemFactory<PsiTypeInfo, PsiTypeItemFactory> {

    /** Construct a [PsiTypeItemFactory] suitable for creating types within [classItem]. */
    fun from(classItem: ClassItem): PsiTypeItemFactory {
        val scope = TypeParameterScope.from(classItem)
        return if (scope.isEmpty()) this else PsiTypeItemFactory(codebase, scope)
    }

    /** Construct a [PsiTypeItemFactory] suitable for creating types within [methodItem]. */
    fun from(methodItem: MethodItem): PsiTypeItemFactory {
        val scope = TypeParameterScope.from(methodItem)
        return if (scope.isEmpty()) this else PsiTypeItemFactory(codebase, scope)
    }

    override fun nestedFactory(
        scopeDescription: String,
        typeParameters: List<TypeParameterItem>
    ): PsiTypeItemFactory {
        val scope = typeParameterScope.nestedScope(scopeDescription, typeParameters)
        return if (scope === typeParameterScope) this else PsiTypeItemFactory(codebase, scope)
    }

    override fun getBoundsType(underlyingType: PsiTypeInfo) =
        getType(underlyingType) as BoundsTypeItem

    override fun getExceptionType(underlyingType: PsiTypeInfo) =
        getType(underlyingType) as ExceptionTypeItem

    override fun getGeneralType(underlyingType: PsiTypeInfo) = getType(underlyingType)

    override fun getInterfaceType(underlyingType: PsiTypeInfo) =
        getSuperType(underlyingType.psiType)

    override fun getSuperClassType(underlyingType: PsiTypeInfo) =
        getSuperType(underlyingType.psiType)

    /**
     * Creates a [PsiClassTypeItem] that is suitable for use as a super type, e.g. in an `extends`
     * or `implements` list.
     */
    private fun getSuperType(psiType: PsiType): PsiClassTypeItem {
        return getType(psiType, typeUse = TypeUse.SUPER_TYPE) as PsiClassTypeItem
    }

    private fun getType(psiTypeInfo: PsiTypeInfo, typeUse: TypeUse = TypeUse.GENERAL): PsiTypeItem {
        return getType(psiTypeInfo.psiType, psiTypeInfo.context, typeUse)
    }

    /**
     * Returns a [PsiTypeItem] representing the [psiType]. The [context] is used to get nullability
     * information for Kotlin types.
     */
    internal fun getType(
        psiType: PsiType,
        context: PsiElement? = null,
        typeUse: TypeUse = TypeUse.GENERAL
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
        return createTypeItem(psiType, kotlinTypeInfo, typeUse)
    }

    /** Get a [PsiClassTypeItem] to represent the [PsiClassItem]. */
    fun getClassTypeForClass(psiClassItem: PsiClassItem): PsiClassTypeItem {
        // Create a PsiType for the class. Specifies `PsiSubstitutor.EMPTY` so that if the class
        // has any type parameters then the PsiType will include references to those parameters.
        val psiTypeWithTypeParametersIfAny = codebase.getClassType(psiClassItem.psiClass)
        // Create a PsiTypeItemFactory that will correctly resolve any references to the class's
        // type parameters.
        val classTypeItemFactory = from(psiClassItem)
        return classTypeItemFactory.createTypeItem(
            psiTypeWithTypeParametersIfAny,
            KotlinTypeInfo.fromContext(psiClassItem.psiClass),
        ) as PsiClassTypeItem
    }

    /** Get a [VariableTypeItem] to represent [PsiTypeParameterItem]. */
    fun getVariableTypeForTypeParameter(
        psiTypeParameterItem: PsiTypeParameterItem
    ): VariableTypeItem {
        val psiTypeParameter = psiTypeParameterItem.psi()
        val psiType = codebase.getClassType(psiTypeParameter)
        return createVariableTypeItem(psiType, null, psiTypeParameterItem)
    }

    // PsiTypeItem factory methods

    /** Creates modifiers based on the annotations of the [type]. */
    private fun createTypeModifiers(
        type: PsiType,
        kotlinType: KotlinTypeInfo?,
        typeUse: TypeUse = TypeUse.GENERAL,
    ): TypeModifiers {
        val annotations = type.annotations.map { PsiAnnotationItem.create(codebase, it) }
        // Some types have defined nullness, and kotlin types have nullness information.
        val nullability =
            when {
                typeUse == TypeUse.SUPER_TYPE || type is PsiPrimitiveType -> TypeNullability.NONNULL
                type is PsiWildcardType -> TypeNullability.UNDEFINED
                else -> kotlinType?.nullability()
            }
        return DefaultTypeModifiers.create(annotations.toMutableList(), nullability)
    }

    /** Create a [PsiTypeItem]. */
    private fun createTypeItem(
        psiType: PsiType,
        kotlinType: KotlinTypeInfo?,
        typeUse: TypeUse = TypeUse.GENERAL,
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
                )
            is PsiClassType -> {
                val typeParameterItem =
                    when (val psiClass = psiType.resolve()) {
                        // If the type resolves to a PsiTypeParameter then the TypeParameterItem
                        // must exist.
                        is PsiTypeParameter -> {
                            val name = psiClass.qualifiedName ?: psiType.name
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
                    createVariableTypeItem(
                        psiType = psiType,
                        kotlinType = kotlinType,
                        typeParameterItem = typeParameterItem,
                    )
                } else {
                    createClassTypeItem(
                        psiType = psiType,
                        kotlinType = kotlinType,
                        typeUse = typeUse,
                    )
                }
            }
            is PsiWildcardType ->
                createWildcardTypeItem(
                    psiType = psiType,
                    kotlinType = kotlinType,
                )
            // There are other [PsiType]s, but none can appear in API surfaces.
            else -> throw IllegalStateException("Invalid type in API surface: $psiType")
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
            modifiers = createTypeModifiers(psiType, kotlinType),
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
    ) =
        PsiArrayTypeItem(
            psiType = psiType,
            componentType =
                createTypeItem(
                    psiType.componentType,
                    kotlinType?.forArrayComponentType(),
                ),
            isVarargs = psiType is PsiEllipsisType,
            modifiers = createTypeModifiers(psiType, kotlinType),
        )

    /** Create a [PsiClassTypeItem]. */
    private fun createClassTypeItem(
        psiType: PsiClassType,
        kotlinType: KotlinTypeInfo?,
        typeUse: TypeUse,
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
                ),
            outerClassType =
                computeOuterClass(
                    psiType,
                    kotlinType,
                ),
            // This should be able to use `psiType.name`, but that sometimes returns null.
            className = ClassTypeItem.computeClassName(qualifiedName),
            modifiers = createTypeModifiers(psiType, kotlinType, typeUse),
        )
    }

    /** Compute the [PsiClassTypeItem.arguments]. */
    private fun computeTypeArguments(
        psiType: PsiClassType,
        kotlinType: KotlinTypeInfo?
    ): List<TypeArgumentTypeItem> {
        val psiParameters =
            psiType.parameters.toList().ifEmpty {
                // Sometimes, when a PsiClassType's arguments are empty it is not because there
                // are no arguments but due to a bug in Psi somewhere. Check to see if the
                // kotlin type info has a different set of type arguments and if it has then use
                // that to fix the type, otherwise just assume it should be empty.
                kotlinType?.ktType?.let { ktType ->
                    (ktType as? KtNonErrorClassType)?.ownTypeArguments?.ifNotEmpty {
                        fixUpPsiTypeMissingTypeArguments(psiType, kotlinType)
                    }
                }
                    ?: emptyList()
            }

        return psiParameters.mapIndexed { i, param ->
            createTypeItem(param, kotlinType?.forParameter(i)) as TypeArgumentTypeItem
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
    private fun fixUpPsiTypeMissingTypeArguments(
        psiType: PsiClassType,
        kotlinType: KotlinTypeInfo
    ): List<PsiType> {
        if (kotlinType.analysisSession == null || kotlinType.ktType == null) return emptyList()

        val ktType = kotlinType.ktType as KtNonErrorClassType

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
                val mode = KtTypeMappingMode.DEFAULT
                ktType.asPsiType(kotlinType.context, false, mode = mode)
            } as? PsiClassType
        return psiTypeFromKotlin?.parameters?.toList() ?: emptyList()
    }

    /** Compute the [PsiClassTypeItem.outerClassType]. */
    private fun computeOuterClass(
        psiType: PsiClassType,
        kotlinType: KotlinTypeInfo?
    ): PsiClassTypeItem? {
        // TODO(b/300081840): this drops annotations on the outer class
        return PsiNameHelper.getOuterClassReference(psiType.canonicalText).let { outerClassName ->
            // [PsiNameHelper.getOuterClassReference] returns an empty string if there is no
            // outer class reference. If the type is not an inner type, it returns the package
            // name (e.g. for "java.lang.String" it returns "java.lang").
            if (outerClassName == "" || codebase.findPsiPackage(outerClassName) != null) {
                null
            } else {
                val psiOuterClassType =
                    codebase.createPsiType(
                        outerClassName,
                        // The context psi element allows variable types to be resolved (with no
                        // context, they would be interpreted as class types). The [psiContext]
                        // works in most cases, but is null when creating a type directly from a
                        // class declaration, so the resolved [psiType] provides context then.
                        psiType.psiContext ?: psiType.resolve()
                    )
                (createTypeItem(
                        psiOuterClassType,
                        kotlinType?.forOuterClass(),
                    )
                        as PsiClassTypeItem)
                    .apply {
                        // An outer class reference can't be null.
                        modifiers.setNullability(TypeNullability.NONNULL)
                    }
            }
        }
    }

    /** Create a [PsiVariableTypeItem]. */
    private fun createVariableTypeItem(
        psiType: PsiClassType,
        kotlinType: KotlinTypeInfo?,
        typeParameterItem: TypeParameterItem,
    ) =
        PsiVariableTypeItem(
            psiType = psiType,
            modifiers = createTypeModifiers(psiType, kotlinType),
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
                    kotlinType,
                ),
            superBound =
                createBound(
                    psiType.superBound,
                    kotlinType,
                ),
            modifiers = createTypeModifiers(psiType, kotlinType),
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
    ): ReferenceTypeItem? {
        return if (bound == PsiTypes.nullType()) {
            null
        } else {
            // Use the same Kotlin type, because the wildcard isn't its own level in the KtType.
            createTypeItem(bound, kotlinType) as ReferenceTypeItem
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
