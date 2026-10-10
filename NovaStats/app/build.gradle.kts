import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

// Lecture des clés API. Priorité : variable d'environnement (GitHub Secrets) > local.properties > api_keys.properties.
// Flux : clé → BuildConfig → ApiKeys.kt → cascade d'APIs
val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
val fallbackProps = Properties().apply {
    val f = rootProject.file("api_keys.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
fun apiKey(name: String): String {
    val value = System.getenv(name)?.takeIf { it.isNotBlank() }
        ?: localProps.getProperty(name)?.takeIf { it.isNotBlank() }
        ?: fallbackProps.getProperty(name, "")
    val escaped = value.trim()
        .replace("\\", "\\\\")
        .replace("\"", "\\\"")
        .replace("\n", "\\n")
        .replace("\r", "\\r")
        .replace("\t", "\\t")
    return "\"$escaped\""
}

android {
    namespace = "com.novastats.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.novastats.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 111
        versionName = "0.22.50"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField("String", "LASTFM_API_KEY", apiKey("LASTFM_API_KEY"))
        buildConfigField("String", "SPOTIFY_CLIENT_ID", apiKey("SPOTIFY_CLIENT_ID"))
        buildConfigField("String", "SPOTIFY_CLIENT_SECRET", apiKey("SPOTIFY_CLIENT_SECRET"))
        buildConfigField("String", "FANART_API_KEY", apiKey("FANART_API_KEY"))
        buildConfigField("String", "GOOGLE_API_KEY", apiKey("GOOGLE_API_KEY"))
        buildConfigField("String", "GOOGLE_ENGINE_ID", apiKey("GOOGLE_ENGINE_ID"))
        buildConfigField("String", "DISCOGS_TOKEN", apiKey("DISCOGS_TOKEN"))
        buildConfigField("String", "THEAUDIODB_API_KEY", apiKey("THEAUDIODB_API_KEY"))
        buildConfigField("String", "GENIUS_ACCESS_TOKEN", apiKey("GENIUS_ACCESS_TOKEN"))
        buildConfigField("String", "YOUTUBE_API_KEY", apiKey("YOUTUBE_API_KEY"))
    }

    // Clé de signature debug STABLE, versionnée dans le dépôt : sans elle, chaque exécution de GitHub Actions
    // génère une clé différente et Android refuse la mise à jour ("package en conflit").
    // Ce n'est pas un secret (mot de passe "android", comme la clé debug standard d'Android).
    signingConfigs {
        create("novaDebug") {
            storeFile = rootProject.file("keystore/debug.p12")
            storeType = "PKCS12"
            storePassword = "android"
            keyAlias = "novadebug"
            keyPassword = "android"
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("novaDebug")
        }
        release {
            signingConfig = signingConfigs.getByName("novaDebug")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
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
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
    arg("room.incremental", "true")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)

    // Compose
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons)
    implementation(libs.androidx.compose.ui.text.google.fonts)
    implementation(libs.androidx.navigation.compose)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    // Room (35 tables)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    // Background / prefs
    implementation(libs.androidx.work.runtime)
    implementation(libs.androidx.datastore.preferences)

    // Sérialisation JSON (import/export)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)

    // Réseau (APIs métadonnées — cascade)
    implementation(libs.retrofit)
    implementation(libs.retrofit.kotlinx.serialization)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)

    // Images
    implementation(libs.coil.compose)

    // Tests
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
}
