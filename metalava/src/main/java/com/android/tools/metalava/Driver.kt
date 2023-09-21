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
@file:JvmName("Driver")

package com.android.tools.metalava

import com.android.SdkConstants.DOT_JAR
import com.android.SdkConstants.DOT_TXT
import com.android.tools.lint.detector.api.assertionsEnabled
import com.android.tools.metalava.CompatibilityCheck.CheckRequest
import com.android.tools.metalava.apilevels.ApiGenerator
import com.android.tools.metalava.cli.common.CommonOptions
import com.android.tools.metalava.cli.common.EarlyOptions
import com.android.tools.metalava.cli.common.MetalavaCliException
import com.android.tools.metalava.cli.common.MetalavaCommand
import com.android.tools.metalava.cli.common.MetalavaLocalization
import com.android.tools.metalava.cli.common.ReporterOptions
import com.android.tools.metalava.cli.common.VersionCommand
import com.android.tools.metalava.cli.common.progressTracker
import com.android.tools.metalava.cli.common.registerPostCommandAction
import com.android.tools.metalava.cli.common.stderr
import com.android.tools.metalava.cli.common.stdout
import com.android.tools.metalava.cli.help.HelpCommand
import com.android.tools.metalava.cli.internal.MakeAnnotationsPackagePrivateCommand
import com.android.tools.metalava.cli.signature.MergeSignaturesCommand
import com.android.tools.metalava.cli.signature.SignatureFormatOptions
import com.android.tools.metalava.cli.signature.UpdateSignatureHeaderCommand
import com.android.tools.metalava.model.ClassItem
import com.android.tools.metalava.model.ClassResolver
import com.android.tools.metalava.model.Codebase
import com.android.tools.metalava.model.Item
import com.android.tools.metalava.model.psi.gatherSources
import com.android.tools.metalava.model.source.EnvironmentManager
import com.android.tools.metalava.model.source.SourceModelProvider
import com.android.tools.metalava.model.source.SourceParser
import com.android.tools.metalava.model.text.ApiClassResolution
import com.android.tools.metalava.model.text.TextClassItem
import com.android.tools.metalava.model.text.TextCodebase
import com.android.tools.metalava.model.text.TextMethodItem
import com.android.tools.metalava.model.visitors.ApiVisitor
import com.android.tools.metalava.reporter.Issues
import com.android.tools.metalava.reporter.Reporter
import com.android.tools.metalava.stub.StubWriter
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.context
import com.github.ajalt.clikt.core.subcommands
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.multiple
import com.github.ajalt.clikt.parameters.groups.OptionGroup
import com.github.ajalt.clikt.parameters.groups.provideDelegate
import com.google.common.base.Stopwatch
import java.io.File
import java.io.IOException
import java.io.OutputStreamWriter
import java.io.PrintWriter
import java.io.StringWriter
import java.util.concurrent.TimeUnit.SECONDS
import java.util.function.Predicate
import kotlin.system.exitProcess
import kotlin.text.Charsets.UTF_8

const val PROGRAM_NAME = "metalava"

fun main(args: Array<String>) {
    val stdout = PrintWriter(OutputStreamWriter(System.out))
    val stderr = PrintWriter(OutputStreamWriter(System.err))

    val exitCode = run(args, stdout, stderr)

    stdout.flush()
    stderr.flush()

    exitProcess(exitCode)
}

/**
 * The metadata driver is a command line interface to extracting various metadata from a source tree
 * (or existing signature files etc.). Run with --help to see more details.
 */
fun run(
    originalArgs: Array<String>,
    stdout: PrintWriter,
    stderr: PrintWriter,
): Int {
    // Preprocess the arguments by adding any additional arguments specified in environment
    // variables.
    val modifiedArgs = preprocessArgv(originalArgs)

    // Process the early options. This does not consume any arguments, they will be parsed again
    // later. A little inefficient but produces cleaner code.
    val earlyOptions = EarlyOptions.parse(modifiedArgs)

    val progressTracker = ProgressTracker(earlyOptions.verbosity.verbose, stdout)

    progressTracker.progress("$PROGRAM_NAME started\n")

    // Dump the arguments, and maybe generate a rerun-script.
    maybeDumpArgv(stdout, originalArgs, modifiedArgs)

    // Actual work begins here.
    val command =
        createMetalavaCommand(
            stdout,
            stderr,
            progressTracker,
        )
    val exitCode = command.process(modifiedArgs)

    stdout.flush()
    stderr.flush()

    progressTracker.progress("$PROGRAM_NAME exiting with exit code $exitCode\n")

    return exitCode
}

