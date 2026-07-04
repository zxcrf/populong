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

rootProject.name = "BubbleShooter"

include(":core")

// The dev container has no Android SDK; :app only participates where one exists
// (CI, or a workstation with local.properties/ANDROID_HOME). `./gradlew test`
// then builds and tests :core alone without ever resolving AGP.
val hasAndroidSdk = System.getenv("ANDROID_HOME") != null ||
    System.getenv("ANDROID_SDK_ROOT") != null ||
    (runCatching { file("local.properties").readText() }.getOrNull()?.contains("sdk.dir") == true)
if (hasAndroidSdk) include(":app")
