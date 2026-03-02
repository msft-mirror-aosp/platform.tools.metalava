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

package com.android.tools.metalava.cli.signature

import com.android.tools.metalava.cli.common.MetalavaSubCommand
import com.android.tools.metalava.cli.common.existingFile
import com.android.tools.metalava.cli.common.stderr
import com.android.tools.metalava.model.text.FileFormat
import com.android.tools.metalava.model.text.SignatureFile
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.multiple
import com.github.ajalt.clikt.parameters.groups.provideDelegate
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option

class SignatureReformatCommand :
    MetalavaSubCommand(
        help =
            """
                Reformats signature files.

                Reformats each signature file to use the specified format.

                The purpose of this is, by working in conjunction with the $ARG_USE_SAME_FORMAT_AS
                option, to simplify the process for updating signature files from one version to the
                next. It assumes a number of things:

                1. That API signature files are checked into some version control system and need to
                be updated to reflect changes to the API. If they are not then this is not needed.

                2. The build uses the $ARG_USE_SAME_FORMAT_AS to pass the checked in API signature
                file so that its format will be used as the output for the file that the build
                generates to replace it.

                If those assumptions are met then updating the format version of the API file (and
                its corresponding removed API file if needed) simply involves:

                1. Running this command on the API file specifying the required format. That will
                reformat it according to the new format.

                2. If this changes the format to one that will include new information that was
                previously not recorded in the signature file then the signature file must be
                regenerated from the sources in order to include the new information.
            """
                .trimIndent(),
        printHelpOnEmptyArgs = false,
    ) {

    private val formatOptions by SignatureFormatOptions(migratingAllowed = true)

    private val preserveStructure by
        option(
                "--preserve-structure",
                help =
                    """
                        Preserve the structure of the file while changing the format.

                        Changes the format of the signature file while preserving the properties
                        from the previous version of the signature file preserving the signature
                        file structure.

                        `--format-defaults` should have the same value as that used when the
                        signature files was last updated to ensure that the structure is preserved.
                    """,
            )
            .flag()

    private val files by
        argument(
                name = "<files>",
                help =
                    """
                        Signature files to reformat.
                    """,
            )
            .existingFile()
            .multiple(required = true)

    /**
     * Compute a [FileFormat] that will preserve the structure of the original [currentFormat] while
     * incorporating changes from [targetFormat].
     *
     * This assumes that every property difference between [currentFormat] and [targetFormat] could
     * result in structural changes.
     */
    private fun computeStructurePreservingFormat(
        currentFormat: FileFormat,
        targetFormat: FileFormat,
    ) =
        // Create a new FileFormat based on the [targetFormat] with properties copied from
        // [currentFormat] where necessary.
        targetFormat.buildCopy {
            // Iterate over all the properties checking to see if the [targetFormat] value needs to
            // be replaced with the [currentFormat] value.
            for (property in FileFormat.CustomizableProperty.entries) {
                // Get the current value of the property, including any defaults. If it is not set
                // then it will have no impact on the resulting format so continue.
                val currentValue = currentFormat.getWithDefault(property) ?: continue

                // Get the target value, including any defaults.
                val targetValue = targetFormat.getWithDefault(property)
                if (targetValue == null) {
                    // The target value is null so use the value from the current file.
                    this[property] = currentValue
                } else if (targetValue != currentValue) {
                    // The target value is different from the current value so use the current
                    // value.
                    this[property] = currentValue
                }
            }
        }

    /**
     * Compute the output [FileFormat] to use.
     *
     * Returns [targetFormat] unless [preserveStructure] is `true` in which case this will call
     * [computeStructurePreservingFormat] to create a [FileFormat] from [targetFormat] with settings
     * from [currentFormat] needed to preserve the structure.
     *
     * @param currentFormat the current [FileFormat] for the file.
     * @param targetFormat the target [FileFormat] to which the file is to be reformatted.
     */
    private fun computeOutputFormat(
        currentFormat: FileFormat,
        targetFormat: FileFormat,
    ) =
        if (preserveStructure) {
            // Make sure to apply any defaults provided to the current format to ensure it is the
            // same format as was used to create the current signature file.
            val currentFormatWithDefaults = formatOptions.applyDefaultsTo(currentFormat)

            // Compute structure preserving format.
            computeStructurePreservingFormat(currentFormatWithDefaults, targetFormat)
        } else {
            targetFormat
        }

    override fun run() {
        // Get the target format for the signature files.
        val targetFormat = formatOptions.fileFormat

        for (file in files) {
            // Read the current format from the file header, if none could be found then the file is
            // empty and intentionally has no file format header so leave it unchanged.
            val currentFormat =
                file.reader().use { reader -> FileFormat.parseHeader(file.toPath(), reader) }
            if (currentFormat == null) continue

            val codebase = readSignatureFiles(SignatureFile.fromFiles(file), stderr)

            // Compute the output format to use when writing out this file.
            val outputFormat = computeOutputFormat(currentFormat, targetFormat)

            file.printWriter().use { writer -> writeSignatureFile(codebase, outputFormat, writer) }
        }
    }
}
