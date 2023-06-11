package com.android.tools.metalava

import com.android.SdkConstants.ATTR_VALUE
import com.android.sdklib.SdkVersionInfo
import com.android.tools.lint.LintCliClient
import com.android.tools.lint.checks.ApiLookup
import com.android.tools.lint.detector.api.ApiConstraint
import com.android.tools.lint.detector.api.editDistance
import com.android.tools.lint.helpers.DefaultJavaEvaluator
import com.android.tools.metalava.apilevels.ApiToExtensionsMap
import com.android.tools.metalava.model.AnnotationAttributeValue
import com.android.tools.metalava.model.AnnotationItem
import com.android.tools.metalava.model.ClassItem
import com.android.tools.metalava.model.Codebase
import com.android.tools.metalava.model.FieldItem
import com.android.tools.metalava.model.Item
import com.android.tools.metalava.model.MemberItem
import com.android.tools.metalava.model.MethodItem
import com.android.tools.metalava.model.PackageItem
import com.android.tools.metalava.model.ParameterItem
import com.android.tools.metalava.model.psi.containsLinkTags
import com.android.tools.metalava.model.visitors.ApiVisitor
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiField
import com.intellij.psi.PsiMethod
import org.w3c.dom.Node
import org.w3c.dom.NodeList
import org.xml.sax.Attributes
import org.xml.sax.helpers.DefaultHandler
import java.io.File
import java.nio.file.Files
import java.util.regex.Pattern
import javax.xml.parsers.SAXParserFactory
import kotlin.math.min

/**
 * Whether to include textual descriptions of the API requirements instead
 * of just inserting a since-tag. This should be off if there is post-processing
 * to convert since tags in the documentation tool used.
 */
const val ADD_API_LEVEL_TEXT = false
const val ADD_DEPRECATED_IN_TEXT = false

/**
 * Walk over the API and apply tweaks to the documentation, such as
 *     - Looking for annotations and converting them to auxiliary tags
 *       that will be processed by the documentation tools later.
 *     - Reading lint's API database and inserting metadata into
 *       the documentation like api levels and deprecation levels.
 *     - Transferring docs from hidden super methods.
 *     - Performing tweaks for common documentation mistakes, such as
 *       ending the first sentence with ", e.g. " where javadoc will sadly
 *       see the ". " and think "aha, that's the end of the sentence!"
 *       (It works around this by replacing the space with &nbsp;.)
 *       This will also attempt to fix common typos (Andriod->Android etc).
 */
