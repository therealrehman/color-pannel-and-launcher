import java.io.FileInputStream
import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// Huawei AppGallery Connect: applied only when agconnect-services.json is in the app folder (download it from AppGallery Connect,
// App information). Without the file the build still works, and the Huawei ads fall back to "unlock" (see ThemeUnlock).
if (file("agconnect-services.json").exists()) apply(plugin = "com.huawei.agconnect")

// Step 10d: release signing. release.sh creates keystore.properties (+ the .jks) the first time.
// Cloud builds can use the RELEASE_STORE_FILE / _PASSWORD / RELEASE_KEY_ALIAS / _PASSWORD environment variables instead.
val keystoreProps = Properties().apply {
    val f = rootProject.file("keystore.properties")
    if (f.exists()) FileInputStream(f).use { load(it) }
}
fun signingValue(name: String, env: String): String? =
    (keystoreProps.getProperty(name) ?: System.getenv(env))?.takeIf { it.isNotBlank() }
val releaseStoreFile: String? = signingValue("storeFile", "RELEASE_STORE_FILE")

// build.sh passes the newest installed SDK platform (33..36) as -Poneui.sdk=NN
val sdkApi: Int = (findProperty("oneui.sdk") as String?)?.toIntOrNull() ?: 34

android {
    namespace = "com.oklabs.oneui9launcher"
    compileSdk = sdkApi

    defaultConfig {
        applicationId = "com.oklabs.oneui9launcher"
        minSdk = 26                       // Android 8.0 and newer
        targetSdk = minOf(sdkApi, 36)   // Google Play: new apps and updates must target API 36
        versionCode = 207
        versionName = "1.1.7"
    }

    signingConfigs {
        create("release") {
            if (releaseStoreFile != null) {
                storeFile = rootProject.file(releaseStoreFile)
                storePassword = signingValue("storePassword", "RELEASE_STORE_PASSWORD")
                keyAlias = signingValue("keyAlias", "RELEASE_KEY_ALIAS")
                keyPassword = signingValue("keyPassword", "RELEASE_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            // Step 10d: shrunk (unused code and resources removed) and signed. Without a keystore the APK stays unsigned.
            isMinifyEnabled = true
            isShrinkResources = true
            isDebuggable = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = if (releaseStoreFile != null) signingConfigs.getByName("release") else null
        }
    }

    // No dependency metadata blob inside the APK.
    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    lint {
        checkReleaseBuilds = false
        abortOnError = false
    }
}

// (Step 79a removed in Step 84.)
// everything else in the app still has no third-party library.
dependencies {
    // Step 84: the Unity LevelPlay SDK was removed: nothing in this app uses it any more (ads are Huawei Ads Kit only).
    // Lets the SDK read the Google advertising / app-set ID (used for ad targeting and fraud checks only).
    implementation("com.google.android.gms:play-services-appset:16.0.0")
    implementation("com.google.android.gms:play-services-ads-identifier:18.1.0")
    implementation("com.google.android.gms:play-services-basement:18.1.0")

    // Huawei Ads Kit (AppGallery build). Ads only load when HMS Core is on the phone; otherwise the app unlocks the gradients anyway.
    implementation("com.huawei.hms:ads-lite:13.4.54.302")
    implementation("com.huawei.agconnect:agconnect-core:1.9.1.301")
}