private fun repeatErrors(writer: PrintWriter, reporters: List<DefaultReporter>, max: Int) {
    writer.println("Error: $PROGRAM_NAME detected the following problems:")
    val totalErrors = reporters.sumOf { it.errorCount }
    var remainingCap = max
    var totalShown = 0
    reporters.forEach {
        val numShown = it.printErrors(writer, remainingCap)
        remainingCap -= numShown
        totalShown += numShown
    }
    if (totalShown < totalErrors) {
        writer.println(
            "${totalErrors - totalShown} more error(s) omitted. Search the log for 'error:' to find all of them."
        )
    }
}

@Suppress("DEPRECATION")
internal fun processFlags(
    environmentManager: EnvironmentManager,
    progressTracker: ProgressTracker
) {
    val stopwatch = Stopwatch.createStarted()

    val reporter = options.reporter
    val sourceParser =
        environmentManager.createSourceParser(
            reporter = reporter,
            annotationManager = options.annotationManager,
            javaLanguageLevel = options.javaLanguageLevelAsString,
            kotlinLanguageLevel = options.kotlinLanguageLevelAsString,
            useK2Uast = options.useK2Uast,
            jdkHome = options.jdkHome,
        )

    val sources = options.sources
    val codebase =
        if (sources.isNotEmpty() && sources[0].path.endsWith(DOT_TXT)) {
            // Make sure all the source files have .txt extensions.
            sources
                .firstOrNull { !it.path.endsWith(DOT_TXT) }
                ?.let {
                    throw MetalavaCliException(
                        "Inconsistent input file types: The first file is of $DOT_TXT, but detected different extension in ${it.path}"
                    )
                }
            val classResolver = getClassResolver(sourceParser)
            val textCodebase = SignatureFileLoader.loadFiles(sources, classResolver)

            // If this codebase was loaded in order to generate stubs then they will need some
            // additional items to be added that were purposely removed from the signature files.
            if (options.stubsDir != null) {
                addMissingItemsRequiredForGeneratingStubs(sourceParser, textCodebase)
            }
            textCodebase
        } else if (options.apiJar != null) {
            loadFromJarFile(progressTracker, reporter, sourceParser, options.apiJar!!)
        } else if (sources.size == 1 && sources[0].path.endsWith(DOT_JAR)) {
            loadFromJarFile(progressTracker, reporter, sourceParser, sources[0])
        } else if (sources.isNotEmpty() || options.sourcePath.isNotEmpty()) {
            loadFromSources(progressTracker, reporter, sourceParser)
        } else {
            return
        }

    progressTracker.progress(
        "$PROGRAM_NAME analyzed API in ${stopwatch.elapsed(SECONDS)} seconds\n"
    )

    options.subtractApi?.let {
        progressTracker.progress("Subtracting API: ")
        subtractApi(progressTracker, reporter, sourceParser, codebase, it)
    }

    if (options.hideAnnotations.matchesAnnotationName(ANDROID_FLAGGED_API)) {
        reallyHideFlaggedSystemApis(codebase)
    }

    val androidApiLevelXml = options.generateApiLevelXml
    val apiLevelJars = options.apiLevelJars
    if (androidApiLevelXml != null && apiLevelJars != null) {
        assert(options.currentApiLevel != -1)

        progressTracker.progress(
            "Generating API levels XML descriptor file, ${androidApiLevelXml.name}: "
        )
        val sdkJarRoot = options.sdkJarRoot
        val sdkInfoFile = options.sdkInfoFile
        var sdkExtArgs: ApiGenerator.SdkExtensionsArguments? =
            if (sdkJarRoot != null && sdkInfoFile != null) {
                ApiGenerator()
                    .SdkExtensionsArguments(
                        sdkJarRoot,
                        sdkInfoFile,
                        options.latestReleasedSdkExtension
                    )
            } else {
                null
            }
        ApiGenerator.generateXml(
            apiLevelJars,
            options.firstApiLevel,
            options.currentApiLevel,
            options.isDeveloperPreviewBuild(),
            androidApiLevelXml,
            codebase,
            sdkExtArgs,
            options.removeMissingClassesInApiLevels
        )
    }

    if (options.docStubsDir != null || options.enhanceDocumentation) {
        if (!codebase.supportsDocumentation()) {
            error("Codebase does not support documentation, so it cannot be enhanced.")
        }
        progressTracker.progress("Enhancing docs: ")
        val docAnalyzer = DocAnalyzer(codebase, reporter)
        docAnalyzer.enhance()
        val applyApiLevelsXml = options.applyApiLevelsXml
        if (applyApiLevelsXml != null) {
            progressTracker.progress("Applying API levels")
            docAnalyzer.applyApiLevels(applyApiLevelsXml)
        }
    }

    val apiVersionsJson = options.generateApiVersionsJson
    val apiVersionNames = options.apiVersionNames
    if (apiVersionsJson != null && apiVersionNames != null) {
        progressTracker.progress(
            "Generating API version history JSON file, ${apiVersionsJson.name}: "
        )
        ApiGenerator.generateJson(
            // The signature files can be null if the current version is the only version
            options.apiVersionSignatureFiles ?: emptyList(),
            codebase,
            apiVersionsJson,
            apiVersionNames
        )
    }

    // Generate the documentation stubs *before* we migrate nullness information.
    options.docStubsDir?.let {
        createStubFiles(
            progressTracker,
            it,
            codebase,
            docStubs = true,
            writeStubList = options.docStubsSourceList != null
        )
    }

    // Based on the input flags, generates various output files such
    // as signature files and/or stubs files
    options.apiFile?.let { apiFile ->
        val apiType = ApiType.PUBLIC_API
        val apiEmit = apiType.getEmitFilter(options.apiPredicateConfig)
        val apiReference = apiType.getReferenceFilter(options.apiPredicateConfig)

        createReportFile(progressTracker, codebase, apiFile, "API") { printWriter ->
            SignatureWriter(
                printWriter,
                apiEmit,
                apiReference,
                codebase.preFiltered,
                fileFormat = options.signatureFileFormat,
                showUnannotated = options.showUnannotated,
                apiVisitorConfig = options.apiVisitorConfig,
            )
        }
    }

    options.apiXmlFile?.let { apiFile ->
        val apiType = ApiType.PUBLIC_API
        val apiEmit = apiType.getEmitFilter(options.apiPredicateConfig)
        val apiReference = apiType.getReferenceFilter(options.apiPredicateConfig)

        createReportFile(progressTracker, codebase, apiFile, "XML API") { printWriter ->
            JDiffXmlWriter(
                printWriter,
                apiEmit,
                apiReference,
                codebase.preFiltered,
                showUnannotated = @Suppress("DEPRECATION") options.showUnannotated,
                config = options.apiVisitorConfig,
            )
        }
    }

    options.removedApiFile?.let { apiFile ->
        val unfiltered = codebase.original ?: codebase

        val apiType = ApiType.REMOVED
        val removedEmit = apiType.getEmitFilter(options.apiPredicateConfig)
        val removedReference = apiType.getReferenceFilter(options.apiPredicateConfig)

        createReportFile(
            progressTracker,
            unfiltered,
            apiFile,
            "removed API",
            options.deleteEmptyRemovedSignatures
        ) { printWriter ->
            SignatureWriter(
                printWriter,
                removedEmit,
                removedReference,
                codebase.original != null,
                options.includeSignatureFormatVersionRemoved,
                options.signatureFileFormat,
                options.showUnannotated,
                options.apiVisitorConfig,
            )
        }
    }

    val apiPredicateConfigIgnoreShown = options.apiPredicateConfig.copy(ignoreShown = true)
    val apiReferenceIgnoreShown = ApiPredicate(config = apiPredicateConfigIgnoreShown)
    options.dexApiFile?.let { apiFile ->
        val apiFilter = FilterPredicate(ApiPredicate())
        val memberIsNotCloned: Predicate<Item> = Predicate { !it.isCloned() }
        val dexApiEmit = memberIsNotCloned.and(apiFilter)

        createReportFile(progressTracker, codebase, apiFile, "DEX API") { printWriter ->
            DexApiWriter(printWriter, dexApiEmit, apiReferenceIgnoreShown)
        }
    }

    options.proguard?.let { proguard ->
        val apiEmit = FilterPredicate(ApiPredicate())
        createReportFile(progressTracker, codebase, proguard, "Proguard file") { printWriter ->
            ProguardWriter(printWriter, apiEmit, apiReferenceIgnoreShown)
        }
    }

    options.sdkValueDir?.let { dir ->
        dir.mkdirs()
        SdkFileWriter(codebase, dir).generate()
    }

    for (check in options.compatibilityChecks) {
        checkCompatibility(progressTracker, reporter, sourceParser, codebase, check)
    }

    val previousApiFile = options.migrateNullsFrom
    if (previousApiFile != null) {
        val previous =
            if (previousApiFile.path.endsWith(DOT_JAR)) {
                loadFromJarFile(progressTracker, reporter, sourceParser, previousApiFile)
            } else {
                SignatureFileLoader.load(file = previousApiFile)
            }

        // If configured, checks for newly added nullness information compared
        // to the previous stable API and marks the newly annotated elements
        // as migrated (which will cause the Kotlin compiler to treat problems
        // as warnings instead of errors

        NullnessMigration.migrateNulls(codebase, previous)

        previous.dispose()
    }

    convertToWarningNullabilityAnnotations(
        codebase,
        options.forceConvertToWarningNullabilityAnnotations
    )

    // Now that we've migrated nullness information we can proceed to write non-doc stubs, if any.

    options.stubsDir?.let {
        createStubFiles(
            progressTracker,
            it,
            codebase,
            docStubs = false,
            writeStubList = options.stubsSourceList != null
        )
    }

    if (options.docStubsDir == null && options.stubsDir == null) {
        val writeStubsFile: (File) -> Unit = { file ->
            val root = File("").absoluteFile
            val rootPath = root.path
            val contents =
                sources.joinToString(" ") {
                    val path = it.path
                    if (path.startsWith(rootPath)) {
                        path.substring(rootPath.length)
                    } else {
                        path
                    }
                }
            file.writeText(contents)
        }
        options.stubsSourceList?.let(writeStubsFile)
        options.docStubsSourceList?.let(writeStubsFile)
    }
    options.externalAnnotations?.let { extractAnnotations(progressTracker, codebase, it) }

    val packageCount = codebase.size()
    progressTracker.progress(
        "$PROGRAM_NAME finished handling $packageCount packages in ${stopwatch.elapsed(SECONDS)} seconds\n"
    )
}