class DocAnalyzer(
    /** The codebase to analyze */
    private val codebase: Codebase
) {

    /** Computes the visible part of the API from all the available code in the codebase */
    fun enhance() {
        // Apply options for packages that should be hidden
        documentsFromAnnotations()

        tweakGrammar()

        // TODO:
        // insertMissingDocFromHiddenSuperclasses()
    }

    val mentionsNull: Pattern = Pattern.compile("\\bnull\\b")

    /** Hide packages explicitly listed in [Options.hidePackages] */
    private fun documentsFromAnnotations() {
        // Note: Doclava1 inserts its own javadoc parameters into the documentation,
        // which is then later processed by javadoc to insert actual descriptions.
        // This indirection makes the actual descriptions of the annotations more
        // configurable from a separate file -- but since this tool isn't hooked
        // into javadoc anymore (and is going to be used by for example Dokka too)
        // instead metalava will generate the descriptions directly in-line into the
        // docs.
        //
        // This does mean that you have to update the metalava source code to update
        // the docs -- but on the other hand all the other docs in the documentation
        // set also requires updating framework source code, so this doesn't seem
        // like an unreasonable burden.

        codebase.accept(object : ApiVisitor() {
            override fun visitItem(item: Item) {
                val annotations = item.modifiers.annotations()
                if (annotations.isEmpty()) {
                    return
                }

                for (annotation in annotations) {
                    handleAnnotation(annotation, item, depth = 0)
                }

                /* Handled via @memberDoc/@classDoc on the annotations themselves right now.
                   That doesn't handle combinations of multiple thread annotations, but those
                   don't occur yet, right?
                // Threading annotations: can't look at them one at a time; need to list them
                // all together
                if (item is ClassItem || item is MethodItem) {
                    val threads = findThreadAnnotations(annotations)
                    threads?.let {
                        val threadList = it.joinToString(separator = " or ") +
                                (if (it.size == 1) " thread" else " threads")
                        val doc = if (item is ClassItem) {
                            "All methods in this class must be invoked on the $threadList, unless otherwise noted"
                        } else {
                            assert(item is MethodItem)
                            "This method must be invoked on the $threadList"
                        }
                        appendDocumentation(doc, item, false)
                    }
                }
                */
                if (findThreadAnnotations(annotations).size > 1) {
                    reporter.report(
                        Issues.MULTIPLE_THREAD_ANNOTATIONS,
                        item,
                        "Found more than one threading annotation on $item; " +
                            "the auto-doc feature does not handle this correctly"
                    )
                }
            }

            private fun findThreadAnnotations(annotations: List<AnnotationItem>): List<String> {
                var result: MutableList<String>? = null
                for (annotation in annotations) {
                    val name = annotation.qualifiedName
                    if (name != null &&
                        name.endsWith("Thread") &&
                        name.startsWith(ANDROIDX_ANNOTATION_PREFIX)
                    ) {
                        if (result == null) {
                            result = mutableListOf()
                        }
                        val threadName = if (name.endsWith("UiThread")) {
                            "UI"
                        } else {
                            name.substring(name.lastIndexOf('.') + 1, name.length - "Thread".length)
                        }
                        result.add(threadName)
                    }
                }
                return result ?: emptyList()
            }

            /** Fallback if field can't be resolved or if an inlined string value is used */
            private fun findPermissionField(codebase: Codebase, value: Any): FieldItem? {
                val perm = value.toString()
                val permClass = codebase.findClass("android.Manifest.permission")
                permClass?.fields()?.filter {
                    it.initialValue(requireConstant = false)?.toString() == perm
                }?.forEach { return it }
                return null
            }

            private fun handleAnnotation(
                annotation: AnnotationItem,
                item: Item,
                depth: Int,
                visitedClasses: MutableSet<String> = mutableSetOf()
            ) {
                val name = annotation.qualifiedName
                if (name == null || name.startsWith(JAVA_LANG_PREFIX)) {
                    // Ignore java.lang.Retention etc.
                    return
                }

                if (item is ClassItem && name == item.qualifiedName()) {
                    // The annotation annotates itself; we shouldn't attempt to recursively
                    // pull in documentation from it; the documentation is already complete.
                    return
                }

                // Some annotations include the documentation they want inlined into usage docs.
                // Copy those here:

                handleInliningDocs(annotation, item)

                when (name) {
                    "androidx.annotation.RequiresPermission" -> handleRequiresPermission(annotation, item)
                    "androidx.annotation.IntRange",
                    "androidx.annotation.FloatRange" -> handleRange(annotation, item)
                    "androidx.annotation.IntDef",
                    "androidx.annotation.LongDef",
                    "androidx.annotation.StringDef" -> handleTypeDef(annotation, item)
                    "android.annotation.RequiresFeature" -> handleRequiresFeature(annotation, item)
                    "androidx.annotation.RequiresApi" -> handleRequiresApi(annotation, item)
                    "android.provider.Column" -> handleColumn(annotation, item)
                    "kotlin.Deprecated" -> handleKotlinDeprecation(annotation, item)
                }

                visitedClasses.add(name)
                // Thread annotations are ignored here because they're handled as a group afterwards

                // TODO: Resource type annotations

                // Handle inner annotations
                annotation.resolve()?.modifiers?.annotations()?.forEach { nested ->
                    if (depth == 20) { // Temp debugging
                        throw StackOverflowError(
                            "Unbounded recursion, processing annotation ${annotation.toSource()} " +
                                "in $item in ${item.sourceFile()} "
                        )
                    } else if (nested.qualifiedName !in visitedClasses) {
                        handleAnnotation(nested, item, depth + 1, visitedClasses)
                    }
                }
            }

            private fun handleKotlinDeprecation(annotation: AnnotationItem, item: Item) {
                val text = (annotation.findAttribute("message") ?: annotation.findAttribute(ATTR_VALUE))
                    ?.value?.value()?.toString() ?: return
                if (text.isBlank() || item.documentation.contains(text)) {
                    return
                }

                item.appendDocumentation(text, "@deprecated")
            }

            private fun handleInliningDocs(
                annotation: AnnotationItem,
                item: Item
            ) {
                if (annotation.isNullable() || annotation.isNonNull()) {
                    // Some docs already specifically talk about null policy; in that case,
                    // don't include the docs (since it may conflict with more specific conditions
                    // outlined in the docs).
                    val doc =
                        if (item is ParameterItem) {
                            item.containingMethod().findTagDocumentation("param", item.name())
                                ?: ""
                        } else if (item is MethodItem) {
                            // Don't inspect param docs (and other tags) for this purpose.
                            item.findMainDocumentation() + (item.findTagDocumentation("return") ?: "")
                        } else {
                            item.documentation
                        }
                    if (doc.contains("null") && mentionsNull.matcher(doc).find()) {
                        return
                    }
                }

                when (item) {
                    is FieldItem -> {
                        addDoc(annotation, "memberDoc", item)
                    }
                    is MethodItem -> {
                        addDoc(annotation, "memberDoc", item)
                        addDoc(annotation, "returnDoc", item)
                    }
                    is ParameterItem -> {
                        addDoc(annotation, "paramDoc", item)
                    }
                    is ClassItem -> {
                        addDoc(annotation, "classDoc", item)
                    }
                }
            }

            private fun handleRequiresPermission(
                annotation: AnnotationItem,
                item: Item
            ) {
                if (item !is MemberItem) {
                    return
                }
                var values: List<AnnotationAttributeValue>? = null
                var any = false
                var conditional = false
                for (attribute in annotation.attributes) {
                    when (attribute.name) {
                        "value", "allOf" -> {
                            values = attribute.leafValues()
                        }
                        "anyOf" -> {
                            any = true
                            values = attribute.leafValues()
                        }
                        "conditional" -> {
                            conditional = attribute.value.value() == true
                        }
                    }
                }

                if (values != null && values.isNotEmpty() && !conditional) {
                    // Look at macros_override.cs for the usage of these
                    // tags. In particular, search for def:dump_permission

                    val sb = StringBuilder(100)
                    sb.append("Requires ")
                    var first = true
                    for (value in values) {
                        when {
                            first -> first = false
                            any -> sb.append(" or ")
                            else -> sb.append(" and ")
                        }

                        val resolved = value.resolve()
                        val field = if (resolved is FieldItem)
                            resolved
                        else {
                            val v: Any = value.value() ?: value.toSource()
                            if (v == CARRIER_PRIVILEGES_MARKER) {
                                // TODO: Warn if using allOf with carrier
                                sb.append("{@link android.telephony.TelephonyManager#hasCarrierPrivileges carrier privileges}")
                                continue
                            }
                            findPermissionField(codebase, v)
                        }
                        if (field == null) {
                            val v = value.value()?.toString() ?: value.toSource()
                            if (editDistance(CARRIER_PRIVILEGES_MARKER, v, 3) < 3) {
                                reporter.report(
                                    Issues.MISSING_PERMISSION, item,
                                    "Unrecognized permission `$v`; did you mean `$CARRIER_PRIVILEGES_MARKER`?"
                                )
                            } else {
                                reporter.report(
                                    Issues.MISSING_PERMISSION, item,
                                    "Cannot find permission field for $value required by $item (may be hidden or removed)"
                                )
                            }
                            sb.append(value.toSource())
                        } else {
                            if (filterReference.test(field)) {
                                sb.append("{@link ${field.containingClass().qualifiedName()}#${field.name()}}")
                            } else {
                                reporter.report(
                                    Issues.MISSING_PERMISSION, item,
                                    "Permission $value required by $item is hidden or removed"
                                )
                                sb.append("${field.containingClass().qualifiedName()}.${field.name()}")
                            }
                        }
                    }

                    appendDocumentation(sb.toString(), item, false)
                }
            }

            private fun handleRange(
                annotation: AnnotationItem,
                item: Item
            ) {
                val from: String? = annotation.findAttribute("from")?.value?.toSource()
                val to: String? = annotation.findAttribute("to")?.value?.toSource()
                // TODO: inclusive/exclusive attributes on FloatRange!
                if (from != null || to != null) {
                    val args = HashMap<String, String>()
                    if (from != null) args["from"] = from
                    if (from != null) args["from"] = from
                    if (to != null) args["to"] = to
                    val doc = if (from != null && to != null) {
                        "Value is between $from and $to inclusive"
                    } else if (from != null) {
                        "Value is $from or greater"
                    } else if (to != null) {
                        "Value is $to or less"
                    } else {
                        null
                    }
                    appendDocumentation(doc, item, true)
                }
            }

            private fun handleTypeDef(
                annotation: AnnotationItem,
                item: Item
            ) {
                val values = annotation.findAttribute("value")?.leafValues() ?: return
                val flag = annotation.findAttribute("flag")?.value?.toSource() == "true"

                // Look at macros_override.cs for the usage of these
                // tags. In particular, search for def:dump_int_def

                val sb = StringBuilder(100)
                sb.append("Value is ")
                if (flag) {
                    sb.append("either <code>0</code> or ")
                    if (values.size > 1) {
                        sb.append("a combination of ")
                    }
                }

                values.forEachIndexed { index, value ->
                    sb.append(
                        when (index) {
                            0 -> {
                                ""
                            }
                            values.size - 1 -> {
                                if (flag) {
                                    ", and "
                                } else {
                                    ", or "
                                }
                            }
                            else -> {
                                ", "
                            }
                        }
                    )

                    val field = value.resolve()
                    if (field is FieldItem)
                        if (filterReference.test(field)) {
                            sb.append("{@link ${field.containingClass().qualifiedName()}#${field.name()}}")
                        } else {
                            // Typedef annotation references field which isn't part of the API: don't
                            // try to link to it.
                            reporter.report(
                                Issues.HIDDEN_TYPEDEF_CONSTANT, item,
                                "Typedef references constant which isn't part of the API, skipping in documentation: " +
                                    "${field.containingClass().qualifiedName()}#${field.name()}"
                            )
                            sb.append(field.containingClass().qualifiedName() + "." + field.name())
                        }
                    else {
                        sb.append(value.toSource())
                    }
                }
                appendDocumentation(sb.toString(), item, true)
            }

            private fun handleRequiresFeature(
                annotation: AnnotationItem,
                item: Item
            ) {
                val value = annotation.findAttribute("value")?.leafValues()?.firstOrNull() ?: return
                val sb = StringBuilder(100)
                val resolved = value.resolve()
                val field = resolved as? FieldItem
                sb.append("Requires the ")
                if (field == null) {
                    reporter.report(
                        Issues.MISSING_PERMISSION, item,
                        "Cannot find feature field for $value required by $item (may be hidden or removed)"
                    )
                    sb.append("{@link ${value.toSource()}}")
                } else {
                    if (filterReference.test(field)) {
                        sb.append("{@link ${field.containingClass().qualifiedName()}#${field.name()} ${field.containingClass().simpleName()}#${field.name()}} ")
                    } else {
                        reporter.report(
                            Issues.MISSING_PERMISSION, item,
                            "Feature field $value required by $item is hidden or removed"
                        )
                        sb.append("${field.containingClass().simpleName()}#${field.name()} ")
                    }
                }

                sb.append("feature which can be detected using ")
                sb.append("{@link android.content.pm.PackageManager#hasSystemFeature(String) ")
                sb.append("PackageManager.hasSystemFeature(String)}.")
                appendDocumentation(sb.toString(), item, false)
            }

            private fun handleRequiresApi(
                annotation: AnnotationItem,
                item: Item
            ) {
                val level = run {
                    val api = annotation.findAttribute("api")?.leafValues()?.firstOrNull()?.value()
                    if (api == null || api == 1) {
                        annotation.findAttribute("value")?.leafValues()?.firstOrNull()?.value() ?: return
                    } else {
                        api
                    }
                }

                if (level is Int) {
                    addApiLevelDocumentation(level, item)
                }
            }

            private fun handleColumn(
                annotation: AnnotationItem,
                item: Item
            ) {
                val value = annotation.findAttribute("value")?.leafValues()?.firstOrNull() ?: return
                val readOnly = annotation.findAttribute("readOnly")?.leafValues()?.firstOrNull()?.value() == true
                val sb = StringBuilder(100)
                val resolved = value.resolve()
                val field = resolved as? FieldItem
                sb.append("This constant represents a column name that can be used with a ")
                sb.append("{@link android.content.ContentProvider}")
                sb.append(" through a ")
                sb.append("{@link android.content.ContentValues}")
                sb.append(" or ")
                sb.append("{@link android.database.Cursor}")
                sb.append(" object. The values stored in this column are ")
                sb.append("")
                if (field == null) {
                    reporter.report(
                        Issues.MISSING_COLUMN, item,
                        "Cannot find feature field for $value required by $item (may be hidden or removed)"
                    )
                    sb.append("{@link ${value.toSource()}}")
                } else {
                    if (filterReference.test(field)) {
                        sb.append("{@link ${field.containingClass().qualifiedName()}#${field.name()} ${field.containingClass().simpleName()}#${field.name()}} ")
                    } else {
                        reporter.report(
                            Issues.MISSING_COLUMN, item,
                            "Feature field $value required by $item is hidden or removed"
                        )
                        sb.append("${field.containingClass().simpleName()}#${field.name()} ")
                    }
                }

                if (readOnly) {
                    sb.append(", and are read-only and cannot be mutated")
                }
                sb.append(".")
                appendDocumentation(sb.toString(), item, false)
            }
        })
    }

    /**
     * Appends the given documentation to the given item.
     * If it's documentation on a parameter, it is redirected to the surrounding method's
     * documentation.
     *
     * If the [returnValue] flag is true, the documentation is added to the description text
     * of the method, otherwise, it is added to the return tag. This lets for example
     * a threading annotation requirement be listed as part of a method description's
     * text, and a range annotation be listed as part of the return value description.
     * */
    private fun appendDocumentation(doc: String?, item: Item, returnValue: Boolean) {
        doc ?: return

        when (item) {
            is ParameterItem -> item.containingMethod().appendDocumentation(doc, item.name())
            is MethodItem ->
                // Document as part of return annotation, not member doc
                item.appendDocumentation(doc, if (returnValue) "@return" else null)
            else -> item.appendDocumentation(doc)
        }
    }

    private fun addDoc(annotation: AnnotationItem, tag: String, item: Item) {
        // TODO: Cache: we shouldn't have to keep looking this up over and over
        // for example for the nullable/non-nullable annotation classes that
        // are used everywhere!
        val cls = annotation.resolve() ?: return

        val documentation = cls.findTagDocumentation(tag)
        if (documentation != null) {
            assert(documentation.startsWith("@$tag")) { documentation }
            // TODO: Insert it in the right place (@return or @param)
            val section = when {
                documentation.startsWith("@returnDoc") -> "@return"
                documentation.startsWith("@paramDoc") -> "@param"
                documentation.startsWith("@memberDoc") -> null
                else -> null
            }

            val insert = stripLeadingAsterisks(stripMetaTags(documentation.substring(tag.length + 2)))
            val qualified = if (containsLinkTags(insert)) {
                val original = "/** $insert */"
                val qualified = cls.fullyQualifiedDocumentation(original)
                if (original != qualified) {
                    qualified.substring(if (qualified[3] == ' ') 4 else 3, qualified.length - 2)
                } else {
                    insert
                }
            } else {
                insert
            }

            item.appendDocumentation(qualified, section) // 2: @ and space after tag
        }
    }

    private fun stripLeadingAsterisks(s: String): String {
        if (s.contains("*")) {
            val sb = StringBuilder(s.length)
            var strip = true
            for (c in s) {
                if (strip) {
                    if (c.isWhitespace() || c == '*') {
                        continue
                    } else {
                        strip = false
                    }
                } else {
                    if (c == '\n') {
                        strip = true
                    }
                }
                sb.append(c)
            }
            return sb.toString()
        }

        return s
    }

    private fun stripMetaTags(string: String): String {
        // Get rid of @hide and @remove tags etc that are part of documentation snippets
        // we pull in, such that we don't accidentally start applying this to the
        // item that is pulling in the documentation.
        if (string.contains("@hide") || string.contains("@remove")) {
            return string.replace("@hide", "").replace("@remove", "")
        }
        return string
    }

    /** Replacements to perform in documentation */
    @Suppress("SpellCheckingInspection")
    val typos = mapOf(
        "JetPack" to "Jetpack",
        "Andriod" to "Android",
        "Kitkat" to "KitKat",
        "LemonMeringuePie" to "Lollipop",
        "LMP" to "Lollipop",
        "KeyLimePie" to "KitKat",
        "KLP" to "KitKat",
        "teh" to "the"
    )

    private fun tweakGrammar() {
        codebase.accept(object : ApiVisitor() {
            override fun visitItem(item: Item) {
                var doc = item.documentation
                if (doc.isBlank()) {
                    return
                }

                if (!reporter.isSuppressed(Issues.TYPO)) {
                    for (typo in typos.keys) {
                        if (doc.contains(typo)) {
                            val replacement = typos[typo] ?: continue
                            val new = doc.replace(Regex("\\b$typo\\b"), replacement)
                            if (new != doc) {
                                reporter.report(
                                    Issues.TYPO,
                                    item,
                                    "Replaced $typo with $replacement in the documentation for $item"
                                )
                                doc = new
                                item.documentation = doc
                            }
                        }
                    }
                }

                // Work around javadoc cutting off the summary line after the first ". ".
                val firstDot = doc.indexOf(".")
                if (firstDot > 0 && doc.regionMatches(firstDot - 1, "e.g. ", 0, 5, false)) {
                    doc = doc.substring(0, firstDot) + ".g.&nbsp;" + doc.substring(firstDot + 4)
                    item.documentation = doc
                }
            }
        })
    }

    fun applyApiLevels(applyApiLevelsXml: File) {
        val apiLookup = getApiLookup(applyApiLevelsXml)
        val elementToSdkExtSinceMap = createSymbolToSdkExtSinceMap(applyApiLevelsXml)

        val pkgApi = HashMap<PackageItem, Int?>(300)
        codebase.accept(object : ApiVisitor(visitConstructorsAsMethods = true) {
            override fun visitMethod(method: MethodItem) {
                val psiMethod = method.psi() as? PsiMethod ?: return
                @Suppress("DEPRECATION")
                addApiLevelDocumentation(apiLookup.getMethodVersion(psiMethod), method)
                elementToSdkExtSinceMap["${psiMethod.containingClass!!.qualifiedName}#${psiMethod.name}"]?.let {
                    addApiExtensionsDocumentation(it, method)
                }
                @Suppress("DEPRECATION")
                addDeprecatedDocumentation(apiLookup.getMethodDeprecatedIn(psiMethod), method)
            }

            override fun visitClass(cls: ClassItem) {
                val psiClass = cls.psi() as PsiClass
                val since = apiLookup.getClassVersion(psiClass)
                if (since != -1) {
                    addApiLevelDocumentation(since, cls)

                    // Compute since version for the package: it's the min of all the classes in the package
                    val pkg = cls.containingPackage()
                    pkgApi[pkg] = min(pkgApi[pkg] ?: Integer.MAX_VALUE, since)
                }
                elementToSdkExtSinceMap["${psiClass.qualifiedName}"]?.let {
                    addApiExtensionsDocumentation(it, cls)
                }
                addDeprecatedDocumentation(apiLookup.getClassDeprecatedIn(psiClass), cls)
            }

            override fun visitField(field: FieldItem) {
                val psiField = field.psi() as PsiField
                addApiLevelDocumentation(apiLookup.getFieldVersion(psiField), field)
                elementToSdkExtSinceMap["${psiField.containingClass!!.qualifiedName}#${psiField.name}"]?.let {
                    addApiExtensionsDocumentation(it, field)
                }
                addDeprecatedDocumentation(apiLookup.getFieldDeprecatedIn(psiField), field)
            }
        })

        val packageDocs = codebase.getPackageDocs()
        if (packageDocs != null) {
            for ((pkg, api) in pkgApi.entries) {
                val code = api ?: 1
                addApiLevelDocumentation(code, pkg)
            }
        }
    }

    private fun addApiLevelDocumentation(level: Int, item: Item) {
        if (level > 0) {
            if (item.originallyHidden) {
                // @SystemApi, @TestApi etc -- don't apply API levels here since we don't have accurate historical data
                return
            }
            if (!options.isDeveloperPreviewBuild() && options.currentApiLevel != -1 && level > options.currentApiLevel) {
                // api-versions.xml currently assigns api+1 to APIs that have not yet been finalized
                // in a dessert (only in an extension), but for release builds, we don't want to
                // include a "future" SDK_INT
                return
            }

            val currentCodeName = options.currentCodeName
            val code: String = if (currentCodeName != null && level > options.currentApiLevel) {
                currentCodeName
            } else {
                level.toString()
            }

            @Suppress("ConstantConditionIf")
            if (ADD_API_LEVEL_TEXT) { // See 113933920: Remove "Requires API level" from method comment
                val description = if (code == currentCodeName) currentCodeName else describeApiLevel(level)
                appendDocumentation("Requires API level $description", item, false)
            }
            // Also add @since tag, unless already manually entered.
            // TODO: Override it everywhere in case the existing doc is wrong (we know
            // better), and at least for OpenJDK sources we *should* since the since tags
            // are talking about language levels rather than API levels!
            if (!item.documentation.contains("@apiSince")) {
                item.appendDocumentation(code, "@apiSince")
            } else {
                reporter.report(
                    Issues.FORBIDDEN_TAG, item,
                    "Documentation should not specify @apiSince " +
                        "manually; it's computed and injected at build time by $PROGRAM_NAME"
                )
            }
        }
    }

    private fun addApiExtensionsDocumentation(sdkExtSince: List<SdkAndVersion>, item: Item) {
        if (item.documentation.contains("@sdkExtSince")) {
            reporter.report(
                Issues.FORBIDDEN_TAG, item,
                "Documentation should not specify @sdkExtSince " +
                    "manually; it's computed and injected at build time by $PROGRAM_NAME"
            )
        }
        // Don't emit an @sdkExtSince for every item in sdkExtSince; instead, limit output to the
        // first non-Android SDK listed for the symbol in sdk-extensions-info.txt (the Android SDK
        // is already covered by @apiSince and doesn't have to be repeated)
        sdkExtSince.find {
            it.sdk != ApiToExtensionsMap.ANDROID_PLATFORM_SDK_ID
        }?.let {
            item.appendDocumentation("${it.name} ${it.version}", "@sdkExtSince")
        }
    }

    private fun addDeprecatedDocumentation(level: Int, item: Item) {
        if (level > 0) {
            if (item.originallyHidden) {
                // @SystemApi, @TestApi etc -- don't apply API levels here since we don't have accurate historical data
                return
            }
            val currentCodeName = options.currentCodeName
            val code: String = if (currentCodeName != null && level > options.currentApiLevel) {
                currentCodeName
            } else {
                level.toString()
            }

            @Suppress("ConstantConditionIf")
            if (ADD_DEPRECATED_IN_TEXT) {
                // TODO: *pre*pend instead!
                val description =
                    "<p class=\"caution\"><strong>This class was deprecated in API level $code.</strong></p>"
                item.appendDocumentation(description, "@deprecated", append = false)
            }

            if (!item.documentation.contains("@deprecatedSince")) {
                item.appendDocumentation(code, "@deprecatedSince")
            } else {
                reporter.report(
                    Issues.FORBIDDEN_TAG, item,
                    "Documentation should not specify @deprecatedSince " +
                        "manually; it's computed and injected at build time by $PROGRAM_NAME"
                )
            }
        }
    }

    private fun describeApiLevel(level: Int): String {
        return "$level (Android ${SdkVersionInfo.getVersionString(level)}, ${SdkVersionInfo.getCodeName(level)})"
    }
}

