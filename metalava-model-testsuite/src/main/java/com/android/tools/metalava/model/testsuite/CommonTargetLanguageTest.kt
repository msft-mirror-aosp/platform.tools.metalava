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

package com.android.tools.metalava.model.testsuite

import com.android.tools.lint.checks.infrastructure.TestFiles.base64gzip
import com.android.tools.metalava.model.PrimitiveTypeItem
import com.android.tools.metalava.model.TargetLanguage
import com.android.tools.metalava.model.VisibilityLevel
import com.android.tools.metalava.testing.generateBase64gzipFromKotlin
import com.android.tools.metalava.testing.java
import com.android.tools.metalava.testing.kotlin
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Tests the target languages of items.
 *
 * To generate a jar file from Kotlin source files, see [generateBase64gzipFromKotlin]. The output
 * can be used with [base64gzip] in tests.
 */
class CommonTargetLanguageTest : BaseModelTest() {
    @Test
    fun `Test regular items which can be used from any language`() {
        runCodebaseTest(
            java(
                """
                    package test.pkg;
                    public class FooClass {
                        public void fooMethod() {}
                        public int fooField = 0;
                    }
                """
            ),
            kotlin(
                """
                    package test.pkg
                    class FooClass {
                        fun fooMethod() = Unit
                        companion object {
                            @JvmField
                            val fooField: Int = 0
                        }
                    }
                """
            ),
            signature(
                """
                    // Signature format: 5.0
                    package test.pkg {
                      public class FooClass {
                        ctor public FooClass();
                        method public void fooMethod();
                        field public int fooField;
                      }
                    }
                """
            )
        ) {
            val cls = codebase.assertClass("test.pkg.FooClass")
            val items =
                listOf(
                    codebase.assertPackage("test.pkg"),
                    cls,
                    cls.assertConstructor(""),
                    cls.assertMethod("fooMethod", ""),
                    cls.assertField("fooField"),
                )

            for (item in items) {
                assertThat(item.targetLanguages)
                    .containsExactly(
                        TargetLanguage.JAVA,
                        TargetLanguage.KOTLIN,
                        TargetLanguage.BYTECODE,
                    )
            }
        }
    }

    @Test
    fun `Test properties can only be used from Kotlin`() {
        runCodebaseTest(
            kotlin(
                """
                    package test.pkg
                    class FooClass {
                        val fooProperty = 0
                    }
                """
            ),
            signature(
                """
                    // Signature format: 5.0
                    package test.pkg {
                      public class FooClass {
                        property public int fooProperty;
                      }
                    }
                """
            )
        ) {
            val prop = codebase.assertClass("test.pkg.FooClass").assertProperty("fooProperty")
            assertThat(prop.targetLanguages).containsExactly(TargetLanguage.KOTLIN)
        }
    }

    @Test
    fun `Test type aliases can only be used from Kotlin`() {
        runCodebaseTest(
            kotlin(
                """
                    package test.pkg
                    typealias Foo = String
                """
            ),
            signature(
                """
                    // Signature format: 5.0
                    package test.pkg {
                      public typealias Foo = String;
                    }
                """
            ),
        ) {
            val alias = codebase.assertTypeAlias("test.pkg.Foo")
            assertThat(alias.targetLanguages).containsExactly(TargetLanguage.KOTLIN)
        }
    }

