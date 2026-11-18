package com.silvaramon.cylinders_ctrl.data

import android.util.Base64
import com.silvaramon.cylinders_ctrl.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.security.KeyFactory
import java.security.Signature
import java.security.spec.PKCS8EncodedKeySpec

class SheetsException(val httpCode: Int, message: String) : IOException(message)

/**
 * Cliente mínimo da Google Sheets API v4 autenticado com a conta de serviço.
 * O JSON da credencial é montado apenas em memória ([ServiceAccountCredentials]) no
 * momento de obter o token; o access token fica em cache até expirar.
 */
class GoogleSheetsClient(
    private val spreadsheetId: String = BuildConfig.SHEETS_SPREADSHEET_ID,
) {
    private val tokenMutex = Mutex()
    private var accessToken: String? = null
    private var accessTokenExpiresAt = 0L

    /** Acrescenta [rows] ao final da aba [sheetName] numa única requisição (tudo ou nada). */
    suspend fun appendRows(sheetName: String, rows: List<List<Any?>>) {
        if (rows.isEmpty()) return
        try {
            doAppend(sheetName, rows)
        } catch (e: SheetsException) {
            if (e.httpCode != HttpURLConnection.HTTP_UNAUTHORIZED) throw e
            // Token expirado/revogado: renova e tenta uma vez mais.
            invalidateToken()
            doAppend(sheetName, rows)
        }
    }

    private suspend fun doAppend(sheetName: String, rows: List<List<Any?>>) {
        val token = accessToken()
        withContext(Dispatchers.IO) {
            val range = URLEncoder.encode("'$sheetName'!A1", "UTF-8").replace("+", "%20")
            val url = URL(
                "$SHEETS_BASE_URL/$spreadsheetId/values/$range:append" +
                    "?valueInputOption=RAW&insertDataOption=INSERT_ROWS"
            )
            val body = JSONObject().put(
                "values",
                JSONArray().apply {
                    rows.forEach { row ->
                        put(JSONArray().apply { row.forEach { put(it ?: "") } })
                    }
                },
            ).toString()
            post(url, body, contentType = "application/json; charset=utf-8", bearer = token)
        }
    }

    private suspend fun accessToken(): String = tokenMutex.withLock {
        val now = System.currentTimeMillis()
        accessToken?.takeIf { now < accessTokenExpiresAt }?.let { return it }

        val (token, expiresInSec) = withContext(Dispatchers.IO) { requestAccessToken() }
        accessToken = token
        accessTokenExpiresAt = now + (expiresInSec - TOKEN_EXPIRY_MARGIN_SEC) * 1000
        token
    }

    private suspend fun invalidateToken() = tokenMutex.withLock {
        accessToken = null
        accessTokenExpiresAt = 0L
    }

    /** OAuth 2.0 para conta de serviço: JWT assinado (RS256) trocado por um access token. */
    private fun requestAccessToken(): Pair<String, Long> {
        val credentials = JSONObject(ServiceAccountCredentials.toJson())
        val tokenUri = credentials.getString("token_uri")
        val nowSec = System.currentTimeMillis() / 1000

        val header = JSONObject().put("alg", "RS256").put("typ", "JWT")
        val claims = JSONObject()
            .put("iss", credentials.getString("client_email"))
            .put("scope", SHEETS_SCOPE)
            .put("aud", tokenUri)
            .put("iat", nowSec)
            .put("exp", nowSec + 3600)
        val unsigned = "${header.toString().base64Url()}.${claims.toString().base64Url()}"
        val signature = Signature.getInstance("SHA256withRSA").run {
            initSign(parsePrivateKey(credentials.getString("private_key")))
            update(unsigned.toByteArray(Charsets.UTF_8))
            sign()
        }
        val jwt = "$unsigned.${signature.base64Url()}"

        val form = "grant_type=" + URLEncoder.encode(JWT_GRANT_TYPE, "UTF-8") +
            "&assertion=" + URLEncoder.encode(jwt, "UTF-8")
        val response = JSONObject(
            post(URL(tokenUri), form, contentType = "application/x-www-form-urlencoded")
        )
        return response.getString("access_token") to response.optLong("expires_in", 3600)
    }

    private fun parsePrivateKey(pem: String) = KeyFactory.getInstance("RSA").generatePrivate(
        PKCS8EncodedKeySpec(
            Base64.decode(
                pem.replace("-----BEGIN PRIVATE KEY-----", "")
                    .replace("-----END PRIVATE KEY-----", "")
                    .replace("\\n", "")
                    .replace(Regex("\\s"), ""),
                Base64.DEFAULT,
            )
        )
    )

    private fun post(url: URL, body: String, contentType: String, bearer: String? = null): String {
        val connection = url.openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "POST"
            connection.connectTimeout = TIMEOUT_MS
            connection.readTimeout = TIMEOUT_MS
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", contentType)
            bearer?.let { connection.setRequestProperty("Authorization", "Bearer $it") }
            connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }

            val code = connection.responseCode
            if (code !in 200..299) {
                val error = connection.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
                throw SheetsException(code, "HTTP $code: ${extractErrorMessage(error)}")
            }
            return connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }

    private fun extractErrorMessage(body: String): String = runCatching {
        val json = JSONObject(body)
        json.optJSONObject("error")?.optString("message")
            ?: json.optString("error_description").ifBlank { json.optString("error") }
    }.getOrNull()?.ifBlank { null } ?: body.take(300)

    private fun String.base64Url() = toByteArray(Charsets.UTF_8).base64Url()

    private fun ByteArray.base64Url(): String =
        Base64.encodeToString(this, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)

    private companion object {
        const val SHEETS_BASE_URL = "https://sheets.googleapis.com/v4/spreadsheets"
        const val SHEETS_SCOPE = "https://www.googleapis.com/auth/spreadsheets"
        const val JWT_GRANT_TYPE = "urn:ietf:params:oauth:grant-type:jwt-bearer"
        const val TOKEN_EXPIRY_MARGIN_SEC = 60L
        const val TIMEOUT_MS = 20_000
    }
}