/**
 * When generate stubs from text signature files some additional items are needed.
 *
 * Those items are:
 * * Constructors - in the signature file a missing constructor means no publicly visible
 *   constructor but the stub classes still need a constructor.
 * * Concrete methods - in the signature file concrete implementations of inherited abstract methods
 *   are not listed on concrete classes but the stub concrete classes need those implementations.
 */
@Suppress("DEPRECATION")
private fun addMissingItemsRequiredForGeneratingStubs(
    sourceParser: SourceParser,
    textCodebase: TextCodebase,
) {
    // Only add constructors if the codebase does not fall back to loading classes from the
    // classpath. This is needed because only the TextCodebase supports adding constructors
    // in this way.
    if (options.apiClassResolution == ApiClassResolution.API) {
        // Reuse the existing ApiAnalyzer support for adding constructors that is used in
        // [loadFromSources], to make sure that the constructors are correct when generating stubs
        // from source files.
        val analyzer =
            ApiAnalyzer(sourceParser, textCodebase, options.reporter, options.apiAnalyzerConfig)
        analyzer.addConstructors { _ -> true }

        addMissingConcreteMethods(
            textCodebase.getPackages().allClasses().map { it as TextClassItem }.toList()
        )
    }
}

/**
 * Add concrete implementations of inherited abstract methods to non-abstract class when generating
 * from-text stubs. Iterate through the hierarchy and collect all super abstract methods that need
 * to be added. These are not included in the signature files but omitting these methods will lead
 * to compile error.
 */
