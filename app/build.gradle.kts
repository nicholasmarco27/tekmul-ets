plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.example.aremotionfilters"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.aremotionfilters"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }

    buildFeatures {
        viewBinding = true
    }
}

// It's common to define versions at the top of the script or in gradle.properties / libs.versions.toml
val cameraxVersion = "1.3.1" // Use the latest stable version
val mlkitFaceDetectionVersion = "17.1.0"
val activityKtxVersion = "1.8.0"
val fragmentKtxVersion = "1.6.2"
val coroutinesVersion = "1.7.3"

dependencies {
    implementation(libs.appcompat) // Assuming libs.appcompat is from your version catalog
    implementation(libs.material)  // Assuming libs.material is from your version catalog
    implementation(libs.activity)   // Assuming libs.activity is from your version catalog
    implementation(libs.constraintlayout) // Assuming libs.constraintlayout is from your version catalog
    testImplementation(libs.junit) // Assuming libs.junit is from your version catalog
    androidTestImplementation(libs.ext.junit) // Assuming libs.ext.junit is from your version catalog
    androidTestImplementation(libs.espresso.core) // Assuming libs.espresso.core is from your version catalog

    // CameraX
    implementation("androidx.camera:camera-core:$cameraxVersion")
    implementation("androidx.camera:camera-camera2:$cameraxVersion")
    implementation("androidx.camera:camera-lifecycle:$cameraxVersion")
    implementation("androidx.camera:camera-view:$cameraxVersion")

    // ML Kit Face Detection
    implementation("com.google.android.gms:play-services-mlkit-face-detection:$mlkitFaceDetectionVersion")

    // For easier permission handling (optional, but good practice)
    implementation("androidx.activity:activity-ktx:$activityKtxVersion")
    implementation("androidx.fragment:fragment-ktx:$fragmentKtxVersion")

    // Coroutines for background tasks (ML Kit processing)
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:$coroutinesVersion")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:$coroutinesVersion")
}