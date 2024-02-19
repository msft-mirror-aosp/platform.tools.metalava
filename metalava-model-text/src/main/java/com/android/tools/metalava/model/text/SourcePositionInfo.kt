/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.metalava.model.text

import java.nio.file.Path

class SourcePositionInfo(
    /** The absolute path to the location. */
    val path: Path,
    /** The line number, may be non-positive indicating that it could not be found. */
    val line: Int = 0,
) {
    override fun toString() = if (line < 1) path.toString() else "$path:$line"

    fun appendTo(builder: StringBuilder) {
        builder.append(path)
        if (line > 0) builder.append(":").append(line)
    }

    companion object {
        val UNKNOWN = SourcePositionInfo(Path.of("unknown"), 0)
    }
}
