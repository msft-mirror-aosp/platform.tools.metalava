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

package com.android.tools.metalava.cli.signature.migration

import com.android.tools.metalava.cli.common.MetalavaCliException
import com.android.tools.metalava.cli.common.MetalavaSubCommand
import com.android.tools.metalava.cli.common.commonOptions
import com.android.tools.metalava.cli.common.existingFile
import com.android.tools.metalava.cli.common.stdout
import com.android.tools.metalava.cli.signature.ARG_USE_SAME_FORMAT_AS
import com.android.tools.metalava.cli.signature.SignatureFormatOptions
import com.android.tools.metalava.cli.signature.writeSignatureFile
import com.android.tools.metalava.model.Codebase
import com.android.tools.metalava.model.Codebase.Config
import com.android.tools.metalava.model.FlaggedApiInheritance
import com.android.tools.metalava.model.StripJavaLangPrefix
import com.android.tools.metalava.model.text.ApiFile
import com.android.tools.metalava.model.text.CustomizableProperty
import com.android.tools.metalava.model.text.FileFormat
import com.android.tools.metalava.model.text.SignatureFile
import com.android.tools.metalava.reporter.ThrowingReporter
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.multiple
import com.github.ajalt.clikt.parameters.groups.provideDelegate
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import java.io.File

internal val defaultMigrationTargetFormat = FileFormat.V6

