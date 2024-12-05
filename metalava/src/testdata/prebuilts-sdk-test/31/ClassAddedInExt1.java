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

package android.test;

public class ClassAddedInExt1 {
    private ClassAddedInExt1() {}
    public static final int FIELD_ADDED_IN_EXT_1 = 1;
    public static final int FIELD_ADDED_IN_API_31_AND_EXT_2 = 2;
    public void methodAddedInExt1() { throw new RuntimeException("Stub!"); }
    public void methodAddedInApi31AndExt2() { throw new RuntimeException("Stub!"); }
}
