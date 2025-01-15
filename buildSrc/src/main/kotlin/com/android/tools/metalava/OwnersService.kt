/*
 * Copyright (C) 2025 The Android Open Source Project
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

import com.google.gson.GsonBuilder
import java.io.File
import org.gradle.api.DefaultTask
import org.gradle.api.Project
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.bundling.Zip

/**
 * Register two tasks needed to generate information for Android test owners service. One task zips
 * all the OWNERS files in tools/metalava, and second task creates a module-info.json that links
 * test modules to paths.
 */
fun Project.registerOwnersServiceTasks() {
    tasks.register(ZIP_OWNERS_FILES, Zip::class.java) { task ->
        task.archiveFileName.set("owners.zip")
        task.destinationDirectory.set(getDistributionDirectory(project))
        task.from(layout.projectDirectory)
        task.include("**/OWNERS")
        task.exclude("buildSrc/.gradle/**")
        task.exclude(".gradle/**")
        task.exclude("build/reports/**")
        task.includeEmptyDirs = false
    }

    tasks.register(CREATE_MODULE_INFO, ModuleInfoGenerator::class.java) { task ->
        task.outputFile.set(File(getDistributionDirectory(project), "module-info.json"))
    }
}

@CacheableTask
abstract class ModuleInfoGenerator : DefaultTask() {
    @get:OutputFile abstract val outputFile: RegularFileProperty

    @get:Internal val testModules: MutableList<TestModule> = mutableListOf()

    @Input
    fun getSerialized(): String {
        val gson = GsonBuilder().setPrettyPrinting().create()
        /* Expected structure
            {
              ":metalava:test": {
                "name": ":metalava:test",
                "path": [
                  "metalava"
                ]
              },
              ":metalava-model:test": {
                "name": ":metalava-model:test",
                "path": [
                  "metalava-model"
                ]
              },
              ...
            }
         */
        val data =
            testModules
                .associateBy { it.name.get() }
                .mapValues {
                    mapOf(
                        "name" to it.value.name.get(),
                        "path" to it.value.path
                    )
                }
        return gson.toJson(data)
    }

    @TaskAction
    fun writeModuleInfo() {
        val file = outputFile.get().asFile
        file.parentFile.mkdirs()
        file.writeText(getSerialized())
    }
}

internal fun Project.addToModuleInfo(testName: Provider<String>) {
    rootProject.tasks.named(CREATE_MODULE_INFO).configure {
        it as ModuleInfoGenerator
        it.testModules.add(
            TestModule(
                name = testName,
                path = listOf(projectDir.toRelativeString(rootDir))
            )
        )
    }
}

data class TestModule(val name: Provider<String>, val path: List<String>)

const val ZIP_OWNERS_FILES = "zipOwnersFiles"
const val CREATE_MODULE_INFO = "createModuleInfo"
