/*
 * Copyright (C) 2025 The Android Open Source Project
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

package com.android.tools.metalava.model.psi.kotlin

import com.android.tools.metalava.model.AnnotationAttribute
import com.android.tools.metalava.model.AnnotationItem
import com.android.tools.metalava.model.ApiVariantSelectors
import com.android.tools.metalava.model.DefaultTypeParameterList
import com.android.tools.metalava.model.ItemDocumentation
import com.android.tools.metalava.model.ItemDocumentationFactory
import com.android.tools.metalava.model.SourceLanguage
import com.android.tools.metalava.model.TypeParameterListAndFactory
import com.android.tools.metalava.model.item.DefaultClassItem
import com.android.tools.metalava.model.item.DefaultPropertyItem
import com.android.tools.metalava.model.item.DefaultTypeParameterItem
import com.android.tools.metalava.model.psi.PsiBasedCodebase
import com.android.tools.metalava.model.psi.PsiFileLocation
import com.android.tools.metalava.model.psi.PsiItemDocumentation
import com.android.tools.metalava.model.psi.isKotlin
import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.annotations.KaAnnotation
import org.jetbrains.kotlin.analysis.api.projectStructure.KaModule
import org.jetbrains.kotlin.analysis.api.symbols.KaAnonymousObjectSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaCallableSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaClassifierSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaNamedClassSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaPackageSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaPropertySymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaSymbolOrigin
import org.jetbrains.kotlin.analysis.api.symbols.KaSymbolVisibility
import org.jetbrains.kotlin.analysis.api.symbols.KaTypeAliasSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaTypeParameterSymbol
import org.jetbrains.kotlin.analysis.api.symbols.receiverType
import org.jetbrains.kotlin.psi.KtAnnotationEntry
import org.jetbrains.kotlin.psi.KtFile

/**
 * Adds items to the [codebase] by using the kotlin analysis API to process elements from the
 * [kaModule] which only have kotlin as a target language.
 */
internal class KaCodebaseAssembler(val codebase: PsiBasedCodebase, val kaModule: KaModule) {
    private val kaTypeItemFactory =
        KaTypeItemFactory(
            codebase,
            this,
            codebase.globalTypeItemFactory.typeParameterScope,
        )
    private val kaValueFactory = KaValueFactory(codebase, this, kaTypeItemFactory)
    private val kaModifierFactory = KaModifierFactory(this)

    /** Analyze the [ktFiles] to add items to the codebase for this [kaModule]. */
    fun assemble(ktFiles: List<KtFile>) {
        analyze(kaModule) {
            val packages = ktFiles.map { it.packageFqName }.toSet().sortedBy { it.asString() }
            for (packageName in packages) {
                val packageSymbol = findPackage(packageName)
                packageSymbol?.let { processPackage(it) }
            }
        }
    }

    /** Analyze the classes of the package as well as any top-level callables. */
    private fun KaSession.processPackage(packageSymbol: KaPackageSymbol) {
        val packageScope = packageSymbol.packageScope
        for (classifierSymbol in packageScope.classifiers) {
            processClassifier(classifierSymbol)
        }
        for (callableSymbol in packageScope.callables) {
            // For top-level callables, find their containing class in the codebase.
            @OptIn(KaExperimentalApi::class)
            val className = callableSymbol.containingJvmClassName ?: continue
            val classItem = codebase.findClass(className) as? DefaultClassItem ?: continue
            val classTypeItemFactory =
                KaTypeItemFactory(codebase, this@KaCodebaseAssembler, classItem)
            processCallable(callableSymbol, classItem, classTypeItemFactory)
        }
    }

    /** Analyze the elements of the class. */
    private fun KaSession.processClassifier(classifierSymbol: KaClassifierSymbol) {
        // Skip Java classes, these won't be kotlin-only.
        if (classifierSymbol.psi?.isKotlin() == false) return
        // Skip classes loaded from the classpath.
        if (classifierSymbol.origin == KaSymbolOrigin.LIBRARY) return
        // Skip private classes since these aren't part of the API surface
        if (classifierSymbol.visibility == KaSymbolVisibility.PRIVATE) return
        when (classifierSymbol) {
            is KaNamedClassSymbol -> {
                // Find the class in the codebase.
                val className = classifierSymbol.classId?.asFqNameString() ?: return
                val classItem = codebase.findClass(className) as? DefaultClassItem ?: return
                val classTypeItemFactory =
                    KaTypeItemFactory(codebase, this@KaCodebaseAssembler, classItem)

                // The combined declared member scope contains both static and non-static members.
                val memberScope = classifierSymbol.combinedDeclaredMemberScope
                for (callableSymbol in memberScope.callables) {
                    processCallable(callableSymbol, classItem, classTypeItemFactory)
                }
                for (nestedClassifierSymbol in memberScope.classifiers) {
                    processClassifier(nestedClassifierSymbol)
                }
            }
            is KaTypeAliasSymbol,
            is KaTypeParameterSymbol,
            is KaAnonymousObjectSymbol -> return
        }
    }

    /** Processes a [KaCallableSymbol], which could be a property or function. */
    private fun KaSession.processCallable(
        callableSymbol: KaCallableSymbol,
        containingClass: DefaultClassItem,
        enclosingTypeItemFactory: KaTypeItemFactory,
    ) {
        // Skip callables loaded from the classpath.
        if (callableSymbol.origin == KaSymbolOrigin.LIBRARY) return
        // TODO(b/421201575): currently, private properties need to be processed in order to reset
        //  the visibility of the property accessors due to a uast bug for value class types

        when (callableSymbol) {
            is KaPropertySymbol ->
                processProperty(callableSymbol, containingClass, enclosingTypeItemFactory)
            // TODO(b/421201575): process functions
            else -> return
        }
    }

