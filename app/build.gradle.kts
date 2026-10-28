plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

// ---------------------------------------------------------------------------
// Firebase: lee app/google-services.json (descárgalo desde la consola de Firebase)
// y genera los recursos que Firebase usa para inicializarse automáticamente.
// Es lo mismo que hace el plugin "google-services", pero sin depender de él.
// Si el archivo no existe, la app compila igual y el login avisa que falta.
// ---------------------------------------------------------------------------
val appPackage = "com.example.myapplication"
val googleServicesFile = file("google-services.json")

android {
    namespace = appPackage
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = appPackage
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        if (googleServicesFile.exists()) {
            @Suppress("UNCHECKED_CAST")
            val json = groovy.json.JsonSlurper().parse(googleServicesFile) as Map<String, Any?>
            val projectInfo = json["project_info"] as Map<*, *>
            val clients = json["client"] as List<Map<*, *>>
            val client = clients.firstOrNull { c ->
                val info = c["client_info"] as Map<*, *>
                val androidInfo = info["android_client_info"] as Map<*, *>
                androidInfo["package_name"] == appPackage
            } ?: error(
                "google-services.json no tiene una app Android con el paquete $appPackage. " +
                    "Registra ese nombre de paquete en Firebase y vuelve a descargar el archivo."
            )
            val clientInfo = client["client_info"] as Map<*, *>
            val apiKeys = client["api_key"] as List<Map<*, *>>

            resValue("string", "google_app_id", clientInfo["mobilesdk_app_id"].toString())
            resValue("string", "google_api_key", apiKeys.first()["current_key"].toString())
            resValue("string", "project_id", projectInfo["project_id"].toString())
            resValue("string", "gcm_defaultSenderId", projectInfo["project_number"].toString())
            projectInfo["storage_bucket"]?.let { resValue("string", "google_storage_bucket", it.toString()) }
        }
    }

    buildTypes {
        release {
            optimization {
                enable = true
                packageScope = setOf("androidx.**", "kotlin.**", "kotlinx.**")
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        resValues = true
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)

    // Autenticación de usuarios (correo y contraseña) con Firebase
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth)

    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
