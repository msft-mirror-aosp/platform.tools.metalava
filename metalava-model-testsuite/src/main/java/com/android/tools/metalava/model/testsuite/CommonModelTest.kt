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

package com.android.tools.metalava.model.testsuite

import com.android.tools.metalava.model.BaseItemVisitor
import com.android.tools.metalava.model.Codebase
import com.android.tools.metalava.model.Item
import com.android.tools.metalava.testing.java
import com.android.tools.metalava.testing.kotlin
import com.google.common.truth.Truth.assertThat
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNotSame
import kotlin.test.assertNull
import kotlin.test.assertSame
import org.junit.Test

class CommonModelTest : BaseModelTest() {
    @Test
    fun `empty file`() {
        runCodebaseTest(
            signature(
                """
                    // Signature format: 2.0
                """
            ),
            java(""),
        ) {
            assertNotNull(codebase)
        }
    }

    @Test
    fun `test findCorrespondingItemIn check all, no super methods`() {
        runCodebaseTest(
            inputSet(
                signature(
                    """
                        // Signature format: 2.0
                        package test.pkg {
                          public class Foo {
                            ctor public Foo();
                            method public void foo(int i);
                          }
                          public class Bar extends test.pkg.Foo {
                            ctor public Bar();
                            method public void foo(int i);
                            method public int bar(String s);
                          }
                        }
                    """
                ),
            ),
            inputSet(
                java(
                    """
                        package test.pkg;
                        public class Foo {
                            public void foo(int i) {}
                        }
                    """
                ),
                java(
                    """
                        package test.pkg;
                        public class Bar extends Foo {
                            public void foo(int i) {}
                            public int bar(String s) {return s.length();}
                        }
                    """
                ),
            ),
        ) {
            // Iterate over the codebase and try and find every item that is visited.
            val items = mutableListOf<Item>()
            codebase.accept(
                object : BaseItemVisitor() {
                    override fun visitItem(item: Item) {
                        val foundItem = item.findCorrespondingItemIn(codebase)
                        assertSame(item, foundItem)
                        foundItem?.let { items += it }
                    }
                }
            )

            // Make sure that at least 11 items were found, that is every item listed in the input
            // but does not include implicit items that may be found from elsewhere, e.g. classpath.
            // TODO: The actual number varies from model to model which needs rectifying.
            assertThat(items.size).isAtLeast(11)
        }
    }

    /**
     * Pairs of [BaseModelTest.InputSet] that are used when testing [Item.findCorrespondingItemIn].
     *
     * [Pair.first] is used to create the base [Codebase] that represents a previously released API.
     * [Pair.second] is used to create the latest [Codebase] that represents the current API. An
     * [Item] is found in the latest [Codebase] and then its [Item.findCorrespondingItemIn] method
     * is called passing in the previously released [Codebase] to find the corresponding [Item] that
     * was previously released, if any.
     */
    private val pairsOfBaseAndLatestCodebasesForFindCorrespondingItemTests =
        listOf(
            Pair(
                // The base API without a Bar.foo(int) override of Foo.foo(int).
                inputSet(
                    signature(
                        """
                                // Signature format: 2.0
                                package test.pkg {
                                  public class Foo {
                                    ctor public Foo();
                                    method public void foo(int i);
                                  }
                                  public class Bar extends test.pkg.Foo {
                                    ctor public Bar();
                                  }
                                }
                            """
                    ),
                ),
                // The latest API with a Bar.foo(int) override of Foo.foo(int).
                inputSet(
                    signature(
                        """
                                // Signature format: 2.0
                                package test.pkg {
                                  public class Foo {
                                    ctor public Foo();
                                    method public void foo(int i);
                                  }
                                  public class Bar extends test.pkg.Foo {
                                    ctor public Bar();
                                    method public void foo(int i);
                                  }
                                }
                            """
                    ),
                )
            ),
            Pair(
                // The base API without a Bar.foo(int) override of Foo.foo(int).
                inputSet(
                    java(
                        """
                                package test.pkg;
                                public class Foo {
                                    public void foo(int i) {}
                                }
                            """
                    ),
                    java(
                        """
                                package test.pkg;
                                public class Bar extends Foo {
                                }
                            """
                    ),
                ),
                // The latest API with a Bar.foo(int) override of Foo.foo(int).
                inputSet(
                    java(
                        """
                                package test.pkg;
                                public class Foo {
                                    public void foo(int i) {}
                                }
                            """
                    ),
                    java(
                        """
                                package test.pkg;
                                public class Bar extends Foo {
                                    public void foo(int i) {}
                                }
                            """
                    ),
                )
            ),
        )