    @Test
    fun `Test value class constructor`() {
        runCodebaseTest(
            inputSet(
                kotlin(
                    """
                        package test.pkg
                        @JvmInline
                        value class IntValue(val v: Int)
                    """
                )
            ),
            // Compiled from the Kotlin source file above with [generateBase64gzipFromKotlin]
            compiledSourceJar =
                base64gzip(
                    "test.jar",
                    // kotlinc version info: kotlinc-jvm 1.9.23 (JRE 17.0.6+10-b802.1)
                    "" +
                        "H4sIAAAAAAAA/wvwZmYRYeDg4GBgYFBkQAYiDCwMvq4hjrqefm76vo5+nm6u" +
                        "wSF6vm7/TjEwfPY9c9rHW1fvIq+3rta5M+c3BxlcMX7wtEjPy1fH0/di6aot" +
                        "QR+8dAu1vM6c0Q77cE7/5Mkzj58+esrEEODNzrFeWHO9JdACcyAOwGm9OBCX" +
                        "pBaX6Bdkp+t75pWEJeaUpuol5yQWF9eG3A2WDhX5f3f2+rNpjbMvBG1a8M35" +
                        "dku/2tGo1SJLFmztXhi6IvDUZaGyQ06bOm/6Luvf8zL+QbO+1a8H3PxWwY0b" +
                        "jfkeza6f/LjiXdpkA8XHPkX252yfl5/7Obf42/v6+v/MB2JzDFVsT38t+vq3" +
                        "6FzBnkWRmVGGBmwb07ediTlacmrn8tvVYSe7FY8I6EXHTVScclKKZ41rvbMz" +
                        "j5eMTGHVt7Lc6YlbChiL5yWeWJ1bwXtb8/ytq1WtD4uOnRCriruVv6R8afy2" +
                        "O1u0rcOU2wTbXjDf3rjX1b5lUZtKkBaLm6SQrP/Ma01Np5dkXns9bfNhg9k7" +
                        "Xzxd9PjAyT1t2hLTZ2S2nXQUnBc+Z0LX5KDCp/9F46d35RtoyPJJ6KWypax/" +
                        "KLXO+660y7KbGs9NeRIjVMvyhRp3L9u9O+j2RiamwyJyUxVvOivyrdmUktUV" +
                        "U7rF9IzC6iSpzVJGn45MVN63Zv2stKO8C44ZuS3c/+Dzzec8WZdvrtv7YavJ" +
                        "nt4VW/adFlhZuTusWbH0YteMfOfTXPxMVwvYi4We/y3jchVNjHQMXGW57nrG" +
                        "tD1RyhzeL398Uz7Dl6F9k4lf5YYz29sYr8ouw2QJI9a4/crTLQ9pvLm+p+vt" +
                        "k29RV+9krc6yKJm4duYbmzWR83YmV6mdrNgqukgysajzNOuVt79V6hNPS1s+" +
                        "sGb5c/nlwdKp/cVTuy8zT62L0dAKV9qintF/yc7HOvzcvjgpe9sZjyu32Uzq" +
                        "4lr3uebFm+MZEZzdJ1bpiew6/lxk4auHhdPS+///2vZ3lsCtVQLOQbNTzkzn" +
                        "ndwqoKwiHBsZtOTa5NiQVqWLh71CjpzblxjT5vXy8Z0pd+5976i9KcejzJr0" +
                        "0HPzrlzTeTNvi3pP2xpmpO26OfF0tYLMrQ9nLv2bFcn/7pXb2psakf05lZ7r" +
                        "zWXPr4tyvb315deFj6/YXN68clFIrh/rtHvBn4Id1SO3cCxS+Tknz9JT5+3E" +
                        "ldMD+F8eCFxi+orb+cch8w+iExYIhzqfc0n4o5d5evsX7Y6zyTFevbKO1/s/" +
                        "zDoh9/CV4L0/R7/fYfZ8rGvPpqauy7BT0mXBCQ6/gtX33V5aLfvJmZNXsuia" +
                        "f8HvDecVLpUaXfbdfN5z3/1lz1uOKW+o9Lp5Mu3y8v8s//kmXnj4RfOH4L3I" +
                        "C+r7p18vmL1eP7O38fj/gwXSRT0tKWItiv7fO7QkD9TuEW84vctj7Q6ejF8s" +
                        "n/d23Hr2UUGp8tNZ1umXxWbosz17dcVQbJlaXfOv1wdubd14RW/aK3NG5TNH" +
                        "9WaGM+ftuHeeK397j8djZh3WW9s5IvcdjW/Mf3U84t36cBsDr1P6YfV7rWKP" +
                        "sZWESWn/2KC/v8XuI3PSw/mCfF9P9Anc9H6+0+vB4oZXLHI/fxbm/Hp1/sOc" +
                        "b7FWtT6/k41MtwkYbch6IMzMuuaioD/Pqq/LoxP07A7/EnOLzdAziznyp/Si" +
                        "6zXXI65XjtbGF/U/O3V0TrekuKT49ZqHE7of/geXOaue8lfrszAw3GbHV+ZI" +
                        "AzG8yMtNzMzTy84vycnMi8/NTynNSU1OSEhIA2KWJD82jYCkC0kMYLO/Ku3Z" +
                        "KwzUKQEuzxiZRBgQpiOXdaACFRXgKl7RTUF2vTiKCfW4S0l0Q5CdKY1iyGcW" +
                        "vN4O8GZlAyljBsIrQNqfFcQDAH2x79Q5BgAA"
                ),
        ) {
            val intValue = codebase.assertClass("test.pkg.IntValue")

            val ctorImpl = intValue.assertMethod("constructor-impl", "int")
            ctorImpl.returnType().assertPrimitiveTypeItem {
                assertThat(kind).isEqualTo(PrimitiveTypeItem.Primitive.INT)
            }
            assertThat(ctorImpl.targetLanguages).containsExactly(TargetLanguage.BYTECODE)
            assertThat(ctorImpl.modifiers.getVisibilityLevel()).isEqualTo(VisibilityLevel.PUBLIC)

            assertThat(intValue.methods().map { it.name() })
                .containsExactly("constructor-impl", "getV", "toString", "hashCode", "equals")

            // TODO(b/407735992): constructor itself should be Kotlin only
            intValue.assertConstructor("int")
        }
    }

