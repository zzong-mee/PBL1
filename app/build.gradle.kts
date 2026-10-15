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
        versionCode = 3
        versionName = "0.3.0"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}
dependencies {
    // Safe receiver registration across supported Android versions.
    implementation("androidx.core:core:1.17.0")
    testImplementation("junit:junit:4.13.2")
    // Android supplies org.json at runtime; use its JVM implementation only for decoder tests.
    testImplementation("org.json:json:20260814")
}
