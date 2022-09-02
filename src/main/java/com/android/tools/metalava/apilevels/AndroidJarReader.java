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
package com.android.tools.metalava.apilevels;

import com.android.SdkConstants;
import com.android.tools.metalava.model.Codebase;
import com.google.common.io.ByteStreams;
import com.google.common.io.Closeables;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.MethodNode;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Reads all the android.jar files found in an SDK and generate a map of {@link ApiClass}.
 */
class AndroidJarReader {
    private int mMinApi;
    private int mCurrentApi;
    private File mCurrentJar;
    private List<String> mPatterns;
    private File[] mApiLevels;
    private final Codebase mCodebase;

    AndroidJarReader(@NotNull List<String> patterns,
                     int minApi,
                     @NotNull File currentJar,
                     int currentApi,
                     @Nullable Codebase codebase) {
        mPatterns = patterns;
        mMinApi = minApi;
        mCurrentJar = currentJar;
        mCurrentApi = currentApi;
        mCodebase = codebase;
    }

    AndroidJarReader(@NotNull File[] apiLevels, int firstApiLevel, @Nullable Codebase codebase) {
        mApiLevels = apiLevels;
        mCodebase = codebase;
        mMinApi = firstApiLevel;
    }

    public Api getApi() throws IOException {
        Api api;
        if (mApiLevels != null) {
            int max = mApiLevels.length - 1;
            if (mCodebase != null) {
                max = mCodebase.getApiLevel();
            }

            api = new Api(mMinApi, max);
            for (int apiLevel = mMinApi; apiLevel < mApiLevels.length; apiLevel++) {
                File jar = getAndroidJarFile(apiLevel);
                JarReaderUtilsKt.readJar(api, apiLevel, jar);
            }
            if (mCodebase != null) {
                int apiLevel = mCodebase.getApiLevel();
                if (apiLevel != -1) {
                    processCodebase(api, apiLevel);
                }
            }
        } else {
            api = new Api(mMinApi, mCurrentApi);
            // Get all the android.jar. They are in platforms-#
            int apiLevel = mMinApi - 1;
            while (true) {
                apiLevel++;
                File jar = null;
                if (apiLevel == mCurrentApi) {
                    jar = mCurrentJar;
                }
                if (jar == null) {
                    jar = getAndroidJarFile(apiLevel);
                }
                if (jar == null || !jar.isFile()) {
                    if (mCodebase != null) {
                        processCodebase(api, apiLevel);
                    }
                    break;
                }
                JarReaderUtilsKt.readJar(api, apiLevel, jar);
            }
        }

        api.inlineFromHiddenSuperClasses();
        api.removeImplicitInterfaces();
        api.removeOverridingMethods();
        api.prunePackagePrivateClasses();

        return api;
    }

    private void processCodebase(Api api, int apiLevel) {
        if (mCodebase == null) {
            return;
        }
        AddApisFromCodebaseKt.addApisFromCodebase(api, apiLevel, mCodebase);
    }

    private File getAndroidJarFile(int apiLevel) {
        if (mApiLevels != null) {
            return mApiLevels[apiLevel];
        }
        for (String pattern : mPatterns) {
            File f = new File(pattern.replace("%", Integer.toString(apiLevel)));
            if (f.isFile()) {
                return f;
            }
        }
        return null;
    }
}