    @Test
    fun `Test function using value class parameter`() {
        runCodebaseTest(
            inputSet(
                kotlin(
                    """
                        package test.pkg
                        @JvmInline
                        value class IntValue(val v: Int)
                        class Foo {
                            fun usesIntValue(iv: IntValue) = Unit
                        }
                    """
                ),
            ),
            // Compiled from the Kotlin source file above with [generateBase64gzipFromKotlin]
            compiledSourceJar =
                base64gzip(
                    "test.jar",
                    // kotlinc version info: kotlinc-jvm 1.9.23 (JRE 17.0.6+10-b802.1)
                    "" +
                        "H4sIAAAAAAAA/31VeTQbWhpPJZZ6sce+1DaqVILWGKqMl8aTROy11RZ7NMRe" +
                        "2qJqLfVC7YLBqNZObVVLUXtijdKILc+orTytWkuNds6Z0Z5p7z2/P+459/t9" +
                        "9/vO734/YzQQBAGwsbEBAAApwOkFAYAAGIS5rhLSUA+G0TVE6iHMzKEYvc8D" +
                        "AMAWhkI2QCtBRznQSgpDlOFaU+XxS/OLflAU5iISMxpYWme6iVLyVUBRKIoW" +
                        "m0Ow/n7KwuIfi0wAYzQrWxXfhSqNkwTqJzD+YXrBEwS4+gfAfG66w5DeARZY" +
                        "fKAr1BmP9fcPNZ8xE70OOZ4hVQ26RZBGTJ/l78DpUUS5bpsySFF+fULB9acm" +
                        "A1TeoM5fnz2gYYqJLSsO85EwzYP5X7g0zSJqLnH+QQpPWQjecEtRllow8NMZ" +
                        "urp0a2g/13/nz/DwY2C7HV5F9ip522/7yG/Ip6XQGmejosxS495Ase0OGGh6" +
                        "Qr9j0Z8g1cUNvWGfLJXaLwIuR4TD4WCUmJjv7Z0grwxsnc8Z/zxsX5lXMAf9" +
                        "wvDU69vRDL+ePoHb9lOEoluPHRqm6xSvWMjE8MQsA+k1rQidqMIYWVMFkJ4w" +
                        "r7hR1sT9++Qi3MRaeu0rZVLT8mLhQnt/S4yiUEYmLqZflyfPMicpPsXUd/GY" +
                        "3yEjnqAsL84pBHVlcaliiFSiZ0SvFdPkl9TAWKu/BBF4I5qLm5tN6TVMTK8g" +
                        "EmlSNLgUZ/kzF89428A6NYpkmZNIrYjqh65kmbbyqmy3bo78HlW9gpfzW7Ql" +
                        "sCeVVtm6WX+5JfFpXRuZuySk2SJSKnA0PpMAJ7NzMb32YfXnXToKYkfwY611" +
                        "TUo1Kic90ltsZNjQK3s7MhROD0UaE5fsGzjLui0qJF7FWUiV2f6lTIZGp/y7" +
                        "yZb49X/t2Lye9izz/FtAckXWO61y67wm59ty/cH1/IXCWL8HZObx9U+y4Viy" +
                        "qMb8FdAhdaUjMI3on5ZABaaF2corWErXnfcgjmkbXLEcarMX0bmauRDSoPUo" +
                        "nr1y6+7yu14Pq7MJfaVQyIveJUjBKsM33Z14fNBwlM09VcoNNyW5UDI4UqK5" +
                        "ZWT57KxNiyZS7MyjpUdfocy7htqwtjGolYXp1OnZ3bhQmgRYhtmJgax94aWW" +
                        "l0XnR6fXW6gqImqx5DuSYlOblLHP2dZcG6t6FTR5ayI+BFmlLj5caYOg169s" +
                        "FyyMa1FrSwrNvQyZ02fNPpjpnreuYyuU3c/x1kBeXE8uyTDmWmk3KVJb/QW+" +
                        "16m+yZ+Uz3cdPnTN8RCKIzd+VIwbdLZFJYrrThI3s/skGKs8s4fdu9NA5IKS" +
                        "DovceSVAk/C1/D42Q5+yOb0VzeL9s3jvgMIJI59P1cOSY4GqVEztMLJtrngp" +
                        "qkemOgRF63ejPjkGHXMmjzA+XtjjmbUeOf8yY9KHVAXDJUb0Hnf4iPo9jHIR" +
                        "iJIy2o1TEG4PbRG8R36hX/Ec7HEA2mqNm3r7XlI65MMgcwZVIBPG8nZ1XEWg" +
                        "WC4s8mCtfaq+Zhyavqp+RobSDc2yBHo/nx1mJzQ+1F8AXmSeamSzbut2iCCs" +
                        "9lptVFlqKaMGYBbhrZp2PSwBFiKKe9Wwl1Ha74FOjH/wcG73/c5NQy81oeb/" +
                        "eW8VJLG/74s/WB3ezNmx0ww1+OSsqtbArVrtOc8HZC4f5TECl24/ueEI1X51" +
                        "IKBn5wH9q23XYeAoYgLRhRjvDnXwI74d6M5JEBYUFpy8y0hKYBx/nTmli1x3" +
                        "YCAAgM76s5nDe3rm6BEI/xk3dsYYAvXv3KHNn+MWiiqoAvrAVKK9jayPSMfZ" +
                        "KJB+8kMm/XvTcENSl2ejLz7VLlZM5vPZQ6ChJKc8u3oYxGnCCxHNdYkw26Ke" +
                        "R5sLDzsEbXLfAQfmyouHCZ2LndGquqcCeuIk12nu0ssgdeBUx/mtwBt8Wcij" +
                        "1I4pnbg1n/mDahNs9fYDc/BaKzlS4hJeUMHEpNg5WaD/KFh7/00eEdl2TrwL" +
                        "bDskZNU1spzonztw8W6ahwqvTVKakZvAeq/B02zA2KOM4cclU1F6seDfyAjy" +
                        "EmZjy7Fps8kdsMtxOaeXREbENtL83be7Hw+R3Rzrg+kbzyPgtKPtXCXB8Wet" +
                        "qPkK7ffxfwaXDTd7ufXcytzHHX3gAN849m6RxNGnmMOUJwLmEu5eG0vMozya" +
                        "7HZJMphL0EVR3GDn4i8LmOFW/cfCMwSp2LG04jchN1xsDB9ZKSBn3K3Tbxa4" +
                        "5l0Qu0+9SSJSbT7uQW6VsA+SjYM2QiN36/FzhcvpRYl0yJo7kznDzDvfQrXn" +
                        "8Ok4NfdYNHcseLs8xPuyB8caRGMj9YD0HN2EprBo3NcuZ0tkI/E0VGO4LaWz" +
                        "fSu4ike6DM9dZ3YoeDyvYAvryO7kiM7+9Qj2RQy6/Iuxg2cAgBSmn4lB9AT/" +
                        "9T8vLM4bepMQgMd5O3gRXALxrs6Ojo5uJwA5GbLIGzuNOAG+Cm1buqWV7yRS" +
                        "6Ku5nWGCAP7Hftr4vrjrt+tHXvs9y2kpC37DEP5jy/ye5HQLeL8h2QL9vz/w" +
                        "ffzpMkW/rYP1p20zRjOzfLkFOtmQkwfYsn45/RvR+gOthggAAA=="
                )
        ) {
            val fooClass = codebase.assertClass("test.pkg.Foo")

            val mangledMethod = fooClass.assertMethod("usesIntValue-Vxmw0xk", "int")
            assertThat(mangledMethod.targetLanguages).containsExactly(TargetLanguage.BYTECODE)
            mangledMethod.returnType().assertPrimitiveTypeItem {
                assertThat(kind).isEqualTo(PrimitiveTypeItem.Primitive.VOID)
            }

            // TODO(b/407735992): non-mangled method should be kotlin only and have IntValue param
            // type instead of int
            fooClass.assertMethod("usesIntValue", "int")

            assertThat(fooClass.methods()).hasSize(2)
        }
    }