    @Test
    fun `test findCorrespondingItemIn does not find super methods`() {
        val pairs = pairsOfBaseAndLatestCodebasesForFindCorrespondingItemTests
        runCodebaseTest(*pairs.map { it.first }.toTypedArray()) {
            val previouslyReleased = codebase
            runCodebaseTest(*pairs.map { it.second }.toTypedArray()) {
                val latest = codebase
                val barFoo = latest.assertClass("test.pkg.Bar").assertMethod("foo", "int")

                // Make sure that super methods are not found by default.
                assertNull(barFoo.findCorrespondingItemIn(previouslyReleased))

                // Ditto for the parameter.
                val barFooParameter = barFoo.parameters().first()
                assertNull(barFooParameter.findCorrespondingItemIn(previouslyReleased))
            }
        }
    }

    @Test
    fun `test findCorrespondingItemIn does find super methods`() {
        val pairs = pairsOfBaseAndLatestCodebasesForFindCorrespondingItemTests
        runCodebaseTest(*pairs.map { it.first }.toTypedArray()) {
            val previouslyReleased = codebase
            val previouslyReleasedFooFoo =
                previouslyReleased.assertClass("test.pkg.Foo").assertMethod("foo", "int")
            val previouslyReleasedFooFooParameter = previouslyReleasedFooFoo.parameters().first()
            runCodebaseTest(*pairs.map { it.second }.toTypedArray()) {
                val latest = codebase
                val barFoo = latest.assertClass("test.pkg.Bar").assertMethod("foo", "int")

                // Make sure that super methods are found when requested
                assertSame(
                    previouslyReleasedFooFoo,
                    barFoo.findCorrespondingItemIn(previouslyReleased, superMethods = true)
                )

                // Ditto for the parameter.
                val barFooParameter = barFoo.parameters().first()
                assertSame(
                    previouslyReleasedFooFooParameter,
                    barFooParameter.findCorrespondingItemIn(previouslyReleased, superMethods = true)
                )
            }
        }
    }

    @Test
    fun `test findCorrespondingItemIn does duplicate super methods`() {
        val pairs = pairsOfBaseAndLatestCodebasesForFindCorrespondingItemTests
        runCodebaseTest(*pairs.map { it.first }.toTypedArray()) {
            val previouslyReleased = codebase
            val previouslyReleasedFooFoo =
                previouslyReleased.assertClass("test.pkg.Foo").assertMethod("foo", "int")
            val previouslyReleasedFooFooParameter = previouslyReleasedFooFoo.parameters().first()
            runCodebaseTest(*pairs.map { it.second }.toTypedArray()) {
                val latest = codebase
                val fooFoo = latest.assertClass("test.pkg.Foo").assertMethod("foo", "int")
                val barFoo = latest.assertClass("test.pkg.Bar").assertMethod("foo", "int")

                // Make sure that super methods are not duplicated when requested unless necessary.
                val correspondingFooFooMethodItem =
                    fooFoo.findCorrespondingItemIn(
                        previouslyReleased,
                        superMethods = true,
                        duplicate = true,
                    )

                assertSame(previouslyReleasedFooFoo, correspondingFooFooMethodItem)

                // Make sure that super methods are duplicated when requested and necessary.
                val correspondingBarFooMethodItem =
                    barFoo.findCorrespondingItemIn(
                        previouslyReleased,
                        superMethods = true,
                        duplicate = true,
                    )

                assertNotNull(correspondingBarFooMethodItem)
                assertNotSame(previouslyReleasedFooFoo, correspondingBarFooMethodItem)
                assertEquals(
                    barFoo.containingClass().qualifiedName(),
                    correspondingBarFooMethodItem.containingClass().qualifiedName()
                )

                // Ditto for the parameter.
                val barFooParameter = barFoo.parameters().first()
                val correspondingBarFooParameterItem =
                    barFooParameter.findCorrespondingItemIn(
                        previouslyReleased,
                        superMethods = true,
                        duplicate = true,
                    )

                assertNotNull(correspondingBarFooParameterItem)
                assertNotSame(previouslyReleasedFooFooParameter, correspondingBarFooParameterItem)
                assertEquals(
                    barFoo.containingClass().qualifiedName(),
                    correspondingBarFooParameterItem.containingClass().qualifiedName()
                )
            }
        }
    }

