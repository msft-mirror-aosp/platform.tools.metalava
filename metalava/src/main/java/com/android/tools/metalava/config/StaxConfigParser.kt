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

package com.android.tools.metalava.config

import java.io.File
import javax.xml.XMLConstants
import javax.xml.stream.XMLInputFactory
import javax.xml.stream.XMLStreamConstants
import javax.xml.stream.XMLStreamException
import javax.xml.stream.XMLStreamReader
import org.xml.sax.InputSource

/** StAX-based parser for XML configuration files. */
internal class StaxConfigParser private constructor(private val systemId: String) {
    /** Errors that were reported while parsing a configuration file. */
    private val errors = StringBuilder()

    /** Record a validation or parsing error at [lineNumber] with [message]. */
    private fun recordError(lineNumber: Int, message: String) {
        errors.apply {
            append("    ")
            val normalizedPath =
                if (systemId.startsWith("file:")) {
                    systemId.replace("file://", "file:")
                } else {
                    "file:$systemId"
                }
            append(normalizedPath)
            if (lineNumber > 0) {
                append(":")
                append(lineNumber)
            }
            append(": ")
            append(message)
            append("\n")
        }
    }

    /** Record an error from a StAX [XMLStreamException]. */
    private fun recordStreamException(exception: XMLStreamException) {
        val lineNumber = exception.location?.lineNumber ?: -1
        val message = exception.message?.substringAfter("\nMessage: ") ?: "Unknown error"
        recordError(lineNumber, message)
    }

    /** Parse a single XML configuration stream from [reader] into a [Config] object. */
    private fun parseFile(reader: XMLStreamReader): Config {
        while (reader.hasNext()) {
            val event = reader.next()
            if (event == XMLStreamConstants.START_ELEMENT) {
                if (reader.namespaceURI == CONFIG_NAMESPACE && reader.localName == "config") {
                    return parseConfig(reader)
                } else {
                    val qName = formatQName(reader.prefix, reader.localName)
                    recordError(
                        reader.location.lineNumber,
                        "cvc-elt.1.a: Cannot find the declaration of element '$qName'.",
                    )
                    skipElement(reader)
                }
            }
        }
        return Config()
    }

    /** Parse the root `<config>` element from [reader] into a [Config] object. */
    private fun parseConfig(reader: XMLStreamReader): Config {
        checkAttributes(
            reader,
            "config",
            allowedAttributes = emptySet(),
            allowSchemaLocation = true
        )

        var buildProperties: BuildPropertiesConfig? = null
        val buildPropertyNames = mutableSetOf<String>()
        var issues: IssuesConfig? = null

        while (reader.hasNext()) {
            when (reader.next()) {
                XMLStreamConstants.START_ELEMENT -> {
                    val localName = reader.localName
                    if (reader.namespaceURI != CONFIG_NAMESPACE) {
                        recordUnexpectedElement(reader, "config")
                        skipElement(reader)
                        continue
                    }
                    when (localName) {
                        "build-properties" -> {
                            val parsed = parseBuildProperties(reader, buildPropertyNames)
                            buildProperties = combine(buildProperties, parsed)
                        }
                        "issues" -> {
                            val parsed = parseIssues(reader)
                            issues = combine(issues, parsed)
                        }
                        else -> {
                            recordUnexpectedElement(reader, "config")
                            skipElement(reader)
                        }
                    }
                }
                XMLStreamConstants.END_ELEMENT ->
                    return Config(
                        buildProperties = buildProperties,
                        issues = issues,
                    )
            }
        }
        return Config(
            buildProperties = buildProperties,
            issues = issues,
        )
    }

    /** Parse a `<build-properties>` element from [reader] into a [BuildPropertiesConfig]. */
    private fun parseBuildProperties(
        reader: XMLStreamReader,
        buildPropertyNames: MutableSet<String>,
    ): BuildPropertiesConfig {
        checkAttributes(reader, "build-properties", allowedAttributes = emptySet())
        val properties = mutableListOf<BuildPropertyConfig>()
        while (reader.hasNext()) {
            when (reader.next()) {
                XMLStreamConstants.START_ELEMENT -> {
                    if (
                        reader.namespaceURI == CONFIG_NAMESPACE &&
                            reader.localName == "build-property"
                    ) {
                        parseBuildProperty(reader, buildPropertyNames)?.let { properties.add(it) }
                    } else {
                        recordUnexpectedElement(reader, "build-properties")
                        skipElement(reader)
                    }
                }
                XMLStreamConstants.END_ELEMENT -> {
                    if (properties.isEmpty()) {
                        recordIncompleteElement(
                            reader.location.lineNumber,
                            "build-properties",
                            "build-property",
                        )
                    }
                    return BuildPropertiesConfig(properties)
                }
            }
        }
        return BuildPropertiesConfig(properties)
    }

