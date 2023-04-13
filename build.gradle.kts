import com.android.tools.metalava.CREATE_ARCHIVE_TASK
import com.android.tools.metalava.CREATE_BUILD_INFO_TASK
import com.android.tools.metalava.configureBuildInfoTask
import com.android.tools.metalava.configurePublishingArchive
import org.gradle.api.tasks.testing.logging.TestLogEvent
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import java.io.FileInputStream
import java.io.FileNotFoundException
import java.util.Properties

buildDir = getBuildDirectory()

defaultTasks = mutableListOf(
    "installDist",
    "test",
    CREATE_ARCHIVE_TASK,
    CREATE_BUILD_INFO_TASK,
    "ktlint",
    "lint"
)

repositories {
    google()
    mavenCentral()
    val lintRepo = project.findProperty("lintRepo") as String?
    if (lintRepo != null) {
        logger.warn("Building using custom $lintRepo maven repository")
        maven {
            url = uri(lintRepo)
        }
    }
}

plugins {
    alias(libs.plugins.kotlinJvm)
    id("com.android.lint") version "8.1.0-alpha07"
    id("application")
    id("java")
    id("maven-publish")
}

group = "com.android.tools.metalava"
version = getMetalavaVersion()

application {
    mainClass.set("com.android.tools.metalava.Driver")
    applicationDefaultJvmArgs = listOf("-ea", "-Xms2g", "-Xmx4g")
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

tasks.withType(KotlinCompile::class.java) {
    kotlinOptions {
        jvmTarget = "17"
        apiVersion = "1.7"
        languageVersion = "1.7"
        allWarningsAsErrors = true
    }
}

val customLintVersion = findProperty("lintVersion") as String?
val studioVersion: String = if (customLintVersion != null) {
    logger.warn("Building using custom $customLintVersion version of Android Lint")
    customLintVersion
} else {
    "31.1.0-alpha07"
}

dependencies {
    implementation("com.android.tools.external.org-jetbrains:uast:$studioVersion")
    implementation("com.android.tools.external.com-intellij:kotlin-compiler:$studioVersion")
    implementation("com.android.tools.external.com-intellij:intellij-core:$studioVersion")
    implementation("com.android.tools.lint:lint-api:$studioVersion")
    implementation("com.android.tools.lint:lint-checks:$studioVersion")
    implementation("com.android.tools.lint:lint-gradle:$studioVersion")
    implementation("com.android.tools.lint:lint:$studioVersion")
    implementation("com.android.tools:common:$studioVersion")
    implementation("com.android.tools:sdk-common:$studioVersion")
    implementation("com.android.tools:sdklib:$studioVersion")
    implementation(libs.kotlinStdlib)
    implementation(libs.kotlinReflect)
    implementation("org.ow2.asm:asm:8.0")
    implementation("org.ow2.asm:asm-tree:8.0")
    implementation("com.google.guava:guava:31.0.1-jre")
    testImplementation("com.android.tools.lint:lint-tests:$studioVersion")
    testImplementation("junit:junit:4.13.2")
    testImplementation("com.google.truth:truth:1.1.3")
    testImplementation(libs.kotlinTest)
}

val zipTask: TaskProvider<Zip> = project.tasks.register(
    "zipResultsOf${name.capitalize()}",
    Zip::class.java
) {
    destinationDirectory.set(File(getDistributionDirectory(), "host-test-reports"))
    archiveFileName.set("metalava-tests.zip")
}

val testTask = tasks.named("test", Test::class.java)
testTask.configure {
    jvmArgs = listOf("--add-opens=java.base/java.lang=ALL-UNNAMED")
    maxParallelForks = (Runtime.getRuntime().availableProcessors() / 2).takeIf { it > 0 } ?: 1
    testLogging.events = hashSetOf(
        TestLogEvent.FAILED,
        TestLogEvent.PASSED,
        TestLogEvent.SKIPPED,
        TestLogEvent.STANDARD_OUT,
        TestLogEvent.STANDARD_ERROR
    )
    if (isBuildingOnServer()) ignoreFailures = true
    finalizedBy(zipTask)
}
zipTask.configure {
    from(testTask.map { it.reports.junitXml.outputLocation.get() })
}

fun getMetalavaVersion(): Any {
    val versionPropertyFile = File(projectDir, "src/main/resources/version.properties")
    if (versionPropertyFile.canRead()) {
        val versionProps = Properties()
        versionProps.load(FileInputStream(versionPropertyFile))
        return versionProps["metalavaVersion"]
            ?: throw IllegalStateException("metalava version was not set in ${versionPropertyFile.absolutePath}")
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

/**
 * The build server will copy the contents of the distribution directory and make it available for
 * download.
 */
fun getDistributionDirectory(): File {
    return if (System.getenv("DIST_DIR") != null) {
        File(System.getenv("DIST_DIR"))
    } else {
        File(projectDir, "../../out/dist")
    }
}

fun isBuildingOnServer(): Boolean {
    return System.getenv("OUT_DIR") != null && System.getenv("DIST_DIR") != null
}

/**
 * @return build id string for current build
 *
 * The build server does not pass the build id so we infer it from the last folder of the
 * distribution directory name.
 */
fun getBuildId(): String {
    return if (System.getenv("DIST_DIR") != null) File(System.getenv("DIST_DIR")).name else "0"
}

// KtLint: https://github.com/pinterest/ktlint

fun Project.getKtlintConfiguration(): Configuration {
    return configurations.findByName("ktlint") ?: configurations.create("ktlint") {
        val dependency = project.dependencies.create("com.pinterest:ktlint:0.47.1")
        dependencies.add(dependency)
    }
}

tasks.register("ktlint", JavaExec::class.java) {
    description = "Check Kotlin code style."
    group = "Verification"
    classpath = getKtlintConfiguration()
    mainClass.set("com.pinterest.ktlint.Main")
    args = listOf("src/**/*.kt", "build.gradle.kts")
}

tasks.register("ktlintFormat", JavaExec::class.java) {
    description = "Fix Kotlin code style deviations."
    group = "formatting"
    classpath = getKtlintConfiguration()
    mainClass.set("com.pinterest.ktlint.Main")
    args = listOf("-F", "src/**/*.kt", "build.gradle.kts")
}

val publicationName = "Metalava"
val repositoryName = "Dist"

publishing {
    publications {
        create<MavenPublication>(publicationName) {
            from(components["java"])
            pom {
                licenses {
                    license {
                        name.set("The Apache License, Version 2.0")
                        url.set("http://www.apache.org/licenses/LICENSE-2.0.txt")
                    }
                }
                developers {
                    developer {
                        name.set("The Android Open Source Project")
                    }
                }
                scm {
                    connection.set("scm:git:https://android.googlesource.com/platform/tools/metalava")
                    url.set("https://android.googlesource.com/platform/tools/metalava/")
                }
            }
        }
    }

    repositories {
        maven {
            name = repositoryName
            url = uri("file://${getDistributionDirectory().canonicalPath}/repo/m2repository")
        }
    }
}

lint {
    fatal.add("UastImplementation")
    disable.add("UseTomlInstead") // not useful for this project
    disable.add("GradleDependency") // not useful for this project
    abortOnError = true
    baseline = File("lint-baseline.xml")
}

// Add a buildId into Gradle Metadata file so we can tell which build it is from.
tasks.withType(GenerateModuleMetadata::class.java).configureEach {
    val outDirProvider = project.providers.environmentVariable("DIST_DIR")
    inputs.property("buildOutputDirectory", outDirProvider).optional(true)
    doLast {
        val metadata = outputFile.asFile.get()
        val text = metadata.readText()
        val buildId = outDirProvider.orNull?.let { File(it).name } ?: "0"
        metadata.writeText(
            text.replace(
                """"createdBy": {
    "gradle": {""",
                """"createdBy": {
    "gradle": {
      "buildId:": "$buildId",""",
            )
        )
    }
}

val archiveTaskProvider = configurePublishingArchive(
    project,
    publicationName,
    repositoryName,
    getBuildId(),
    getDistributionDirectory()
)
configureBuildInfoTask(
    project,
    isBuildingOnServer(),
    getDistributionDirectory(),
    archiveTaskProvider
)