    @Test
    fun `Test function in value class`() {
        runCodebaseTest(
            inputSet(
                kotlin(
                    """
                        package test.pkg
                        @JvmInline
                        value class IntValue(val v: Int) {
                            fun foo() = Unit
                        }
                    """
                ),
            ),
            // Compiled from the Kotlin source file above with [generateBase64gzipFromKotlin]
            compiledSourceJar =
                base64gzip(
                    "test.jar",
                    // kotlinc version info: kotlinc-jvm 1.9.23 (JRE 17.0.6+10-b802.1)
                    "" +
                        "H4sIAAAAAAAA/wvwZmYRYeDg4GBgYFBkQAYiDCwMvq4hjrqefm76vo5+nm6u" +
                        "wSF6vm7/TjEwfPY9c9rHW1fvIq+3rta5M+c3BxlcMX7wtEjPy1fH0/di6aot" +
                        "QR+8dAu1vM6c0Q77cE7/5Mkzj58+esrEEODNzrFeWHO9JdACcyAOwGm9OBCX" +
                        "pBaX6Bdkp+t75pWEJeaUpuol5yQWF9eGeAcrh4r8e+er/rLEaqHwPM4Vi43P" +
                        "PVFkkSg5lpggcaFQ6vACAzfJkostZiZPUnc5n5009x+TeN6WOzsSF4ZyTQmJ" +
                        "7LCun5xambf88wqHyKl3qs8//n1u5/M5t+v///4tz/DsA6vzGcNc9XB7jadf" +
                        "VgUbnPI15ji24K10u9u6nBDvnV/n1m5+wfIweYLXsuCED0svWTFbL9m/TonD" +
                        "kqVPYOZc29NpEmfiH85Jn7B/zVW1tV57feo0bX1NJl3U8DV2mXNr2eUNq1TP" +
                        "W/lcqzGvOe3GsvjvrqfLQu+ZSjsekWTiX7vqqc6unaIhPIkRBxXcJA69FQlq" +
                        "5zY6duLyiutnX+kdTnmVNWV1416Fz1qTJMTnCPMIX0+98frHqoIaX4t1f/Y/" +
                        "fn3oI6+W3242HueV2XnrH4ndE9m3PTSjQzPkvUho3vkN38Oydqf5x6403Kg3" +
                        "Ry9/mkvnvUUSufmiwSe0Zrj4ubrNsUhbEfAs3HdxTnWIsqbtfXumzy/Febw2" +
                        "7wquflm1skKasbGu/WB88bczbM5/tLq87i0SW7u3L+Ro7BnOee1cKheN10as" +
                        "vuW3OLIki1dffqfsFV/d2rMtncvcWU8bNd8qFp9lUO913fXdHjvlaZGPTshf" +
                        "mbMp9Oxb+bCi1e2ie72aZlyKuL0hzerYuocSSx5PdZa3FVebNiHCobne4df0" +
                        "aRM0GO6rPvrcvbjn0PEdh7Z2K7fWxUwW1fudyX9Z7+I5W0sv6+n6++OkdlvM" +
                        "eno5p1JB7kLFJP/fevU8HpuW/AyfPMunKCPs2pQlJseW3by8f19u5OFZu6Qu" +
                        "KqX07vrUsGxtVpTPgpPtO3dZGGbcUnjZZrUrMTdF+VqnUnCbpydfxsHT+RLi" +
                        "4Qs2XTha88WnOu7slUbnYtVlbYkK/qZV19a8mdH7/JjfTZG760R7iw5mRDh9" +
                        "mzLR97iG3BrJUv8ws+yzbhdrkjgVN15cYxV2VCdim+u141ezzhrKdP/qlXw1" +
                        "94mYud4+zZIuBTZ7piU/pyyZo2H99t+Z9QF9Lw98lFzLGP7ysNjarNOMiVaM" +
                        "nEodVQ/WePv8j//VEMQ/Yd2LY7yd5s8Y30/cvkH1nOGO0q6LFY9Y1qv4Fzh7" +
                        "z3/wjPPdnzdMu1U4+1s61V9PvPMtaLrrq3SP78kp+o8nVPTLPUv7veuksY/8" +
                        "d8Y/cxWC5/76wjrz7fGS5gvFnw1el1gbHj75Zv6D+wffW5+La5j/4J5T0aNq" +
                        "Ra+1TvsXmHLI/WK/KPaUQ5Pz2VJFv+8f180+wHym+smihYe0VYROzUtcODuQ" +
                        "W+6TkZC6+LrCjx22PxaxhxstjzjT859t6f7yfXJ6Sw8v3XH5cMfnwOUzzPfc" +
                        "+pBSH1qRfW0Ly7QPNz7vcz5zSMyK/b7XvKSl++MV+UWE4w/8q9t6fHKs9aT3" +
                        "D//u+906+bG9gsZflpPXdbfN79p7JXq1wbyKpXyPmb/XydxeV804//K7Cy81" +
                        "31o5hm9/ePnjwr49faY2i5Wvlda09G8/mJOTUCy92CxE+nwuy92vE3vX8O7v" +
                        "behlX/dsX+9us2c5NY+6BKUDpYPNjMzydkqddXtjDyqfjkRd3B/NwsDgyYGv" +
                        "fJIGYnjxmJuYmaeXnV+Sk5kXn5ufUpqTmpyQkJAGxCxJfmwaAUkXkhjAZd9X" +
                        "pT17hYE6JcBlHyOTCAPCdORyEVT4ogJcRTG6KciuF0cxoR53iYpuCLIzpVEM" +
                        "kWfF6+0Ab1Y2kDJmILwCpKtZQTwAfGF5dWUGAAA="
                )
        ) {
            val intValue = codebase.assertClass("test.pkg.IntValue")

            val mangledMethod = intValue.assertMethod("foo-impl", "int")
            assertThat(mangledMethod.targetLanguages).containsExactly(TargetLanguage.BYTECODE)
            mangledMethod.returnType().assertPrimitiveTypeItem {
                assertThat(kind).isEqualTo(PrimitiveTypeItem.Primitive.VOID)
            }

            // TODO(b/407735992): non-mangled method should be kotlin only
            intValue.assertMethod("foo", "")

            assertThat(intValue.methods().map { it.name() })
                .containsExactly(
                    "foo-impl",
                    "foo",
                    "constructor-impl",
                    "getV",
                    "toString",
                    "hashCode",
                    "equals",
                )
        }
    }

