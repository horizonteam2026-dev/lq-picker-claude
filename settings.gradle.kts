pluginManagement {
    repositories {
        google()
        // Bản sao Maven Central do Google lưu trữ: tránh lỗi 429 (quá nhiều yêu cầu) của Maven Central.
        maven("https://maven-central.storage-download.googleapis.com/maven2/")
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        maven("https://maven-central.storage-download.googleapis.com/maven2/")
        mavenCentral()
    }
}
rootProject.name = "LienQuanPicker"
include(":app")
