package net.thunderbird.feature.mail.mailink.internal

import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import net.thunderbird.feature.mail.mailink.api.MailLinkAttachment
import net.thunderbird.feature.mail.mailink.api.MailLinkClient
import net.thunderbird.feature.mail.mailink.api.MailLinkFailureReason
import net.thunderbird.feature.mail.mailink.api.MailLinkResult
import net.thunderbird.feature.mail.mailink.api.MailLinkUploadRequest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * OkHttp implementation of [MailLinkClient] for the MailLink backend (see ADR irmail-0002).
 *
 * [requireHttps] is true in the app. Tests may turn it off to talk to a local server.
 */
@Suppress("TooManyFunctions")
class OkHttpMailLinkClient(
    private val baseUrl: String,
    private val httpClient: OkHttpClient,
    private val requireHttps: Boolean = true,
) : MailLinkClient {

    init {
        val base = baseUrl.trimEnd('/')
        require(!requireHttps || base.startsWith("https://")) { "MailLink base URL must use https" }
    }

    private val base: String = baseUrl.trimEnd('/')

    override suspend fun requestEnrollmentCode(email: String): MailLinkResult<Unit> {
        val body = buildJsonObject { put("email", email) }.toString().toRequestBody(JSON_TYPE)
        val (code, _) = execute(Request.Builder().url("$base/v1/enroll/request").post(body).build())
            ?: return failure(MailLinkFailureReason.NETWORK)
        return when (code) {
            ACCEPTED -> MailLinkResult.Success(Unit)
            SERVICE_UNAVAILABLE -> failure(MailLinkFailureReason.NETWORK)
            else -> failure(MailLinkFailureReason.REJECTED)
        }
    }

    override suspend fun verifyEnrollmentCode(email: String, code: String): MailLinkResult<String> {
        val body = buildJsonObject {
            put("email", email)
            put("code", code)
        }.toString().toRequestBody(JSON_TYPE)
        val (status, text) = execute(Request.Builder().url("$base/v1/enroll/verify").post(body).build())
            ?: return failure(MailLinkFailureReason.NETWORK)
        return when (status) {
            OK -> {
                val token = parseJson(text)?.get("device_token")?.jsonPrimitive?.content
                if (token.isNullOrBlank()) failure(MailLinkFailureReason.REJECTED) else MailLinkResult.Success(token)
            }

            UNAUTHORIZED -> failure(MailLinkFailureReason.INVALID_CODE)

            SERVICE_UNAVAILABLE -> failure(MailLinkFailureReason.NETWORK)

            else -> failure(MailLinkFailureReason.REJECTED)
        }
    }

    @Suppress("ReturnCount")
    override suspend fun revokeDeviceToken(token: String): MailLinkResult<Unit> {
        val request = Request.Builder()
            .url("$base/v1/enroll/logout")
            .header("Authorization", "Bearer $token")
            .post(ByteArray(0).toRequestBody(null))
            .build()
        val (status, _) = execute(request) ?: return failure(MailLinkFailureReason.NETWORK)
        return if (status == OK) MailLinkResult.Success(Unit) else failure(MailLinkFailureReason.REJECTED)
    }

    @Suppress("ReturnCount")
    override suspend fun upload(
        token: String,
        request: MailLinkUploadRequest,
        onProgress: (Int) -> Unit,
    ): MailLinkResult<MailLinkAttachment> {
        val ticket = when (val result = requestTicket(token, request)) {
            is MailLinkResult.Success -> result.value
            is MailLinkResult.Failure -> return result
        }
        val s3Body = buildS3Body(ticket, request, onProgress)
        val (status, _) = execute(Request.Builder().url(ticket.uploadUrl).post(s3Body).build())
            ?: return failure(MailLinkFailureReason.NETWORK)
        return if (status == CREATED) {
            MailLinkResult.Success(
                MailLinkAttachment(fileName = request.fileName, sizeBytes = request.sizeBytes, url = ticket.fileUrl),
            )
        } else {
            failure(MailLinkFailureReason.REJECTED)
        }
    }

    @Suppress("ReturnCount")
    private suspend fun requestTicket(
        token: String,
        request: MailLinkUploadRequest,
    ): MailLinkResult<UploadTicket> {
        val body = buildJsonObject {
            put("filename", request.fileName)
            put("size", request.sizeBytes)
            put("contentType", request.mimeType)
        }.toString().toRequestBody(JSON_TYPE)
        val req = Request.Builder()
            .url("$base/v1/uploads")
            .header("Authorization", "Bearer $token")
            .post(body)
            .build()
        val (status, text) = execute(req) ?: return failure(MailLinkFailureReason.NETWORK)
        if (status != OK) {
            return when (status) {
                UNAUTHORIZED -> failure(MailLinkFailureReason.NOT_ENROLLED)
                BAD_REQUEST -> failure(sizeOrRejected(text))
                SERVICE_UNAVAILABLE -> failure(MailLinkFailureReason.NETWORK)
                else -> failure(MailLinkFailureReason.REJECTED)
            }
        }
        val ticket = parseTicket(text) ?: return failure(MailLinkFailureReason.REJECTED)
        return MailLinkResult.Success(ticket)
    }

    private fun sizeOrRejected(text: String): MailLinkFailureReason {
        return if (text.contains(
                "size",
                ignoreCase = true,
            )
        ) {
            MailLinkFailureReason.TOO_LARGE
        } else {
            MailLinkFailureReason.REJECTED
        }
    }

    private fun buildS3Body(
        ticket: UploadTicket,
        request: MailLinkUploadRequest,
        onProgress: (Int) -> Unit,
    ): MultipartBody {
        val mediaType = request.mimeType.toMediaTypeOrNull() ?: OCTET_STREAM
        val file = ProgressRequestBody(mediaType, request.sizeBytes, request.openStream, onProgress)
        val builder = MultipartBody.Builder().setType(MultipartBody.FORM)
        // Signed fields first, in the order the server sent them. The file must be the last part.
        ticket.fields.forEach { (name, value) -> builder.addFormDataPart(name, value) }
        builder.addFormDataPart("file", request.fileName, file)
        return builder.build()
    }

    /** Runs a call off the main thread. Returns null for network errors. Never logs URL or body. */
    private suspend fun execute(request: Request): Pair<Int, String>? = withContext(Dispatchers.IO) {
        try {
            httpClient.newCall(request).execute().use { response ->
                response.code to response.body.string()
            }
        } catch (_: IOException) {
            null
        }
    }

    @Suppress("ReturnCount")
    private fun parseTicket(text: String): UploadTicket? {
        val obj = parseJson(text) ?: return null
        if (obj["method"]?.jsonPrimitive?.content != "POST") return null
        val uploadUrl = obj["uploadUrl"]?.jsonPrimitive?.content ?: return null
        val fileUrl = obj["fileUrl"]?.jsonPrimitive?.content ?: return null
        if (!isAcceptableTicketUrl(uploadUrl, requireHttps) ||
            !isAcceptableTicketUrl(fileUrl, requireHttps)
        ) {
            return null
        }
        val fieldsObj = obj["fields"]?.jsonObject ?: return null
        val fields = fieldsObj.mapValues { (_, v) -> v.jsonPrimitive.content }
        return UploadTicket(uploadUrl = uploadUrl, fileUrl = fileUrl, fields = fields)
    }

    /** Upload and file URLs from the server must be HTTPS in the app. Local test servers may use HTTP. */
    internal fun isAcceptableTicketUrl(url: String, requireHttps: Boolean): Boolean {
        return url.isNotBlank() && (!requireHttps || url.startsWith("https://"))
    }

    private fun parseJson(text: String): JsonObject? = try {
        Json.parseToJsonElement(text).jsonObject
    } catch (_: Exception) {
        null
    }

    private fun failure(reason: MailLinkFailureReason) = MailLinkResult.Failure(reason)

    private data class UploadTicket(
        val uploadUrl: String,
        val fileUrl: String,
        val fields: Map<String, String>,
    )

    private companion object {
        val JSON_TYPE = "application/json; charset=utf-8".toMediaType()
        val OCTET_STREAM = "application/octet-stream".toMediaType()
        const val OK = 200
        const val ACCEPTED = 202
        const val CREATED = 201
        const val BAD_REQUEST = 400
        const val UNAUTHORIZED = 401
        const val SERVICE_UNAVAILABLE = 503
    }
}