    @Test
    fun `Test functions using different value classes with the same inlined value`() {
        runCodebaseTest(
            inputSet(
                kotlin(
                    """
                        package test.pkg
                        @JvmInline
                        value class IntValueA(val v: Int)
                        @JvmInline
                        value class IntValueB(val v: Int)
                        class Foo {
                            fun foo(ivA: IntValueA) = Unit
                            fun foo(ivB: IntValueB) = Unit
                        }
                    """
                )
            ),
            // Compiled from the Kotlin source file above with [generateBase64gzipFromKotlin]
            compiledSourceJar =
                base64gzip(
                    "test.jar",
                    // kotlinc version info: kotlinc-jvm 1.9.23 (JRE 17.0.6+10-b802.1)
                    "" +
                        "H4sIAAAAAAAA/5WXeTQUXhvHh+xG1hAiWxEzlsjyo2KsM/ZtNMhOMmObQSLr" +
                        "GGRJlmgsFUpk38k2lrJPQirKUCO7UXbRq97feX/VeX+/97zPPc8f95x7v/ee" +
                        "5zz38zzXBHaEhgvAwMAAAACEAT8bF4AGYKhtoQHSN9KRMdQw0tfRNrcAG+oc" +
                        "9AEAXwwH+g1gIPAwCwx0ZmiAWG0mO3qWRPYDQw2l9Q2H/YtrzChQkO8Z6MCA" +
                        "lBVlSKa3d+ADeYZMDTCB0TOUc0qWqxweoHToJn97PO+hY1zRGBkfT3cZfS+M" +
                        "lSPS31UD7Ix0RKNvWMDQ/NpsB27riNJXm3r6Ap6ok6x6YhdEurFsPsiv4cmK" +
                        "ftQBbMrnS8mgIJSHeI2UZVIrQYbUTst01DYkvNjhqd4x14HEKS3T9RKrFJiV" +
                        "qfhgfuHe4kr5063VsLBvR9rVkXIFav0bfhv7fkM+zXmXPBAwOVm6Sve6Adtu" +
                        "TF9j4USwVW+8cBcb2ObybeG0Xj5giXYYBAKECgj4Xt8MQGVM+02LzAl0JsMn" +
                        "KHx15rmVTzaYOtMTboPWJ2um9FavtiEbPeABJTpAUeBwTJ1zMHMoUJtFv8CC" +
                        "wdbpRVOZfGkQLVxvrGQc5B7jeAo9PKhNjMhQYYHLndIYAXJGaaqVK4OPnS6Y" +
                        "6fsm1Qo+NuVgoirGZniXat6zIYnfbOvUsMe5viAWCK9V3JRNF6lsfqVsNKAk" +
                        "nCAbrRutt2kSS/NEuggvBj+BYhnpKc7sSMAlqPl+5OV4BTesnqEzyT+e/8L+" +
                        "c+MSmq52Hjl+eRvB917HAjHlHDvabPdEYx89fDPTG9LPwUo95sNTzfFpP4BJ" +
                        "+5jjJU3TYpWy8St3mhGiDLD57Ud1ybW8L6yrGAWhNeJXI9EqZMmeBMcegWv7" +
                        "ZbZukoMdoYNPXht5lVsOltYV4yA88ZpGAYKvpImTcuqpLs1vmYazZRu7yNRX" +
                        "t2+ktFWQEwUoJ6gP5tdJuL7nDX3d8xHQC2CudCn2t5w1uh5Cj3Kk3O1lknIE" +
                        "Bz6r2Qjqa9OOB53HbDorlGC1XSx5mN59qL5pskHh7a9+HnZjItSQAWUZK1xk" +
                        "KD/bj+vVjGVPiSuTljRb7AUXa96ecxQ/4+Q+JQuGPFz3DTVaWQruakOy4W5T" +
                        "D1MKLr2r4yMOLN8UZ7aRupue96aCrM52FHXNzSNssIQ+0D//FTKtRLeuuWA8" +
                        "K8kdXpq3/Ha96aOyInEdWSw5lmUe93Av7avtC30rUKSE/vlCGyN5WLDLZ/FR" +
                        "iKoPl0fibgdbK4UlhFZsToucbM7O2wZ7+xm0z+T0hTfDPF/3xYZo86kq3YYA" +
                        "rFLYmvrWxZfX+m9F9F5ivuhFyy4hH8k5uVJv2pTkEUmdlZXzeINzUmXpbCwm" +
                        "xyBwZAmdslo/r/KAihGZaxZQNbFedIHQBhHDKPszhWIDZzfiVsmKk7OLMIQz" +
                        "aS1MdhInIC58l9axZ+RGHpSOAlilJVWBzaTAR/CqBFuZvLogtVi93BA0gdxA" +
                        "O8sc0agalLDTuzZ1/0C1IgAlXRv/cNewnQH9ADZbG85ftlXdMQESfbkNiI/Y" +
                        "RGne8V6Dk7J25e7sLR5jTUj5nDVmX26UUUmVk/6M6fxSv8yDW9/CrbfdsRD1" +
                        "KlGtzZmgMo5tifuqEbqhoQJZrbtn1QpvZBgJjRxkGiSCsMASvu2ui1SemC7O" +
                        "qKv789yZ8cSK1mgYWIGHH+x00EQSWRSZEVmYiZBpNEaTnV5118QnxC+2+vR0" +
                        "U8K+Y2c/e6hejgYAINH/39jR/As7ExZcBysbsDvpWgETClbU61SlObrLODMm" +
                        "D01Bk2JSTTIXBJ/u5IkUr0NKvxlxDaAwW1dERNBmSTs8Ejuu2omt+KAkB/wE" +
                        "rcrxVRt4vZUbvBIU8umPTwt/YmdJtsB2xBc9uA81qn+XZ+qZ9eQQO/7EOd5G" +
                        "vCPczzhwayCFHxIrbCv3TgQikTp23DrmoCmSt0BZeQazbAWKOcSO0wtaHHt9" +
                        "JblO+qm8+1iWIk7quLrNXOHaas/Kwn2vWtRYn5sUTg43B+mvmxRnhZhqcEge" +
                        "PwOWk2ixuDuUh5VyuaqQ9cjOLbPf1gAth0521SBYGsTe98AVObIR+Y2YOiUd" +
                        "Gza/4cqZO5V4C8CMWuZuejtMZR9wFS36GL5S6GUCg47rzBa3nLVi9gIiH0kH" +
                        "xDEKKoq8uaUplnlMY0zh4QJCKlPb6hlwlssF23XbWS/TvjYRz4nlI85FE+7I" +
                        "uNw724MKrl/rqDajnMvr2BOEFExbDXIPErVlnN6zghBq0o8jn2by+L7fgkn0" +
                        "McRzFz/ELTeUBvSiaqitzZSUEqPnupMJHBCKiZbNA2FluHnuRTeM3Wh7VmC3" +
                        "vLEr9ya/12AVOjjnuleL+ThKNZNG5U3La1m7fGQD3fy1Fs68wI4uDh7mK6Qt" +
                        "/W8dHKeh7f1c24UjUfzy90jycGNWl11fy5RGrgfqGsFpm6ZvGzGbG5+75glq" +
                        "xOqxSgsLMh8xZig0Afewgu/SuEXc0FqQfnotheYHdhanTlm42lnqYhXnMwv5" +
                        "xVktT4jmvB0RHxt1q+KxFBFDWN8srqz25uUxKQrJDj4tFbhpN2MPZOi0jrhy" +
                        "7WXZKr7U/ZnXIFciKi0BMXzl0oyAhG6dbX5Nm6NCVHXz2JV66KwoTOBlFmqi" +
                        "dsFt2E+6ya5yGefZwL2RtjtY1C1yAxoC1uOwiz8JxRPn0zgHXgreDWXcSaZJ" +
                        "Eik1tY+TUaO3N0rWWmYrF/EJzSjptts9NrrsbGuUKKQxkkTB49U7akWbd+Iz" +
                        "GoXEEuvVgAbNtSd7SgERYmzZpM37t0ZhBjISlMY32qU5pKHpcEhBhksdfPZi" +
                        "ytf7xhcY1ZPbP0lann5kmLXD+fUPLc2Ys4Uk3Wvuplm7lwU6z9vbv+Q7cutb" +
                        "lOmFj8qSvaBw4YI9rXTAd+wQtO2xlrYAvApBU+j2qiArG2MvGt54zStiz4wE" +
                        "NhZy9r3+Wamn7ehyjhBspTulRdiBC12k+wM7LFUd+7dNR7YB/REbqBmtyW77" +
                        "CG/P59YnluBqPtCb3q8c2prMn+EMrPg8t33ut2LP7Rxxmr7HfnSjN4ntTf+n" +
                        "RigpP3yBRjBowxcZPEukZG+e0f+OHduNE1hMyeK2li4Vv9hMnFHLAYZ+gOD+" +
                        "epIgTn8zCkQv523nl+zP5svmT2kfmSwMJDu8J5REJUQt5vp1EeZ+YOfjHUP1" +
                        "/40djp+xo+Pt/W/gRJvaeXNocKmf7Q+teFE1ivAYQ1wYwc+Z5RkrnnIws4s6" +
                        "ZxoppwaqKDV3gN+NwG9rfj29yxryctuBoXUnPyGwruuczge1yeAVla3cybs7" +
                        "a18WlQCBPlHC5SJFX8dbjoQp6SNl6VZlBWJCLySwfm3ZdBw1PQekW8yNjW4i" +
                        "OiC9FL/IOjb2tLv1PIZryku/MuZflv24mfrxVafuXiKPrvrCW2AdXkH6mSUd" +
                        "9wA533f9EXubwQ4fpPI9zMK+6yBVL/qdSbRLJ71zVICrqos/YDBVOEwKY3lq" +
                        "9J3bNaUcZENzl92K4WlwoAKpDIdHJygYn344X9DRX+I2dB4xSmn4eNyzObvI" +
                        "Olf/ze5BtOFpMVeBCbRR3KW+lbjML9lvgr2ePjq7V3HZ7Xo000j8cYwkjpxa" +
                        "yOA0BtzLf9z0Bd6kgzkIJC7n5sVcT8aI4jTtuoZHhu+fYOF3fGo8YKY+j913" +
                        "U+Zqyk1d+eKisKT1VJPnCVGnkiPqsWTByVq+53FM6XpOcSifXdANICL1/K0Z" +
                        "1+KQfXzaYNa96RCRdt/ovPQU7jKzASGlk8wZfuyNacOdgSOhTVv3ogJUG/yk" +
                        "h+gVEe0i3SJM3+p9DhynH3ILjL88YLEtbM7A462vEhh8TrX7hVdbC8eiqaDW" +
                        "qAj9kukQ5iBG+EwkVtxXjpaZY5FDpUjceCZFa5rne/akZfSEH1ABAEJH/il7" +
                        "+A/9P606ytHDC+zpjUF6eNmjvF38ka7ODg4ObodO42REJ2Hi9MIJ8KMP3xBp" +
                        "buH8s+SZwKiouQB/qf/co3//CPxqf/ct+F3l55LL+4tC2D9097+r/PyCflXZ" +
                        "pPn7Yv27ys+R5PhFBcX4397e7/t/jhb/L/ufMP9j9E1gtHTfl9EeDpvDC9AD" +
                        "v8/+Be9L4gd4DQAA"
                )
        ) {
            val foo = codebase.assertClass("test.pkg.Foo")

            val mangledMethodA = foo.assertMethod("foo-GHfTWwk", "int")
            assertThat(mangledMethodA.targetLanguages).containsExactly(TargetLanguage.BYTECODE)
            mangledMethodA.returnType().assertPrimitiveTypeItem {
                assertThat(kind).isEqualTo(PrimitiveTypeItem.Primitive.VOID)
            }

            val mangledMethodB = foo.assertMethod("foo-wveqTnY", "int")
            assertThat(mangledMethodB.targetLanguages).containsExactly(TargetLanguage.BYTECODE)
            mangledMethodB.returnType().assertPrimitiveTypeItem {
                assertThat(kind).isEqualTo(PrimitiveTypeItem.Primitive.VOID)
            }

            // TODO(b/407735992): non-mangled methods should have distinct signatures
            assertThat(foo.methods()).hasSize(4)
        }
    }

