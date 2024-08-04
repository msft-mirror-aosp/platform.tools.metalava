/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.metalava.model.text

import com.android.tools.metalava.model.ANDROIDX_NONNULL
import com.android.tools.metalava.model.ANDROIDX_NULLABLE
import com.android.tools.metalava.model.AnnotationItem
import com.android.tools.metalava.model.AnnotationItem.Companion.unshortenAnnotation
import com.android.tools.metalava.model.AnnotationManager
import com.android.tools.metalava.model.ArrayTypeItem
import com.android.tools.metalava.model.CallableItem
import com.android.tools.metalava.model.ClassItem
import com.android.tools.metalava.model.ClassKind
import com.android.tools.metalava.model.ClassResolver
import com.android.tools.metalava.model.ClassTypeItem
import com.android.tools.metalava.model.Codebase
import com.android.tools.metalava.model.ConstructorItem
import com.android.tools.metalava.model.DefaultAnnotationItem
import com.android.tools.metalava.model.DefaultModifierList
import com.android.tools.metalava.model.DefaultTypeParameterList
import com.android.tools.metalava.model.ExceptionTypeItem
import com.android.tools.metalava.model.FixedFieldValue
import com.android.tools.metalava.model.Item
import com.android.tools.metalava.model.ItemDocumentation
import com.android.tools.metalava.model.JAVA_LANG_DEPRECATED
import com.android.tools.metalava.model.JAVA_LANG_OBJECT
import com.android.tools.metalava.model.MetalavaApi
import com.android.tools.metalava.model.MethodItem
import com.android.tools.metalava.model.ParameterItem
import com.android.tools.metalava.model.PrimitiveTypeItem
import com.android.tools.metalava.model.PrimitiveTypeItem.Primitive
import com.android.tools.metalava.model.TypeItem
import com.android.tools.metalava.model.TypeNullability
import com.android.tools.metalava.model.TypeParameterItem
import com.android.tools.metalava.model.TypeParameterList
import com.android.tools.metalava.model.TypeParameterListAndFactory
import com.android.tools.metalava.model.VisibilityLevel
import com.android.tools.metalava.model.item.DefaultClassItem
import com.android.tools.metalava.model.item.DefaultPackageItem
import com.android.tools.metalava.model.item.DefaultTypeParameterItem
import com.android.tools.metalava.model.item.DefaultValue
import com.android.tools.metalava.model.item.MutablePackageDoc
import com.android.tools.metalava.model.item.PackageDocs
import com.android.tools.metalava.model.javaUnescapeString
import com.android.tools.metalava.model.noOpAnnotationManager
import com.android.tools.metalava.model.type.MethodFingerprint
import com.android.tools.metalava.reporter.FileLocation
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.StringReader
import java.nio.file.Path
import java.util.IdentityHashMap
import kotlin.text.Charsets.UTF_8

/** Encapsulates information needed to process a signature file. */
data class SignatureFile(
    /** The underlying signature [File]. */
    val file: File,

    /**
     * Indicates whether [file] is for the current API surface, i.e. the one that is being created.
     *
     * This will be stored in [Item.emit].
     */
    val forCurrentApiSurface: Boolean = true,
) {
    companion object {
        /** Create a [SignatureFile] from a [File]. */
        fun fromFile(file: File) = SignatureFile(file)

        /** Create a list of [SignatureFile]s from a list of [File]s. */
        fun fromFiles(files: List<File>): List<SignatureFile> =
            files.map {
                SignatureFile(
                    it,
                )
            }
    }
}

