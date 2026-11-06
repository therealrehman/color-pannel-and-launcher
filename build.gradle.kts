buildscript {
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://developer.huawei.com/repo/") }
    }
    dependencies {
        // Huawei AppGallery Connect plugin. The app applies it only when agconnect-services.json exists (see app/build.gradle.kts).
        classpath("com.huawei.agconnect:agcp:1.6.0.300")
    }
}

plugins {
    id("com.android.application") version "8.7.3" apply false
    id("org.jetbrains.kotlin.android") version "2.0.21" apply false
}
