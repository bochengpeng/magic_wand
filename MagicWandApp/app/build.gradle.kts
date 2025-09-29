import java.util.Properties
import java.io.FileInputStream

val localProps = Properties().apply {
    load(FileInputStream(rootProject.file("local.properties")))
}

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)

    // added plugins
    id("kotlin-parcelize")
}

android {
    namespace = "com.example.magicwand"
    compileSdk = 36

    // api key configure

    buildFeatures {
        buildConfig = true
    }

    defaultConfig {
        applicationId = "com.example.magicwand"
        minSdk = 21
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Read values with sensible defaults
        val token = localProps.getProperty("TMDB_ACCESS_TOKEN", "")
        val aiKey = localProps.getProperty("AI_API_KEY", "")
        val aiBase = localProps.getProperty("AI_BASE_URL", "https://api.openai.com/")
        val aiModel = localProps.getProperty("AI_MODEL", "gpt-4o-mini")

        // Expose to BuildConfig
        buildConfigField("String", "TMDB_ACCESS_TOKEN", "\"$token\"")
        buildConfigField("String", "AI_API_KEY", "\"$aiKey\"")
        buildConfigField("String", "AI_BASE_URL", "\"$aiBase\"")
        buildConfigField("String", "AI_MODEL", "\"$aiModel\"")
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
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
}

dependencies {

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.window)
    implementation(libs.androidx.material3)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)

    implementation("com.squareup.moshi:moshi-kotlin:1.15.1")
    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.retrofit2:converter-moshi:2.11.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    implementation("io.coil-kt:coil:2.6.0")
    // add this so reflection works reliably
    implementation(kotlin("reflect"))
}