fun addMissingConcreteMethods(allClasses: List<TextClassItem>) {
    for (cl in allClasses) {
        // If class is interface, naively iterate through all parent class and interfaces
        // and resolve inheritance of override equivalent signatures
        // Find intersection of super class/interface default methods
        // Resolve conflict by adding signature
        // https://docs.oracle.com/javase/specs/jls/se8/html/jls-9.html#jls-9.4.1.3
        if (cl.isInterface()) {
            // We only need to track one method item(value) with the signature(key),
            // since the containing class does not matter if a method to be added is found
            // as method.duplicate(cl) sets containing class to cl.
            // Therefore, the value of methodMap can be overwritten.
            val methodMap = mutableMapOf<String, TextMethodItem>()
            val methodCount = mutableMapOf<String, Int>()
            val hasDefault = mutableMapOf<String, Boolean>()
            for (superInterfaceOrClass in cl.getParentAndInterfaces()) {
                val methods = superInterfaceOrClass.methods().map { it as TextMethodItem }
                for (method in methods) {
                    val signature = method.toSignatureString()
                    val isDefault = method.modifiers.isDefault()
                    val newCount = methodCount.getOrDefault(signature, 0) + 1
                    val newHasDefault = hasDefault.getOrDefault(signature, false) || isDefault

                    methodMap[signature] = method
                    methodCount[signature] = newCount
                    hasDefault[signature] = newHasDefault

                    // If the method has appeared more than once, there may be a potential
                    // conflict
                    // thus add the method to the interface
                    if (
                        newHasDefault && newCount == 2 && !cl.containsMethodInClassContext(method)
                    ) {
                        val m = method.duplicate(cl) as TextMethodItem
                        m.modifiers.setAbstract(true)
                        m.modifiers.setDefault(false)
                        cl.addMethod(m)
                    }
                }
            }
        }

        // If class is a concrete class, iterate through all hierarchy and
        // find all missing abstract methods.
        // Only add methods that are not implemented in the hierarchy and not included
        else if (!cl.isAbstractClass() && !cl.isEnum()) {
            val superMethodsToBeOverridden = mutableListOf<TextMethodItem>()
            val hierarchyClassesList = cl.getAllSuperClassesAndInterfaces().toMutableList()
            while (hierarchyClassesList.isNotEmpty()) {
                val ancestorClass = hierarchyClassesList.removeLast()
                val abstractMethods = ancestorClass.methods().filter { it.modifiers.isAbstract() }
                for (method in abstractMethods) {
                    // We do not compare this against all ancestors of cl,
                    // because an abstract method cannot be overridden at its ancestor class.
                    // Thus, we compare against hierarchyClassesList.
                    if (
                        hierarchyClassesList.all { !it.containsMethodInClassContext(method) } &&
                            !cl.containsMethodInClassContext(method)
                    ) {
                        superMethodsToBeOverridden.add(method as TextMethodItem)
                    }
                }
            }
            for (superMethod in superMethodsToBeOverridden) {
                // MethodItem.duplicate() sets the containing class of
                // the duplicated method item as the input parameter.
                // Thus, the method items to be overridden are duplicated here after the
                // ancestor classes iteration so that the method items are correctly compared.
                val m = superMethod.duplicate(cl) as TextMethodItem
                m.modifiers.setAbstract(false)
                cl.addMethod(m)
            }
        }
    }
}

