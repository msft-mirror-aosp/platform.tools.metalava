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

package com.android.tools.metalava.model

import com.android.tools.metalava.model.item.DefaultCodebase
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DefaultAnnotationItemTest {
    // Placeholder for use in test where we don't need codebase functionality
    private val placeholderCodebase =
        object :
            DefaultCodebase(
                location = File("").canonicalFile,
                description = "",
                preFiltered = false,
                annotationManager = noOpAnnotationManager,
                trustedApi = false,
                supportsDocumentation = false,
            ) {
            override fun findClass(className: String) = unsupported()

            override fun resolveClass(className: String) = unsupported()
        }

    private fun createDefaultAnnotationItem(source: String) =
        DefaultAnnotationItem.create(placeholderCodebase, source)
            ?: error("Could not create annotation from: '$source'")

    @Test
    fun testSimple() {
        val annotation = createDefaultAnnotationItem("@androidx.annotation.Nullable")
        assertEquals("@androidx.annotation.Nullable", annotation.toSource())
        assertEquals("androidx.annotation.Nullable", annotation.qualifiedName)
        assertTrue(annotation.attributes.isEmpty())
    }

    @Test
    fun testIntRange() {
        val annotation =
            createDefaultAnnotationItem("@androidx.annotation.IntRange(from = 20, to = 40)")
        assertEquals("@androidx.annotation.IntRange(from=20, to=40)", annotation.toSource())
        assertEquals("androidx.annotation.IntRange", annotation.qualifiedName)
        assertEquals(2, annotation.attributes.size)
        assertEquals("from", annotation.findAttribute("from")?.name)
        assertEquals("20", annotation.findAttribute("from")?.value.toString())
        assertEquals("to", annotation.findAttribute("to")?.name)
        assertEquals("40", annotation.findAttribute("to")?.value.toString())
    }

    @Test
    fun testIntDef() {
        val annotation =
            createDefaultAnnotationItem(
                "@androidx.annotation.IntDef({STYLE_NORMAL, STYLE_NO_TITLE, STYLE_NO_FRAME, STYLE_NO_INPUT})"
            )
        assertEquals(
            "@androidx.annotation.IntDef({STYLE_NORMAL, STYLE_NO_TITLE, STYLE_NO_FRAME, STYLE_NO_INPUT})",
            annotation.toSource()
        )
        assertEquals("androidx.annotation.IntDef", annotation.qualifiedName)
        assertEquals(1, annotation.attributes.size)
        val attribute = annotation.findAttribute("value")
        assertNotNull(attribute)
        assertEquals("value", attribute?.name)
        assertEquals(
            "{STYLE_NORMAL, STYLE_NO_TITLE, STYLE_NO_FRAME, STYLE_NO_INPUT}",
            annotation.findAttribute("value")?.value.toString()
        )

        assertTrue(attribute?.value is AnnotationArrayAttributeValue)
        if (attribute is AnnotationArrayAttributeValue) {
            val list = attribute.values
            assertEquals(3, list.size)
            assertEquals("STYLE_NO_TITLE", list[1].toSource())
        }
    }
}
