import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

// Dados da conta de serviço lidos do local.properties (fora do controle de versão).
val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) file.inputStream().use(::load)
}

/** Lê a chave do local.properties e devolve como literal Java para o BuildConfig. */
fun localPropertyLiteral(key: String): String {
    val value = localProperties.getProperty(key).orEmpty()
    val escaped = value
        .replace("\\", "\\\\")
        .replace("\"", "\\\"")
        .replace("\r", "")
        .replace("\n", "\\n")
    return "\"$escaped\""
}

android {
    namespace = "com.silvaramon.cylinders_ctrl"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.silvaramon.cylinders_ctrl"
        minSdk = 24
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        listOf(
            "GSA_PROJECT_ID" to "gsa.project_id",
            "GSA_PRIVATE_KEY_ID" to "gsa.private_key_id",
            "GSA_PRIVATE_KEY" to "gsa.private_key",
            "GSA_CLIENT_EMAIL" to "gsa.client_email",
            "GSA_CLIENT_ID" to "gsa.client_id",
            "GSA_CLIENT_X509_CERT_URL" to "gsa.client_x509_cert_url",
        ).forEach { (field, key) ->
            buildConfigField("String", field, localPropertyLiteral(key))
        }
        listOf("gsa.private_key", "gsa.client_email")
            .filter { localProperties.getProperty(it).isNullOrBlank() }
            .takeIf { it.isNotEmpty() }
            ?.let { missing ->
                logger.warn(
                    "w: Credenciais da conta de serviço ausentes no local.properties: " +
                        "${missing.joinToString()}. O envio para o Google Sheets vai falhar."
                )
            }

        // Planilha de destino (abas "entregas" e "coletas").
        buildConfigField(
            "String",
            "SHEETS_SPREADSHEET_ID",
            "\"1-jbPTgq0mCmimqxnr_K-_lkc15rcfKmdC3sSKqJTDX8\"",
        )
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
        buildConfig = true
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
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)
    implementation(libs.androidx.camera.mlkit.vision)
    implementation(libs.mlkit.text.recognition)
    implementation(libs.mlkit.barcode.scanning)
    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}