package com.silvaramon.cylinders_ctrl.data

import com.silvaramon.cylinders_ctrl.BuildConfig
import org.json.JSONObject

/**
 * Credenciais da conta de serviço Google montadas **apenas em memória** a partir do
 * [BuildConfig] (valores vindos do `local.properties`). Nenhum arquivo JSON é gravado
 * em disco nem empacotado em `res/raw`.
 *
 * Usado por [GoogleSheetsClient] para assinar o JWT na hora de obter o access token.
 */
object ServiceAccountCredentials {

    val isConfigured: Boolean
        get() = BuildConfig.GSA_PRIVATE_KEY.isNotBlank() && BuildConfig.GSA_CLIENT_EMAIL.isNotBlank()

    /** Gera o JSON da conta de serviço. Chame só no momento da autenticação e descarte em seguida. */
    fun toJson(): String {
        check(isConfigured) {
            val missing = buildList {
                if (BuildConfig.GSA_PRIVATE_KEY.isBlank()) add("gsa.private_key")
                if (BuildConfig.GSA_CLIENT_EMAIL.isBlank()) add("gsa.client_email")
            }
            "Credenciais ausentes (${missing.joinToString()}). Preencha no local.properties " +
                "e recompile o app."
        }
        return JSONObject().apply {
            put("type", "service_account")
            put("project_id", BuildConfig.GSA_PROJECT_ID)
            put("private_key_id", BuildConfig.GSA_PRIVATE_KEY_ID)
            put("private_key", BuildConfig.GSA_PRIVATE_KEY)
            put("client_email", BuildConfig.GSA_CLIENT_EMAIL)
            put("client_id", BuildConfig.GSA_CLIENT_ID)
            put("auth_uri", "https://accounts.google.com/o/oauth2/auth")
            put("token_uri", "https://oauth2.googleapis.com/token")
            put("auth_provider_x509_cert_url", "https://www.googleapis.com/oauth2/v1/certs")
            put("client_x509_cert_url", BuildConfig.GSA_CLIENT_X509_CERT_URL)
            put("universe_domain", "googleapis.com")
        }.toString()
    }
}
