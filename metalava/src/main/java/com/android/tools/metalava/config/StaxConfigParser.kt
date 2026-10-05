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
                        else -> {
                            recordUnexpectedElement(reader, "config")
                            skipElement(reader)
                        }
                    }
                }
                XMLStreamConstants.END_ELEMENT -> return Config()
            }
        }
        return Config()
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