/**
 * A constraint that will only match for Android Platform SDKs.
 */
val androidSdkConstraint = ApiConstraint.get(1)

/**
 * Get the min API level, i.e. the lowest version of the Android Platform SDK.
 *
 * TODO(b/282932318): Replace with call to ApiConstraint.min() when bug is fixed.
 */
fun ApiConstraint.minApiLevel(): Int {
    return getConstraints()
        .filter { it != ApiConstraint.UNKNOWN }
        // Remove any constraints that are not for the Android Platform SDK.
        .filter { it.isAtLeast(androidSdkConstraint) }
        // Get the lowest API level from that constraint.
        .map { it.fromInclusive() }
        // Get the minimum of all the lowest API levels, or -1 if there are no API levels in the
        // constraints.
        .minOrNull() ?: -1
}

fun ApiLookup.getClassVersion(cls: PsiClass): Int {
    val owner = cls.qualifiedName ?: return -1
    return getClassVersions(owner).minApiLevel()
}

val defaultEvaluator = DefaultJavaEvaluator(null, null)

fun ApiLookup.getMethodVersion(method: PsiMethod): Int {
    val containingClass = method.containingClass ?: return -1
    val owner = containingClass.qualifiedName ?: return -1
    val desc = defaultEvaluator.getMethodDescription(
        method,
        includeName = false,
        includeReturn = false
    )
    return getMethodVersions(owner, if (method.isConstructor) "<init>" else method.name, desc)
        .minApiLevel()
}

