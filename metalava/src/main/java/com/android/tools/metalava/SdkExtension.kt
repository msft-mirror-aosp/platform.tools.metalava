/*
 * Copyright (C) 2022 The Android Open Source Project
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

/**
 * ID and aliases for a given SDK extension.
 *
 * An SDK extension has an [id] corresponding to an Android dessert release that it extends, e.g.
 * the T extension has an [id] of 33.
 *
 * @param id: numerical ID of the extension SDK, primarily used in generated artifacts and consumed
 *   by tools
 * @param shortname: short name for the SDK, primarily used in configuration files
 * @param name: human-readable name for the SDK; used in the official documentation
 * @param reference: Java symbol in the Android SDK with the same numerical value as the id, using a
 *   JVM signature like syntax: "some/clazz$INNER$FIELD"
 */
data class SdkExtension(
    val id: Int,
    val shortname: String,
    val name: String,
    val reference: String
) {
    init {
        require(id >= 1) { "SDK extensions cannot have an id less than 1 but it is $id" }
    }
}