fun subtractApi(
    progressTracker: ProgressTracker,
    reporter: Reporter,
    sourceParser: SourceParser,
    codebase: Codebase,
    subtractApiFile: File,
) {
    val path = subtractApiFile.path
    val oldCodebase =
        when {
            path.endsWith(DOT_TXT) -> SignatureFileLoader.load(subtractApiFile)
            path.endsWith(DOT_JAR) ->
                loadFromJarFile(progressTracker, reporter, sourceParser, subtractApiFile)
            else ->
                throw MetalavaCliException(
                    "Unsupported $ARG_SUBTRACT_API format, expected .txt or .jar: ${subtractApiFile.name}"
                )
        }

    @Suppress("DEPRECATION")
    CodebaseComparator()
        .compare(
            object : ComparisonVisitor() {
                override fun compare(old: ClassItem, new: ClassItem) {
                    new.emit = false
                }
            },
            oldCodebase,
            codebase,
            ApiType.ALL.getReferenceFilter(options.apiPredicateConfig)
        )
}

fun reallyHideFlaggedSystemApis(codebase: Codebase) {
    @Suppress("DEPRECATION")
    val apiPredicateConfigIgnoreShown = options.apiPredicateConfig.copy(ignoreShown = true)
    val apiEmitAndReference = ApiPredicate(config = apiPredicateConfigIgnoreShown)
    codebase.accept(
        object :
            ApiVisitor(
                filterEmit = apiEmitAndReference,
                filterReference = apiEmitAndReference,
                includeEmptyOuterClasses = true
            ) {
            override fun visitItem(item: Item) {
                item.modifiers.findAnnotation(ANDROID_FLAGGED_API) ?: return
                item.hidden = true
                item.mutableModifiers().removeAnnotations { it.isShowAnnotation() }
            }
        }
    )
}

/** Checks compatibility of the given codebase with the codebase described in the signature file. */
@Suppress("DEPRECATION")
fun checkCompatibility(
    progressTracker: ProgressTracker,
    reporter: Reporter,
    sourceParser: SourceParser,
    newCodebase: Codebase,
    check: CheckRequest,
) {
    progressTracker.progress("Checking API compatibility ($check): ")
    val signatureFile = check.file

    val oldCodebase =
        if (signatureFile.path.endsWith(DOT_JAR)) {
            loadFromJarFile(progressTracker, reporter, sourceParser, signatureFile)
        } else {
            val classResolver = getClassResolver(sourceParser)
            SignatureFileLoader.load(signatureFile, classResolver)
        }

    var baseApi: Codebase? = null

    val apiType = check.apiType

    if (options.showUnannotated && apiType == ApiType.PUBLIC_API) {
        // Fast path: if we've already generated a signature file, and it's identical, we're good!
        val apiFile = options.apiFile
        if (apiFile != null && apiFile.readText(UTF_8) == signatureFile.readText(UTF_8)) {
            return
        }
        val baseApiFile = options.baseApiForCompatCheck
        if (baseApiFile != null) {
            baseApi = SignatureFileLoader.load(file = baseApiFile)
        }
    } else if (options.baseApiForCompatCheck != null) {
        // This option does not make sense with showAnnotation, as the "base" in that case
        // is the non-annotated APIs.
        throw MetalavaCliException(
            "$ARG_CHECK_COMPATIBILITY_BASE_API is not compatible with --showAnnotation."
        )
    }

    // If configured, compares the new API with the previous API and reports
    // any incompatibilities.
    CompatibilityCheck.checkCompatibility(
        newCodebase,
        oldCodebase,
        apiType,
        baseApi,
        options.reporterCompatibilityReleased,
        options.issueConfiguration,
    )
}

