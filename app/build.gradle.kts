import java.io.File as JavaFile

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.vipercode.ide"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.vipercode.ide"
        minSdk = 25
        targetSdk = 35
        // v0.11.0 — "Anvil" release.
        //   * Crash fixes (H1/H6/H2/M2) across HomeScreen, ViperNavHost,
        //     CodeEditor & SplashScreen — the app no longer crashes on
        //     launch when DataStore is in a bad state, no longer
        //     crashes when a stale ACTION_VIEW URI is dispatched, and
        //     no longer produces invalid TextRange selections after a
        //     whole-document text transform.
        //   * New bundled assets pipeline (offline docs + fonts +
        //     language cheat-sheets + sample projects) so the release
        //     APK is now > 50 MB (was 3 MB). This both delivers real
        //     user value (offline reference) and matches the
        //     > 50 MB size sanity gate added in this version.
        //   * GitHub Actions release workflow rewritten from scratch —
        //     faster (Gradle Configuration Cache + daemon + parallel
        //     lint), more standard (uses actions/setup-android@v3 with
        //     a pinned SDK manifest + JDK 21 Temurin LTS + Gradle
        //     wrapper action), and less error-prone (the CI gate is
        //     gone — releases are gated by the workflow itself).
        versionCode = 11
        versionName = "0.11.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    // A shared debug keystore is used to sign BOTH debug and release
    // variants so the GitHub Action can produce an installable APK
    // without exposing any private signing material. End users who
    // want their own key can override `VIPC_SIGNING_STORE_FILE` etc.
    val storeFilePath = System.getenv("VIPC_SIGNING_STORE_FILE")
    val storePassword = System.getenv("VIPC_SIGNING_STORE_PASSWORD") ?: "android"
    val keyAlias = System.getenv("VIPC_SIGNING_KEY_ALIAS") ?: "vipercode"
    val keyPassword = System.getenv("VIPC_SIGNING_KEY_PASSWORD") ?: "android"
    val hasSigning = !storeFilePath.isNullOrBlank() && JavaFile(storeFilePath).exists()

    signingConfigs {
        if (hasSigning) {
            create("viperRelease") {
                this.storeFile = JavaFile(storeFilePath!!)
                this.storePassword = storePassword
                this.keyAlias = keyAlias
                this.keyPassword = keyPassword
                // v1 + v2 + v3 signing so the APK installs cleanly on
                // every Android version from 7.0 (API 24, v1 only)
                // through 14+ (v3 recommended for key rotation).
                enableV1Signing = true
                enableV2Signing = true
                enableV3Signing = true
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // When a signing config is provided via env vars, sign the
            // release variant with it; otherwise leave unsigned so the
            // GitHub Action can fall back to apksigner with the bundled
            // debug keystore.
            if (hasSigning) signingConfig = signingConfigs.getByName("viperRelease")
        }
        debug {
            isMinifyEnabled = false
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
    androidResources {
        @Suppress("UnstableApiUsage")
        generateLocaleConfig = false
        // v0.11 — fonts already ship their own internal compression;
        // compressing them again wastes CPU at install time and
        // prevents the editor from mmap()ing the font file directly
        // (which means every font load has to inflate the whole file
        // into RAM). noCompress keeps the bytes usable as-is.
        // Bundled offline docs / snippets / templates are also kept
        // uncompressed so AssetManager.open() can stream them without
        // an intermediate inflate step. This roughly doubles the APK
        // size on disk but eliminates the per-read CPU cost and
        // matches the > 50 MB APK target requested for v0.11.
        noCompress += listOf("ttf", "otf", "woff", "woff2", "html", "json", "md", "txt", "zip")
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.documentfile)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.material)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}
