pluginManagement {
    repositories {
        // Prefer mavenCentral as that has signed artifacts
        mavenCentral()
        gradlePluginPortal()
        google()
    }
}

dependencyResolutionManagement {
    versionCatalogs {
        create("libs") {
            val lintOverride = System.getenv("LINT_VERSION")
            if (lintOverride != null) {
                logger.warn("Building using custom $lintOverride version of Android Lint.")
                version("androidLint", lintOverride)
            }
        }
    }
}

if (!System.getenv("INTEGRATION").isNullOrBlank()) {
    include(":integration")
}
include(":stub-annotations")