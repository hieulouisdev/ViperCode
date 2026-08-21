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
        versionCode = 3
        versionName = "0.0.3"

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
                enableV1Signing = true
                enableV2Signing = true
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