    /** Parse a `<build-property>` element from [reader] into a [BuildPropertyConfig]. */
    private fun parseBuildProperty(
        reader: XMLStreamReader,
        buildPropertyNames: MutableSet<String>,
    ): BuildPropertyConfig? {
        val lineNumber = reader.location.lineNumber
        checkAttributes(reader, "build-property", BUILD_PROPERTY_ATTRIBUTES)
        val name = requiredAttribute(reader, lineNumber, "build-property", "name")
        val value = requiredAttribute(reader, lineNumber, "build-property", "value")
        expectEmptyElement(reader, "build-property")

        if (name == null || value == null) return null
        validatePattern(
            lineNumber,
            "build-property",
            "name",
            name,
            BUILD_PROPERTY_NAME_REGEX,
            "BuildPropertyNameType",
        )
        checkUniqueKey(lineNumber, buildPropertyNames, name, "BuildPropertyName")
        return BuildPropertyConfig(name = name, value = value)
    }

    /** Parse an `<issues>` element from [reader] into an [IssuesConfig]. */
    private fun parseIssues(reader: XMLStreamReader): IssuesConfig {
        checkAttributes(reader, "issues", allowedAttributes = emptySet())
        val issues = mutableListOf<IssueConfig>()
        while (reader.hasNext()) {
            when (reader.next()) {
                XMLStreamConstants.START_ELEMENT -> {
                    if (reader.namespaceURI == CONFIG_NAMESPACE && reader.localName == "issue") {
                        parseIssue(reader)?.let { issues.add(it) }
                    } else {
                        recordUnexpectedElement(reader, "issues")
                        skipElement(reader)
                    }
                }
                XMLStreamConstants.END_ELEMENT -> return IssuesConfig(issues)
            }
        }
        return IssuesConfig(issues)
    }

    /** Parse an `<issue>` element from [reader] into an [IssueConfig]. */
    private fun parseIssue(reader: XMLStreamReader): IssueConfig? {
        val lineNumber = reader.location.lineNumber
        checkAttributes(reader, "issue", ISSUE_ATTRIBUTES)
        val name = requiredAttribute(reader, lineNumber, "issue", "name")
        val severityStr = requiredAttribute(reader, lineNumber, "issue", "severity")
        expectEmptyElement(reader, "issue")

        if (name == null || severityStr == null) return null
        validatePattern(lineNumber, "issue", "name", name, ISSUE_NAME_REGEX, "IssueNameType")
        val severity =
            validateEnum(
                lineNumber,
                "issue",
                "severity",
                severityStr,
                SEVERITY_MAP,
                "IssueSeverityType",
            ) ?: return null
        return IssueConfig(name = name, severity = severity)
    }

    /** Retrieve a required attribute, recording an error if it is missing. */
    private fun requiredAttribute(
        reader: XMLStreamReader,
        lineNumber: Int,
        elementName: String,
        attrName: String,
    ): String? {
        val value = reader.getAttributeValue(null, attrName)
        if (value == null) {
            recordError(
                lineNumber,
                "cvc-complex-type.4: Attribute '$attrName' must appear on element '$elementName'.",
            )
        }
        return value
    }

    /**
     * Validate an attribute [value] against [regex], recording XSD-compatible errors on failure.
     */
    private fun validatePattern(
        lineNumber: Int,
        elementName: String,
        attrName: String,
        value: String,
        regex: Regex,
        typeName: String,
    ): Boolean {
        if (!regex.matches(value)) {
            recordError(
                lineNumber,
                "cvc-pattern-valid: Value '$value' is not facet-valid with respect to pattern '${regex.pattern}' for type '$typeName'.",
            )
            recordError(
                lineNumber,
                "cvc-attribute.3: The value '$value' of attribute '$attrName' on element '$elementName' is not valid with respect to its type, '$typeName'.",
            )
            return false
        }
        return true
    }

    /** Validate an attribute [value] against an enum [valuesMap]. */
    private fun <E> validateEnum(
        lineNumber: Int,
        elementName: String,
        attrName: String,
        value: String,
        valuesMap: Map<String, E>,
        typeName: String,
    ): E? {
        val enumValue = valuesMap[value]
        if (enumValue == null) {
            recordError(
                lineNumber,
                "cvc-enumeration-valid: Value '$value' is not facet-valid with respect to enumeration '${valuesMap.keys}'. It must be a value from the enumeration.",
            )
            recordError(
                lineNumber,
                "cvc-attribute.3: The value '$value' of attribute '$attrName' on element '$elementName' is not valid with respect to its type, '$typeName'.",
            )
        }
        return enumValue
    }

    /** Check that [key] has not already been seen in [seenKeys] for [constraintName]. */
    private fun checkUniqueKey(
        lineNumber: Int,
        seenKeys: MutableSet<String>,
        key: String,
        constraintName: String,
    ) {
        if (!seenKeys.add(key)) {
            recordError(
                lineNumber,
                "cvc-identity-constraint.4.2.2: Duplicate key value [$key] declared for identity constraint \"$constraintName\" of element \"config\".",
            )
        }
    }

    /** Record an error when [elementName] is missing a required [expectedChild] element. */
    private fun recordIncompleteElement(
        lineNumber: Int,
        elementName: String,
        expectedChild: String,
    ) {
        recordError(
            lineNumber,
            "cvc-complex-type.2.4.b: The content of element '$elementName' is not complete. One of '{\"$CONFIG_NAMESPACE\":$expectedChild}' is expected.",
        )
    }

