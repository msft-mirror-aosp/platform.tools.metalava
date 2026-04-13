pluginManagement {
    repositories {
        // For CI builds, it is important to use prebuilts because maven central throttles requests
        // for artifacts. The `DOWNLOAD_DEPENDENCIES` flag allows building locally in repos which do
        // not include the metalava prebuilts repo.
        if (System.getenv("DOWNLOAD_DEPENDENCIES") == "true") {
            // Prefer mavenCentral as that has signed artifacts
            mavenCentral()
            gradlePluginPortal()
        } else {
            // Use custom repo path if provided (necessary for androidx_with_metalava).
            maven(System.getenv("METALAVA_PREBUILTS_REPO") ?: "../../prebuilts/metalava")
        }
    }
}

dependencyResolutionManagement {
    versionCatalogs {
        create("libs") {
            from(files("../gradle/libs.versions.toml"))
        }
    }
}