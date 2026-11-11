rootProject.name = "Gemmory"

pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

include(":shared")
include(":androidApp")
// iOS (T59): the shared Compose UI; Stage 0 holds only the spike, and :androidApp doesn't depend on it yet.
include(":composeApp")