private fun convertToWarningNullabilityAnnotations(codebase: Codebase, filter: PackageFilter?) {
    if (filter != null) {
        // Our caller has asked for these APIs to not trigger nullness errors (only warnings) if
        // their callers make incorrect nullness assumptions (for example, calling a function on a
        // reference of nullable type). The way to communicate this to kotlinc is to mark these
        // APIs as RecentlyNullable/RecentlyNonNull
        codebase.accept(MarkPackagesAsRecent(filter))
    }
}

@Suppress("DEPRECATION")
private fun loadFromSources(
    progressTracker: ProgressTracker,
    reporter: Reporter,
    sourceParser: SourceParser,
): Codebase {
    progressTracker.progress("Processing sources: ")

    val sources =
        options.sources.ifEmpty {
            if (options.verbose) {
                options.stdout.println(
                    "No source files specified: recursively including all sources found in the source path (${options.sourcePath.joinToString()}})"
                )
            }
            gatherSources(options.reporter, options.sourcePath)
        }

    progressTracker.progress("Reading Codebase: ")
    val codebase =
        sourceParser.parseSources(
            sources,
            "Codebase loaded from source folders",
            sourcePath = options.sourcePath,
            classPath = options.classpath,
        )

    progressTracker.progress("Analyzing API: ")

    val analyzer = ApiAnalyzer(sourceParser, codebase, options.reporter, options.apiAnalyzerConfig)
    analyzer.mergeExternalInclusionAnnotations()
    analyzer.computeApi()

    val apiPredicateConfigIgnoreShown = options.apiPredicateConfig.copy(ignoreShown = true)
    val filterEmit = ApiPredicate(ignoreRemoved = false, config = apiPredicateConfigIgnoreShown)
    val apiEmitAndReference = ApiPredicate(config = apiPredicateConfigIgnoreShown)

    // Copy methods from soon-to-be-hidden parents into descendant classes, when necessary. Do
    // this before merging annotations or performing checks on the API to ensure that these methods
    // can have annotations added and are checked properly.
    progressTracker.progress("Insert missing stubs methods: ")
    analyzer.generateInheritedStubs(apiEmitAndReference, apiEmitAndReference)

    analyzer.mergeExternalQualifierAnnotations()
    options.nullabilityAnnotationsValidator?.validateAllFrom(
        codebase,
        options.validateNullabilityFromList
    )
    options.nullabilityAnnotationsValidator?.report()
    analyzer.handleStripping()

    // General API checks for Android APIs
    AndroidApiChecks(options.reporter).check(codebase)

    if (options.checkApi) {
        progressTracker.progress("API Lint: ")
        val localTimer = Stopwatch.createStarted()
        // See if we should provide a previous codebase to provide a delta from?
        val previousApiFile = options.checkApiBaselineApiFile
        val previous =
            when {
                previousApiFile == null -> null
                previousApiFile.path.endsWith(DOT_JAR) ->
                    loadFromJarFile(progressTracker, reporter, sourceParser, previousApiFile)
                else -> SignatureFileLoader.load(file = previousApiFile)
            }
        val apiLintReporter = options.reporterApiLint as DefaultReporter
        ApiLint(codebase, previous, apiLintReporter, options.manifest, options.apiVisitorConfig)
            .check()
        progressTracker.progress(
            "$PROGRAM_NAME ran api-lint in ${localTimer.elapsed(SECONDS)} seconds with ${apiLintReporter.getBaselineDescription()}"
        )
    }

    // Compute default constructors (and add missing package private constructors
    // to make stubs compilable if necessary). Do this after all the checks as
    // these are not part of the API.
    if (options.stubsDir != null || options.docStubsDir != null) {
        progressTracker.progress("Insert missing constructors: ")
        analyzer.addConstructors(filterEmit)
    }

    progressTracker.progress("Performing misc API checks: ")
    analyzer.performChecks()

    return codebase
}

@Suppress("DEPRECATION")
private fun getClassResolver(sourceParser: SourceParser): ClassResolver? {
    val apiClassResolution = options.apiClassResolution
    val classpath = options.classpath
    return if (apiClassResolution == ApiClassResolution.API_CLASSPATH && classpath.isNotEmpty()) {
        sourceParser.getClassResolver(classpath)
    } else {
        null
    }
}