@MetalavaApi
class ApiFile
private constructor(
    private val codebase: TextCodebase,
    private val formatForLegacyFiles: FileFormat?,
) {

    /**
     * Provides support for parsing and caching [TypeItem]s.
     *
     * Defer creation until after the first file has been read and [kotlinStyleNulls] has been set
     * to a non-null value to ensure that it picks up the correct setting of [kotlinStyleNulls].
     */
    private val typeParser by
        lazy(LazyThreadSafetyMode.NONE) { TextTypeParser(codebase, kotlinStyleNulls!!) }

    /**
     * Provides support for creating [TypeItem]s for specific uses.
     *
     * Defer creation as it depends on [typeParser].
     */
    private val globalTypeItemFactory by
        lazy(LazyThreadSafetyMode.NONE) { TextTypeItemFactory(codebase, typeParser) }

    /** Supports the initialization of a [TextCodebase]. */
    private val assembler = codebase.assembler

    /** Creates [Item] instances for [codebase]. */
    private val itemFactory = assembler.itemFactory

    /**
     * Whether types should be interpreted to be in Kotlin format (e.g. ? suffix means nullable, !
     * suffix means unknown, and absence of a suffix means not nullable.
     *
     * Updated based on the header of the signature file being parsed.
     */
    private var kotlinStyleNulls: Boolean? = null

    /** The file format of the file being parsed. */
    lateinit var format: FileFormat

    /**
     * Indicates whether the file currently being parsed is for the current API surface, i.e. the
     * one that is being created.
     *
     * See [SignatureFile.forCurrentApiSurface].
     */
    private var forCurrentApiSurface: Boolean = true

    /** Map from [ClassItem] to [TextTypeItemFactory]. */
    private val classToTypeItemFactory = IdentityHashMap<ClassItem, TextTypeItemFactory>()

    companion object {
        /**
         * Parse API signature files.
         *
         * Used by non-Metalava Kotlin code.
         */
        @MetalavaApi
        fun parseApi(
            files: List<File>,
        ) = parseApi(SignatureFile.fromFiles(files))

        /**
         * Same as `parseApi(List<SignatureFile>, ...)`, but takes a single file for convenience.
         *
         * @param signatureFile input signature file
         */
        fun parseApi(
            signatureFile: SignatureFile,
            annotationManager: AnnotationManager,
            description: String? = null,
        ) =
            parseApi(
                signatureFiles = listOf(signatureFile),
                annotationManager = annotationManager,
                description = description,
            )

        /**
         * Read API signature files into a [TextCodebase].
         *
         * Note: when reading from them multiple files, [TextCodebase.location] would refer to the
         * first file specified. each [Item.fileLocation] would correctly point out the source file
         * of each item.
         *
         * @param signatureFiles input signature files
         */
        fun parseApi(
            signatureFiles: List<SignatureFile>,
            annotationManager: AnnotationManager = noOpAnnotationManager,
            description: String? = null,
            classResolver: ClassResolver? = null,
            formatForLegacyFiles: FileFormat? = null,
            // Provides the called with access to the ApiFile.
            apiStatsConsumer: (Stats) -> Unit = {},
        ): Codebase {
            require(signatureFiles.isNotEmpty()) { "files must not be empty" }
            val api =
                TextCodebase(
                    location = signatureFiles[0].file,
                    annotationManager = annotationManager,
                    classResolver = classResolver,
                )
            val actualDescription =
                description
                    ?: buildString {
                        append("Codebase loaded from ")
                        signatureFiles.joinTo(this)
                    }
            val parser = ApiFile(api, formatForLegacyFiles)
            var first = true
            for (signatureFile in signatureFiles) {
                val file = signatureFile.file
                val apiText: String =
                    try {
                        file.readText(UTF_8)
                    } catch (ex: IOException) {
                        throw ApiParseException(
                            "Error reading API file",
                            location = FileLocation.createLocation(file.toPath()),
                            cause = ex
                        )
                    }
                parser.parseApiSingleFile(
                    appending = !first,
                    path = file.toPath(),
                    apiText = apiText,
                    forCurrentApiSurface = signatureFile.forCurrentApiSurface,
                )
                first = false
            }
            api.description = actualDescription
            parser.postProcess()

            apiStatsConsumer(parser.stats)

            return api
        }

        /** <p>DO NOT MODIFY - used by com/android/gts/api/ApprovedApis.java */
        @Deprecated("Exists only for external callers.")
        @JvmStatic
        @MetalavaApi
        @Throws(ApiParseException::class)
        fun parseApi(
            filename: String,
            apiText: String,
            @Suppress("UNUSED_PARAMETER") kotlinStyleNulls: Boolean?,
        ): Codebase {
            return parseApi(
                filename,
                apiText,
            )
        }

        /**
         * Parse the API signature file from the [inputStream].
         *
         * This will consume the whole contents of the [inputStream] but it is the caller's
         * responsibility to close it.
         */
        @JvmStatic
        @MetalavaApi
        @Throws(ApiParseException::class)
        fun parseApi(filename: String, inputStream: InputStream): Codebase {
            val apiText = inputStream.bufferedReader().readText()
            return parseApi(filename, apiText)
        }

        /** Entry point for testing. Take a filename and content separately. */
        fun parseApi(
            filename: String,
            apiText: String,
            classResolver: ClassResolver? = null,
            formatForLegacyFiles: FileFormat? = null,
        ): Codebase {
            val path = Path.of(filename)
            val api =
                TextCodebase(
                    location = path.toFile(),
                    annotationManager = noOpAnnotationManager,
                    classResolver = classResolver,
                )
            api.description = "Codebase loaded from $filename"
            val parser = ApiFile(api, formatForLegacyFiles)
            parser.parseApiSingleFile(
                appending = false,
                path = path,
                apiText = apiText,
                forCurrentApiSurface = true,
            )
            parser.postProcess()
            return api
        }

        /**
         * Extracts the bounds string list from the [typeParameterString].
         *
         * Given `T extends a.B & b.C<? super T>` this will return a list of `a.B` and `b.C<? super
         * T>`.
         */
        fun extractTypeParameterBoundsStringList(typeParameterString: String?): List<String> {
            val s = typeParameterString ?: return emptyList()
            val index = s.indexOf("extends ")
            if (index == -1) {
                return emptyList()
            }
            val list = mutableListOf<String>()
            var angleBracketBalance = 0
            var start = index + "extends ".length
            val length = s.length
            for (i in start until length) {
                val c = s[i]
                if (c == '&' && angleBracketBalance == 0) {
                    addNonBlankStringToList(list, typeParameterString, start, i)
                    start = i + 1
                } else if (c == '<') {
                    angleBracketBalance++
                } else if (c == '>') {
                    angleBracketBalance--
                    if (angleBracketBalance == 0) {
                        addNonBlankStringToList(list, typeParameterString, start, i + 1)
                        start = i + 1
                    }
                }
            }
            if (start < length) {
                addNonBlankStringToList(list, typeParameterString, start, length)
            }
            return list
        }

        private fun addNonBlankStringToList(
            list: MutableList<String>,
            s: String,
            from: Int,
            to: Int
        ) {
            val element = s.substring(from, to).trim()
            if (element.isNotEmpty()) list.add(element)
        }
    }

    /**
     * Mark this [Item] as being part of the current API surface, i.e. the one that is being
     * created.
     *
     * See [SignatureFile.forCurrentApiSurface].
     *
     * This will set [Item.emit] to [forCurrentApiSurface] and should only be called on [Item]s
     * which have been created from the current signature file.
     */
    private fun Item.markForCurrentApiSurface() {
        emit = forCurrentApiSurface
    }

    /**
     * It is only necessary to mark an existing class as being part of the current API surface, if
     * it should be but is not already.
     *
     * This will set [Item.emit] to `true` iff it was previously `false` and [forCurrentApiSurface]
     * is `true`. That ensures that a class that is not in the current API surface can be included
     * in it by another signature file, but once it is included it cannot be removed.
     *
     * e.g. Imagine that there are two files, `public.txt` and `system.txt` where the second extends
     * the first. When generating the system API classes in the `public.txt` will not be considered
     * part of it but any classes defined in `system.txt` will be, even if they were initially
     * created in `public.txt`. While `public.txt` should come first this ensures the correct
     * behavior irrespective of the order.
     */
    private fun ClassItem.markExistingClassForCurrentApiSurface() {
        if (!emit && forCurrentApiSurface) {
            markForCurrentApiSurface()
        }
    }

    /**
     * Perform any final steps to initialize the [TextCodebase] after parsing the signature files.
     */
    private fun postProcess() {
        codebase.resolveSuperTypes()
    }

    private fun parseApiSingleFile(
        appending: Boolean,
        path: Path,
        apiText: String,
        forCurrentApiSurface: Boolean = true,
    ) {
        // Parse the header of the signature file to determine the format. If the signature file is
        // empty then `parseHeader` will return null, so it will default to `FileFormat.V2`.
        format =
            FileFormat.parseHeader(path, StringReader(apiText), formatForLegacyFiles)
                ?: FileFormat.V2

        // Disallow a mixture of kotlinStyleNulls settings.
        if (kotlinStyleNulls == null) {
            kotlinStyleNulls = format.kotlinStyleNulls
        } else if (kotlinStyleNulls != format.kotlinStyleNulls) {
            throw ApiParseException(
                "Cannot mix signature files with different settings of kotlinStyleNulls"
            )
        }

        if (appending) {
            // When we're appending, and the content is empty, nothing to do.
            if (apiText.isBlank()) {
                return
            }
        }

        // Remember whether the file being parsed is for the current API surface, so that Items
        // created from it can be marked correctly.
        this.forCurrentApiSurface = forCurrentApiSurface

        val tokenizer = Tokenizer(path, apiText.toCharArray())
        while (true) {
            val token = tokenizer.getToken() ?: break
            // TODO: Accept annotations on packages.
            if ("package" == token) {
                parsePackage(tokenizer)
            } else {
                throw ApiParseException("expected package got $token", tokenizer)
            }
        }
    }

    private fun parsePackage(tokenizer: Tokenizer) {
        var token: String = tokenizer.requireToken()

        // Metalava: including annotations in file now
        val annotations = getAnnotations(tokenizer, token)
        val modifiers = createModifiers(VisibilityLevel.PUBLIC, annotations)
        token = tokenizer.current
        tokenizer.assertIdent(token)
        val name: String = token

        // Wrap the modifiers and file location in a PackageDocs so that findOrCreatePackage(...)
        // will create a package with them and will check to make sure that an existing package, if
        // any, has matching modifiers.
        val packageDoc =
            MutablePackageDoc(
                name,
                fileLocation = tokenizer.fileLocation(),
                modifiers = modifiers,
            )
        val packageDocs = PackageDocs(mapOf(name to packageDoc))
        val pkg =
            try {
                codebase.findOrCreatePackage(
                    name,
                    packageDocs,
                    // Make sure that this package is included in the current API surface, even if
                    // it was created in a separate file which is not part of the current API
                    // surface.
                    emit = forCurrentApiSurface,
                )
            } catch (e: IllegalStateException) {
                throw ApiParseException(e.message!!, tokenizer)
            }

        token = tokenizer.requireToken()
        if ("{" != token) {
            throw ApiParseException("expected '{' got $token", tokenizer)
        }
        while (true) {
            token = tokenizer.requireToken()
            if ("}" == token) {
                break
            } else {
                parseClass(pkg, tokenizer, token)
            }
        }
    }

    private fun parseClass(pkg: DefaultPackageItem, tokenizer: Tokenizer, startingToken: String) {
        var token = startingToken
        var classKind = ClassKind.CLASS
        var superClassType: ClassTypeItem? = null

        // Metalava: including annotations in file now
        val annotations = getAnnotations(tokenizer, token)
        token = tokenizer.current
        val modifiers = parseModifiers(tokenizer, token, annotations)

        // Remember this position as this seems like a good place to use to report issues with the
        // class item.
        val classPosition = tokenizer.fileLocation()

        token = tokenizer.current
        when (token) {
            "class" -> {
                token = tokenizer.requireToken()
            }
            "interface" -> {
                classKind = ClassKind.INTERFACE
                modifiers.setAbstract(true)
                token = tokenizer.requireToken()
            }
            "@interface" -> {
                classKind = ClassKind.ANNOTATION_TYPE
                modifiers.setAbstract(true)
                token = tokenizer.requireToken()
            }
            "enum" -> {
                classKind = ClassKind.ENUM
                modifiers.setFinal(true)
                modifiers.setStatic(true)
                superClassType = globalTypeItemFactory.superEnumType
                token = tokenizer.requireToken()
            }
            else -> {
                throw ApiParseException("missing class or interface. got: $token", tokenizer)
            }
        }
        tokenizer.assertIdent(token)

        // The declaredClassType consists of the full name (i.e. preceded by the containing class's
        // full name followed by a '.' if there is one) plus the type parameter string.
        val declaredClassType: String = token

        // Extract lots of information from the declared class type.
        val (
            className,
            fullName,
            qualifiedClassName,
            outerClass,
            typeParameterList,
            typeItemFactory,
        ) = parseDeclaredClassType(pkg, declaredClassType, classPosition)

        token = tokenizer.requireToken()

        if ("extends" == token && classKind != ClassKind.INTERFACE) {
            val superClassTypeString = parseSuperTypeString(tokenizer, tokenizer.requireToken())
            superClassType =
                typeItemFactory.getSuperClassType(
                    superClassTypeString,
                )
            token = tokenizer.current
        }

        val interfaceTypes = mutableSetOf<ClassTypeItem>()
        if ("implements" == token || "extends" == token) {
            token = tokenizer.requireToken()
            while (true) {
                if ("{" == token) {
                    break
                } else if ("," != token) {
                    val interfaceTypeString = parseSuperTypeString(tokenizer, token)
                    val interfaceType = typeItemFactory.getInterfaceType(interfaceTypeString)
                    interfaceTypes.add(interfaceType)
                    token = tokenizer.current
                } else {
                    token = tokenizer.requireToken()
                }
            }
        }
        if (superClassType == globalTypeItemFactory.superEnumType) {
            // This can be taken either for an enum class, or a normal class that extends
            // java.lang.Enum (which was the old way of representing an enum in the API signature
            // files.
            classKind = ClassKind.ENUM
        } else if (classKind == ClassKind.ANNOTATION_TYPE) {
            // If the annotation was defined using @interface then add the implicit
            // "implements java.lang.annotation.Annotation".
            interfaceTypes.add(globalTypeItemFactory.superAnnotationType)
        } else if (globalTypeItemFactory.superAnnotationType in interfaceTypes) {
            // A normal class that implements java.lang.annotation.Annotation which was the old way
            // of representing an annotation in the API signature files. So, update the class kind
            // to match.
            classKind = ClassKind.ANNOTATION_TYPE
        }

        if ("{" != token) {
            throw ApiParseException("expected {, was $token", tokenizer)
        }

        // Above we marked all enums as static but for a top level class it's implicit
        if (classKind == ClassKind.ENUM && !fullName.contains(".")) {
            modifiers.setStatic(false)
        }

        // Get the characteristics of the class being added as they may be needed to compare against
        // the characteristics of the same class from a previously processed signature file.
        val newClassCharacteristics =
            ClassCharacteristics(
                fileLocation = classPosition,
                qualifiedName = qualifiedClassName,
                fullName = fullName,
                classKind = classKind,
                modifiers = modifiers,
                superClassType = superClassType,
            )

        // Check to see if there is an existing class, if so merge this class definition into that
        // one and return. Otherwise, drop through and create a whole new class.
        if (tryMergingIntoExistingClass(tokenizer, newClassCharacteristics)) {
            return
        }

        // Default the superClassType() to java.lang.Object for any class that is not an interface,
        // annotation, or enum and which is not itself java.lang.Object.
        if (
            classKind == ClassKind.CLASS &&
                superClassType == null &&
                qualifiedClassName != JAVA_LANG_OBJECT
        ) {
            superClassType = globalTypeItemFactory.superObjectType
        }

        // Create the DefaultClassItem and set its package but do not add it to the package or
        // register it.
        val cl =
            itemFactory.createClassItem(
                fileLocation = classPosition,
                modifiers = modifiers,
                classKind = classKind,
                containingClass = outerClass,
                containingPackage = pkg,
                qualifiedName = qualifiedClassName,
                simpleName = className,
                fullName = fullName,
                typeParameterList = typeParameterList,
                isFromClassPath = false,
                superClassType = superClassType,
                interfaceTypes = interfaceTypes.toList(),
            )
        cl.markForCurrentApiSurface()

        // Store the [TypeItemFactory] for this [ClassItem] so it can be retrieved later in
        // [typeItemFactoryForClass].
        if (!typeItemFactory.typeParameterScope.isEmpty()) {
            classToTypeItemFactory[cl] = typeItemFactory
        }

        // Parse the class body adding each member created to the class item being populated.
        parseClassBody(tokenizer, cl, typeItemFactory)
    }

    /**
     * Try merging the new class into an existing class that was previously loaded from a separate
     * signature file.
     *
     * Will throw an exception if there is an existing class but it is not compatible with the new
     * class.
     *
     * @return `false` if there is no existing class, `true` if there is and the merge succeeded.
     */
    private fun tryMergingIntoExistingClass(
        tokenizer: Tokenizer,
        newClassCharacteristics: ClassCharacteristics,
    ): Boolean {
        // Check for the existing class from a previously parsed file. If it could not be found
        // then return.
        val existingClass =
            codebase.findClassInCodebase(newClassCharacteristics.qualifiedName) ?: return false

        // Make sure the new class characteristics are compatible with the old class
        // characteristic.
        val existingCharacteristics = ClassCharacteristics.of(existingClass)
        if (!existingCharacteristics.isCompatible(newClassCharacteristics)) {
            throw ApiParseException(
                "Incompatible $existingClass definitions",
                newClassCharacteristics.fileLocation
            )
        }

        // Add new annotations to the existing class
        val newClassAnnotations = newClassCharacteristics.modifiers.annotations().toSet()
        val existingClassAnnotations = existingCharacteristics.modifiers.annotations().toSet()

        val extraAnnotations = newClassAnnotations.subtract(existingClassAnnotations)
        if (extraAnnotations.isNotEmpty()) {
            existingClass.mutateModifiers { mutateAnnotations { addAll(extraAnnotations) } }
        }

        // Use the latest super class.
        val newSuperClassType = newClassCharacteristics.superClassType
        if (
            newSuperClassType != null && existingCharacteristics.superClassType != newSuperClassType
        ) {
            // Duplicate class with conflicting superclass names are found. Since the class
            // definition found later should be prioritized, overwrite the superclass type.
            existingClass.setSuperClassType(newSuperClassType)
        }

        // Parse the class body adding each member created to the existing class.
        parseClassBody(tokenizer, existingClass, typeItemFactoryForClass(existingClass))

        // Although the class was first defined in a separate file it is being modified in the
        // current file so that may include it in the current API surface.
        existingClass.markExistingClassForCurrentApiSurface()

        return true
    }

    /** Get the [TextTypeItemFactory] for a previously created [ClassItem]. */
    private fun typeItemFactoryForClass(classItem: ClassItem?): TextTypeItemFactory =
        classItem?.let { classToTypeItemFactory[classItem] } ?: globalTypeItemFactory

    /** Parse the class body, adding members to [cl]. */
    private fun parseClassBody(
        tokenizer: Tokenizer,
        cl: DefaultClassItem,
        classTypeItemFactory: TextTypeItemFactory,
    ) {
        var token = tokenizer.requireToken()
        while (true) {
            if ("}" == token) {
                break
            } else if ("ctor" == token) {
                token = tokenizer.requireToken()
                parseConstructor(tokenizer, cl, classTypeItemFactory, token)
            } else if ("method" == token) {
                token = tokenizer.requireToken()
                parseMethod(tokenizer, cl, classTypeItemFactory, token)
            } else if ("field" == token) {
                token = tokenizer.requireToken()
                parseField(tokenizer, cl, classTypeItemFactory, token, false)
            } else if ("enum_constant" == token) {
                token = tokenizer.requireToken()
                parseField(tokenizer, cl, classTypeItemFactory, token, true)
            } else if ("property" == token) {
                token = tokenizer.requireToken()
                parseProperty(tokenizer, cl, classTypeItemFactory, token)
            } else {
                throw ApiParseException("expected ctor, enum_constant, field or method", tokenizer)
            }
            token = tokenizer.requireToken()
        }
    }

    /**
     * Parse a super type string, i.e. a string representing a super class type or a super interface
     * type.
     */
    private fun parseSuperTypeString(tokenizer: Tokenizer, initialToken: String): String {
        var token = getAnnotationCompleteToken(tokenizer, initialToken)

        // Use the token directly if it is complete, otherwise construct the super class type
        // string from as many tokens as necessary.
        return if (!isIncompleteTypeToken(token)) {
            token
        } else {
            buildString {
                append(token)

                // Make sure full super class name is found if there are type use
                // annotations. This can't use [parseType] because the next token might be a
                // separate type (classes only have a single `extends` type, but all
                // interface supertypes are listed as `extends` instead of `implements`).
                // However, this type cannot be an array, so unlike [parseType] this does
                // not need to check if the next token has annotations.
                do {
                    token = getAnnotationCompleteToken(tokenizer, tokenizer.current)
                    append(" ")
                    append(token)
                } while (isIncompleteTypeToken(token))
            }
        }
    }

    /** Encapsulates multiple return values from [parseDeclaredClassType]. */
    private data class DeclaredClassTypeComponents(
        /** The simple name of the class, i.e. not including any outer class prefix. */
        val simpleName: String,
        /** The full name of the class, including outer class prefix. */
        val fullName: String,
        /** The fully qualified name, including package and full name. */
        val qualifiedName: String,
        /** The optional, resolved outer [ClassItem]. */
        val outerClass: DefaultClassItem?,
        /** The set of type parameters. */
        val typeParameterList: TypeParameterList,
        /**
         * The [TextTypeItemFactory] including any type parameters in the [typeParameterList] in its
         * [TextTypeItemFactory.typeParameterScope].
         */
        val typeItemFactory: TextTypeItemFactory,
    )

    /**
     * Splits the declared class type into [DeclaredClassTypeComponents].
     *
     * For example "Foo" would split into full name "Foo" and an empty type parameter list, while
     * `"Foo.Bar<A, B extends java.lang.String, C>"` would split into full name `"Foo.Bar"` and type
     * parameter list with `"A"`,`"B extends java.lang.String"`, and `"C"` as type parameters.
     *
     * If the qualified name matches an existing class then return its information.
     */
    private fun parseDeclaredClassType(
        pkg: DefaultPackageItem,
        declaredClassType: String,
        classFileLocation: FileLocation,
    ): DeclaredClassTypeComponents {
        // Split the declared class type into full name and type parameters.
        val paramIndex = declaredClassType.indexOf('<')
        val (fullName, typeParameterListString) =
            if (paramIndex == -1) {
                Pair(declaredClassType, "")
            } else {
                Pair(
                    declaredClassType.substring(0, paramIndex),
                    declaredClassType.substring(paramIndex)
                )
            }
        val pkgName = pkg.qualifiedName()
        val qualifiedName = qualifiedName(pkgName, fullName)

        // Split the full name into an optional outer class and a simple name.
        val nestedClassIndex = fullName.lastIndexOf('.')
        val (outerClass, simpleName) =
            if (nestedClassIndex == -1) {
                Pair(null, fullName)
            } else {
                val outerClassFullName = fullName.substring(0, nestedClassIndex)
                val qualifiedOuterClassName = qualifiedName(pkgName, outerClassFullName)

                // Search for the outer class in the codebase. This is safe as the outer class
                // always precedes its nested classes.
                val outerClass =
                    codebase.getOrCreateClass(qualifiedOuterClassName, isOuterClass = true)
                        as DefaultClassItem

                val nestedClassName = fullName.substring(nestedClassIndex + 1)
                Pair(outerClass, nestedClassName)
            }

        // Get the [TextTypeItemFactory] for the outer class, if any, from a previously stored one,
        // otherwise use the [globalTypeItemFactory] as the [ClassItem] is a stub and so has no type
        // parameters.
        val outerClassTypeItemFactory = typeItemFactoryForClass(outerClass)

        // Create type parameter list and factory from the string and optional outer class factory.
        val (typeParameterList, typeItemFactory) =
            if (typeParameterListString == "")
                TypeParameterListAndFactory(TypeParameterList.NONE, outerClassTypeItemFactory)
            else
                createTypeParameterList(
                    outerClassTypeItemFactory,
                    "class $qualifiedName",
                    typeParameterListString,
                )

        // Decide which type parameter list and factory to actually use.
        //
        // If the class already exists then reuse its type parameter list and factory, otherwise use
        // the newly created one.
        //
        // The reason for this is that otherwise any types parsed with the newly created factory
        // would reference type parameters in the newly created list which are different to the ones
        // belonging to the existing class.
        val (actualTypeParameterList, actualTypeItemFactory) =
            codebase.findClassInCodebase(qualifiedName)?.let { existingClass ->
                // Check to make sure that the type parameter lists are the same.
                val existingTypeParameterList = existingClass.typeParameterList
                val existingTypeParameterListString = existingTypeParameterList.toString()
                val normalizedTypeParameterListString = typeParameterList.toString()
                if (normalizedTypeParameterListString != existingTypeParameterListString) {
                    val location = existingClass.fileLocation
                    throw ApiParseException(
                        "Inconsistent type parameter list for $qualifiedName, this has $normalizedTypeParameterListString but it was previously defined as $existingTypeParameterListString at $location",
                        classFileLocation
                    )
                }

                Pair(existingTypeParameterList, typeItemFactoryForClass(existingClass))
            }
                ?: Pair(typeParameterList, typeItemFactory)

        return DeclaredClassTypeComponents(
            simpleName = simpleName,
            fullName = fullName,
            qualifiedName = qualifiedName,
            outerClass = outerClass,
            typeParameterList = actualTypeParameterList,
            typeItemFactory = actualTypeItemFactory,
        )
    }

    /**
     * If the [startingToken] contains the beginning of an annotation, pulls additional tokens from
     * [tokenizer] to complete the annotation, returning the full token. If there isn't an
     * annotation, returns the original [startingToken].
     *
     * When the method returns, the [tokenizer] will point to the token after the end of the
     * returned string.
     */
    private fun getAnnotationCompleteToken(tokenizer: Tokenizer, startingToken: String): String {
        return if (startingToken.contains('@')) {
            val prefix = startingToken.substringBefore('@')
            val annotationStart = startingToken.substring(startingToken.indexOf('@'))
            val annotation = getAnnotationSource(tokenizer, annotationStart)
            "$prefix$annotation"
        } else {
            tokenizer.requireToken()
            startingToken
        }
    }

    /**
     * If the [startingToken] is the beginning of an annotation, returns the annotation parsed from
     * the [tokenizer]. Returns null otherwise.
     *
     * When the method returns, the [tokenizer] will point to the token after the annotation.
     */
    private fun getAnnotationSource(tokenizer: Tokenizer, startingToken: String): String? {
        var token = startingToken
        if (token.startsWith('@')) {
            // Annotation
            var annotation = token

            // Restore annotations that were shortened on export
            annotation = unshortenAnnotation(annotation)
            token = tokenizer.requireToken()
            if (token == "(") {
                // Annotation arguments; potentially nested
                var balance = 0
                val start = tokenizer.offset() - 1
                while (true) {
                    if (token == "(") {
                        balance++
                    } else if (token == ")") {
                        balance--
                        if (balance == 0) {
                            break
                        }
                    }
                    token = tokenizer.requireToken()
                }
                annotation += tokenizer.getStringFromOffset(start)
                // Move the tokenizer so that when the method returns it points to the token after
                // the end of the annotation.
                tokenizer.requireToken()
            }
            return annotation
        } else {
            return null
        }
    }

    /**
     * Collects all the sequential annotations from the [tokenizer] beginning with [startingToken],
     * returning them as a (possibly empty) list.
     *
     * When the method returns, the [tokenizer] will point to the token after the annotation list.
     */
    private fun getAnnotations(tokenizer: Tokenizer, startingToken: String) = buildList {
        var token = startingToken
        while (true) {
            val annotationSource = getAnnotationSource(tokenizer, token) ?: break
            token = tokenizer.current
            DefaultAnnotationItem.create(codebase, annotationSource)?.let { annotationItem ->
                add(annotationItem)
            }
        }
    }

    /**
     * Create [ParameterItem]s for the [containingCallable] from the [parameters] using the
     * [typeItemFactory] to create types.
     *
     * This is called from within the constructor of the [containingCallable] so must only access
     * its `name` and its reference. In particularly it must not access its
     * [CallableItem.parameters] property as this is called during its initialization.
     */
    private fun createParameterItems(
        containingCallable: CallableItem,
        parameters: List<ParameterInfo>,
        typeItemFactory: TextTypeItemFactory
    ): List<ParameterItem> {
        val methodFingerprint = MethodFingerprint(containingCallable.name(), parameters.size)
        return parameters.map { it.create(containingCallable, typeItemFactory, methodFingerprint) }
    }

    private fun parseConstructor(
        tokenizer: Tokenizer,
        containingClass: DefaultClassItem,
        classTypeItemFactory: TextTypeItemFactory,
        startingToken: String
    ) {
        var token = startingToken
        val method: ConstructorItem

        // Metalava: including annotations in file now
        val annotations = getAnnotations(tokenizer, token)
        token = tokenizer.current
        val modifiers = parseModifiers(tokenizer, token, annotations)
        token = tokenizer.current

        // Get a TypeParameterList and accompanying TypeItemFactory
        val (typeParameterList, typeItemFactory) =
            if ("<" == token) {
                parseTypeParameterList(tokenizer, classTypeItemFactory).also {
                    token = tokenizer.requireToken()
                }
            } else {
                TypeParameterListAndFactory(TypeParameterList.NONE, classTypeItemFactory)
            }

        tokenizer.assertIdent(token)
        val name: String =
            token.substring(
                token.lastIndexOf('.') + 1
            ) // For nested classes, strip outer classes from name
        val parameters = parseParameterList(tokenizer)
        token = tokenizer.requireToken()
        var throwsList = emptyList<ExceptionTypeItem>()
        if ("throws" == token) {
            throwsList = parseThrows(tokenizer, typeItemFactory)
            token = tokenizer.current
        }
        if (";" != token) {
            throw ApiParseException("expected ; found $token", tokenizer)
        }

        method =
            itemFactory.createConstructorItem(
                fileLocation = tokenizer.fileLocation(),
                modifiers = modifiers,
                documentationFactory = ItemDocumentation.NONE_FACTORY,
                name = name,
                containingClass = containingClass,
                typeParameterList = typeParameterList,
                returnType = containingClass.type(),
                parameterItemsFactory = { methodItem ->
                    createParameterItems(methodItem, parameters, typeItemFactory)
                },
                throwsTypes = throwsList,
                // Signature files do not track implicit constructors, all constructors are treated
                // the same as whether it was created by the compiler or in the source has no effect
                // on the API surface.
                implicitConstructor = false,
            )
        method.markForCurrentApiSurface()

        if (!containingClass.constructors().contains(method)) {
            containingClass.addConstructor(method)
        }
    }

    private fun parseMethod(
        tokenizer: Tokenizer,
        cl: DefaultClassItem,
        classTypeItemFactory: TextTypeItemFactory,
        startingToken: String
    ) {
        var token = startingToken
        val method: MethodItem

        // Metalava: including annotations in file now
        val annotations = getAnnotations(tokenizer, token)
        token = tokenizer.current
        val modifiers = parseModifiers(tokenizer, token, annotations)
        token = tokenizer.current

        // Get a TypeParameterList and accompanying TypeParameterScope
        val (typeParameterList, typeItemFactory) =
            if ("<" == token) {
                parseTypeParameterList(tokenizer, classTypeItemFactory).also {
                    token = tokenizer.requireToken()
                }
            } else {
                TypeParameterListAndFactory(TypeParameterList.NONE, classTypeItemFactory)
            }

        tokenizer.assertIdent(token)

        val returnTypeString: String
        val parameters: List<ParameterInfo>
        val name: String
        if (format.kotlinNameTypeOrder) {
            // Kotlin style: parse the name, the parameter list, then the return type.
            name = token
            parameters = parseParameterList(tokenizer)
            token = tokenizer.requireToken()
            if (token != ":") {
                throw ApiParseException(
                    "Expecting \":\" after parameter list, found $token.",
                    tokenizer
                )
            }
            token = tokenizer.requireToken()
            tokenizer.assertIdent(token)
            returnTypeString = scanForTypeString(tokenizer, token)
            token = tokenizer.current
        } else {
            // Java style: parse the return type, the name, and then the parameter list.
            returnTypeString = scanForTypeString(tokenizer, token)
            token = tokenizer.current
            tokenizer.assertIdent(token)
            name = token
            parameters = parseParameterList(tokenizer)
            token = tokenizer.requireToken()
        }

        val returnType =
            typeItemFactory.getMethodReturnType(
                returnTypeString,
                annotations,
                MethodFingerprint(name, parameters.size),
                cl.isAnnotationType()
            )
        synchronizeNullability(returnType, modifiers)

        if (cl.isInterface() && !modifiers.isDefault() && !modifiers.isStatic()) {
            modifiers.setAbstract(true)
        }

        var throwsList = emptyList<ExceptionTypeItem>()
        var defaultAnnotationMethodValue = ""

        when (token) {
            "throws" -> {
                throwsList = parseThrows(tokenizer, typeItemFactory)
                token = tokenizer.current
            }
            "default" -> {
                defaultAnnotationMethodValue = parseDefault(tokenizer)
                token = tokenizer.current
            }
        }
        if (";" != token) {
            throw ApiParseException("expected ; found $token", tokenizer)
        }

        method =
            itemFactory.createMethodItem(
                fileLocation = tokenizer.fileLocation(),
                modifiers = modifiers,
                documentationFactory = ItemDocumentation.NONE_FACTORY,
                name = name,
                containingClass = cl,
                typeParameterList = typeParameterList,
                returnType = returnType,
                parameterItemsFactory = { containingCallable ->
                    createParameterItems(containingCallable, parameters, typeItemFactory)
                },
                throwsTypes = throwsList,
                annotationDefault = defaultAnnotationMethodValue,
            )

        // Ignore enum synthetic methods. They are no longer included in signature files as they add
        // no information. However, they did use to be included and so this filters them out to
        // ensure that the resulting Codebase is consistent with the original source Codebase.
        if (method.isEnumSyntheticMethod()) return

        method.markForCurrentApiSurface()

        // If the method already exists in the class item because it was defined in a previous
        // signature file then replace it with this one, otherwise just add this method.
        cl.replaceOrAddMethod(method)
    }

    private fun parseField(
        tokenizer: Tokenizer,
        cl: DefaultClassItem,
        classTypeItemFactory: TextTypeItemFactory,
        startingToken: String,
        isEnumConstant: Boolean,
    ) {
        var token = startingToken
        val annotations = getAnnotations(tokenizer, token)
        token = tokenizer.current
        val modifiers = parseModifiers(tokenizer, token, annotations)
        token = tokenizer.current
        tokenizer.assertIdent(token)

        val typeString: String
        val name: String
        if (format.kotlinNameTypeOrder) {
            // Kotlin style: parse the name, then the type.
            name = parseNameWithColon(token, tokenizer)
            token = tokenizer.requireToken()
            tokenizer.assertIdent(token)
            typeString = scanForTypeString(tokenizer, token)
            token = tokenizer.current
        } else {
            // Java style: parse the name, then the type.
            typeString = scanForTypeString(tokenizer, token)
            token = tokenizer.current
            tokenizer.assertIdent(token)
            name = token
            token = tokenizer.requireToken()
        }

        // Get the optional value.
        val valueString =
            if ("=" == token) {
                token = tokenizer.requireToken(false)
                token.also { token = tokenizer.requireToken() }
            } else null

        // Parse the type string and then synchronize the field's nullability with the type.
        val type =
            classTypeItemFactory.getFieldType(
                underlyingType = typeString,
                isEnumConstant = isEnumConstant,
                isFinal = modifiers.isFinal(),
                isInitialValueNonNull = { valueString != null && valueString != "null" },
                itemAnnotations = annotations,
            )
        synchronizeNullability(type, modifiers)

        // Parse the value string.
        val fieldValue =
            valueString?.let { FixedFieldValue(parseValue(type, valueString, tokenizer)) }

        if (";" != token) {
            throw ApiParseException("expected ; found $token", tokenizer)
        }
        val field =
            itemFactory.createFieldItem(
                fileLocation = tokenizer.fileLocation(),
                modifiers = modifiers,
                documentationFactory = ItemDocumentation.NONE_FACTORY,
                name = name,
                containingClass = cl,
                type = type,
                isEnumConstant = isEnumConstant,
                fieldValue = fieldValue,
            )
        field.markForCurrentApiSurface()
        cl.addField(field)
    }

    private fun parseModifiers(
        tokenizer: Tokenizer,
        startingToken: String?,
        annotations: List<AnnotationItem>
    ): DefaultModifierList {
        var token = startingToken
        val modifiers = createModifiers(VisibilityLevel.PACKAGE_PRIVATE, annotations)

        processModifiers@ while (true) {
            token =
                when (token) {
                    "public" -> {
                        modifiers.setVisibilityLevel(VisibilityLevel.PUBLIC)
                        tokenizer.requireToken()
                    }
                    "protected" -> {
                        modifiers.setVisibilityLevel(VisibilityLevel.PROTECTED)
                        tokenizer.requireToken()
                    }
                    "private" -> {
                        modifiers.setVisibilityLevel(VisibilityLevel.PRIVATE)
                        tokenizer.requireToken()
                    }
                    "internal" -> {
                        modifiers.setVisibilityLevel(VisibilityLevel.INTERNAL)
                        tokenizer.requireToken()
                    }
                    "static" -> {
                        modifiers.setStatic(true)
                        tokenizer.requireToken()
                    }
                    "final" -> {
                        modifiers.setFinal(true)
                        tokenizer.requireToken()
                    }
                    "deprecated" -> {
                        modifiers.setDeprecated(true)
                        tokenizer.requireToken()
                    }
                    "abstract" -> {
                        modifiers.setAbstract(true)
                        tokenizer.requireToken()
                    }
                    "transient" -> {
                        modifiers.setTransient(true)
                        tokenizer.requireToken()
                    }
                    "volatile" -> {
                        modifiers.setVolatile(true)
                        tokenizer.requireToken()
                    }
                    "sealed" -> {
                        modifiers.setSealed(true)
                        tokenizer.requireToken()
                    }
                    "default" -> {
                        modifiers.setDefault(true)
                        tokenizer.requireToken()
                    }
                    "synchronized" -> {
                        modifiers.setSynchronized(true)
                        tokenizer.requireToken()
                    }
                    "native" -> {
                        modifiers.setNative(true)
                        tokenizer.requireToken()
                    }
                    "strictfp" -> {
                        modifiers.setStrictFp(true)
                        tokenizer.requireToken()
                    }
                    "infix" -> {
                        modifiers.setInfix(true)
                        tokenizer.requireToken()
                    }
                    "operator" -> {
                        modifiers.setOperator(true)
                        tokenizer.requireToken()
                    }
                    "inline" -> {
                        modifiers.setInline(true)
                        tokenizer.requireToken()
                    }
                    "value" -> {
                        modifiers.setValue(true)
                        tokenizer.requireToken()
                    }
                    "suspend" -> {
                        modifiers.setSuspend(true)
                        tokenizer.requireToken()
                    }
                    "vararg" -> {
                        modifiers.setVarArg(true)
                        tokenizer.requireToken()
                    }
                    "fun" -> {
                        modifiers.setFunctional(true)
                        tokenizer.requireToken()
                    }
                    "data" -> {
                        modifiers.setData(true)
                        tokenizer.requireToken()
                    }
                    else -> break@processModifiers
                }
        }
        return modifiers
    }

    /** Creates a [DefaultModifierList], setting the deprecation based on the [annotations]. */
    private fun createModifiers(
        visibility: VisibilityLevel,
        annotations: List<AnnotationItem>
    ): DefaultModifierList {
        val modifiers = DefaultModifierList(visibility, annotations)
        // @Deprecated is also treated as a "modifier"
        if (annotations.any { it.qualifiedName == JAVA_LANG_DEPRECATED }) {
            modifiers.setDeprecated(true)
        }
        return modifiers
    }

    private fun parseValue(
        type: TypeItem,
        value: String?,
        fileLocationTracker: FileLocationTracker,
    ): Any? {
        return if (value != null) {
            if (type is PrimitiveTypeItem) {
                parsePrimitiveValue(type, value, fileLocationTracker)
            } else if (type.isString()) {
                if ("null" == value) {
                    null
                } else {
                    javaUnescapeString(value.substring(1, value.length - 1))
                }
            } else {
                value
            }
        } else null
    }

    private fun parsePrimitiveValue(
        type: PrimitiveTypeItem,
        value: String,
        fileLocationTracker: FileLocationTracker,
    ): Any {
        return when (type.kind) {
            Primitive.BOOLEAN ->
                if ("true" == value) java.lang.Boolean.TRUE else java.lang.Boolean.FALSE
            Primitive.BYTE,
            Primitive.SHORT,
            Primitive.INT -> Integer.valueOf(value)
            Primitive.LONG -> java.lang.Long.valueOf(value.substring(0, value.length - 1))
            Primitive.FLOAT ->
                when (value) {
                    "(1.0f/0.0f)",
                    "(1.0f / 0.0f)" -> Float.POSITIVE_INFINITY
                    "(-1.0f/0.0f)",
                    "(-1.0f / 0.0f)" -> Float.NEGATIVE_INFINITY
                    "(0.0f/0.0f)",
                    "(0.0f / 0.0f)" -> Float.NaN
                    else -> java.lang.Float.valueOf(value)
                }
            Primitive.DOUBLE ->
                when (value) {
                    "(1.0/0.0)",
                    "(1.0 / 0.0)" -> Double.POSITIVE_INFINITY
                    "(-1.0/0.0)",
                    "(-1.0 / 0.0)" -> Double.NEGATIVE_INFINITY
                    "(0.0/0.0)",
                    "(0.0 / 0.0)" -> Double.NaN
                    else -> java.lang.Double.valueOf(value)
                }
            Primitive.CHAR -> value.toInt().toChar()
            Primitive.VOID ->
                throw ApiParseException(
                    "Found value $value assigned to void type",
                    fileLocationTracker
                )
        }
    }

    private fun parseProperty(
        tokenizer: Tokenizer,
        cl: DefaultClassItem,
        classTypeItemFactory: TextTypeItemFactory,
        startingToken: String
    ) {
        var token = startingToken

        // Metalava: including annotations in file now
        val annotations = getAnnotations(tokenizer, token)
        token = tokenizer.current
        val modifiers = parseModifiers(tokenizer, token, annotations)
        token = tokenizer.current
        tokenizer.assertIdent(token)

        val typeString: String
        val name: String
        if (format.kotlinNameTypeOrder) {
            // Kotlin style: parse the name, then the type.
            name = parseNameWithColon(token, tokenizer)
            token = tokenizer.requireToken()
            tokenizer.assertIdent(token)
            typeString = scanForTypeString(tokenizer, token)
            token = tokenizer.current
        } else {
            // Java style: parse the type, then the name.
            typeString = scanForTypeString(tokenizer, token)
            token = tokenizer.current
            tokenizer.assertIdent(token)
            name = token
            token = tokenizer.requireToken()
        }
        val type = classTypeItemFactory.getGeneralType(typeString)
        synchronizeNullability(type, modifiers)

        if (";" != token) {
            throw ApiParseException("expected ; found $token", tokenizer)
        }
        val property =
            itemFactory.createPropertyItem(tokenizer.fileLocation(), modifiers, name, cl, type)
        property.markForCurrentApiSurface()
        cl.addProperty(property)
    }

    private fun parseTypeParameterList(
        tokenizer: Tokenizer,
        enclosingTypeItemFactory: TextTypeItemFactory,
    ): TypeParameterListAndFactory<TextTypeItemFactory> {
        var token: String
        val start = tokenizer.offset() - 1
        var balance = 1
        while (balance > 0) {
            token = tokenizer.requireToken()
            if (token == "<") {
                balance++
            } else if (token == ">") {
                balance--
            }
        }
        val typeParameterListString = tokenizer.getStringFromOffset(start)
        return if (typeParameterListString.isEmpty()) {
            TypeParameterListAndFactory(TypeParameterList.NONE, enclosingTypeItemFactory)
        } else {
            // Use the file location as a part of the description of the scope as at this point
            // there is no other information available.
            val scopeDescription = "${tokenizer.fileLocation()}"
            createTypeParameterList(
                enclosingTypeItemFactory,
                scopeDescription,
                typeParameterListString
            )
        }
    }

    /**
     * Creates a [TypeParameterList] and accompanying [TypeParameterScope].
     *
     * The [typeParameterListString] should be the string representation of a list of type
     * parameters, like "<A>" or "<A, B extends java.lang.String, C>".
     *
     * @return a [Pair] of [TypeParameterList] and [TextTypeItemFactory] that contains those type
     *   parameters.
     */
    private fun createTypeParameterList(
        enclosingTypeItemFactory: TextTypeItemFactory,
        scopeDescription: String,
        typeParameterListString: String
    ): TypeParameterListAndFactory<TextTypeItemFactory> {
        // Split the type parameter list string into a list of strings, one for each type
        // parameter.
        val typeParameterStrings = TextTypeParser.typeParameterStrings(typeParameterListString)

        // Create the List<TypeParameterItem> and the corresponding TypeItemFactory that can be
        // used to resolve TypeParameterItems from the list. This performs the construction in two
        // stages to handle cycles between the parameters.
        return DefaultTypeParameterList.createTypeParameterItemsAndFactory(
            enclosingTypeItemFactory,
            scopeDescription,
            typeParameterStrings,
            // Create a `TextTypeParameterItem` from the type parameter string.
            { createTypeParameterItem(it) },
            // Create, set and return the [BoundsTypeItem] list.
            { typeItemFactory, typeParameterString ->
                val boundsStringList = extractTypeParameterBoundsStringList(typeParameterString)
                boundsStringList.map { typeItemFactory.getBoundsType(it) }
            },
        )
    }

    /**
     * Create a partially initialized [DefaultTypeParameterItem].
     *
     * This extracts the [TypeParameterItem.isReified] and [TypeParameterItem.name] from the
     * [typeParameterString] and creates a [DefaultTypeParameterItem] with those properties
     * initialized but the [DefaultTypeParameterItem.bounds] is not.
     */
    private fun createTypeParameterItem(typeParameterString: String): DefaultTypeParameterItem {
        val length = typeParameterString.length
        var nameEnd = length

        val isReified = typeParameterString.startsWith("reified ")
        val nameStart =
            if (isReified) {
                8 // "reified ".length
            } else {
                0
            }

        for (i in nameStart until length) {
            val c = typeParameterString[i]
            if (!Character.isJavaIdentifierPart(c)) {
                nameEnd = i
                break
            }
        }
        val name = typeParameterString.substring(nameStart, nameEnd)

        // TODO: Type use annotations support will need to handle annotations on the parameter.
        val modifiers = DefaultModifierList(VisibilityLevel.PUBLIC)

        return itemFactory.createTypeParameterItem(
            modifiers = modifiers,
            name = name,
            isReified = isReified,
        )
    }

    /**
     * Parses a list of parameters. Before calling, [tokenizer] should point to the token *before*
     * the opening `(` of the parameter list (the method starts by calling
     * [Tokenizer.requireToken]).
     *
     * When the method returns, [tokenizer] will point to the closing `)` of the parameter list.
     */
    private fun parseParameterList(
        tokenizer: Tokenizer,
    ): List<ParameterInfo> {
        val parameters = mutableListOf<ParameterInfo>()
        var token: String = tokenizer.requireToken()
        if ("(" != token) {
            throw ApiParseException("expected (, was $token", tokenizer)
        }
        token = tokenizer.requireToken()
        var index = 0
        while (true) {
            if (")" == token) {
                // All parameters are parsed, return them.
                return parameters
            }

            // Each item can be
            // optional annotations optional-modifiers type-with-use-annotations-and-generics
            // optional-name optional-equals-default-value

            // Used to represent the presence of a default value, instead of showing the entire
            // default value
            val hasOptionalKeyword = token == "optional"
            if (hasOptionalKeyword) {
                token = tokenizer.requireToken()
            }

            // Metalava: including annotations in file now
            val annotations = getAnnotations(tokenizer, token)
            token = tokenizer.current
            val modifiers = parseModifiers(tokenizer, token, annotations)
            token = tokenizer.current

            val typeString: String
            val name: String
            val publicName: String?
            if (format.kotlinNameTypeOrder) {
                // Kotlin style: parse the name (only considered a public name if it is not `_`,
                // which is used as a placeholder for params without public names), then the type.
                name = parseNameWithColon(token, tokenizer)
                publicName =
                    if (name == "_") {
                        null
                    } else {
                        name
                    }
                token = tokenizer.requireToken()
                // Token should now represent the type
                typeString = scanForTypeString(tokenizer, token)
                token = tokenizer.current
            } else {
                // Java style: parse the type, then the public name if it has one.
                typeString = scanForTypeString(tokenizer, token)
                token = tokenizer.current
                if (Tokenizer.isIdent(token) && token != "=") {
                    name = token
                    publicName = name
                    token = tokenizer.requireToken()
                } else {
                    name = "arg" + (index + 1)
                    publicName = null
                }
            }

            var defaultValueString: String? = null
            if ("=" == token) {
                if (hasOptionalKeyword) {
                    throw ApiParseException(
                        "cannot have both optional keyword and default value",
                        tokenizer
                    )
                }
                defaultValueString = tokenizer.requireToken(true)
                val sb = StringBuilder(defaultValueString)
                if (defaultValueString == "{") {
                    var balance = 1
                    while (balance > 0) {
                        token = tokenizer.requireToken(parenIsSep = false, eatWhitespace = false)
                        sb.append(token)
                        if (token == "{") {
                            balance++
                        } else if (token == "}") {
                            balance--
                            if (balance == 0) {
                                break
                            }
                        }
                    }
                    token = tokenizer.requireToken()
                } else {
                    var balance = if (defaultValueString == "(") 1 else 0
                    while (true) {
                        token = tokenizer.requireToken(parenIsSep = true, eatWhitespace = false)
                        if ((token.endsWith(",") || token.endsWith(")")) && balance <= 0) {
                            if (token.length > 1) {
                                sb.append(token, 0, token.length - 1)
                                token = token[token.length - 1].toString()
                            }
                            break
                        }
                        sb.append(token)
                        if (token == "(") {
                            balance++
                        } else if (token == ")") {
                            balance--
                        }
                    }
                }
                defaultValueString = sb.toString()
            }
            when (token) {
                "," -> {
                    token = tokenizer.requireToken()
                }
                ")" -> {
                    // closing parenthesis
                }
                else -> {
                    throw ApiParseException("expected , or ), found $token", tokenizer)
                }
            }

            // Select the DefaultValue for the parameter.
            val defaultValue =
                when {
                    hasOptionalKeyword ->
                        // It has an optional keyword, so it has a default value but the actual
                        // value is not known.
                        DefaultValue.UNKNOWN
                    defaultValueString == null ->
                        // It has neither an optional keyword nor an actual default value.
                        DefaultValue.NONE
                    else ->
                        // It has an actual default value.
                        DefaultValue.fixedDefaultValue(defaultValueString)
                }
            parameters.add(
                ParameterInfo(
                    name,
                    publicName,
                    defaultValue,
                    typeString,
                    modifiers,
                    tokenizer.fileLocation(),
                    index
                )
            )
            index++
        }
    }

    /**
     * Container for parsed information on a parameter. This is an intermediate step before a
     * [ParameterItem] is created, which is needed because
     * [TextTypeItemFactory.getMethodParameterType] requires a [MethodFingerprint] with the total
     * number of method parameters.
     */
    private inner class ParameterInfo(
        val name: String,
        val publicName: String?,
        val defaultValue: DefaultValue,
        val typeString: String,
        val modifiers: DefaultModifierList,
        val location: FileLocation,
        val index: Int
    ) {
        /** Turn this [ParameterInfo] into a [ParameterItem] by parsing the [typeString]. */
        fun create(
            containingCallable: CallableItem,
            typeItemFactory: TextTypeItemFactory,
            methodFingerprint: MethodFingerprint
        ): ParameterItem {
            val type =
                typeItemFactory.getMethodParameterType(
                    typeString,
                    modifiers.annotations(),
                    methodFingerprint,
                    index,
                    modifiers.isVarArg()
                )
            synchronizeNullability(type, modifiers)

            val parameter =
                itemFactory.createParameterItem(
                    location,
                    modifiers,
                    name,
                    { publicName },
                    containingCallable,
                    index,
                    type,
                    defaultValue,
                )

            parameter.markForCurrentApiSurface()

            return parameter
        }
    }

    private fun parseDefault(tokenizer: Tokenizer): String {
        return buildString {
            while (true) {
                val token = tokenizer.requireToken()
                if (";" == token) {
                    break
                } else {
                    append(token)
                }
            }
        }
    }

    private fun parseThrows(
        tokenizer: Tokenizer,
        typeItemFactory: TextTypeItemFactory,
    ): List<ExceptionTypeItem> {
        var token = tokenizer.requireToken()
        val throwsList = buildList {
            var comma = true
            while (true) {
                when (token) {
                    ";" -> {
                        break
                    }
                    "," -> {
                        if (comma) {
                            throw ApiParseException("Expected exception, got ','", tokenizer)
                        }
                        comma = true
                    }
                    else -> {
                        if (!comma) {
                            throw ApiParseException("Expected ',' or ';' got $token", tokenizer)
                        }
                        comma = false
                        val exceptionType = typeItemFactory.getExceptionType(token)
                        add(exceptionType)
                    }
                }
                token = tokenizer.requireToken()
            }
        }

        return throwsList
    }

    /**
     * Scans the token stream from [tokenizer] for a type string, starting with the [startingToken]
     * and ensuring that the full type string is gathered, even when there are type-use annotations.
     *
     * After this method is called, `tokenizer.current` will point to the token after the type.
     *
     * Note: this **should not** be used when the token after the type could contain annotations,
     * such as when multiple types appear as consecutive tokens. (This happens in the `implements`
     * list of a class definition, e.g. `class Foo implements test.pkg.Bar test.pkg.@A Baz`.)
     *
     * To handle arrays with type-use annotations, this looks forward at the next token and includes
     * it if it contains an annotation. This is necessary to handle type strings like "Foo @A []".
     */
    private fun scanForTypeString(tokenizer: Tokenizer, startingToken: String): String {
        var prev = getAnnotationCompleteToken(tokenizer, startingToken)
        var type = prev
        var token = tokenizer.current
        // Look both at the last used token and the next one:
        // If the last token has annotations, the type string was broken up by annotations, and the
        // next token is also part of the type.
        // If the next token has annotations, this is an array type like "Foo @A []", so the next
        // token is part of the type.
        while (isIncompleteTypeToken(prev) || isIncompleteTypeToken(token)) {
            token = getAnnotationCompleteToken(tokenizer, token)
            type += " $token"
            prev = token
            token = tokenizer.current
        }
        return type
    }

    /**
     * Synchronize nullability annotations on the API item and [TypeNullability].
     *
     * If the type string uses a Kotlin nullability suffix, this adds an annotation representing
     * that nullability to [modifiers].
     *
     * @param typeItem the type of the API item.
     * @param modifiers the API item's modifiers.
     */
    private fun synchronizeNullability(typeItem: TypeItem, modifiers: DefaultModifierList) {
        if (typeParser.kotlinStyleNulls) {
            // Add an annotation to the context item for the type's nullability if applicable.
            val annotationToAdd =
                // Treat varargs as non-null for consistency with the psi model.
                if (typeItem is ArrayTypeItem && typeItem.isVarargs) {
                    ANDROIDX_NONNULL
                } else {
                    val nullability = typeItem.modifiers.nullability
                    if (typeItem !is PrimitiveTypeItem && nullability == TypeNullability.NONNULL) {
                        ANDROIDX_NONNULL
                    } else if (nullability == TypeNullability.NULLABLE) {
                        ANDROIDX_NULLABLE
                    } else {
                        // No annotation to add, return.
                        return
                    }
                }
            modifiers.addAnnotation(codebase.createAnnotation("@$annotationToAdd"))
        }
    }

    /**
     * Determines whether the [type] is an incomplete type string broken up by annotations. This is
     * the case when there's an annotation that isn't contained within a parameter list (because
     * [Tokenizer.requireToken] handles not breaking in the middle of a parameter list).
     */
    private fun isIncompleteTypeToken(type: String): Boolean {
        val firstAnnotationIndex = type.indexOf('@')
        val paramStartIndex = type.indexOf('<')
        val lastAnnotationIndex = type.lastIndexOf('@')
        val paramEndIndex = type.lastIndexOf('>')
        return firstAnnotationIndex != -1 &&
            (paramStartIndex == -1 ||
                firstAnnotationIndex < paramStartIndex ||
                paramEndIndex == -1 ||
                paramEndIndex < lastAnnotationIndex)
    }

    /**
     * For Kotlin-style name/type ordering in signature files, the name is generally followed by a
     * colon (besides methods, where the colon comes after the parameter list). This method takes
     * the name [token] and removes the trailing colon, throwing an [ApiParseException] if one isn't
     * present (the [tokenizer] is only used for context for the error, if needed).
     */
    private fun parseNameWithColon(token: String, tokenizer: Tokenizer): String {
        if (!token.endsWith(':')) {
            throw ApiParseException("Expecting name ending with \":\" but found $token.", tokenizer)
        }
        return token.removeSuffix(":")
    }

    private fun qualifiedName(pkg: String, className: String): String {
        return "$pkg.$className"
    }

    private val stats
        get() =
            Stats(
                codebase.getPackages().allClasses().count(),
                typeParser.requests,
                typeParser.cacheSkip,
                typeParser.cacheHit,
                typeParser.cacheSize,
            )

    data class Stats(
        val totalClasses: Int,
        val typeCacheRequests: Int,
        val typeCacheSkip: Int,
        val typeCacheHit: Int,
        val typeCacheSize: Int,
    )
}
