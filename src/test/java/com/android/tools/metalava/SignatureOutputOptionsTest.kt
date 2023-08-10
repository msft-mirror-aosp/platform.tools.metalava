/*
 * Copyright (C) 2023 The Android Open Source Project
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

import com.github.ajalt.clikt.core.BadParameterValue
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.groups.provideDelegate
import com.google.common.truth.Truth.assertThat
import org.junit.Assert.assertThrows
import org.junit.Test

class SignatureOutputOptionsTest {

    private fun runTest(vararg args: String, test: (SignatureOutputOptions) -> Unit) {
        val command = MockCommand(test)
        command.parse(args.toList())
    }

    private class MockCommand(val test: (SignatureOutputOptions) -> Unit) : CliktCommand() {
        val options by SignatureOutputOptions()

        override fun run() {
            test(options)
        }
    }

    @Test
    fun `V1 not supported`() {
        val e = assertThrows(BadParameterValue::class.java) { runTest("--format=v1") {} }
        assertThat(e.message).startsWith("""Invalid value for "--format": invalid choice: v1.""")
    }

    @Test
    fun `V2 not compatible with --output-kotlin-nulls=yes (format first)`() {
        val e =
            assertThrows(BadParameterValue::class.java) {
                runTest("--format=v2", "--output-kotlin-nulls=yes") {}
            }
        assertThat(e.message)
            .startsWith(
                """Invalid value for "--output-kotlin-nulls": '--output-kotlin-nulls=yes' requires '--format=v3'"""
            )
    }

    @Test
    fun `V2 not compatible with --output-kotlin-nulls=yes (format last)`() {
        val e =
            assertThrows(BadParameterValue::class.java) {
                runTest("--output-kotlin-nulls=yes", "--format=v2") {}
            }
        assertThat(e.message)
            .startsWith(
                """Invalid value for "--output-kotlin-nulls": '--output-kotlin-nulls=yes' requires '--format=v3'"""
            )
    }

    @Test
    fun `Can override format default with --output-kotlin-nulls=no`() {
        runTest("--output-kotlin-nulls=no", "--format=v3") {
            assertThat(it.effectiveOutputKotlinStyleNulls).isFalse()
        }
    }
}
