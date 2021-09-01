/*
 * Copyright (C) 2021 The Android Open Source Project
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

import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@RunWith(Parameterized::class) // TODO(b/198440244): Remove parameterization
class HideAnnotationTest(enableKotlinPsi: Boolean) : DriverTest(enableKotlinPsi) {
    companion object {
        @Parameterized.Parameters(name = "enableKotlinPsi = {0}")
        @JvmStatic
        fun parameters() = arrayOf(false, true)
    }

    // Regression test for b/133364476 crash
    @Test
    fun `Using hide annotation with Kotlin source`() {
        check(
            expectedIssues = """
                src/test/pkg/).kt:3: warning: Public class test.pkg.ExtendingMyHiddenClass stripped of unavailable superclass test.pkg.MyHiddenClass [HiddenSuperclass]
            """,
            sourceFiles = arrayOf(
                kotlin(
                    """
                    package test.pkg
                    @Retention(AnnotationRetention.BINARY)
                    @Target(AnnotationTarget.ANNOTATION_CLASS)
                    annotation class MetaHide
                """
                ),
                kotlin(
                    """
                    package test.pkg
                    @MetaHide
                    annotation class RegularHide
                """
                ),
                kotlin(
                    """
                    package test.pkg
                    @RegularHide
                    open class MyHiddenClass<T> {
                        @RegularHide
                        fun myHiddenFun(target: T, name: String) {}
                    }
                """
                ),
                kotlin(
                    """
                    package test.pkg
                    @OptIn(MyHiddenClass::class)
                    open class ExtendingMyHiddenClass<Float> : MyHiddenClass<Float>() {
                    }
                """
                )
            ),
            hideAnnotations = arrayOf(
                "test.pkg.MetaHide"
            ),
            hideMetaAnnotations = arrayOf(
                "test.pkg.MetaHide"
            ),
            api = """
                package test.pkg {
                  public class ExtendingMyHiddenClass<Float> {
                    ctor public ExtendingMyHiddenClass();
                  }
                  @kotlin.annotation.Retention(kotlin.annotation.AnnotationRetention) @kotlin.annotation.Target(allowedTargets=kotlin.annotation.AnnotationTarget) public @interface MetaHide {
                  }
                }
                """
        )
    }

    @Test
    fun `Using hide annotation interface order`() {
        check(
            expectedIssues = """
                src/test/pkg/InterfaceWithHiddenInterfaceFirst.java:2: warning: Public class test.pkg.InterfaceWithHiddenInterfaceFirst stripped of unavailable superclass test.pkg.HiddenInterface [HiddenSuperclass]
                src/test/pkg/InterfaceWithVisibleInterfaceFirst.java:2: warning: Public class test.pkg.InterfaceWithVisibleInterfaceFirst stripped of unavailable superclass test.pkg.HiddenInterface [HiddenSuperclass]
            """,
            sourceFiles = arrayOf(
                java(
                    """
                    package test.pkg;
                    @Hide
                    public @interface Hide {}
                """
                ),
                java(
                    """
                    package test.pkg;
                    @Hide
                    public interface HiddenInterface {
                      void hiddenInterfaceMethod();
                    }
                """
                ),
                java(
                    """
                    package test.pkg;
                    public interface VisibleInterface {
                      void visibleInterfaceMethod();
                    }
                """
                ),
                java(
                    """
                    package test.pkg;
                    public interface InterfaceWithVisibleInterfaceFirst
                        extends VisibleInterface, HiddenInterface {}
                    """
                ),
                java(
                    """
                    package test.pkg;
                    public interface InterfaceWithHiddenInterfaceFirst
                        extends HiddenInterface, VisibleInterface {}
                    """
                )
            ),
            hideAnnotations = arrayOf(
                "test.pkg.Hide"
            ),
            includeStrippedSuperclassWarnings = true,
            api = """
                package test.pkg {
                  public interface InterfaceWithHiddenInterfaceFirst extends test.pkg.VisibleInterface {
                  }
                  public interface InterfaceWithVisibleInterfaceFirst extends test.pkg.VisibleInterface {
                  }
                  public interface VisibleInterface {
                    method public void visibleInterfaceMethod();
                  }
                }
                """
        )
    }
}
