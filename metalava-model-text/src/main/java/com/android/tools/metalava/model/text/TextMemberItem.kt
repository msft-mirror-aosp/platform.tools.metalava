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

package com.android.tools.metalava.model.text

import com.android.tools.metalava.model.ClassItem
import com.android.tools.metalava.model.DefaultCodebase
import com.android.tools.metalava.model.DefaultModifierList
import com.android.tools.metalava.model.MemberItem
import com.android.tools.metalava.reporter.FileLocation

internal abstract class TextMemberItem(
    codebase: DefaultCodebase,
    private val name: String,
    private val containingClass: ClassItem,
    fileLocation: FileLocation,
    modifiers: DefaultModifierList
) : TextItem(codebase, fileLocation = fileLocation, modifiers = modifiers), MemberItem {

    override fun name(): String = name

    override fun containingClass(): ClassItem = containingClass
}
