plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

// Version number comes from the GitHub build number (-Pvc=123)
val vc = (project.findProperty("vc") as String?)?.toIntOrNull() ?: 1

android {
    namespace = "com.taiful.tfvoicetyper"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.taiful.tfvoicetyper"
        minSdk = 26
        targetSdk = 35
        versionCode = vc
        versionName = "1.0.$vc"
        // The offline voice engine ships for these phone types (covers practically every Android phone in use)
        ndk { abiFilters += listOf("arm64-v8a", "armeabi-v7a") }
    }

    signingConfigs {
        // Permanent key (later): supplied only by the GitHub build from repository secrets.
        // Nothing secret is ever stored in this repository.
        create("permanent") {
            val ks = System.getenv("TF_KEYSTORE")
            if (ks != null) {
                storeFile = file(ks)
                storePassword = System.getenv("TF_KEY_PASSWORD")
                keyAlias = System.getenv("TF_KEY_ALIAS") ?: "tfvoicetyper"
                keyPassword = System.getenv("TF_KEY_PASSWORD")
            }
        }
        // Temporary test key: a debug key kept only on the build server (never in GitHub).
        create("test") {
            val ks = System.getenv("TF_TEST_KEYSTORE")
            if (ks != null) {
                storeFile = file(ks)
                storePassword = "android"
                keyAlias = "androiddebugkey"
                keyPassword = "android"
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = when {
                System.getenv("TF_KEYSTORE") != null -> signingConfigs.getByName("permanent")
                System.getenv("TF_TEST_KEYSTORE") != null -> signingConfigs.getByName("test")
                else -> signingConfigs.getByName("debug")
            }
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
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
        // Keep the app download small: the offline engine files are stored compressed inside the APK
        jniLibs.useLegacyPackaging = true
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    val bom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(bom)
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-service:2.8.7")
    implementation("androidx.savedstate:savedstate-ktx:1.2.1")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.animation:animation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("androidx.datastore:datastore-preferences:1.1.1")
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    // Free Bangla → English translation inside the phone (used in no-key mode)
    implementation("com.google.mlkit:translate:17.0.3")
    // Offline Bangla voice engine (sherpa-onnx, Apache-2.0). The official file is fetched and
    // fingerprint-checked by the GitHub build (see .github/workflows/build-apk.yml); it is not stored in this repo.
    implementation(files("libs/sherpa-onnx.aar"))
}
