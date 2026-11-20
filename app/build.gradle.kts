import java.util.Properties

// Signature de la version finale : clé personnelle d'Adama, hors dépôt (keystore.properties + fichier .jks ignorés par git).
val releaseKeys: Properties? = rootProject.file("keystore.properties").let { f ->
    if (f.exists()) Properties().also { p -> f.inputStream().use { s -> p.load(s) } } else null
}

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.aditv"
    compileSdk {
        version = release(37) { minorApiLevel = 2 }
    }

    defaultConfig {
        applicationId = "com.aditv.app"
        // Appareil cible confirmé : Fire TV Stick 4K (1re génération), Fire OS 6.7.1.1 = Android 7.1 (API 25).
        minSdk = 25
        targetSdk = 34
        versionCode = 13
        versionName = "0.8.2"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        // Fire OS 6 exécute un espace utilisateur 32 bits : seules les bibliothèques ARMv7 sont embarquées.
        ndk { abiFilters += listOf("armeabi-v7a") }
    }

    signingConfigs {
        releaseKeys?.let { k: Properties ->
            create("release") {
                storeFile = rootProject.file(k.getProperty("storeFile"))
                storePassword = k.getProperty("storePassword")
                keyAlias = k.getProperty("keyAlias")
                keyPassword = k.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // Sans keystore.properties : APK non signé (vérification de la compilation R8 uniquement).
            signingConfig = signingConfigs.findByName("release")
        }
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        // java.time & co. n'existent qu'à partir d'Android 8 : désucrage obligatoire pour Fire OS 6.
        isCoreLibraryDesugaringEnabled = true
    }
    testOptions {
        unitTests.isReturnDefaultValues = true
        unitTests.isIncludeAndroidResources = true
        unitTests.all {
            it.systemProperty("aditv.screens", rootProject.file("design/captures-app").absolutePath)
            it.maxHeapSize = "3g"
        }
    }
    lint {
        // Les appels d'API trop récents pour Fire OS 6 bloquent le build.
        error += listOf("NewApi", "InlinedApi")
        abortOnError = true
    }
    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

kotlin {
    compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

// Schémas Room exposés aux tests de migration (Robolectric lit les assets du build debug ; absents du build release).
android.sourceSets.getByName("debug").assets.srcDir("$projectDir/schemas")

dependencies {
    coreLibraryDesugaring(libs.desugar.jdk.libs)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.foundation)
    implementation(libs.compose.ui.tooling.preview)
    debugImplementation(libs.compose.ui.tooling)
    implementation(libs.tv.material)
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)
    implementation(libs.datastore.preferences)
    implementation(libs.work.runtime.ktx)
    implementation(libs.coroutines.android)
    implementation(libs.serialization.json)
    implementation(libs.okhttp)
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)
    implementation(libs.media3.exoplayer)
    implementation(libs.media3.exoplayer.hls)
    implementation(libs.media3.datasource.okhttp)
    implementation(libs.media3.ui)
    // Second moteur de lecture (phase 4) : formats que Media3 ne lit pas (ARMv7 seulement, voir abiFilters).
    implementation(libs.libvlc)
    // QR code de la saisie depuis le téléphone (génération seulement).
    implementation(libs.zxing.core)

    testImplementation(libs.junit)
    testImplementation(libs.coroutines.test)
    testImplementation(libs.okhttp.mockwebserver)
    testImplementation(libs.robolectric)
    testImplementation(libs.room.testing)
    testImplementation(libs.androidx.test.junit)
    testImplementation(platform(libs.compose.bom))
    testImplementation(libs.compose.ui.test.junit4)
    debugImplementation(libs.compose.ui.test.manifest)
}
