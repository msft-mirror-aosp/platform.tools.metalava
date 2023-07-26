import com.android.tools.metalava.CREATE_ARCHIVE_TASK
import com.android.tools.metalava.CREATE_BUILD_INFO_TASK
import java.io.FileInputStream
import java.io.FileNotFoundException
import java.util.Properties

/** Take a copy of the original [buildDir] for use by [testPrebuiltsSdkDir] */
val originalBuildDir = buildDir

buildDir = getBuildDirectory()

defaultTasks =
    mutableListOf(
        "installDist",
        "test",
        CREATE_ARCHIVE_TASK,
        CREATE_BUILD_INFO_TASK,
        "lint",
        "ktfmtCheck",
    )

plugins {
    id("application")
    id("org.jetbrains.kotlin.jvm")
    id("maven-publish")
    id("metalava-build-plugin")
}

version = getMetalavaVersion()

application {
    mainClass.set("com.android.tools.metalava.Driver")
    applicationDefaultJvmArgs = listOf("-ea", "-Xms2g", "-Xmx4g")
}

dependencies {
    implementation(libs.androidToolsExternalUast)
    implementation(libs.androidToolsExternalKotlinCompiler)
    implementation(libs.androidToolsExternalIntellijCore)
    implementation(libs.androidLintApi)
    implementation(libs.androidLintChecks)
    implementation(libs.androidLintGradle)
    implementation(libs.androidLint)
    implementation(libs.androidToolsCommon)
    implementation(libs.androidToolsSdkCommon)
    implementation(libs.androidToolsSdklib)
    implementation("com.github.ajalt.clikt:clikt-jvm:3.5.3")
    implementation(libs.kotlinStdlib)
    implementation(libs.kotlinReflect)
    implementation("org.ow2.asm:asm:8.0")
    implementation("org.ow2.asm:asm-tree:8.0")
    implementation("com.google.guava:guava:31.0.1-jre")
    implementation("com.google.code.gson:gson:2.8.9")
    testImplementation(libs.androidLintTests)
    testImplementation("junit:junit:4.13.2")
    testImplementation("com.google.truth:truth:1.1.3")
    testImplementation(libs.kotlinTest)
}

/**
 * The location into which a fake representation of the prebuilts/sdk directory will be written.
 *
 * This uses [originalBuildDir] rather than [buildDir] as the latter depends on the `OUT_DIR`
 * environment variable. As this value is passed in to the tests this would make the tests dependent
 * on the `OUT_DIR` which as it varies from build to build would reduce the benefit of remote
 * caching.
 */
val testPrebuiltsSdkDir = originalBuildDir.resolve("prebuilts/sdk")

/**
 * Register tasks to emulate parts of the prebuilts/sdk repository using source from this directory.
 *
 * [sourceDir] is the path to the root directory of code that is compiled into a jar that
 * corresponds to the jar specified by [destJar].
 *
 * [destJar] is the path to a jar within prebuilts/sdk. The fake jar created from [sourceDir] is
 * copied to getBuildDirectory()/prebuilts/sdk/$destJar.
 *
 * The jars created by this can be accessed by tests via the `METALAVA_TEST_PREBUILTS_SDK_ROOT`
 * environment variable which is set to point to getBuildDirectory()/prebuilts/sdk; see [testTask].
 */
fun registerTestPrebuiltsSdkTasks(sourceDir: String, destJar: String): TaskProvider<Jar> {
    val basename = sourceDir.replace("/", "-")
    val javaCompileTaskName = "$basename.classes"
    val jarTaskName = "$basename.jar"

    val compileTask =
        project.tasks.register(javaCompileTaskName, JavaCompile::class) {
            options.compilerArgs = listOf("--patch-module", "java.base=" + file(sourceDir))
            source = fileTree(sourceDir)
            classpath = project.files()
            destinationDirectory.set(originalBuildDir.resolve(javaCompileTaskName))
        }

    val destJarFile = File(destJar)
    val dir = destJarFile.parent
    val filename = destJarFile.name
    if (dir == ".") {
        throw IllegalArgumentException("bad destJar argument '$destJar'")
    }

    val jarTask =
        project.tasks.register(jarTaskName, Jar::class) {
            from(compileTask.flatMap { it.destinationDirectory })
            archiveFileName.set(filename)
            destinationDirectory.set(testPrebuiltsSdkDir.resolve(dir))
        }

    return jarTask
}

val testPrebuiltsSdkApi30 =
    registerTestPrebuiltsSdkTasks("src/testdata/prebuilts-sdk-test/30", "30/public/android.jar")
val testPrebuiltsSdkApi31 =
    registerTestPrebuiltsSdkTasks("src/testdata/prebuilts-sdk-test/31", "31/public/android.jar")
val testPrebuiltsSdkExt1 =
    registerTestPrebuiltsSdkTasks(
        "src/testdata/prebuilts-sdk-test/extensions/1",
        "extensions/1/public/framework-ext.jar"
    )
val testPrebuiltsSdkExt2 =
    registerTestPrebuiltsSdkTasks(
        "src/testdata/prebuilts-sdk-test/extensions/2",
        "extensions/2/public/framework-ext.jar"
    )
val testPrebuiltsSdkExt3 =
    registerTestPrebuiltsSdkTasks(
        "src/testdata/prebuilts-sdk-test/extensions/3",
        "extensions/3/public/framework-ext.jar"
    )

project.tasks.register("test-sdk-extensions-info.xml", Copy::class) {
    from("src/testdata/prebuilts-sdk-test/sdk-extensions-info.xml")
    into(testPrebuiltsSdkDir)
}

project.tasks.register("test-prebuilts-sdk") {
    dependsOn(testPrebuiltsSdkApi30)
    dependsOn(testPrebuiltsSdkApi31)
    dependsOn(testPrebuiltsSdkExt1)
    dependsOn(testPrebuiltsSdkExt2)
    dependsOn(testPrebuiltsSdkExt3)
    dependsOn("test-sdk-extensions-info.xml")
}

tasks.named<Test>("test").configure {
    dependsOn("test-prebuilts-sdk")
    setEnvironment("METALAVA_TEST_PREBUILTS_SDK_ROOT" to testPrebuiltsSdkDir)
}

fun getMetalavaVersion(): Any {
    val versionPropertyFile = File(projectDir, "src/main/resources/version.properties")
    if (versionPropertyFile.canRead()) {
        val versionProps = Properties()
        versionProps.load(FileInputStream(versionPropertyFile))
        return versionProps["metalavaVersion"]
            ?: throw IllegalStateException(
                "metalava version was not set in ${versionPropertyFile.absolutePath}"
            )
    } else {
        throw FileNotFoundException("Could not read ${versionPropertyFile.absolutePath}")
    }
}

fun getBuildDirectory(): File {
    return if (System.getenv("OUT_DIR") != null) {
        File(System.getenv("OUT_DIR"), "metalava")
    } else {
        File(projectDir, "../../out/metalava")
    }
}
