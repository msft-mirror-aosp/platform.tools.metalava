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

package com.android.tools.metalava.apilevels

import com.android.tools.metalava.model.ClassKind
import com.android.tools.metalava.model.MutableModifierList
import com.android.tools.metalava.model.TargetLanguage
import com.android.tools.metalava.model.VisibilityLevel
import com.android.tools.metalava.model.createMutableModifiers
import com.android.tools.metalava.model.parser.LineMap
import com.android.tools.metalava.model.parser.SharedTokenType
import com.android.tools.metalava.model.parser.TokenStream
import com.android.tools.metalava.model.text.FileFormat
import com.android.tools.metalava.model.text.ParsedParameter
import com.android.tools.metalava.model.text.ParsedRecordComponent
import com.android.tools.metalava.model.text.ParsedTypeParameter
import com.android.tools.metalava.model.text.SignatureFile
import com.android.tools.metalava.model.text.SignatureFileConsumer
import com.android.tools.metalava.model.type.TypeString
import com.android.tools.metalava.reporter.FileLocation
import java.nio.file.Path

/**
 * [SignatureFileConsumer] that populates an [Api] directly from parsed signature files using
 * [updater] without constructing a full [com.android.tools.metalava.model.Codebase].
 *
 * @param api the [Api] to populate.
 * @param updater the [ApiHistoryUpdater] for the API version being loaded.
 */
