buildscript {
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://developer.huawei.com/repo/") }
    }
    dependencies {
        // Huawei AppGallery Connect plugin. The app applies it only when agconnect-services.json exists (see app/build.gradle.kts).
        // agcp looks for the Android Gradle Plugin in the root buildscript classpath, so it must be listed here too (same version as the plugins block below).
        classpath("com.android.tools.build:gradle:8.7.3")
        classpath("com.huawei.agconnect:agcp:1.9.1.301")
    }
}

plugins {
    id("com.android.application") version "8.7.3" apply false
    id("org.jetbrains.kotlin.android") version "2.0.21" apply false
}