    /** Consume an element that must not have any child elements. */
    private fun expectEmptyElement(reader: XMLStreamReader, elementName: String) {
        while (reader.hasNext()) {
            when (reader.next()) {
                XMLStreamConstants.START_ELEMENT -> {
                    recordUnexpectedElement(reader, elementName)
                    skipElement(reader)
                }
                XMLStreamConstants.END_ELEMENT -> return
            }
        }
    }

    /** Check that all attributes on the current element are in [allowedAttributes]. */
    private fun checkAttributes(
        reader: XMLStreamReader,
        elementName: String,
        allowedAttributes: Set<String>,
        allowSchemaLocation: Boolean = false,
    ) {
        val lineNumber = reader.location.lineNumber
        for (i in 0 until reader.attributeCount) {
            val attrNs = reader.getAttributeNamespace(i)
            val attrLocal = reader.getAttributeLocalName(i)
            if (
                allowSchemaLocation &&
                    attrNs == XMLConstants.W3C_XML_SCHEMA_INSTANCE_NS_URI &&
                    attrLocal == "schemaLocation"
            ) {
                continue
            }
            if (!attrNs.isNullOrEmpty() || attrLocal !in allowedAttributes) {
                val attrName = formatQName(reader.getAttributePrefix(i), attrLocal)
                recordError(
                    lineNumber,
                    "cvc-complex-type.3.2.2: Attribute '$attrName' is not allowed to appear in element '$elementName'.",
                )
            }
        }
    }

    /** Record an error for an unexpected child element inside [parentElement]. */
    private fun recordUnexpectedElement(reader: XMLStreamReader, parentElement: String) {
        val qName = formatQName(reader.prefix, reader.localName)
        recordError(
            reader.location.lineNumber,
            "cvc-complex-type.2.4.a: Invalid content was found starting with element '$qName' in '$parentElement'.",
        )
    }

    /** Skip the current element and all of its descendants. */
    private fun skipElement(reader: XMLStreamReader) {
        var depth = 1
        while (depth > 0 && reader.hasNext()) {
            when (reader.next()) {
                XMLStreamConstants.START_ELEMENT -> depth++
                XMLStreamConstants.END_ELEMENT -> depth--
            }
        }
    }

    /** Format an XML qualified name from an optional [prefix] and [localName]. */
    private fun formatQName(prefix: String?, localName: String): String =
        if (prefix.isNullOrEmpty()) localName else "$prefix:$localName"

    companion object : ConfigParser {
        private val BUILD_PROPERTY_ATTRIBUTES = setOf("name", "value")
        private val BUILD_PROPERTY_NAME_REGEX = Regex("[a-zA-Z0-9_]+")
        private val ISSUE_ATTRIBUTES = setOf("name", "severity")
        private val ISSUE_NAME_REGEX = Regex("([A-Z][a-z0-9]*)+")
        private val SEVERITY_MAP =
            IssueConfig.SeverityConfig.entries.associateBy { it.configFileValue }

        /** Parse a list of configuration files in order, returning a single [Config] object. */
        override fun parse(files: List<File>): Config {
            if (files.isEmpty()) return Config()
            return parseInputSources(files.map { InputSource(it.path) })
        }

        /**
         * Parse a list of configuration [InputSource]s in order, returning a single [Config]
         * object.
         */
        override fun parseInputSources(inputSources: List<InputSource>): Config {
            if (inputSources.isEmpty()) return Config()

            val xmlInputFactory = XMLInputFactory.newDefaultFactory()
            val allErrors = StringBuilder()
            val configs = mutableListOf<Config>()

            for (inputSource in inputSources) {
                val systemId = inputSource.systemId ?: ""
                val parser = StaxConfigParser(systemId)
                try {
                    val charStream = inputSource.characterStream
                    val byteStream = inputSource.byteStream
                    when {
                        charStream != null -> {
                            val reader = xmlInputFactory.createXMLStreamReader(systemId, charStream)
                            try {
                                configs.add(parser.parseFile(reader))
                            } finally {
                                reader.close()
                            }
                        }
                        byteStream != null -> {
                            val reader = xmlInputFactory.createXMLStreamReader(systemId, byteStream)
                            try {
                                configs.add(parser.parseFile(reader))
                            } finally {
                                reader.close()
                            }
                        }
                        else -> {
                            File(systemId).bufferedReader().use { fileReader ->
                                val reader =
                                    xmlInputFactory.createXMLStreamReader(systemId, fileReader)
                                try {
                                    configs.add(parser.parseFile(reader))
                                } finally {
                                    reader.close()
                                }
                            }
                        }
                    }
                } catch (e: XMLStreamException) {
                    parser.recordStreamException(e)
                } catch (e: Exception) {
                    parser.recordError(-1, e.message ?: "")
                }
                allErrors.append(parser.errors)
            }

            if (allErrors.isNotEmpty()) {
                error("Errors found while parsing configuration file(s):\n$allErrors")
            }

            return configs.reduceOrNull(Config::combineWith)?.apply { validate() } ?: Config()
        }

        override fun toString(): String = "stax"
    }
}