    @Test
    fun `Test iterate and resolve unknown super classes`() {
        // TODO(b/323516595): Find a better way.
        runCodebaseTest(
            inputSet(
                signature(
                    """
                        // Signature format: 2.0
                        package test.pkg {
                          public class Foo extends test.pkg.Unknown {
                            ctor public Foo();
                          }
                          public class Bar extends test.unknown.Foo {
                            ctor public Bar();
                          }
                        }
                    """
                ),
            ),
            inputSet(
                java(
                    """
                        package test.pkg;
                        public class Foo extends test.pkg.Unknown {
                        }
                    """
                ),
                java(
                    """
                        package test.pkg;
                        public class Bar extends test.unknown.Foo {
                        }
                    """
                ),
            ),
        ) {
            // Iterate over the codebase classes and resolve the super class of every class visited.
            val items = mutableListOf<Item>()
            for (classItem in codebase.getPackages().allClasses()) {
                items += classItem
                // Resolve the super class which might trigger a change in the packages/classes.
                classItem.superClass()?.let { items += it }
            }

            // Make sure that at least 2 items were found, that is every item listed in the input
            // but does not include undefined classes which are treated differently in text model to
            // other models.
            // TODO: The actual number varies from model to model which needs rectifying.
            assertThat(items.size).isAtLeast(2)
        }
    }

    @Test
    fun `Test iterate and resolve unknown interface classes`() {
        // TODO(b/323516595): Find a better way.
        runCodebaseTest(
            inputSet(
                signature(
                    """
                        // Signature format: 2.0
                        package test.pkg {
                          public class Foo implements test.pkg.Unknown {
                            ctor public Foo();
                          }
                          public class Bar implements test.unknown.Foo {
                            ctor public Bar();
                          }
                        }
                    """
                ),
            ),
            inputSet(
                java(
                    """
                        package test.pkg;
                        public class Foo implements test.pkg.Unknown {
                        }
                    """
                ),
                java(
                    """
                        package test.pkg;
                        public class Bar implements test.unknown.Foo {
                        }
                    """
                ),
            ),
        ) {
            // Iterate over the codebase classes and resolve the super interfaces of every class
            // visited.
            val items = mutableListOf<Item>()
            for (classItem in codebase.getPackages().allClasses()) {
                items += classItem
                for (interfaceType in classItem.interfaceTypes()) {
                    // Resolve the interface type which might trigger a change in the
                    // packages/classes.
                    interfaceType.asClass()?.let { items += it }
                }
            }

            // Make sure that at least 2 items were found, that is every item listed in the input
            // but does not include undefined classes which are treated differently in text model to
            // other models.
            // TODO: The actual number varies from model to model which needs rectifying.
            assertThat(items.size).isAtLeast(2)
        }
    }

    @Test
    fun `Test unknown inner class`() {
        runCodebaseTest(
            java(
                """
                    package test.pkg;
                    public class Foo {
                        private Foo() {}
                    }
                """
            ),
            kotlin(
                """
                    package test.pkg
                    class Foo private constructor() {
                    }
                """
            ),
            // No signature test as it will just fabricate an inner class on demand.
        ) {
            val unknownInner = codebase.resolveClass("test.pkg.Foo.UnknownInner")
            assertNull(unknownInner)
        }
    }
}
