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

@file:Suppress("ALL")

package com.android.tools.metalava

import com.android.tools.metalava.model.text.FileFormat
import com.android.tools.metalava.testing.java
import org.junit.Test

class AddAdditionalOverridesTest : DriverTest() {
    @Test
    fun `Add additional overrides -- Does not emit Object method override to signature file`() {
        check(
            format = FileFormat.V2,
            sourceFiles =
                arrayOf(
                    java(
                        """
                    package test.pkg;
                    public abstract class ParentClass implements java.util.Comparator<Object> {
                        @Override
                        public abstract int hashCode();

                        @Override
                        public abstract int someMethod();
                    }
                    """
                    ),
                    java(
                        """
                    package test.pkg;
                    public class ChildClass extends ParentClass {
                        @Override
                        public int hashCode() {
                            return 0;
                        }

                        @Override
                        public int someMethod() {
                            return 0;
                        }
                    }
                    """
                    ),
                ),
            api =
                """
            // Signature format: 2.0
            package test.pkg {
              public class ChildClass extends test.pkg.ParentClass {
                ctor public ChildClass();
                method public int someMethod();
              }
              public abstract class ParentClass implements java.util.Comparator<java.lang.Object> {
                ctor public ParentClass();
                method public abstract int hashCode();
                method public abstract int someMethod();
              }
            }
        """,
        )
    }

    @Test
    fun `Add additional overrides -- Does not emit override with identical signature`() {
        check(
            format = FileFormat.V2,
            sourceFiles =
                arrayOf(
                    java(
                        """
                    package test.pkg;
                    public class ChildClass extends ParentClass implements ChildInterface {
                        public void someMethod() {}
                    }
                    """
                    ),
                    java(
                        """
                    package test.pkg;
                    public abstract class ParentClass implements ParentInterface {
                    }
                    """
                    ),
                    java(
                        """
                    package test.pkg;
                    public interface ParentInterface {
                        default void someMethod() {}
                    }
                    """
                    ),
                    java(
                        """
                    package test.pkg;
                    public interface ChildInterface extends ParentInterface {
                        void someMethod();
                    }
                    """
                    ),
                ),
            api =
                """
            // Signature format: 2.0
            package test.pkg {
              public class ChildClass extends test.pkg.ParentClass implements test.pkg.ChildInterface {
                ctor public ChildClass();
              }
              public interface ChildInterface extends test.pkg.ParentInterface {
                method public void someMethod();
              }
              public abstract class ParentClass implements test.pkg.ParentInterface {
                ctor public ParentClass();
              }
              public interface ParentInterface {
                method public default void someMethod();
              }
            }
        """,
        )
    }

    @Test
    fun `Add additional overrides -- Does not add override-equivalent signatures`() {
        check(
            format = FileFormat.V2,
            sourceFiles =
                arrayOf(
                    java(
                        """
                    package test.pkg;
                    public interface ChildInterface extends ParentInterface1, ParentInterface2 {
                        void someMethod();
                    }
                    """
                    ),
                    java(
                        """
                    package test.pkg;
                    public interface ParentInterface1 {
                        default void someMethod() {}
                    }
                    """
                    ),
                    java(
                        """
                    package test.pkg;
                    public interface ParentInterface2 {
                        void someMethod();
                    }
                    """
                    ),
                ),
            api =
                """
            // Signature format: 2.0
            package test.pkg {
              public interface ChildInterface extends test.pkg.ParentInterface1 test.pkg.ParentInterface2 {
              }
              public interface ParentInterface1 {
                method public default void someMethod();
              }
              public interface ParentInterface2 {
                method public void someMethod();
              }
            }
        """,
        )
    }

    @Test
    fun `Add nonessential overrides classes -- Does not emit override with identical signature`() {
        check(
            format = FileFormat.V2,
            sourceFiles =
                arrayOf(
                    java(
                        """
                    package test.pkg;
                    public class Activity extends ContextThemeWrapper {
                        @Override
                        public void startActivity(Intent intent) {}

                        /** @hide */
                        @Override
                        public void startActivityAsUser(Intent intent, UserHandle user) {}
                    }
                    """
                    ),
                    java(
                        """
                    package test.pkg;
                    public class ContextThemeWrapper extends ContextWrapper {
                    }
                    """
                    ),
                    java(
                        """
                    package test.pkg;
                    public class ContextWrapper extends Context {
                        @Override
                        public void startActivity(Intent intent) {}

                        /** @hide */
                        @Override
                        public void startActivityAsUser(Intent intent, UserHandle user) {}
                    }
                    """
                    ),
                    java(
                        """
                    package test.pkg;

                    import android.annotation.SystemApi;

                    public abstract class Context {
                        public abstract void startActivity(Intent intent);

                        /** @hide */
                        @SystemApi
                        public void startActivityAsUser(Intent intent, UserHandle user) {}
                    }
                    """
                    ),
                    systemApiSource
                ),
            api =
                """
            // Signature format: 2.0
            package test.pkg {
              public class Activity extends test.pkg.ContextThemeWrapper {
                ctor public Activity();
              }
              public abstract class Context {
                ctor public Context();
                method public abstract void startActivity(Intent);
              }
              public class ContextThemeWrapper extends test.pkg.ContextWrapper {
                ctor public ContextThemeWrapper();
              }
              public class ContextWrapper extends test.pkg.Context {
                ctor public ContextWrapper();
                method public void startActivity(Intent);
              }
            }
        """,
            extraArguments =
                arrayOf(
                    ARG_HIDE_PACKAGE,
                    "android.annotation",
                )
        )
    }
}
