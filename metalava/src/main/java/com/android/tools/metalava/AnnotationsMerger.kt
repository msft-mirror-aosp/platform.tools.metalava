/*
 * Copyright (C) 2017 The Android Open Source Project
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

package com.android.tools.metalava

import com.android.SdkConstants.AMP_ENTITY
import com.android.SdkConstants.APOS_ENTITY
import com.android.SdkConstants.ATTR_NAME
import com.android.SdkConstants.DOT_CLASS
import com.android.SdkConstants.DOT_JAR
import com.android.SdkConstants.DOT_XML
import com.android.SdkConstants.DOT_ZIP
import com.android.SdkConstants.GT_ENTITY
import com.android.SdkConstants.LT_ENTITY
import com.android.SdkConstants.QUOT_ENTITY
import com.android.SdkConstants.TYPE_DEF_FLAG_ATTRIBUTE
import com.android.SdkConstants.TYPE_DEF_VALUE_ATTRIBUTE
import com.android.tools.lint.annotations.Extractor.ANDROID_INT_DEF
import com.android.tools.lint.annotations.Extractor.ANDROID_NOTNULL
import com.android.tools.lint.annotations.Extractor.ANDROID_NULLABLE
import com.android.tools.lint.annotations.Extractor.ANDROID_STRING_DEF
import com.android.tools.lint.annotations.Extractor.ATTR_PURE
import com.android.tools.lint.annotations.Extractor.ATTR_VAL
import com.android.tools.lint.annotations.Extractor.IDEA_CONTRACT
import com.android.tools.lint.annotations.Extractor.IDEA_MAGIC
import com.android.tools.lint.annotations.Extractor.IDEA_NOTNULL
import com.android.tools.lint.annotations.Extractor.IDEA_NULLABLE
import com.android.tools.lint.annotations.Extractor.SUPPORT_NOTNULL
import com.android.tools.lint.annotations.Extractor.SUPPORT_NULLABLE
import com.android.tools.lint.detector.api.getChildren
import com.android.tools.metalava.cli.common.MetalavaCliException
import com.android.tools.metalava.model.ANDROIDX_INT_DEF
import com.android.tools.metalava.model.ANDROIDX_NONNULL
import com.android.tools.metalava.model.ANDROIDX_NULLABLE
import com.android.tools.metalava.model.ANDROIDX_STRING_DEF
import com.android.tools.metalava.model.ANNOTATION_VALUE_TRUE
import com.android.tools.metalava.model.AnnotationAttribute
import com.android.tools.metalava.model.AnnotationItem
import com.android.tools.metalava.model.ClassItem
import com.android.tools.metalava.model.Codebase
import com.android.tools.metalava.model.DefaultAnnotationAttribute
import com.android.tools.metalava.model.DefaultAnnotationItem
import com.android.tools.metalava.model.Item
import com.android.tools.metalava.model.ModifierList
import com.android.tools.metalava.model.PackageItem
import com.android.tools.metalava.model.TraversingVisitor
import com.android.tools.metalava.model.hasAnnotation
import com.android.tools.metalava.model.source.SourceParser
import com.android.tools.metalava.model.source.SourceSet
import com.android.tools.metalava.model.text.ApiFile
import com.android.tools.metalava.model.text.ApiParseException
import com.android.tools.metalava.model.text.SignatureFile
import com.android.tools.metalava.model.visitors.ApiVisitor
import com.android.tools.metalava.reporter.Issues
import com.android.tools.metalava.reporter.Reporter
import com.android.tools.metalava.xml.parseDocument
import com.google.common.io.Closeables
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.lang.reflect.Field
import java.util.jar.JarInputStream
import java.util.regex.Pattern
import java.util.zip.ZipEntry
import kotlin.text.Charsets.UTF_8
import org.w3c.dom.Document
import org.w3c.dom.Element
import org.xml.sax.SAXParseException

/** Merges annotations into classes already registered in the given [Codebase] */
@Suppress("DEPRECATION")
class AnnotationsMerger(
    private val sourceParser: SourceParser,
    private val codebase: Codebase,
    private val reporter: Reporter,
) {

    /** Merge annotations which will appear in the output API. */
    fun mergeQualifierAnnotationsFromFiles(files: List<File>) {
        mergeAll(
            files,
            ::mergeQualifierAnnotationsFromFile,
            ::mergeAndValidateQualifierAnnotationsFromJavaStubsCodebase
        )
    }

    /** Merge annotations which control what is included in the output API. */
    fun mergeInclusionAnnotationsFromFiles(files: List<File>) {
        mergeAll(
            files,
            {
                throw MetalavaCliException(
                    "External inclusion annotations files must be .java, found ${it.path}"
                )
            },
            ::mergeInclusionAnnotationsFromCodebase
        )
    }

    /**
     * Given a list of directories containing various files, scan those files merging them into the
     * [codebase].
     *
     * All `.java` files are collated and
     */
    private fun mergeAll(
        mergeAnnotations: List<File>,
        mergeFile: (File) -> Unit,
        mergeJavaStubsCodebase: (Codebase) -> Unit
    ) {
        // Process each file (which are almost certainly directories) separately. That allows for a
        // single Java class to merge in annotations from multiple separate files.
        mergeAnnotations.forEach {
            val javaStubFiles = mutableListOf<File>()
            mergeFileOrDir(it, mergeFile, javaStubFiles)
            if (javaStubFiles.isNotEmpty()) {
                // Set up class path to contain our main sources such that we can
                // resolve types in the stubs
                val roots =
                    SourceSet(options.sources, options.sourcePath).extractRoots(reporter).sourcePath
                val javaStubsCodebase =
                    sourceParser.parseSources(
                        SourceSet(javaStubFiles, roots),
                        SourceSet.empty(),
                        "Codebase loaded from stubs",
                        classPath = options.classpath
                    )
                mergeJavaStubsCodebase(javaStubsCodebase)
            }
        }
    }

    /**
     * Merges annotations from `file`, or from all the files under it if `file` is a directory. All
     * files apart from Java stub files are merged using [mergeFile]. Java stub files are not merged
     * by this method, instead they are added to [javaStubFiles] and should be merged later (so that
     * all the Java stubs can be loaded as a single codebase).
     */
    private fun mergeFileOrDir(
        file: File,
        mergeFile: (File) -> Unit,
        javaStubFiles: MutableList<File>
    ) {
        if (file.isDirectory) {
            val files = file.listFiles()
            if (files != null) {
                for (child in files) {
                    mergeFileOrDir(child, mergeFile, javaStubFiles)
                }
            }
        } else if (file.isFile) {
            if (file.path.endsWith(".java")) {
                javaStubFiles.add(file)
            } else {
                mergeFile(file)
            }
        }
    }

    private fun mergeQualifierAnnotationsFromFile(file: File) {
        if (file.path.endsWith(DOT_JAR) || file.path.endsWith(DOT_ZIP)) {
            mergeFromJar(file)
        } else if (file.path.endsWith(DOT_XML)) {
            try {
                val xml = file.readText()
                mergeQualifierAnnotationsFromXml(file.path, xml)
            } catch (e: IOException) {
                error("I/O problem during transform: $e")
            }
        } else if (
            file.path.endsWith(".txt") ||
                file.path.endsWith(".signatures") ||
                file.path.endsWith(".api")
        ) {
            try {
                // .txt: Old style signature files
                // Others: new signature files (e.g. kotlin-style nullness info)
                mergeQualifierAnnotationsFromSignatureFile(file)
            } catch (e: IOException) {
                error("I/O problem during transform: $e")
            }
        }
    }

    private fun mergeFromJar(jar: File) {
        // Reads in an existing annotations jar and merges in entries found there
        // with the annotations analyzed from source.
        var zis: JarInputStream? = null
        try {
            val fis = FileInputStream(jar)
            zis = JarInputStream(fis)
            var entry: ZipEntry? = zis.nextEntry
            while (entry != null) {
                if (entry.name.endsWith(".xml")) {
                    val bytes = zis.readBytes()
                    val xml = String(bytes, UTF_8)
                    mergeQualifierAnnotationsFromXml(jar.path + ": " + entry, xml)
                }
                entry = zis.nextEntry
            }
        } catch (e: IOException) {
            error("I/O problem during transform: $e")
        } finally {
            try {
                Closeables.close(zis, true /* swallowIOException */)
            } catch (e: IOException) {
                // cannot happen
            }
        }
    }

    private fun mergeQualifierAnnotationsFromXml(path: String, xml: String) {
        try {
            val document = parseDocument(xml, false)
            mergeDocument(document)
        } catch (e: Exception) {
            var message = "Failed to merge $path: $e"
            if (e is SAXParseException) {
                message = "Line ${e.lineNumber}:${e.columnNumber}: $message"
            }
            if (e !is IOException) {
                message += "\n" + e.stackTraceToString().prependIndent("  ")
            }
            error(message)
        }
    }

    private fun mergeQualifierAnnotationsFromSignatureFile(file: File) {
        try {
            val signatureCodebase =
                ApiFile.parseApi(
                    SignatureFile.fromFile(file),
                    codebase.annotationManager,
                    "Signature files for annotation merger: loaded from $file"
                )
            mergeQualifierAnnotationsFromCodebase(signatureCodebase)
        } catch (ex: ApiParseException) {
            val message = "Unable to parse signature file $file: ${ex.message}"
            throw MetalavaCliException(message)
        }
    }

    private fun mergeAndValidateQualifierAnnotationsFromJavaStubsCodebase(
        javaStubsCodebase: Codebase
    ) {
        mergeQualifierAnnotationsFromCodebase(javaStubsCodebase)
        if (options.validateNullabilityFromMergedStubs) {
            options.nullabilityAnnotationsValidator?.validateAll(
                codebase,
                javaStubsCodebase.getTopLevelClassesFromSource().map(ClassItem::qualifiedName)
            )
        }
    }

    private fun mergeQualifierAnnotationsFromCodebase(externalCodebase: Codebase) {
        val visitor =
            object : ComparisonVisitor() {
                override fun compare(old: Item, new: Item) {
                    mergeQualifierAnnotations(old.modifiers.annotations(), new)
                    old.type()?.let { mergeQualifierAnnotations(it.modifiers.annotations, new) }
                }

                override fun removed(old: Item, from: Item?) {
                    // Do not report missing items if there are no annotations to copy.
                    if (old.modifiers.annotations().isEmpty()) {
                        old.type()?.let { typeItem ->
                            if (typeItem.modifiers.annotations.isEmpty()) return
                        }
                            ?: return
                    }

                    reporter.report(
                        Issues.UNMATCHED_MERGE_ANNOTATION,
                        old,
                        "qualifier annotations were given for $old but no matching item was found"
                    )
                }
            }

        CodebaseComparator(
                apiVisitorConfig = @Suppress("DEPRECATION") options.apiVisitorConfig,
            )
            .compare(visitor, externalCodebase, codebase)
    }

    private fun mergeInclusionAnnotationsFromCodebase(externalCodebase: Codebase) {
        val visitor =
            object : TraversingVisitor() {
                override fun visitItem(item: Item): TraversalAction {
                    // Find any show/hide annotations or FlaggedApi annotations to merge from the
                    // external to the main codebase. If there are none to copy then return.
                    val annotationsToMerge =
                        item.modifiers.annotations().filter { annotation ->
                            val qualifiedName = annotation.qualifiedName
                            annotation.isShowabilityAnnotation() ||
                                qualifiedName == ANDROID_FLAGGED_API
                        }
                    if (annotationsToMerge.isEmpty()) {
                        // Just because there are no annotations on an [Item] does not mean that
                        // there will not be on the children so make sure to visit them as normal.
                        return TraversalAction.CONTINUE
                    }

                    // Find the item to which the annotations should be copied, reporting an issue
                    // if it could not be found.
                    val mainItem =
                        item.findCorrespondingItemIn(codebase)
                            ?: run {
                                reporter.report(
                                    Issues.UNMATCHED_MERGE_ANNOTATION,
                                    item,
                                    "inclusion annotations were given for $item but no matching item was found"
                                )

                                // If an [Item] cannot be found then there is no point in visiting
                                // its children as they will not be found either.
                                return TraversalAction.SKIP_CHILDREN
                            }

                    // Merge the annotations to the main item, ignoring any that match, i.e. are of
                    // the same type as, an existing annotation.
                    mainItem.mutateModifiers {
                        mutateAnnotations {
                            for (annotation in annotationsToMerge) {
                                val qualifiedName = annotation.qualifiedName
                                if (none { it.qualifiedName == qualifiedName }) {
                                    // TODO: This simply uses the AnnotationItem from the Codebase
                                    //  being merged from in the Codebase being merged into. That is
                                    //  not safe as the Codebases may be from different models.
                                    add(annotation)
                                }
                            }
                        }
                    }

                    return TraversalAction.CONTINUE
                }
            }
        externalCodebase.accept(visitor)
    }

    internal fun error(message: String) {
        reporter.report(Issues.INTERNAL_ERROR, reportable = null, message)
    }

    internal fun warning(message: String) {
        if (options.verbose) {
            options.stdout.println("Warning: $message")
        }
    }

    @Suppress("PrivatePropertyName")
    private val XML_SIGNATURE: Pattern =
        Pattern.compile(
            // Class (FieldName | Type? Name(ArgList) Argnum?)
            // "(\\S+) (\\S+|(.*)\\s+(\\S+)\\((.*)\\)( \\d+)?)");
            "(\\S+) (\\S+|((.*)\\s+)?(\\S+)\\((.*)\\)( \\d+)?)"
        )

    private fun mergeDocument(document: Document) {
        val root = document.documentElement
        val rootTag = root.tagName
        assert(rootTag == "root") { rootTag }

        for (item in getChildren(root)) {
            var signature: String? = item.getAttribute(ATTR_NAME)
            if (signature == null || signature == "null") {
                continue // malformed item
            }

            signature = unescapeXml(signature)

            val matcher = XML_SIGNATURE.matcher(signature)
            if (matcher.matches()) {
                val containingClass = matcher.group(1)
                if (containingClass == null) {
                    warning("Could not find class for $signature")
                    continue
                }

                val classItem = codebase.findClass(containingClass)
                if (classItem == null) {
                    // Well known exceptions from IntelliJ's external annotations
                    // we won't complain loudly about
                    if (wellKnownIgnoredImport(containingClass)) {
                        continue
                    }

                    warning("Could not find class $containingClass; omitting annotation from merge")
                    continue
                }

                val methodName = matcher.group(5)
                if (methodName != null) {
                    val parameters = matcher.group(6)
                    val parameterIndex =
                        if (matcher.group(7) != null) {
                            Integer.parseInt(matcher.group(7).trim())
                        } else {
                            -1
                        }
                    mergeMethodOrParameter(
                        item,
                        containingClass,
                        classItem,
                        methodName,
                        parameterIndex,
                        parameters
                    )
                } else {
                    val fieldName = matcher.group(2)
                    mergeField(item, containingClass, classItem, fieldName)
                }
            } else if (signature.indexOf(' ') == -1 && signature.indexOf('.') != -1) {
                // Must be just a class
                val containingClass = signature
                val classItem = codebase.findClass(containingClass)
                if (classItem == null) {
                    if (wellKnownIgnoredImport(containingClass)) {
                        continue
                    }

                    warning("Could not find class $containingClass; omitting annotation from merge")
                    continue
                }

                mergeQualifierAnnotationsFromXmlElement(item, classItem)
            } else {
                warning("No merge match for signature $signature")
            }
        }
    }

    private fun wellKnownIgnoredImport(containingClass: String): Boolean {
        if (
            containingClass.startsWith("javax.swing.") ||
                containingClass.startsWith("javax.naming.") ||
                containingClass.startsWith("java.awt.") ||
                containingClass.startsWith("org.jdom.")
        ) {
            return true
        }
        return false
    }

    // The parameter declaration used in XML files should not have duplicated spaces,
    // and there should be no space after commas (we can't however strip out all spaces,
    // since for example the spaces around the "extends" keyword needs to be there in
    // types like Map<String,? extends Number>
    private fun fixParameterString(parameters: String): String {
        return parameters
            .replace("  ", " ")
            .replace(", ", ",")
            .replace("?super", "? super ")
            .replace("?extends", "? extends ")
    }

    private fun mergeMethodOrParameter(
        item: Element,
        containingClass: String,
        classItem: ClassItem,
        methodName: String,
        parameterIndex: Int,
        parameters: String
    ) {
        @Suppress("NAME_SHADOWING") val parameters = fixParameterString(parameters)

        val callableItem = classItem.findCallable(methodName, parameters)
        if (callableItem == null) {
            if (wellKnownIgnoredImport(containingClass)) {
                return
            }

            warning(
                "Could not find method $methodName($parameters) in $containingClass; omitting annotation from merge"
            )
            return
        }

        if (parameterIndex != -1) {
            val parameterItem = callableItem.parameters()[parameterIndex]
            mergeQualifierAnnotationsFromXmlElement(item, parameterItem)
        } else {
            // Annotation on the method itself
            mergeQualifierAnnotationsFromXmlElement(item, callableItem)
        }
    }

    private fun mergeField(
        item: Element,
        containingClass: String,
        classItem: ClassItem,
        fieldName: String
    ) {
        val fieldItem = classItem.findField(fieldName)
        if (fieldItem == null) {
            if (wellKnownIgnoredImport(containingClass)) {
                return
            }

            warning(
                "Could not find field $fieldName in $containingClass; omitting annotation from merge"
            )
            return
        }

        mergeQualifierAnnotationsFromXmlElement(item, fieldItem)
    }

    private fun getAnnotationName(element: Element): String {
        val tagName = element.tagName
        assert(tagName == "annotation") { tagName }

        val qualifiedName = element.getAttribute(ATTR_NAME)
        assert(qualifiedName.isNotEmpty())
        return qualifiedName
    }

    private fun mergeQualifierAnnotationsFromXmlElement(xmlElement: Element, item: Item) {
        loop@ for (annotationElement in getChildren(xmlElement)) {
            val originalName = getAnnotationName(annotationElement)
            val qualifiedName =
                codebase.annotationManager.normalizeInputName(originalName) ?: originalName
            if (hasNullnessConflicts(item, qualifiedName)) {
                continue@loop
            }

            val annotationItem = createAnnotation(annotationElement) ?: continue
            mergeQualifierAnnotation(item, annotationItem)
        }
    }

    private fun hasNullnessConflicts(item: Item, qualifiedName: String): Boolean {
        var haveNullable = false
        var haveNotNull = false
        for (existing in item.modifiers.annotations()) {
            val name = existing.qualifiedName
            if (isNonNull(name)) {
                haveNotNull = true
            }
            if (isNullable(name)) {
                haveNullable = true
            }
            if (name == qualifiedName) {
                return true
            }
        }

        // Make sure we don't have a conflict between nullable and not nullable
        if (isNonNull(qualifiedName) && haveNullable) {
            reporter.report(
                Issues.INCONSISTENT_MERGE_ANNOTATION,
                item,
                "Merge conflict, has @Nullable (or equivalent) attempting to merge @NonNull (or equivalent)"
            )
            return true
        } else if (isNullable(qualifiedName) && haveNotNull) {
            reporter.report(
                Issues.INCONSISTENT_MERGE_ANNOTATION,
                item,
                "Merge conflict, has @NonNull (or equivalent) attempting to merge @Nullable (or equivalent)"
            )
            return true
        }
        return false
    }

    /**
     * Reads in annotation data from an XML item (using IntelliJ IDE's external annotations XML
     * format) and creates a corresponding [AnnotationItem], performing some "translations" in the
     * process (e.g. mapping from IntelliJ annotations like `org.jetbrains.annotations.Nullable` to
     * `androidx.annotation.Nullable`.
     */
    private fun createAnnotation(annotationElement: Element): AnnotationItem? {
        val tagName = annotationElement.tagName
        assert(tagName == "annotation") { tagName }
        val name = annotationElement.getAttribute(ATTR_NAME)
        assert(name.isNotEmpty())
        when {
            name == "org.jetbrains.annotations.Range" -> {
                val children = getChildren(annotationElement)
                assert(children.size == 2) { children.size }
                val valueElement1 = children[0]
                val valueElement2 = children[1]
                val valName1 = valueElement1.getAttribute(ATTR_NAME)
                val value1 = valueElement1.getAttribute(ATTR_VAL)
                val valName2 = valueElement2.getAttribute(ATTR_NAME)
                val value2 = valueElement2.getAttribute(ATTR_VAL)
                return DefaultAnnotationItem.create(
                    codebase,
                    "androidx.annotation.IntRange",
                    listOf(
                        // Add "L" suffix to ensure that we don't for example interpret "-1" as
                        // an integer -1 and then end up recording it as "ffffffff" instead of
                        // -1L
                        DefaultAnnotationAttribute.create(
                            valName1,
                            value1 + (if (value1.last().isDigit()) "L" else "")
                        ),
                        DefaultAnnotationAttribute.create(
                            valName2,
                            value2 + (if (value2.last().isDigit()) "L" else "")
                        )
                    ),
                )
            }
            name == IDEA_MAGIC -> {
                val children = getChildren(annotationElement)
                assert(children.size == 1) { children.size }
                val valueElement = children[0]
                val valName = valueElement.getAttribute(ATTR_NAME)
                var value = valueElement.getAttribute(ATTR_VAL)
                val flagsFromClass = valName == "flagsFromClass"
                val flag = valName == "flags" || flagsFromClass
                if (valName == "valuesFromClass" || flagsFromClass) {
                    // Not supported
                    var found = false
                    if (value.endsWith(DOT_CLASS)) {
                        val clsName = value.substring(0, value.length - DOT_CLASS.length)
                        val sb = StringBuilder()
                        sb.append('{')

                        var reflectionFields: Array<Field>? = null
                        try {
                            val cls = Class.forName(clsName)
                            reflectionFields = cls.declaredFields
                        } catch (ignore: Exception) {
                            // Class not available: not a problem. We'll rely on API filter.
                            // It's mainly used for sorting anyway.
                        }

                        // Attempt to sort in reflection order
                        if (!found && reflectionFields != null) {
                            val filterEmit =
                                ApiVisitor(
                                        config = @Suppress("DEPRECATION") options.apiVisitorConfig,
                                    )
                                    .filterEmit

                            // Attempt with reflection
                            var first = true
                            for (field in reflectionFields) {
                                if (
                                    field.type == Integer.TYPE ||
                                        field.type == Int::class.javaPrimitiveType
                                ) {
                                    // Make sure this field is included in our API too
                                    val fieldItem =
                                        codebase.findClass(clsName)?.findField(field.name)
                                    if (fieldItem == null || !filterEmit.test(fieldItem)) {
                                        continue
                                    }

                                    if (first) {
                                        first = false
                                    } else {
                                        sb.append(',').append(' ')
                                    }
                                    sb.append(clsName).append('.').append(field.name)
                                }
                            }
                        }
                        sb.append('}')
                        value = sb.toString()
                        if (sb.length > 2) { // 2: { }
                            found = true
                        }
                    }

                    if (!found) {
                        return null
                    }
                }

                val attributes = mutableListOf<AnnotationAttribute>()
                attributes.add(DefaultAnnotationAttribute.create(TYPE_DEF_VALUE_ATTRIBUTE, value))
                if (flag) {
                    attributes.add(
                        DefaultAnnotationAttribute.create(
                            TYPE_DEF_FLAG_ATTRIBUTE,
                            ANNOTATION_VALUE_TRUE
                        )
                    )
                }
                return DefaultAnnotationItem.create(
                    codebase,
                    if (valName == "stringValues") ANDROIDX_STRING_DEF else ANDROIDX_INT_DEF,
                    attributes,
                )
            }
            name == ANDROIDX_STRING_DEF ||
                name == ANDROID_STRING_DEF ||
                name == ANDROIDX_INT_DEF ||
                name == ANDROID_INT_DEF -> {
                val attributes = mutableListOf<AnnotationAttribute>()
                val parseChild: (Element) -> Unit = { child: Element ->
                    val elementName = child.getAttribute(ATTR_NAME)
                    val value = child.getAttribute(ATTR_VAL)
                    when (elementName) {
                        TYPE_DEF_VALUE_ATTRIBUTE -> {
                            attributes.add(
                                DefaultAnnotationAttribute.create(TYPE_DEF_VALUE_ATTRIBUTE, value)
                            )
                        }
                        TYPE_DEF_FLAG_ATTRIBUTE -> {
                            if (ANNOTATION_VALUE_TRUE == value) {
                                attributes.add(
                                    DefaultAnnotationAttribute.create(
                                        TYPE_DEF_FLAG_ATTRIBUTE,
                                        ANNOTATION_VALUE_TRUE
                                    )
                                )
                            }
                        }
                        else -> {
                            error("Unrecognized element: " + elementName)
                        }
                    }
                }
                val children = getChildren(annotationElement)
                parseChild(children[0])
                if (children.size == 2) {
                    parseChild(children[1])
                }
                val intDef = ANDROIDX_INT_DEF == name || ANDROID_INT_DEF == name
                return DefaultAnnotationItem.create(
                    codebase,
                    if (intDef) ANDROIDX_INT_DEF else ANDROIDX_STRING_DEF,
                    attributes,
                )
            }
            name == IDEA_CONTRACT -> {
                val children = getChildren(annotationElement)
                val valueElement = children[0]
                val value = valueElement.getAttribute(ATTR_VAL)
                val pure = valueElement.getAttribute(ATTR_PURE)
                return if (pure != null && pure.isNotEmpty()) {
                    DefaultAnnotationItem.create(
                        codebase,
                        name,
                        listOf(
                            DefaultAnnotationAttribute.create(TYPE_DEF_VALUE_ATTRIBUTE, value),
                            DefaultAnnotationAttribute.create(ATTR_PURE, pure)
                        ),
                    )
                } else {
                    DefaultAnnotationItem.create(
                        codebase,
                        name,
                        listOf(DefaultAnnotationAttribute.create(TYPE_DEF_VALUE_ATTRIBUTE, value)),
                    )
                }
            }
            isNonNull(name) -> return codebase.createAnnotation("@$ANDROIDX_NONNULL")
            isNullable(name) -> return codebase.createAnnotation("@$ANDROIDX_NULLABLE")
            else -> {
                val children = getChildren(annotationElement)
                if (children.isEmpty()) {
                    return codebase.createAnnotation("@$name")
                }
                val attributes = mutableListOf<AnnotationAttribute>()
                for (valueElement in children) {
                    attributes.add(
                        DefaultAnnotationAttribute.create(
                            valueElement.getAttribute(ATTR_NAME) ?: continue,
                            valueElement.getAttribute(ATTR_VAL) ?: continue
                        )
                    )
                }
                return DefaultAnnotationItem.create(codebase, name, attributes)
            }
        }
    }

    private fun isNonNull(name: String): Boolean {
        return name == IDEA_NOTNULL ||
            name == ANDROID_NOTNULL ||
            name == ANDROIDX_NONNULL ||
            name == SUPPORT_NOTNULL
    }

    private fun isNullable(name: String): Boolean {
        return name == IDEA_NULLABLE ||
            name == ANDROID_NULLABLE ||
            name == ANDROIDX_NULLABLE ||
            name == SUPPORT_NULLABLE
    }

    /** Merge qualifier annotations in [annotations] into the [Item.modifiers] of [item]. */
    private fun mergeQualifierAnnotations(annotations: List<AnnotationItem>, item: Item) {
        val modifiers = item.modifiers
        for (annotation in annotations) {
            mergeQualifierAnnotation(annotation, modifiers, item)
        }
    }

    private fun mergeQualifierAnnotation(
        annotation: AnnotationItem,
        newModifiers: ModifierList,
        new: Item
    ) {
        var addAnnotation = false
        if (annotation.isNullnessAnnotation()) {
            if (!newModifiers.hasAnnotation(AnnotationItem::isNullnessAnnotation)) {
                addAnnotation = true
            }
        } else {
            // TODO: Check for other incompatibilities than nullness?
            val qualifiedName = annotation.qualifiedName
            if (newModifiers.findAnnotation(qualifiedName) == null) {
                addAnnotation = true
            }
        }

        if (addAnnotation) {
            new.codebase
                .createAnnotation(
                    annotation.toSource(showDefaultAttrs = false),
                    new,
                )
                ?.let { mergeQualifierAnnotation(new, it) }
        }
    }

    private fun mergeQualifierAnnotation(item: Item, annotation: AnnotationItem) {
        item.mutateModifiers { addAnnotation(annotation) }

        // Update the type nullability from the annotation, if necessary.

        // Nullability annotations do not make sense on class definitions or in package-info.java
        // files and in fact many nullability annotations do not support targeting them at all. Some
        // nullability checkers do support annotating packages and classes with annotations to set
        // the default nullability for unannotated types but Metalava does not currently support
        // them. If it did then they would need special treatment here anyway so, for now we just
        // ignore them.
        if (item is ClassItem || item is PackageItem) return

        // Check to make sure that the annotation is a nullability annotation.
        val annotationNullability = annotation.typeNullability ?: return
        // Check to make sure that the item has a type.
        val typeItem = item.type() ?: return
        // Check to make sure that the type nullability is different to the annotation's
        // nullability.
        if (typeItem.modifiers.nullability != annotationNullability) {
            // Finally, duplicate the type with the new nullability.
            item.setType(typeItem.substitute(annotationNullability))
        }
    }

    private fun unescapeXml(escaped: String): String {
        var workingString = escaped.replace(QUOT_ENTITY, "\"")
        workingString = workingString.replace(LT_ENTITY, "<")
        workingString = workingString.replace(GT_ENTITY, ">")
        workingString = workingString.replace(APOS_ENTITY, "'")
        workingString = workingString.replace(AMP_ENTITY, "&")

        return workingString
    }
}