internal class ApiSignatureFileConsumer(
    private val api: Api,
    private val updater: ApiHistoryUpdater,
) : SignatureFileConsumer {

    /**
     * Tracks per-class state accumulated while parsing one or more signature files for a version.
     *
     * @property apiClass the [ApiClass] created or updated in [api].
     * @property effectivelyDeprecated whether the class is deprecated.
     * @property superClassName the internal name of the class's superclass, or `null` for
     *   `java/lang/Object`.
     * @property interfaceNames the internal names of the interfaces implemented by the class.
     */
    private class ClassContext(
        val apiClass: ApiClass,
        val effectivelyDeprecated: Boolean,
        var superClassName: String?,
        var interfaceNames: List<String>,
    )

    /** Resolver for converting raw type strings into erased JVM internal names and descriptors. */
    private val typeDescriptorResolver = ErasedTypeDescriptorResolver()

    /** Whether [updater] has already updated [api] for this version. */
    private var updatedApi = false

    /** The `/`‑separated internal name of the current `package` block. */
    private var currentPackageInternalName: String = ""

    /** The [ClassContext] of the class currently being parsed. */
    private lateinit var currentClass: ClassContext

    /** All [ClassContext]s created while parsing signature files for this version. */
    private val classes = mutableListOf<ClassContext>()

    override fun startFile(
        signatureFile: SignatureFile,
        path: Path,
        lineMap: LineMap,
        format: FileFormat,
    ) {
        // Record the version on the Api once before processing any files for this version.
        if (!updatedApi) {
            updater.update(api)
            updatedApi = true
        }
    }

    override fun finish() {
        // Apply the superclass and super-interfaces accumulated for each class.
        for (classContext in classes) {
            val apiClass = classContext.apiClass
            classContext.superClassName?.let { superClass ->
                apiClass.updateSuperClass(superClass, updater)
            }
            for (interfaceName in classContext.interfaceNames) {
                apiClass.updateInterface(interfaceName, updater)
            }
        }
    }

    override fun parseModifierAnnotations(
        tokenStream: TokenStream,
        apiText: String,
        visibilityLevel: VisibilityLevel,
    ): MutableModifierList {
        // Skip all modifier annotations without allocating AnnotationItem instances.
        while (tokenStream.peekType() == SharedTokenType.AT) {
            skipAnnotation(tokenStream)
        }
        return createMutableModifiers(visibilityLevel)
    }

    /**
     * Skips a single `@QualifiedName(...)` annotation starting at the current `@` token in
     * [tokenStream].
     */
    private fun skipAnnotation(tokenStream: TokenStream) {
        // Consume `@` and the first identifier of the annotation name.
        tokenStream.consume()
        tokenStream.consume()
        // Consume any `.Identifier` segments of a qualified annotation name.
        while (tokenStream.match(SharedTokenType.DOT)) {
            tokenStream.consume()
        }
        // If the annotation has an argument list, consume balanced `(...)` tokens.
        if (tokenStream.match(SharedTokenType.PAREN_OPEN)) {
            var balance = 1
            while (balance > 0 && tokenStream.peekType() != SharedTokenType.EOF) {
                when (tokenStream.consume().type) {
                    SharedTokenType.PAREN_OPEN -> balance++
                    SharedTokenType.PAREN_CLOSE -> balance--
                    else -> {}
                }
            }
        }
    }

    override fun startPackage(
        location: FileLocation,
        modifiers: MutableModifierList,
        name: String,
    ) {
        // Convert the dot-separated package name into its `/`‑separated JVM internal prefix.
        currentPackageInternalName = name.replace('.', '/')
    }

    override fun consumeTypeAlias(
        location: FileLocation,
        modifiers: MutableModifierList,
        name: String,
        typeParameters: List<ParsedTypeParameter>,
        aliasedTypeString: TypeString,
    ) {
        // Kotlin type aliases do not exist in bytecode and are not tracked in Api.
    }

    override fun startClass(
        location: FileLocation,
        modifiers: MutableModifierList,
        targetLanguages: Set<TargetLanguage>,
        classKind: ClassKind,
        fullName: String,
        typeParameters: List<ParsedTypeParameter>,
        superClassTypeString: TypeString?,
        interfaceTypeStrings: List<TypeString>,
        permitTypeStrings: List<TypeString>,
        recordComponents: List<ParsedRecordComponent>?,
    ) {
        val internalName =
            if (currentPackageInternalName.isEmpty()) {
                fullName
            } else {
                "$currentPackageInternalName/$fullName"
            }

        val effectivelyDeprecated = modifiers.isDeprecated()
        val apiClass =
            api.updateClass(
                internalName,
                updater,
                effectivelyDeprecated,
                classKind == ClassKind.ENUM,
            )

        // Resolve the explicit superclass if specified, or fall back to the default binary
        // superclass for this class kind. Every class kind except java/lang/Object itself has a
        // binary superclass (java/lang/Object, java/lang/Enum, or java/lang/Record).
        val superClassName =
            if (superClassTypeString != null) {
                typeDescriptorResolver.resolveInternalName(superClassTypeString.type)
            } else {
                when (classKind) {
                    ClassKind.ENUM -> JAVA_LANG_ENUM_INTERNAL_NAME
                    ClassKind.RECORD -> JAVA_LANG_RECORD_INTERNAL_NAME
                    ClassKind.CLASS ->
                        if (internalName == JAVA_LANG_OBJECT_INTERNAL_NAME) null
                        else JAVA_LANG_OBJECT_INTERNAL_NAME
                    ClassKind.INTERFACE,
                    ClassKind.ANNOTATION_TYPE -> JAVA_LANG_OBJECT_INTERNAL_NAME
                    ClassKind.TYPEALIAS -> null
                }
            }

        // Resolve any explicit interfaces, combining them with java.lang.annotation.Annotation for
        // annotation types.
        val interfaceNames =
            if (interfaceTypeStrings.isEmpty()) {
                if (classKind == ClassKind.ANNOTATION_TYPE) {
                    listOf(JAVA_LANG_ANNOTATION_INTERNAL_NAME)
                } else {
                    emptyList()
                }
            } else {
                buildList(interfaceTypeStrings.size + 1) {
                    if (classKind == ClassKind.ANNOTATION_TYPE) {
                        add(JAVA_LANG_ANNOTATION_INTERNAL_NAME)
                    }
                    for (interfaceTypeString in interfaceTypeStrings) {
                        add(typeDescriptorResolver.resolveInternalName(interfaceTypeString.type))
                    }
                }
            }

        val classContext =
            ClassContext(
                apiClass = apiClass,
                effectivelyDeprecated = effectivelyDeprecated,
                superClassName = superClassName,
                interfaceNames = interfaceNames,
            )
        currentClass = classContext
        classes.add(classContext)
    }

    override fun consumeConstructor(
        location: FileLocation,
        modifiers: MutableModifierList,
        targetLanguages: Set<TargetLanguage>,
        name: String,
        typeParameters: List<ParsedTypeParameter>,
        parameters: List<ParsedParameter>,
        throwsTypeStrings: List<TypeString>,
    ) {
        // Private and package-private constructors are not included in the API history.
        if (modifiers.isPrivate() || modifiers.isPackagePrivate()) {
            return
        }

        val descriptor = buildString {
            append("<init>(")
            for (parameter in parameters) {
                typeDescriptorResolver.appendDescriptor(
                    this,
                    parameter.typeString.type,
                    isVarargs = parameter.modifiers.isVarArg(),
                )
            }
            append(")V")
        }

        val effectivelyDeprecated = modifiers.isDeprecated() || currentClass.effectivelyDeprecated
        currentClass.apiClass.updateMethod(descriptor, updater, effectivelyDeprecated)
    }

    override fun consumeMethod(
        location: FileLocation,
        modifiers: MutableModifierList,
        targetLanguages: Set<TargetLanguage>,
        name: String,
        returnTypeString: TypeString,
        typeParameters: List<ParsedTypeParameter>,
        parameters: List<ParsedParameter>,
        throwsTypeStrings: List<TypeString>,
        defaultAnnotationMethodValue: String?,
    ) {}

    override fun consumeField(
        location: FileLocation,
        modifiers: MutableModifierList,
        targetLanguages: Set<TargetLanguage>,
        name: String,
        typeString: TypeString,
        valueString: String?,
        isEnumConstant: Boolean,
    ) {
        // Private and package-private fields are not included in the API history.
        if (modifiers.isPrivate() || modifiers.isPackagePrivate()) {
            return
        }

        // Field names in Api do not include their type descriptor or constant value, so updateField
        // can be called directly without parsing typeString or valueString.
        val effectivelyDeprecated = modifiers.isDeprecated() || currentClass.effectivelyDeprecated
        currentClass.apiClass.updateField(name, updater, effectivelyDeprecated)
    }

    override fun consumeProperty(
        location: FileLocation,
        modifiers: MutableModifierList,
        name: String,
        receiverTypeString: TypeString?,
        typeString: TypeString,
        typeParameters: List<ParsedTypeParameter>,
        contextParameters: List<ParsedParameter>,
    ) {
        // Kotlin properties are represented by their getter/setter methods and backing fields in
        // bytecode and are not tracked separately in Api.
    }

    companion object {
        private const val JAVA_LANG_OBJECT_INTERNAL_NAME = "java/lang/Object"
        private const val JAVA_LANG_ENUM_INTERNAL_NAME = "java/lang/Enum"
        private const val JAVA_LANG_RECORD_INTERNAL_NAME = "java/lang/Record"
        private const val JAVA_LANG_ANNOTATION_INTERNAL_NAME = "java/lang/annotation/Annotation"
    }
}
