plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.room)
}

android {
    namespace = "com.tvapp"
    compileSdk = 37 // Compose UI 1.12.1 (BOM 2026.09.00) needs 37, Media3 1.11.0 needs 36: AAR metadata
    defaultConfig {
        applicationId = "com.tvapp"
        minSdk = 28
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
        buildConfigField("String", "CATALOG_BASE_URL", "\"https://flekc09.github.io/tvapp\"")
        buildConfigField("String", "USER_AGENT", "\"TVApp/1.0 (Android TV; Media3)\"")
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    buildFeatures { compose = true; buildConfig = true }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    testOptions { unitTests.isReturnDefaultValues = true }
}

// Room exports every schema version as JSON; the files are committed so each later version ships a tested migration (Opus adversarial review 2026-09-23, major 17).
room { schemaDirectory("$projectDir/schemas") }

// AGP 9's built-in Kotlin registers the `kotlin` extension itself: `kotlinOptions` is gone and jvmTarget follows
// compileOptions.targetCompatibility, so only the opt-in is set here.
kotlin {
    compilerOptions {
        // Media3's DefaultLoadControl, OkHttpDataSource, DefaultMediaSourceFactory, AnalyticsListener and PlayerView
        // extras are @UnstableApi, which is an error-level opt-in. Without this nothing in playback/ compiles.
        freeCompilerArgs.add("-opt-in=androidx.media3.common.util.UnstableApi")
    }
}

dependencies {
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui); implementation(libs.compose.foundation); implementation(libs.compose.material3)
    implementation(libs.tv.material); implementation(libs.activity.compose)
    implementation(libs.lifecycle.viewmodel.compose); implementation(libs.lifecycle.runtime.compose)
    implementation(libs.media3.exoplayer); implementation(libs.media3.exoplayer.hls); implementation(libs.media3.exoplayer.dash)
    implementation(libs.media3.datasource.okhttp); implementation(libs.media3.ui)
    implementation(libs.room.runtime); ksp(libs.room.compiler)
    implementation(libs.work.runtime); implementation(libs.okhttp)
    implementation(libs.coil.compose); implementation(libs.coil.okhttp)
    implementation(libs.gson); implementation(libs.coroutines.android)
    debugImplementation(libs.compose.ui.tooling)
    testImplementation(libs.junit); testImplementation(libs.coroutines.test); testImplementation(libs.mockwebserver)
    androidTestImplementation(libs.androidx.test.ext); androidTestImplementation(libs.androidx.test.espresso)
    androidTestImplementation(libs.room.testing); androidTestImplementation(libs.mockwebserver); androidTestImplementation(libs.coroutines.test)
}