fun ApiLookup.getFieldVersion(field: PsiField): Int {
    val containingClass = field.containingClass ?: return -1
    val owner = containingClass.qualifiedName ?: return -1
    return getFieldVersions(owner, field.name).minApiLevel()
}

@Suppress("DEPRECATION")
fun ApiLookup.getClassDeprecatedIn(cls: PsiClass): Int {
    val owner = cls.qualifiedName ?: return -1
    return getClassDeprecatedIn(owner)
}

@Suppress("DEPRECATION")
fun ApiLookup.getMethodDeprecatedIn(method: PsiMethod): Int {
    val containingClass = method.containingClass ?: return -1
    val owner = containingClass.qualifiedName ?: return -1
    val desc = defaultEvaluator.getMethodDescription(
        method,
        includeName = false,
        includeReturn = false
    )
    return getMethodDeprecatedIn(owner, method.name, desc)
}

@Suppress("DEPRECATION")
fun ApiLookup.getFieldDeprecatedIn(field: PsiField): Int {
    val containingClass = field.containingClass ?: return -1
    val owner = containingClass.qualifiedName ?: return -1
    return getFieldDeprecatedIn(owner, field.name)
}

fun getApiLookup(xmlFile: File, cacheDir: File? = null): ApiLookup {
    val client = object : LintCliClient(PROGRAM_NAME) {
        override fun getCacheDir(name: String?, create: Boolean): File? {
            if (cacheDir != null) {
                return cacheDir
            }

            if (create && isUnderTest()) {
                // Pick unique directory during unit tests
                return Files.createTempDirectory(PROGRAM_NAME).toFile()
            }

            val sb = StringBuilder(PROGRAM_NAME)
            if (name != null) {
                sb.append(File.separator)
                sb.append(name)
            }
            val relative = sb.toString()

            val tmp = System.getenv("TMPDIR")
            if (tmp != null) {
                // Android Build environment: Make sure we're really creating a unique
                // temp directory each time since builds could be running in
                // parallel here.
                val dir = File(tmp, relative)
                if (!dir.isDirectory) {
                    dir.mkdirs()
                }

                return Files.createTempDirectory(dir.toPath(), null).toFile()
            }

            val dir = File(System.getProperty("java.io.tmpdir"), relative)
            if (create && !dir.isDirectory) {
                dir.mkdirs()
            }
            return dir
        }
    }

    val xmlPathProperty = "LINT_API_DATABASE"
    val prev = System.getProperty(xmlPathProperty)
    try {
        System.setProperty(xmlPathProperty, xmlFile.path)
        return ApiLookup.get(client) ?: error("ApiLookup creation failed")
    } finally {
        if (prev != null) {
            System.setProperty(xmlPathProperty, xmlFile.path)
        } else {
            System.clearProperty(xmlPathProperty)
        }
    }
}