@Suppress("DEPRECATION")
fun loadFromJarFile(
    progressTracker: ProgressTracker,
    reporter: Reporter,
    sourceParser: SourceParser,
    apiJar: File,
    preFiltered: Boolean = false,
    apiAnalyzerConfig: ApiAnalyzer.Config = options.apiAnalyzerConfig,
    codebaseValidator: (Codebase) -> Unit = { codebase ->
        options.nullabilityAnnotationsValidator?.validateAllFrom(
            codebase,
            options.validateNullabilityFromList
        )
        options.nullabilityAnnotationsValidator?.report()
    },
    apiPredicateConfig: ApiPredicate.Config = options.apiPredicateConfig,
): Codebase {
    progressTracker.progress("Processing jar file: ")

    val codebase = sourceParser.loadFromJar(apiJar, preFiltered)
    val apiEmit =
        ApiPredicate(
            config = apiPredicateConfig.copy(ignoreShown = true),
        )
    val apiReference = apiEmit
    val analyzer = ApiAnalyzer(sourceParser, codebase, reporter, apiAnalyzerConfig)
    analyzer.mergeExternalInclusionAnnotations()
    analyzer.computeApi()
    analyzer.mergeExternalQualifierAnnotations()
    codebaseValidator(codebase)
    analyzer.generateInheritedStubs(apiEmit, apiReference)
    return codebase
}

internal fun disableStderrDumping(): Boolean {
    return !assertionsEnabled() &&
        System.getenv(ENV_VAR_METALAVA_DUMP_ARGV) == null &&
        !isUnderTest()
}

@Suppress("DEPRECATION")
private fun extractAnnotations(progressTracker: ProgressTracker, codebase: Codebase, file: File) {
    val localTimer = Stopwatch.createStarted()

    options.externalAnnotations?.let { outputFile ->
        ExtractAnnotations(codebase, options.reporter, outputFile).extractAnnotations()
        if (options.verbose) {
            progressTracker.progress(
                "$PROGRAM_NAME extracted annotations into $file in ${localTimer.elapsed(SECONDS)} seconds\n"
            )
        }
    }
}

@Suppress("DEPRECATION")
private fun createStubFiles(
    progressTracker: ProgressTracker,
    stubDir: File,
    codebase: Codebase,
    docStubs: Boolean,
    writeStubList: Boolean
) {
    if (codebase is TextCodebase) {
        if (options.verbose) {
            options.stdout.println(
                "Generating stubs from text based codebase is an experimental feature. " +
                    "It is not guaranteed that stubs generated from text based codebase are " +
                    "class level equivalent to the stubs generated from source files. "
            )
        }
    }

    // Temporary bug workaround for org.chromium.arc
    if (options.sourcePath.firstOrNull()?.path?.endsWith("org.chromium.arc") == true) {
        codebase.findClass("org.chromium.mojo.bindings.Callbacks")?.hidden = true
    }

    if (docStubs) {
        progressTracker.progress("Generating documentation stub files: ")
    } else {
        progressTracker.progress("Generating stub files: ")
    }

    val localTimer = Stopwatch.createStarted()

    val stubWriter =
        StubWriter(
            codebase = codebase,
            stubsDir = stubDir,
            generateAnnotations = options.generateAnnotations,
            preFiltered = codebase.preFiltered,
            docStubs = docStubs,
            reporter = options.reporter,
        )
    codebase.accept(stubWriter)

    if (docStubs) {
        // Overview docs? These are generally in the empty package.
        codebase.findPackage("")?.let { empty ->
            val overview = codebase.getPackageDocs()?.getOverviewDocumentation(empty)
            if (!overview.isNullOrBlank()) {
                stubWriter.writeDocOverview(empty, overview)
            }
        }
    }

    if (writeStubList) {
        // Optionally also write out a list of source files that were generated; used
        // for example to point javadoc to the stubs output to generate documentation
        val file =
            if (docStubs) {
                options.docStubsSourceList ?: options.stubsSourceList
            } else {
                options.stubsSourceList
            }
        file?.let {
            val root = File("").absoluteFile
            stubWriter.writeSourceList(it, root)
        }
    }

    progressTracker.progress(
        "$PROGRAM_NAME wrote ${if (docStubs) "documentation" else ""} stubs directory $stubDir in ${
        localTimer.elapsed(SECONDS)} seconds\n"
    )
}

@Suppress("DEPRECATION")
fun createReportFile(
    progressTracker: ProgressTracker,
    codebase: Codebase,
    apiFile: File,
    description: String?,
    deleteEmptyFiles: Boolean = false,
    createVisitor: (PrintWriter) -> ApiVisitor
) {
    if (description != null) {
        progressTracker.progress("Writing $description file: ")
    }
    val localTimer = Stopwatch.createStarted()
    try {
        val stringWriter = StringWriter()
        val writer = PrintWriter(stringWriter)
        writer.use { printWriter ->
            val apiWriter = createVisitor(printWriter)
            codebase.accept(apiWriter)
        }
        val text = stringWriter.toString()
        if (text.isNotEmpty() || !deleteEmptyFiles) {
            apiFile.writeText(text)
        }
    } catch (e: IOException) {
        options.reporter.report(Issues.IO_ERROR, apiFile, "Cannot open file for write.")
    }
    if (description != null) {
        progressTracker.progress(
            "$PROGRAM_NAME wrote $description file $apiFile in ${localTimer.elapsed(SECONDS)} seconds\n"
        )
    }
}

