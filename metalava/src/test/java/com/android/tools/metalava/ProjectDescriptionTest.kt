/*
 * Copyright (C) 2024 The Android Open Source Project
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

import com.android.tools.metalava.model.provider.Capability
import com.android.tools.metalava.model.testing.RequiresCapabilities
import com.android.tools.metalava.testing.getAndroidJar
import com.android.tools.metalava.testing.java
import com.android.tools.metalava.testing.kotlin
import com.android.tools.metalava.testing.xml
import org.junit.Test

@RequiresCapabilities(Capability.KOTLIN)
class ProjectDescriptionTest : DriverTest() {

    @Test
    fun `conflict declarations`() {
        // Example from b/364480872
        // Conflict declarations in Foo.java and Foo.kt are intentional.
        // project.xml will use "androidMain" as root so that it can discard one in jvmMain.
        check(
            commonSourceFiles =
                arrayOf(
                    kotlin(
                        "commonMain/src/some/common/Bogus.kt",
                        """
                            // bogus file to trigger multi-folder structure
                            package some.common

                            class Bogus
                        """
                    )
                ),
            sourceFiles =
                arrayOf(
                    kotlin(
                        "androidMain/src/some/pkg/Foo.kt",
                        """
                            package some.pkg

                            class Foo {
                              companion object {
                                @JvmStatic
                                public fun foo(x: String): String {
                                  return x
                                }
                              }
                            }
                        """
                    ),
                    java(
                        "androidMain/src/test/Bar.java",
                        """
                            package test;

                            import some.pkg.Foo;

                            public class Bar {
                                public String bar(String x) {
                                    return Foo.foo(x);
                                }
                            }
                        """
                    ),
                    java(
                        "jvmMain/src/some/pkg/Foo.java",
                        """
                            package some.pkg;

                            public class Foo {
                                public static String foo(String x) {
                                    return x;
                                }
                            }
                        """
                    ),
                ),
            projectDescription =
                xml(
                    "project.xml",
                    """
                        <project>
                          <module name="app" android="true" library="false">
                            <src file="androidMain/src/some/pkg/Foo.kt" />
                            <src file="androidMain/src/test/Bar.java" />
                            <classpath file="${getAndroidJar()}"/>
                          </module>
                        </project>
                    """
                ),
            api =
                """
                package some.pkg {
                  public final class Foo {
                    ctor public Foo();
                    field public static final some.pkg.Foo.Companion Companion;
                  }
                  public static final class Foo.Companion {
                    method public String foo(String x);
                  }
                }
                package test {
                  public class Bar {
                    ctor public Bar();
                    method public String! bar(String!);
                  }
                }
                """
        )
    }
}