/**
 * Generate a map of symbol -> (list of SDKs and corresponding versions the symbol first appeared)
 * in by parsing an api-versions.xml file. This will be used when injecting @sdkExtSince annotations,
 * which convey the same information, in a format documentation tools can consume.
 *
 * A symbol is either of a class, method or field.
 *
 * The symbols are Strings on the format "com.pkg.Foo#MethodOrField", with no method signature.
 */
private fun createSymbolToSdkExtSinceMap(xmlFile: File): Map<String, List<SdkAndVersion>> {
    data class OuterClass(val name: String, val idAndVersionList: List<IdAndVersion>?)

    val sdkIdentifiers = mutableMapOf<Int, SdkIdentifier>(
        ApiToExtensionsMap.ANDROID_PLATFORM_SDK_ID to SdkIdentifier(ApiToExtensionsMap.ANDROID_PLATFORM_SDK_ID, "Android", "Android", "null")
    )
    var lastSeenClass: OuterClass? = null
    val elementToIdAndVersionMap = mutableMapOf<String, List<IdAndVersion>>()
    val memberTags = listOf("class", "method", "field")
    val parser = SAXParserFactory.newDefaultInstance().newSAXParser()
    parser.parse(
        xmlFile,
        object : DefaultHandler() {
            override fun startElement(uri: String, localName: String, qualifiedName: String, attributes: Attributes) {
                if (qualifiedName == "sdk") {
                    val id: Int = attributes.getValue("id")?.toIntOrNull() ?: throw IllegalArgumentException("<sdk>: missing or non-integer id attribute")
                    val shortname: String = attributes.getValue("shortname") ?: throw IllegalArgumentException("<sdk>: missing shortname attribute")
                    val name: String = attributes.getValue("name") ?: throw IllegalArgumentException("<sdk>: missing name attribute")
                    val reference: String = attributes.getValue("reference") ?: throw IllegalArgumentException("<sdk>: missing reference attribute")
                    sdkIdentifiers.put(id, SdkIdentifier(id, shortname, name, reference))
                } else if (memberTags.contains(qualifiedName)) {
                    val name: String = attributes.getValue("name") ?: throw IllegalArgumentException("<$qualifiedName>: missing name attribute")
                    val idAndVersionList: List<IdAndVersion>? = attributes.getValue("sdks")?.split(",")?.map {
                        val (sdk, version) = it.split(":")
                        IdAndVersion(sdk.toInt(), version.toInt())
                    }?.toList()

                    // Populate elementToIdAndVersionMap. The keys constructed here are derived from
                    // api-versions.xml; when used elsewhere in DocAnalyzer, the keys will be
                    // derived from PsiItems. The two sources use slightly different nomenclature,
                    // so change "api-versions.xml nomenclature" to "PsiItems nomenclature" before
                    // inserting items in the map.
                    //
                    // Nomenclature differences:
                    //   - constructors are named "<init>()V" in api-versions.xml, but
                    //     "ClassName()V" in PsiItems
                    //   - inner classes are named "Outer#Inner" in api-versions.xml, but
                    //     "Outer.Inner" in PsiItems
                    when (qualifiedName) {
                        "class" -> {
                            lastSeenClass = OuterClass(name.replace('/', '.').replace('$', '.'), idAndVersionList)
                            if (idAndVersionList != null) {
                                elementToIdAndVersionMap["${lastSeenClass!!.name}"] = idAndVersionList
                            }
                        }
                        "method", "field" -> {
                            val shortName = if (name.startsWith("<init>")) {
                                // constructors in api-versions.xml are named '<init>': rename to
                                // name of class instead, and strip signature: '<init>()V' -> 'Foo'
                                lastSeenClass!!.name.substringAfterLast('.')
                            } else {
                                // strip signature: 'foo()V' -> 'foo'
                                name.substringBefore('(')
                            }
                            val element = "${lastSeenClass!!.name}#$shortName"
                            if (idAndVersionList != null) {
                                elementToIdAndVersionMap[element] = idAndVersionList
                            } else if (lastSeenClass!!.idAndVersionList != null) {
                                elementToIdAndVersionMap[element] = lastSeenClass!!.idAndVersionList!!
                            }
                        }
                    }
                }
            }

            override fun endElement(uri: String, localName: String, qualifiedName: String) {
                if (qualifiedName == "class") {
                    lastSeenClass = null
                }
            }
        }
    )

    val elementToSdkExtSinceMap = mutableMapOf<String, List<SdkAndVersion>>()
    for (entry in elementToIdAndVersionMap.entries) {
        elementToSdkExtSinceMap[entry.key] = entry.value.map {
            val name = sdkIdentifiers.get(it.first)?.name ?: throw IllegalArgumentException("SDK reference to unknown <sdk> with id ${it.first}")
            SdkAndVersion(it.first, name, it.second)
        }
    }
    return elementToSdkExtSinceMap
}

private fun NodeList.firstOrNull(): Node? = if (length > 0) { item(0) } else { null }

private typealias IdAndVersion = Pair<Int, Int>

private data class SdkAndVersion(val sdk: Int, val name: String, val version: Int)
