plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}
android {
    namespace = "com.guideeye.app"
    compileSdk = 36
    defaultConfig {
        applicationId = "com.guideeye.app"
        minSdk = 26
        targetSdk = 36
        versionCode = 16
        versionName = "0.9.1-hrtf"
        // Galaxy A34 is ARM64; avoid shipping unused native runtimes.
        ndk { abiFilters += "arm64-v8a" }
    }
    sourceSets.getByName("test").resources.srcDir("src/main/assets/hrtf")
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}
dependencies {
    // Safe receiver registration across supported Android versions.
    implementation("androidx.core:core:1.17.0")
    // CPU inference on locally bundled YOLOX-Nano weights.
    implementation("com.microsoft.onnxruntime:onnxruntime-android:1.31.0")
    testImplementation("junit:junit:4.13.2")
    // Android supplies org.json at runtime; use its JVM implementation only for decoder tests.
    testImplementation("org.json:json:20260814")
}