/** Whether metalava is running unit tests */
fun isUnderTest() = java.lang.Boolean.getBoolean(ENV_VAR_METALAVA_TESTS_RUNNING)

/** Whether metalava is being invoked as part of an Android platform build */
fun isBuildingAndroid() = System.getenv("ANDROID_BUILD_TOP") != null && !isUnderTest()

private fun createMetalavaCommand(
    stdout: PrintWriter,
    stderr: PrintWriter,
    progressTracker: ProgressTracker
): MetalavaCommand {
    val command =
        MetalavaCommand(
            stdout,
            stderr,
            ::DriverCommand,
            progressTracker,
            OptionsHelp::getUsage,
        )
    command.subcommands(
        AndroidJarsToSignaturesCommand(),
        HelpCommand(),
        MakeAnnotationsPackagePrivateCommand(),
        MergeSignaturesCommand(),
        SignatureToJDiffCommand(),
        UpdateSignatureHeaderCommand(),
        VersionCommand(),
    )
    return command
}

/**
 * A command that is passed to [MetalavaCommand.defaultCommand] when the main metalava functionality
 * needs to be run when no subcommand is provided.
 */
private class DriverCommand(
    commonOptions: CommonOptions,
) : CliktCommand(treatUnknownOptionsAsArgs = true) {

    init {
        // Although, the `helpFormatter` is inherited from the parent context unless overridden the
        // same is not true for the `localization` so make sure to initialize it for this command.
        context { localization = MetalavaLocalization() }
    }

    /**
     * Property into which all the arguments (and unknown options) are gathered.
     *
     * This does not provide any `help` so that it is excluded from the `help` by
     * [MetalavaCommand.excludeArgumentsWithNoHelp].
     */
    private val flags by argument().multiple()

    /** Issue reporter configuration. */
    private val reporterOptions by ReporterOptions()

    /** Signature file options. */
    private val signatureFileOptions by SignatureFileOptions()

    /** Signature format options. */
    private val signatureFormatOptions by SignatureFormatOptions()

    /** Stub generation options. */
    private val stubGenerationOptions by StubGenerationOptions()

    /**
     * Add [Options] (an [OptionGroup]) so that any Clikt defined properties will be processed by
     * Clikt.
     */
    private val optionGroup by
        Options(
            commonOptions = commonOptions,
            reporterOptions = reporterOptions,
            signatureFileOptions = signatureFileOptions,
            signatureFormatOptions = signatureFormatOptions,
            stubGenerationOptions = stubGenerationOptions,
        )

    override fun run() {
        // Make sure to flush out the baseline files, close files and write any final messages.
        registerPostCommandAction {
            // Update and close all baseline files.
            optionGroup.allBaselines.forEach { baseline ->
                if (optionGroup.verbose) {
                    baseline.dumpStats(optionGroup.stdout)
                }
                if (baseline.close()) {
                    if (!optionGroup.quiet) {
                        stdout.println(
                            "$PROGRAM_NAME wrote updated baseline to ${baseline.updateFile}"
                        )
                    }
                }
            }

            optionGroup.reportEvenIfSuppressedWriter?.close()

            // Show failure messages, if any.
            optionGroup.allReporters.forEach { it.writeErrorMessage(stderr) }
        }

        // Get any remaining arguments/options that were not handled by Clikt.
        val remainingArgs = flags.toTypedArray()

        // Parse any remaining arguments
        optionGroup.parse(remainingArgs, stdout, stderr)

        // Update the global options.
        @Suppress("DEPRECATION")
        options = optionGroup

        val sourceModelProvider =
            SourceModelProvider.getImplementation(optionGroup.sourceModelProvider)
        sourceModelProvider.createEnvironmentManager(disableStderrDumping()).use {
            processFlags(it, progressTracker)
        }

        if (optionGroup.allReporters.any { it.hasErrors() } && !optionGroup.passBaselineUpdates) {
            // Repeat the errors at the end to make it easy to find the actual problems.
            if (reporterOptions.repeatErrorsMax > 0) {
                repeatErrors(stderr, optionGroup.allReporters, reporterOptions.repeatErrorsMax)
            }

            // Make sure that the process exits with an error code.
            throw MetalavaCliException(exitCode = -1)
        }
    }
}