    @Test
    fun `Test only one copy of function with type variable`() {
        runCodebaseTest(
            inputSet(
                kotlin(
                    """
                    package test.pkg
                    class Foo<T> {
                        fun foo(t: T) = Unit
                    }
                    """
                )
            ),
            // Compiled from the Kotlin source file above with [generateBase64gzipFromKotlin]
            compiledSourceJar =
                base64gzip(
                    "test.jar",
                    // kotlinc version info: kotlinc-jvm 1.9.23 (JRE 17.0.6+10-b802.1)
                    "" +
                        "H4sIAAAAAAAA/wvwZmYRYeDg4GBgYFBkQAYiDCwMvq4hjrqefm76vo5+nm6u" +
                        "wSF6vm7/TjEwfPY9c9rHW1fvIq+3rta5M+c3BxlcMX7wtEjPy1fH0/di6aot" +
                        "QR+8dAu1vM6c0Q77cE7/5Mkzj58+esrEEODNzrFeWHO9JdACcyAOwGm9EBCX" +
                        "pBaX6Bdkp+u75efrJeckFhfnBvrmX3YQqN3t9+aIr/TypYq890WWbGGf46ag" +
                        "uELLcWnXAgahrUUnMyfvSJx9pHvT7K7F9dz/G4SF2FMKn7grps2VC+jmz7+3" +
                        "p9L48cb6/z8/Ch8ItklbMSNi9ofPJULzNJ42Gm7hWyy+t7hQ8dBMD7WH7Q8r" +
                        "bYz0+9d58xwMPevceNB2SqeJO49f9vr4Ru7z7oKqbU0/VUwPu72/P9fkmGVw" +
                        "V+Lu0tqjcfOPbV2koX7GIOffe9OsvSuNjGe2vE9SPnaI49KPP4cWSs9s3cR6" +
                        "oUHzZfySs7eOHGPnyd+05d1ntu+brzxvbBVSP5Sbk/KybuGUpYyvP5jVx/75" +
                        "evVp4+cybQ2Tyt03Cpm3n5yfnqlXXRbXHuZyJq/o5Qb14Kr7hzZKHee5Os/n" +
                        "0ffTPw3iXJIsut8qZqmk1W6cYLZf5PTyokkcm2MEvvtfLTLs8l+i+6YmRs3t" +
                        "3ON9VybNWT/JRTJxiZcJ34GXr2+13z66oL6jekrrt4Suo3/fa9SfPv2d827M" +
                        "kwqHiEjJB8m9Sb2fSlfdeJa+s11v560XT3q/hn7cdHGTFtOh9p3VzTHazxLM" +
                        "+8s/djwytMzaklfMsLx7rV5eycZeq8A7bxY1zmWc4Sa8nU0jc03mnWs7Fj/M" +
                        "DCkKuhrxnxuUFpb0Ryw4w8jAsJsJX1qQBmJ4UsxNzMzTy84vycnMi8/NTynN" +
                        "SU1OSEhIA2KWJD82jYCkC0kM4HT2VWnPXmGgTglwOmNkEmFAmI6cBkEJHRXg" +
                        "SvbopiC7XgjFhHqsqRddP7ILpVH0dzPh9XGANysbSBkzEJ4H0s+ZQDwAzcJc" +
                        "6cwDAAA="
                )
        ) {
            val fooClass = codebase.assertClass("test.pkg.Foo")
            fooClass.assertMethod("foo", "java.lang.Object")
            assertThat(fooClass.methods()).hasSize(1)
        }
    }