class SignatureMigrateCommand(
    /**
     * Allows tests to provide their own [ChangeCommitter].
     *
     * Defaults to using [GitChangeCommitter].
     */
    private val committerFactory: SignatureMigrateCommand.() -> ChangeCommitter = {
        GitChangeCommitter()
    },
) :
    MetalavaSubCommand(
        help =
            """
                Migrates signature files to a new format.

                The purpose of this is, by working in conjunction with the $ARG_USE_SAME_FORMAT_AS
                option, to simplify the process for updating signature files from one version to the
                next. It assumes a number of things:

                1. That API signature files are checked into some version control system and need to
                be updated to reflect changes to the API. If they are not then this is not needed.

                2. The build uses the $ARG_USE_SAME_FORMAT_AS to pass the checked in API signature
                file so that its format will be used as the output for the file that the build
                generates to replace it.

                If those assumptions are met then updating the format version of the API file (and
                its corresponding removed API file if needed) simply involves running this command
                on the API files that need migrating, specifying the target format. That will apply
                a number of migration steps, creating a separate commit for each step. The migration
                steps will be optimized to make the minimum number of changes to each file.
            """
                .trimIndent(),
        printHelpOnEmptyArgs = false,
    ) {

    private val formatOptions by
        SignatureFormatOptions(
            migratingAllowed = true,
            defaultFileFormat = defaultMigrationTargetFormat,
        )

    private val initialTitle by
        option(metavar = "<text>", help = "Title for the initial commit").required()

    private val titlePrefix by
        option(
            metavar = "<prefix>",
            help =
                """
                    Short prefix for commit titles.

                    If supplied then it will be prepended on every commit title.
                """,
        )

    private val commitProlog by
        option(
                metavar = "<text>",
                help = "Text to include at the beginning of each commit message.",
            )
            .default("")

    private val commitEpilog by
        option(
                metavar = "<text>",
                help = "Text to include at the end of each commit message.",
            )
            .default("")

    private val files by
        argument(
                name = "<files>",
                help =
                    """
                        Signature files to migrate.
                    """,
            )
            .existingFile()
            .multiple(required = true)

    override fun run() {
        // Get the target format for the signature files.
        val targetFormat = formatOptions.fileFormat

        // Iterate over the files, creating migration info for each one, if needed.
        val filesToMigrate = files.mapNotNull { file -> createFileToMigrate(file, targetFormat) }
        if (filesToMigrate.isEmpty()) {
            stdout.println("No files need migrating")
            return
        }

        // Make sure that they are all the same format.
        ensureFilesAreTheSameFormat(filesToMigrate)

        // Create a map from PropertyChange to a FileMigrationPlan for each file that requires that
        // change applying.
        val propertyChangeToAffectedFiles =
            filesToMigrate
                // Map from a list of FileToMigrate instances to a list of pairs from PropertyChange
                // to FileToMigrate
                .flatMap { fileToMigrate ->
                    fileToMigrate.propertyChanges.map { it to fileToMigrate }
                }
                // Then group the FileToMigrate instances by PropertyChange.
                .groupBy(keySelector = { it.first }, valueTransform = { it.second })

        // Build a list of [MigrationStep]s to perform.
        val migrationSteps = buildList {
            // Add the initial reformat step. It will reformat all files that require migrating.
            add(
                createInitialMigrationStep(
                    filesToMigrate,
                    targetFormat,
                    propertyChangeToAffectedFiles.size
                )
            )

            // Create a MigrationStep for each property that moves the format towards the
            // target.
            propertyChangeToAffectedFiles.mapTo(this) { (propertyChange, files) ->
                createMigrationStepForPropertyChange(propertyChange, files)
            }
        }

        // Print a summary of the migration process to stdout.
        if (commonOptions.verbosity.verbose) {
            summarizeMigrationProcess(migrationSteps)
        }

        // Perform the actual migration.
        performMigration(migrationSteps)
    }

    /**
     * Create a [PropertyChange] for this [CustomizableProperty] between [oldFormat] and [newFormat]
     * if the values have changed, otherwise return `null`.
     */
    private fun <T> CustomizableProperty<T>.computeChangeIfAny(
        oldFormat: FileFormat,
        newFormat: FileFormat,
    ): PropertyChange<T>? {
        val oldValue = oldFormat[this]
        val newValue = newFormat[this]
        return if (oldValue == newValue) null else PropertyChange(this, oldValue, newValue)
    }

    /** Create [FileToMigrate] for migrating [file] to [targetFormat]. */
    private fun createFileToMigrate(file: File, targetFormat: FileFormat): FileToMigrate? {
        // Read the current format from the file header, if none could be found then the file is
        // empty and intentionally has no file format header so leave it unchanged.
        val fileFormat =
            file.reader().use { reader -> FileFormat.parseHeader(file.toPath(), reader) }
        if (fileFormat == null) return null

        // Make sure to apply any defaults provided to the file format to ensure it is the same
        // format as was used to create the signature file.
        val currentFormat = formatOptions.applyDefaultsTo(fileFormat)

        //  If the format is the same as the target format then there is nothing to do either.
        if (currentFormat == targetFormat) return null

        // Read the codebase.
        val codebase = readSignatureFile(file)

        // Compute the output format to use when writing out this file.
        val outputFormat = computeInitialOutputFormat(currentFormat, targetFormat, codebase)

        val propertyChanges =
            CustomizableProperty.entries.mapNotNull { property ->
                property.takeIf { it.defaultable }?.computeChangeIfAny(outputFormat, targetFormat)
            }

        return FileToMigrate(file, currentFormat, codebase, outputFormat, propertyChanges)
    }

    /**
     * Compute the initial output [FileFormat] to use.
     *
     * Uses [FileStructurePreserver] to create a [FileFormat] from [targetFormat] with settings from
     * [currentFormat] needed to preserve the structure.
     *
     * @param currentFormat the current [FileFormat] for the file.
     * @param targetFormat the target [FileFormat] to which the file is to be reformatted.
     * @param codebase the [Codebase] loaded from the signature file. Used to determine whether a
     *   property setting affects the structure of the file.
     */
    private fun computeInitialOutputFormat(
        currentFormat: FileFormat,
        targetFormat: FileFormat,
        codebase: Codebase,
    ) = FileStructurePreserver(currentFormat, targetFormat, codebase).computeFormat()

    /**
     * Ensure that all the files that require migration are being migrated from the same
     * [FileFormat] and return it.
     */
    private fun ensureFilesAreTheSameFormat(filesToMigrate: List<FileToMigrate>) {
        val groupedByOriginalFormat =
            filesToMigrate.groupBy(
                keySelector = { it.originalFormat },
                valueTransform = { it.file },
            )
        if (groupedByOriginalFormat.size != 1) {
            throw MetalavaCliException(
                stderr =
                    buildString {
                        append("This can only be used to migrate files of the same format but\n")
                        append("there are ${groupedByOriginalFormat.size} different formats.\n")
                        for ((originalFormat, files) in groupedByOriginalFormat) {
                            append("\n")
                            append(
                                "  The following files use format: ${originalFormat.specifier()}\n"
                            )
                            for (file in files) {
                                append("    $file\n")
                            }
                        }
                        append("\n")
                    }
            )
        }
    }

    /**
     * Create a commit [ChangeDescription] from [description].
     *
     * Adds the [titlePrefix], [commitProlog] and [commitEpilog].
     */
    private fun createCommitDescription(description: ChangeDescription): ChangeDescription =
        ChangeDescription(
            title =
                buildString {
                    titlePrefix?.let { append(it) }
                    append(description.title)
                },
            detail =
                buildString {
                    val prolog = commitProlog
                    if (prolog.isNotBlank()) {
                        append(prolog.trimIndent())
                        append("\n\n")
                    }

                    append(description.detail)

                    val epilog = commitEpilog
                    if (epilog.isNotBlank()) {
                        append("\n\n")
                        append(epilog.trimIndent())
                    }
                },
        )

    /**
     * Create a [MigrationStep], calling [createCommitDescription] on [description] to add any
     * additional information needed in the commit description.
     */
    private fun createMigrationStep(
        filesToMigrate: List<FileToMigrate>,
        description: ChangeDescription,
        optionalPropertyChange: PropertyChange<*>?,
    ) =
        MigrationStep(
            filesToMigrate = filesToMigrate,
            description = createCommitDescription(description),
            optionalPropertyChange = optionalPropertyChange,
        )

    /**
     * Create a migration step for [files] that will change them from their current format to their
     * initial output format.
     */
    private fun createInitialMigrationStep(
        files: List<FileToMigrate>,
        targetFormat: FileFormat,
        additionalStepCount: Int,
    ): MigrationStep {
        val detail =
            when (val totalStepCount = additionalStepCount + 1) {
                1 -> "This change migrates these files to format `${targetFormat.specifier()}`."
                else ->
                    """
                        This change is the first in a series of $totalStepCount steps to migrate
                        these files to format `${targetFormat.specifier()}`.

                        This initial change reformats the files to be as close to the target
                        format as possible while not changing the structure of the file. It
                        sets properties in each file that are needed to preserve that
                        structure. The follow-up changes will change the value of one property
                        at a time from the original value to the target value. The intent is to
                        simplify the review process by only making one form of structural
                        change at a time.
                    """
                        .trimIndent()
            }

        return createMigrationStep(
            filesToMigrate = files,
            description =
                ChangeDescription(
                    title = initialTitle,
                    detail = detail,
                ),
            optionalPropertyChange = null,
        )
    }

    /**
     * Create a migration step for [files] that will apply [propertyChange] to their
     * [FileToMigrate.outputFormat].
     */
    private fun <T> createMigrationStepForPropertyChange(
        propertyChange: PropertyChange<T>,
        files: List<FileToMigrate>
    ): MigrationStep {
        val migrationChange =
            propertyChangeToMigrationChange[propertyChange]
                ?: error("property change $propertyChange is not supported")

        return createMigrationStep(
            filesToMigrate = files,
            description = migrationChange.description,
            optionalPropertyChange = propertyChange,
        )
    }

    /** Print a summary of the migration process to [stdout]. */
    private fun summarizeMigrationProcess(migrationSteps: List<MigrationStep>) {
        // Print a summary of the migration process.
        for ((index, step) in migrationSteps.withIndex()) {
            step.summarize(index + 1)
        }
        println()
    }

    /** Perform the migration by performing each of the [MigrationStep]s in turn. */
    private fun performMigration(migrationSteps: List<MigrationStep>) {
        val changeCommitter = committerFactory()

        // Perform the migration steps in order.
        for (step in migrationSteps) {
            step.perform(changeCommitter)
        }
    }

    companion object {
        /** A migration change. */
        private data class MigrationChange(
            /** The property change that this applies. */
            val propertyChange: PropertyChange<*>,

            /** The description of the change. */
            val description: ChangeDescription,
        )

        /** Contains the supported migration changes. */
        private fun createMigrationChanges(): List<MigrationChange> {
            /** Create a [ChangeDescription]. */
            @Suppress("SameParameterValue")
            fun <T> migrationChange(
                property: CustomizableProperty<T>,
                oldValue: T,
                newValue: T,
                title: String,
                detail: String,
            ) =
                MigrationChange(
                    PropertyChange(property, oldValue, newValue),
                    ChangeDescription(title, detail.trimIndent()),
                )

            return listOf(
                migrationChange(
                    property = CustomizableProperty.FLAGGED_API_INHERITANCE,
                    oldValue = FlaggedApiInheritance.NONE,
                    newValue = FlaggedApiInheritance.NESTED_CLASSES,
                    title = "Track inherited @FlaggedApi on nested classes",
                    detail =
                        """
                            An `@FlaggedApi` annotation on a class affects all of its members and
                            nested classes that do not have their own `@FlaggedApi` annotation.
                            Previously, inherited `@FlaggedApi` annotations were not tracked on
                            nested classes which complicated the reviewing of signature file changes
                            as it was difficult to determine which `@FlaggedApi` if any applied to
                            the change.

                            This change sets `flagged-api-inheritance=nested-classes` to fix that.
                        """,
                ),
                migrationChange(
                    property = CustomizableProperty.NORMALIZE_ABSTRACT_MODIFIER,
                    oldValue = false,
                    newValue = true,
                    title = "Normalize abstract modifiers in annotations and enums",
                    detail =
                        """
                            Previously, `abstract` modifiers were not removed from annotation and
                            enum methods even though they were unnecessary.

                            This change cleans them up by setting `normalize-abstract-modifier=yes`.
                        """,
                ),
                migrationChange(
                    property = CustomizableProperty.NORMALIZE_FINAL_MODIFIER,
                    oldValue = false,
                    newValue = true,
                    title = "Normalize final modifiers in final classes",
                    detail =
                        """
                            Previously, `final` modifiers were not removed from methods in `final`
                            classes.

                            This change cleans them up by setting `normalize-final-modifier=yes`.
                        """,
                ),
                migrationChange(
                    property = CustomizableProperty.OVERLOADED_METHOD_ORDER,
                    oldValue = FileFormat.OverloadedMethodOrder.SOURCE,
                    newValue = FileFormat.OverloadedMethodOrder.SIGNATURE,
                    title = "Sort overloaded methods by signature",
                    detail =
                        """
                            Previously, overloaded methods were sorted by their order in the source
                            file. That meant that refactoring the sources could cause changes to
                            signature files even though there were no actual API changes.

                            This change fixes that by setting `overloaded-method-order=signature`
                            which will sort overloaded methods by their signature.
                        """,
                ),
                migrationChange(
                    property = CustomizableProperty.SORT_WHOLE_EXTENDS_LIST,
                    oldValue = false,
                    newValue = true,
                    title = "Sort the whole extends list",
                    detail =
                        """
                            Previously, an interface that had an `extends` list with multiple super
                            interfaces would sort all but the first item in the list. That meant
                            that refactoring the sources could cause changes to signature files even
                            though there were no actual API changes.

                            This change fixes that by setting `sort-whole-extends-list=yes` which
                            will sort the whole list.
                        """,
                ),
                migrationChange(
                    property = CustomizableProperty.STRIP_JAVA_LANG_PREFIX,
                    oldValue = StripJavaLangPrefix.LEGACY,
                    newValue = StripJavaLangPrefix.ALWAYS,
                    title = "Always strip java.lang. prefixes from types",
                    detail =
                        """
                            Previously, a `java.lang.` prefixes were only stripped from the start of
                            a type. That is legacy behavior from when types were modelled as
                            strings.

                            This change fixes that by setting `strip-java-lang-prefix=always` which
                            will remove the prefix from all types. Note, that does not include
                            annotations, so `java.lang.SafeVarargs` is unaffected.
                        """,
                ),
                migrationChange(
                    property = CustomizableProperty.TYPE_ARGUMENT_SPACING,
                    oldValue = FileFormat.TypeArgumentSpacing.LEGACY,
                    newValue = FileFormat.TypeArgumentSpacing.SPACE,
                    title = "Always separate type arguments with a space",
                    detail =
                        """
                            Previously, the separation of type arguments was inconsistent depending
                            on where the type was used.

                            This change fixes that by setting `type-argument-spacing=space` which
                            will separate them with a space separator everywhere.
                        """,
                ),
            )
        }

        /** Map from [PropertyChange] to [MigrationChange]. */
        private val propertyChangeToMigrationChange =
            createMigrationChanges().associateBy { it.propertyChange }
    }
}

