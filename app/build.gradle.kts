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
        minSdk = 25
        targetSdk = 34
        versionCode = 49
        versionName = "0.19.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // Deux versions de l'appli, mêmes sources :
    // - stick : Fire TV Stick 4K 1re génération (Fire OS 6.7.1.1 = Android 7.1, API 25, espace utilisateur 32 bits ARMv7) ;
    // - cube  : Fire TV Cube 3e génération (Fire OS 7 = Android 9, API 28 ; système 32 bits) — réglages moins bridés.
    flavorDimensions += "device"
    productFlavors {
        create("stick") {
            dimension = "device"
            isDefault = true
            minSdk = 25
            ndk { abiFilters += listOf("armeabi-v7a") }
            buildConfigField("boolean", "HIGH_END", "false")
        }
        create("cube") {
            dimension = "device"
            minSdk = 28
            versionNameSuffix = "-cube"
            // Cube 3e génération d'Adama, relevé par adb : Fire OS 7 (API 28), ro.product.cpu.abilist = armeabi-v7a,armeabi
            // (processeur 64 bits, système 32 bits) : ARMv7 seulement, comme la Stick.
            ndk { abiFilters += listOf("armeabi-v7a") }
            buildConfigField("boolean", "HIGH_END", "true")
        }
    }

    signingConfigs {
        // Clé des versions installées chez Adama (« rapide ») : fournie par GitHub Actions (secret ADITV_KEYSTORE_B64)
        // via ADITV_SIGNING_STORE ; sinon la clé de débogage de la machine. Une mise à jour n'est acceptée par Android
        // qu'avec la MÊME clé : scripts/publish_update.py vérifie l'empreinte avant toute publication.
        System.getenv("ADITV_SIGNING_STORE")?.takeIf { file(it).exists() }?.let { store ->
            create("aditv") {
                storeFile = file(store)
                storePassword = System.getenv("ADITV_SIGNING_PASSWORD") ?: "android"
                keyAlias = System.getenv("ADITV_SIGNING_ALIAS") ?: "androiddebugkey"
                keyPassword = System.getenv("ADITV_SIGNING_KEY_PASSWORD") ?: "android"
            }
        }
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
        // Version « rapide » : optimisée comme une version finale (R8, sans débogage, profils de démarrage Compose),
        // mais signée et identifiée comme la version de test : elle s'installe par-dessus sans perdre compte ni réglages.
        // Compose est 2 à 3 fois plus fluide qu'en version de test (debug).
        create("rapide") {
            initWith(getByName("release"))
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-rapide"
            isDebuggable = false
            signingConfig = signingConfigs.findByName("aditv") ?: signingConfigs.getByName("debug")
            matchingFallbacks += listOf("release")
        }
    }
    // Bibliothèques natives compressées dans l'APK (LibVLC en double sur le Cube) : téléchargement et copie plus légers.
    packaging { jniLibs { useLegacyPackaging = true } }
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

// Tests Robolectric : sources communes, exécutés sur la version Stick (API 25 et API 35).
// La version Cube (minSdk 28) ne peut pas charger les tests épinglés sur l'API 25 ; elle est vérifiée par compilation + lint.
tasks.matching { (it.name.startsWith("testCube") || it.name.contains("Rapide")) && it.name.endsWith("UnitTest") }.configureEach { enabled = false }
