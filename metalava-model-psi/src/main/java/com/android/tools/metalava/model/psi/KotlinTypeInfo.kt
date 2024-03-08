/*
 * Copyright (C) 2023 The Android Open Source Project
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

import com.android.tools.metalava.model.TypeNullability
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.analysis.api.KtAnalysisSession
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.components.buildClassType
import org.jetbrains.kotlin.analysis.api.symbols.KtNamedClassOrObjectSymbol
import org.jetbrains.kotlin.analysis.api.types.KtNonErrorClassType
import org.jetbrains.kotlin.analysis.api.types.KtType
import org.jetbrains.kotlin.analysis.api.types.KtTypeNullability
import org.jetbrains.kotlin.analysis.api.types.KtTypeParameterType
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtCallableDeclaration
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtFunction
import org.jetbrains.kotlin.psi.KtParameter
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtPropertyAccessor
import org.jetbrains.kotlin.psi.KtTypeReference
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.UParameter
import org.jetbrains.uast.getContainingUMethod

/**
 * A wrapper for a [KtType] and the [KtAnalysisSession] needed to analyze it and the [PsiElement]
 * that is the use site.
 */
internal class KotlinTypeInfo
private constructor(
    val analysisSession: KtAnalysisSession?,
    ktType: KtType?,
    val context: PsiElement,
    /**
     * Override list of type arguments that should have been, but for some reason could not be,
     * encapsulated within [ktType].
     */
    val overrideTypeArguments: List<KotlinTypeInfo>? = null,
) {
    constructor(context: PsiElement) : this(null, null, context)

    /** Make sure that any typealiases are fully expanded. */
    val ktType =
        analysisSession?.run { ktType?.fullyExpandedType }
            ?: ktType?.let {
                error("cannot have non-null ktType ($ktType) with a null analysisSession")
            }

    override fun toString(): String {
        return "KotlinTypeInfo($ktType for $context)"
    }

    fun copy(
        ktType: KtType? = this.ktType,
        overrideTypeArguments: List<KotlinTypeInfo>? = this.overrideTypeArguments,
    ) = KotlinTypeInfo(analysisSession, ktType, context, overrideTypeArguments)

    /**
     * Finds the nullability of the [ktType]. If there is no [analysisSession] or [ktType], defaults
     * to [TypeNullability.NONNULL] since the type is from Kotlin source.
     */
    fun nullability(): TypeNullability? {
        return if (analysisSession != null && ktType != null) {
            analysisSession.run {
                if (analysisSession.isInheritedGenericType(ktType)) {
                    TypeNullability.UNDEFINED
                } else if (ktType.nullability == KtTypeNullability.NULLABLE) {
                    TypeNullability.NULLABLE
                } else if (ktType.nullability == KtTypeNullability.NON_NULLABLE) {
                    TypeNullability.NONNULL
                } else {
                    // No nullability information, possibly a propagated platform type.
                    null
                }
            }
        } else {
            TypeNullability.NONNULL
        }
    }

    /**
     * Creates [KotlinTypeInfo] for the component type of this [ktType], assuming it is an array.
     */
    fun forArrayComponentType(): KotlinTypeInfo {
        return KotlinTypeInfo(
            analysisSession,
            analysisSession?.run { ktType?.getArrayElementType() },
            context,
        )
    }

    /**
     * Creates [KotlinTypeInfo] for the type argument at [index] of this [KotlinTypeInfo], assuming
     * it is a class type.
     */
    fun forTypeArgument(index: Int): KotlinTypeInfo {
        overrideTypeArguments?.getOrNull(index)?.let {
            return it
        }
        return KotlinTypeInfo(
            analysisSession,
            analysisSession?.run {
                when (ktType) {
                    is KtNonErrorClassType -> ktType.ownTypeArguments.getOrNull(index)?.type
                    else -> null
                }
            },
            context,
        )
    }

    /**
     * Creates [KotlinTypeInfo] for the outer class type of this [ktType], assuming it is a class.
     */
    fun forOuterClass(): KotlinTypeInfo {
        return KotlinTypeInfo(
            analysisSession,
            analysisSession?.run {
                (ktType as? KtNonErrorClassType)?.classId?.outerClassId?.let { outerClassId ->
                    buildClassType(outerClassId) {
                        // Add the parameters of the class type with nullability information.
                        ktType.qualifiers
                            .firstOrNull { it.name == outerClassId.shortClassName }
                            ?.typeArguments
                            ?.forEach { argument(it) }
                    }
                }
            },
            context,
        )
    }

    /** Get a [KotlinTypeInfo] that represents a suspend function's `Continuation` parameter. */
    fun forSyntheticContinuationParameter(returnType: KtType): KotlinTypeInfo {
        // This cast is safe as this will only be called for a lambda function whose context will
        // be [KtFunction].
        val ktElement = context as KtElement
        return analyze(ktElement) { syntheticContinuationParameter(context, returnType) }
    }

    /** Get a [KotlinTypeInfo] that represents `Any?`. */
    fun nullableAny(): KotlinTypeInfo {
        // This cast is safe as this will only be called for a lambda function whose context will
        // be [KtFunction].
        val ktElement = context as KtElement
        return analyze(ktElement) { KotlinTypeInfo(this, builtinTypes.NULLABLE_ANY, context) }
    }

    companion object {
        /**
         * Creates a [KotlinTypeInfo] instance from the given [context], with null values if the
         * [KtType] for the [context] can't be resolved.
         */
        fun fromContext(context: PsiElement): KotlinTypeInfo {
            return if (context is KtElement) {
                fromKtElement(context)
            } else {
                when (val sourcePsi = (context as? UElement)?.sourcePsi) {
                    is KtElement -> fromKtElement(sourcePsi)
                    else -> {
                        typeFromSyntheticElement(context)
                    }
                }
            }
                ?: KotlinTypeInfo(context)
        }

        /** Try and compute [KotlinTypeInfo] from a [KtElement]. */
        private fun fromKtElement(context: KtElement): KotlinTypeInfo? =
            when (context) {
                is KtCallableDeclaration -> {
                    analyze(context) { KotlinTypeInfo(this, context.getReturnKtType(), context) }
                }
                is KtTypeReference ->
                    analyze(context) { KotlinTypeInfo(this, context.getKtType(), context) }
                is KtPropertyAccessor ->
                    analyze(context) { KotlinTypeInfo(this, context.getKtType(), context) }
                is KtClass -> {
                    analyze(context) {
                        // If this is a named class or object then return a KotlinTypeInfo for the
                        // class. If it is generic then the type parameters will be used as the
                        // type arguments.
                        (context.getSymbol() as? KtNamedClassOrObjectSymbol)?.let { symbol ->
                            KotlinTypeInfo(this, symbol.buildSelfClassType(), context)
                        }
                    }
                }
                else -> null
            }

        /**
         * Try and compute the type from a synthetic elements, e.g. a property setter.
         *
         * In order to get this far the [context] is either not a [UElement], or it has a null
         * [UElement.sourcePsi]. That means it is most likely a parameter in a synthetic method
         * created for use by code that operates on a "Psi" view of the source, i.e. java code. This
         * method will attempt to reverse engineer the "Kt" -> "Psi" mapping to find the real Kotlin
         * types.
         */
        private fun typeFromSyntheticElement(context: PsiElement): KotlinTypeInfo? {
            // If this is not a UParameter in a UMethod then it is an unknown synthetic element so
            // just return.
            val containingMethod = (context as? UParameter)?.getContainingUMethod() ?: return null

            // Get the parameter index from the containing methods `uastParameters` as the parameter
            // is a `UParameter`.
            val parameterIndex = containingMethod.uastParameters.indexOf(context)

            return when (val sourcePsi = containingMethod.sourcePsi) {
                is KtProperty -> {
                    // This is the parameter of a synthetic setter, so get its type from the
                    // containing method.
                    fromContext(containingMethod)
                }
                is KtParameter -> {
                    // The underlying source representation of the synthetic method is a parameter,
                    // most likely a parameter of the primary constructor. In which case the
                    // synthetic method is most like a property setter. Whatever it may be, use the
                    // type of the parameter as it is most likely to be the correct type.
                    fromKtElement(sourcePsi)
                }
                is KtClass -> {
                    // The underlying source representation of the synthetic method is a whole
                    // class.
                    typeFromKtClass(parameterIndex, containingMethod, sourcePsi)
                }
                is KtFunction -> {
                    if (
                        sourcePsi.modifierList?.hasModifier(KtTokens.SUSPEND_KEYWORD) == true &&
                            parameterIndex == containingMethod.parameters.size - 1
                    ) {
                        // Compute the [KotlinTypeInfo] for the suspend function's synthetic
                        // [kotlin.coroutines.Continuation] parameter.
                        analyze(sourcePsi) {
                            val returnKtType = sourcePsi.getReturnKtType()
                            syntheticContinuationParameter(sourcePsi, returnKtType)
                        }
                    } else null
                }
                else -> null
            }
        }

        /**
         * Create a [KotlinTypeInfo] that represents the continuation parameter of a `suspend`
         * function with [returnKtType].
         *
         * Ideally, this would create a [KtNonErrorClassType] for `Continuation<$returnType$>`,
         * where `$returnType$` is the return type of the Kotlin suspend function but while that
         * works in K2 it fails in K1 as it cannot resolve the `Continuation` type even though it is
         * in the Kotlin stdlib which will be on the classpath.
         *
         * Fortunately, doing that is not strictly necessary as the [KtType] is only used to
         * retrieve nullability for the `Continuation` type and its type argument. So, this uses
         * non-nullable [Any] as the fake type for `Continuation` (as that is always non-nullable)
         * and stores the suspend function's return type in [KotlinTypeInfo.overrideTypeArguments]
         * from where it will be retrieved.
         */
        internal fun KtAnalysisSession.syntheticContinuationParameter(
            context: PsiElement,
            returnKtType: KtType
        ): KotlinTypeInfo {
            val returnTypeInfo = KotlinTypeInfo(this, returnKtType, context)
            val fakeContinuationKtType = builtinTypes.ANY
            return KotlinTypeInfo(this, fakeContinuationKtType, context, listOf(returnTypeInfo))
        }

        /** Try and get the type for [parameterIndex] in [containingMethod] from the [ktClass]. */
        private fun typeFromKtClass(
            parameterIndex: Int,
            containingMethod: UMethod,
            ktClass: KtClass
        ) =
            when {
                ktClass.isData() && containingMethod.name == "copy" -> {
                    // The parameters in the copy constructor correspond to the parameters in the
                    // primary constructor so find the corresponding parameter in the primary
                    // constructor and use its type.
                    ktClass.primaryConstructor?.let { primaryConstructor ->
                        val ktParameter = primaryConstructor.valueParameters[parameterIndex]
                        analyze(ktParameter) {
                            KotlinTypeInfo(
                                this,
                                ktParameter.getReturnKtType(),
                                ktParameter,
                            )
                        }
                    }
                }
                else -> null
            }

        // Mimic `hasInheritedGenericType` in `...uast.kotlin.FirKotlinUastResolveProviderService`
        fun KtAnalysisSession.isInheritedGenericType(ktType: KtType): Boolean {
            return ktType is KtTypeParameterType &&
                // explicitly nullable, e.g., T?
                !ktType.isMarkedNullable &&
                // non-null upper bound, e.g., T : Any
                ktType.canBeNull
        }
    }
}