/** Read the [Codebase] from the signature [file]. */
private fun readSignatureFile(file: File) =
    ApiFile.parseApi(
        signatureFiles = SignatureFile.fromFiles(file),
        codebaseConfig =
            Config(
                reporter = ThrowingReporter.INSTANCE,
            ),
    )

/** Information about each file that requires migration. */
private data class FileToMigrate(
    /** The [File] from which this was generated. */
    val file: File,

    /** The original format of [File]. */
    val originalFormat: FileFormat,

    /** The [Codebase] loaded from [file]. */
    val codebase: Codebase,

    /**
     * The output format that [file] will be written as.
     *
     * This is initialized to the format that is closest to the target format while preserving the
     * file's structure. Each [MigrationStep] that affects this file will update this to match the
     * format of the file's content after the migration step has been performed.
     */
    var outputFormat: FileFormat,

    /** The [PropertyChange]s that need to be made to migrate this file to the target format. */
    val propertyChanges: List<PropertyChange<*>>,
) {
    fun write(outputFormat: FileFormat) {
        file.printWriter().use { writer -> writeSignatureFile(codebase, outputFormat, writer) }
    }
}

/** A step in the migration process. */
private class MigrationStep(
    /** The files to migrate. */
    private val filesToMigrate: List<FileToMigrate>,

    /** The description of the change made in this step. */
    private val description: ChangeDescription,

    /** Optional [PropertyChange] to apply to [FileToMigrate.outputFormat]. */
    private val optionalPropertyChange: PropertyChange<*>?,
) {
    /** Print a summary of this as step numbered [stepNumber]. */
    fun summarize(stepNumber: Int) {
        println()
        println("Step $stepNumber: ${description.title}")
        println("  Will affect the following files:")
        for (fileMigrationPlan in filesToMigrate) {
            println("    ${fileMigrationPlan.file}")
        }
    }

    /** Perform the migration step. */
    fun perform(changeCommitter: ChangeCommitter) {
        for (fileToMigrate in filesToMigrate) {
            // Compute the new output format by taking the current output format and applying this
            // step's [optionalPropertyChange].
            val outputFormat = fileToMigrate.outputFormat
            val newOutputFormat =
                optionalPropertyChange?.let { propertyChange ->
                    outputFormat.buildCopy { propertyChange.setNewValueIn(this) }
                } ?: outputFormat

            // Write the file using the new output format.
            fileToMigrate.write(newOutputFormat)

            // Update the file state to remember the new output format.
            fileToMigrate.outputFormat = newOutputFormat
        }

        val files = filesToMigrate.map { it.file }

        changeCommitter.commit(description, files)
    }
}