    @Test
    fun `Test function of nested class using value class type`() {
        runCodebaseTest(
            inputSet(
                kotlin(
                    """
                        package test.pkg
                        @JvmInline
                        value class IntValue(val intValue: Int)

                        class Foo {
                            class Bar {
                                fun foobar() = IntValue(0)
                            }
                        }
                        """,
                )
            ),
            // Compiled from the Kotlin source file above with [generateBase64gzipFromKotlin]
            compiledSourceJar =
                base64gzip(
                    "test.jar",
                    // kotlinc version info: kotlinc-jvm 1.9.23 (JRE 17.0.6+10-b802.1)
                    "" +
                        "H4sIAAAAAAAA/31WeTQUahsf+0y2MNdYqgmZpmFmGjtxs2SSuaMZocUSE4MJ" +
                        "kwzXklvWylguiqJRQrlhZAkzQsiIcK3dMfZwy5X1utaMT91zvk+dr573PH+8" +
                        "57zP73nPs/zOj4ATEgYDgEAgAABQAew0MEAYgLe0M0OesMGi8WY2J7CWp+xQ" +
                        "eCy/FQD4G9/2+iccEtUliUMiOtp+L7M93Ks9OnkZZY3XPIHvCnxSbjtvjfRH" +
                        "WLe1aTjMd6BbWtrGJ99OCgIIODFgsdyhYsPtBPrbTvhmesi20zwCaOhLFz3R" +
                        "J/xoDm4+gR6oCz5uAQHhdkOnlO3BW0OZxe3kiMxO29IHyxYD0UmwpnMF4FzC" +
                        "RYgFMZ8Yh8ZxQeItMLL+T9CQnroXYudpS/WQfTRsRIm2VLf+Fqx5YbAd5mbO" +
                        "ucvY8J9d2+xg1QXM8jfnoFcFq+hoxZSsyVDWuouhp4FLV6FOkYJCFFd3pd2p" +
                        "idbK6ityLpho2k2yuoUsnuHsbnWXFG2aVbiTrSKqLpVoYMEdKHGOHWuIAZZJ" +
                        "CLn4Dl3k2oTwmG80JNK1sRWdCf1GanUaNRVNj5z7K5QwEphjUG7iTPvGXbDu" +
                        "gWRblcNtv1KOt5anZp66dBdArvEzHJDouUuvR3GkSe2UqkjiCQZZYS79fUgz" +
                        "ZsoAtTedP7IQOLUqCuKmRY7ZPkoCKjHH2Es1ZhkZCZddGgGi9rJrsKmqC+wj" +
                        "uGe+Gp2Yn6aIxBRn+ZQGBK4eBrcbcGwElyJgmCIVtdN7DBHF/9j3FFYJ9Flx" +
                        "4EtB9T7LhlE8WgUFfQUtSVVDdP2sqKpLbab8ugoh5l6hn5NnQ3+LPXIn2Xiu" +
                        "kkmwk0mLAedljIcmkFsKdCPLbwX5HG30uT+bwpg3TTHJTRv07Oxw1jgZQpaw" +
                        "cIn6Y+6VW2zwrjDdYtIR1tAmxS+kjmJQnuyc4QMtPJPF8g2F9QcHHj2WdL1F" +
                        "BSVGuGVSWvd0MlFw3leVP7U0ym49zmptmoqwhkLiWm8D20EZOTrS3W23y4oh" +
                        "2JPS5OAspFTew8hpY6jRBkbJoUG1tFA8ZnlB0TI/NPjWZH/q6ZEKGzlzR3mS" +
                        "aWhqwlqFmTkQc0yvq0TdkoLsd7OHy5kUHrP88EGZ4/GEFs42fYgcnjFJQux3" +
                        "jHbLN0UY+zpUFnK0AwpILd1vrMl5Xno+LOl4oemsszxHzHS9i3OxrrL6dAlS" +
                        "f+rZo/F++wKvaj3Gj7R7GZ6heva3ER2/vdIKtebreqmLQ8xuaVryJnOf6hyH" +
                        "/RIVaiWe+Lt3/Dpnd+28QK2QbPlDyqufZa6NqFND0n4BlfjH435LVnu/LuMi" +
                        "16/mZNKQWbeaXH3A+/nkuxcTF8Wt/EQV4VpRcoN/NcEZL5ZiBdsSgt6sg/yg" +
                        "ywBVxv5TjIvLwi0rJLbZ+5vCd+zZDB+98EX0/Nxu0F6brEjoGMMw/GMYS7Jy" +
                        "ifcAI7pvSywK2SqjJatpZcGcTbbTitgIRu9PD0x28b/bOQEiVys+G13Yr8Za" +
                        "HIt8DYG8eCdUZeQfz7pZMpLN51UGIVHPGvPW8S9uBNSq/dlnqsxcedLgiFTr" +
                        "XgWIR6wgSWmeC+mjyusY+bDpH5pTpK1b6xyoNTxnzjrNQSlp3u33Tdl1rvSN" +
                        "mKtYg94fjpiT0Pf+QEQ0R/SCrkyMJI2z5viKet7D2X1yGZKRKKqq++gFIREh" +
                        "jWBrXKHSqvB9v0jWmcZ1WKyzFyqLdWNt/Dm9kH6Dnk/fqJ3cV6MB0UY9cnJ3" +
                        "cvo7QQgluiX0iX0Uz0c/NxQGAJbEvsc+sjvZB0ul/ks8eAIep0wEm6zgtbr8" +
                        "dYiOcnUDbxvYl+x2iWhE7mowtSO0wArSJ5Jr3RcftYXJpK8hN8WYwhBZ9ock" +
                        "RE3OWfeAWcN3QxuzzSNX0cWA2jFtFVor+/3pOmXnR96gY/lLE1Hxst7sLnzB" +
                        "Yx5TOGn0vlfO0KH6gU0ekBgNfXBNNnv0FwruTP7Jq0tRT8/MPUZ1wdi2NkG5" +
                        "qR9u1kc1Jxy63r/H9MGPG0lGp8XajK5Ho8eJCjcygMc8jHe9jM9POH787bXE" +
                        "liNo/toMPe6VdhK8eKXDg2Nx8ucRt4JoJTeUHlMlNZs3dyRrSXUGbs5iJrD3" +
                        "SZ1DVKcXRTPyQnlpJyzIRn/IaccnEPstVYvsn5+/lC5VVsHuXRv4a6bTwIQg" +
                        "6at78L5iqtEUjaIppbKKSu1b1MYwy+KGdQiDYZw2ObkISezW0EvYBYOxMqBB" +
                        "KJckwXWcHESMLzv2Kf3o/7GDv+ddLD4Y8+cFzZfwnAYwHZP2dFD47JUXzbBd" +
                        "8hFNcTFU4qb0p+bZe8duhAsAABsC32ue/FfNO2DudvnfBvoSzaiyZmA+WT5o" +
                        "KnLG9ol5H+UZorc3hpPJgeMzCrP77BpjohRdC0+lDZE9vYxlt25m1QSXLrgC" +
                        "hz8SOZUPsAUBq+Rh/ZGV4ef6I7VX+YKj+2YPB2Mp41Bpqc0hg2GvuJcCastW" +
                        "94BHwV3ZW+VhB8I4EU2uRwnJWtKKhZQiZ1P5CrrEntj4kCKdxsvTPqUbEvTi" +
                        "t1UaKUVRQZULpXX62mISTSPd+7V8ZRJzowLsqiDHeQdysm6mInWNpQ6F6OaL" +
                        "iGjWB5TcUD3rGMWNRiyu5MqEuwnbjzXK0c+RDmnmAkT19vMTrHOMGOuSsZ6p" +
                        "C3GvXw9ODrt/WNxzX8zQJSu+MoShW9azoreympriWbQY+uf0ocx4kELXXac3" +
                        "ZY+pIVlL0356+uGVDp7ew75pDmXvwrVundsqx0prfjRUEpvWuU5+0rHVPbhX" +
                        "4a/Ag7O42Ly0uAm+F/d2i3iXpIcRr+pgQ4WVndLWAXEzcIv7QfA5nFLdhvE+" +
                        "dRNWQXd1az1ciwIfsNaQPZr4kAvM8WJ7XeupzKP3VE9BQFTbicfaOSbVayBW" +
                        "Utm0Y/RyfLl/5tPDEFcB1IzlhGfbefI0+DW04w5j6w7SDuT81oA5LorIb3dS" +
                        "5uZuyiyodzNpriIG0FHza3R4j8xtQEFVr0BPVbzGXgMlJicjtsjffjwD65Iz" +
                        "/3n7l+6BeJvbwzMh+L0BUt72/0ofXzdvP9RFKs3H2++8L9U90MfjgqurK3nb" +
                        "hUk2onACqZME+Kxr/lF9XiO3HanwWdcICIIB/0PfqXk+Casv7Vsy62uUndwF" +
                        "+QLh6rfV0tcgO3dI9guQTeH/R3pfx+8sofwX8WzRb+3d1xg7S6X8BcYG8Lul" +
                        "J+BERD89E9k+Z7Y/cRr06fYfVDQfO8UKAAA="
                )
        ) {
            val barClass = codebase.assertClass("test.pkg.Foo.Bar")

            val mangledMethod = barClass.assertMethod("foobar-RVb1_dM", "")
            assertThat(mangledMethod.targetLanguages).containsExactly(TargetLanguage.BYTECODE)
            mangledMethod.returnType().assertPrimitiveTypeItem {
                assertThat(kind).isEqualTo(PrimitiveTypeItem.Primitive.INT)
            }

            // TODO(b/407735992): non-mangled method should be kotlin only and have IntValue return
            // type instead of int
            barClass.assertMethod("foobar", "")

            assertThat(barClass.methods()).hasSize(2)
        }
    }
}
