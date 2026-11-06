// Imports explícitos: en Kotlin DSL el accessor `java` del plugin Java sombrea el
// paquete `java`, por lo que `java.util.Properties` no resuelve.
import java.net.URI
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.ksp)
    id("com.google.gms.google-services")
}

// URL del servidor web, configurable por entorno en lugar de fija en el código.
//   Emulador ......... 10.0.2.2 (localhost de la PC)
//   Equipo en la LAN . http://<IP-de-la-PC>:5000
//   Producción ....... https://<dominio>
//
// Formas de fijarla, en orden de prioridad:
//   Tarea        .\gradlew assembleDebug -Pgn.serverUrl="https://api.gn.gob.pa"
//   Entorno      $env:GN_SERVER_URL="http://192.168.1.50:5000"
//   Archivo      gn.properties -> gestion.serverUrl=http://192.168.1.50:5000
val gnServerUrl: String = run {
    val desdePropiedad = providers.gradleProperty("gn.serverUrl").orNull
    val desdeEntorno = providers.environmentVariable("GN_SERVER_URL").orNull
    val desdeArchivo = rootProject.file("gn.properties")
        .takeIf { it.isFile }
        ?.let { archivo: File ->
            Properties().apply { archivo.inputStream().use { cargar -> load(cargar) } }
                .getProperty("gestion.serverUrl")
        }
    (desdePropiedad ?: desdeEntorno ?: desdeArchivo ?: "https://thermos-provider-populace.ngrok-free.dev")
        .trim()
        .trimEnd('/')
}

// Hosts considerada de desarrollo, a los que se permite HTTP sin TLS.
// Incluye el alias del emulador y los rangos privados porque GestioN está pensada
// para despliegue en intranet: en producción debe usarse un dominio con HTTPS.
val gnHostsDesarrollo = setOf("10.0.2.2", "localhost", "127.0.0.1")

fun esHostPrivado(host: String): Boolean {
    if (host in gnHostsDesarrollo) return true
    if (host.startsWith("10.")) return true
    if (host.startsWith("192.168.")) return true
    // 172.16.0.0/12
    val octetos = host.split('.')
    if (octetos.size == 4 && octetos[0] == "172") {
        val segundo = octetos[1].toIntOrNull()
        if (segundo != null && segundo in 16..31) return true
    }
    // Enlace local 169.254.0.0/16
    if (host.startsWith("169.254.")) return true
    return false
}

val gnEsHostDesarrollo = runCatching { URI(gnServerUrl).host }.getOrNull()
    ?.let { esHostPrivado(it) } == true

android {
    namespace = "com.gn.gestion"
    compileSdk = 36
    buildToolsVersion = "36.0.0"

    defaultConfig {
        applicationId = "com.gn.gestion"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }

        // URL resuelta arriba (propiedad de tarea > variable de entorno > gn.properties).
        buildConfigField("String", "SERVER_URL", "\"$gnServerUrl\"")
        manifestPlaceholders["allowCleartext"] = gnEsHostDesarrollo.toString()
    }

    buildTypes {
        debug {
            // Cleartext siempre habilitado en debug (IP local, emulador).
            manifestPlaceholders["allowCleartext"] = "true"
            buildConfigField("boolean", "ALLOW_CLEARTEXT", "true")
        }
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // En release, HTTP solo si el destino es un host de intranet.
            manifestPlaceholders["allowCleartext"] = gnEsHostDesarrollo.toString()
            buildConfigField("boolean", "ALLOW_CLEARTEXT", gnEsHostDesarrollo.toString())
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        buildConfig = true
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.activity.ktx)
    implementation(libs.androidx.fragment.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.navigation.fragment.ktx)
    implementation(libs.androidx.navigation.ui.ktx)
    implementation(libs.androidx.material)
    implementation(libs.androidx.recyclerview)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.swiperefreshlayout)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)

    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.security.crypto)
    implementation(libs.androidx.biometric)

    implementation(libs.retrofit)
    implementation(libs.retrofit.converter.gson)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)

    implementation(libs.mlkit.text.recognition)
    implementation(libs.mlkit.document.scanner)
    implementation(libs.opencv)
    implementation(libs.zxing.core)

    implementation(libs.kotlinx.coroutines.android)
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.10.2")

    // Socket.io para notificaciones en tiempo real
    implementation("io.socket:socket.io-client:2.1.1")

    // Firebase Cloud Messaging (sin google-services plugin — funciona en modo degradado)
    implementation(platform("com.google.firebase:firebase-bom:33.13.0"))
    implementation("com.google.firebase:firebase-messaging")
    implementation("androidx.core:core-splashscreen:1.0.1")
}