    /** Constructs a property from the [propertySymbol] and adds it to the [containingClass]. */
    private fun KaSession.processProperty(
        propertySymbol: KaPropertySymbol,
        containingClass: DefaultClassItem,
        enclosingTypeItemFactory: KaTypeItemFactory
    ) {
        // Skip creating enum entry properties, which exist for all enums.
        if (
            containingClass.isEnum() &&
                propertySymbol.name.identifier == "entries" &&
                propertySymbol.receiverType == null
        )
            return

        val typeParameterListAndFactory =
            typeParameterListAndFactory(
                enclosingTypeItemFactory,
                "for property ${propertySymbol.name}",
                propertySymbol.typeParameters
            )
        val type = typeParameterListAndFactory.factory.getGeneralType(propertySymbol.returnType)
        val receiverType =
            propertySymbol.receiverType?.let {
                typeParameterListAndFactory.factory.getGeneralType(it)
            }
        // Private properties currently still need to be processed when they use a value class type
        // to reset incorrect nullability on the accessors from psi. But other private properties
        // can be skipped since they aren't part of the API surface.
        if (
            propertySymbol.visibility == KaSymbolVisibility.PRIVATE &&
                !type.isValueClassType() &&
                receiverType?.isValueClassType() != true
        )
            return

        val propertyItem =
            DefaultPropertyItem(
                codebase = codebase,
                fileLocation = PsiFileLocation.fromPsiElement(propertySymbol.psi),
                sourceLanguage = SourceLanguage.KOTLIN,
                documentationFactory = propertySymbol.getDocumentation(),
                variantSelectorsFactory = ApiVariantSelectors.MUTABLE_FACTORY,
                modifiers = kaModifierFactory.createForProperty(propertySymbol, containingClass),
                name = propertySymbol.name.identifier,
                containingClass = containingClass,
                type = type,
                // TODO: accessors, constructor parameter, and backing field added in followup
                getter = null,
                setter = null,
                constructorParameter = null,
                backingField = null,
                receiver = receiverType,
                typeParameterList = typeParameterListAndFactory.typeParameterList,
            )
        containingClass.addProperty(propertyItem)
    }

    /** Creates documentation for the symbol through psi, if possible. */
    private fun KaSymbol.getDocumentation(): ItemDocumentationFactory {
        return psi?.let { PsiItemDocumentation.factory(it, codebase) }
            ?: ItemDocumentation.NONE_FACTORY
    }

    /**
     * Creates a list of type parameters from the [typeParameterSymbols] and a type factory based on
     * the [enclosingTypeItemFactory].
     */
    private fun typeParameterListAndFactory(
        enclosingTypeItemFactory: KaTypeItemFactory,
        scopeDescription: String,
        typeParameterSymbols: List<KaTypeParameterSymbol>,
    ): TypeParameterListAndFactory<KaTypeItemFactory> {
        return DefaultTypeParameterList.createTypeParameterItemsAndFactory(
            enclosingTypeItemFactory,
            scopeDescription,
            typeParameterSymbols,
            // Construct type parameter items from the symbols
            { typeParameterSymbol ->
                DefaultTypeParameterItem(
                    codebase,
                    kaModifierFactory.createForDeclaration(typeParameterSymbol),
                    typeParameterSymbol.name.identifier,
                    typeParameterSymbol.isReified,
                )
            },
            // Get the bounds of the type parameter from the symbols
            { typeItemFactory, typeParameterSymbol ->
                typeParameterSymbol.upperBounds.map { typeItemFactory.getBoundsType(it) }
            },
        )
    }

    /** Creates an annotation from the [kaAnnotation], if possible. */
    fun createAnnotation(kaAnnotation: KaAnnotation): AnnotationItem? {
        val qualifiedName = kaAnnotation.classId?.asFqNameString() ?: return null
        return AnnotationItem.createAttributesLazily(
            codebase,
            fileLocation = PsiFileLocation.fromPsiElement(kaAnnotation.psi),
            originalName = qualifiedName,
            attributesGetter = {
                // Find the psi attributes as well, if they exist, because for inlined constant
                // values they have information about the field used that the analysis api does not.
                val psiAttributes =
                    (kaAnnotation.psi as? KtAnnotationEntry)?.valueArguments ?: emptyList()
                kaAnnotation.arguments.mapIndexed { i, kaNamedAnnotationValue ->
                    val psiAttribute = psiAttributes.getOrNull(i)
                    AnnotationAttribute.createLazyAttribute(
                        name = kaNamedAnnotationValue.name.identifier,
                        valueProvider =
                            kaValueFactory.providerForAnnotationValue(
                                kaNamedAnnotationValue.expression,
                                psiAttribute,
                            )
                    )
                }
            }
        )
    }

    companion object {
        /** Adds kotlin-only elements to the [codebase] by analyzing the [ktFiles]. */
        fun assembleFromKotlin(ktFiles: List<KtFile>, codebase: PsiBasedCodebase) {
            if (ktFiles.isEmpty()) return
            // TODO(b/407735063): analyze all modules for KMP projects
            val analysisModule =
                codebase.mainAnalysisModule
                    ?: error("No main analysis module found for project with Kotlin files")
            val assembler = KaCodebaseAssembler(codebase, analysisModule)
            assembler.assemble(ktFiles)
        }
    }
}
