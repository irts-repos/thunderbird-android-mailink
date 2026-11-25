package net.thunderbird.feature.mail.mailink.api

/**
 * Talks to the MailLink backend: device enrolment (email OTP) and S3 upload.
 *
 * Implementations must never log email addresses, codes, device tokens, or presigned fields.
 */
interface MailLinkClient {

    /** Asks the server to send an enrolment code. The server answers the same way for every email. */
    suspend fun requestEnrollmentCode(email: String): MailLinkResult<Unit>

    /** Exchanges the emailed code for a device token. */
    suspend fun verifyEnrollmentCode(email: String, code: String): MailLinkResult<String>

    /** Revokes the device [token] on the server. Callers should still clear the local token if this fails. */
    suspend fun revokeDeviceToken(token: String): MailLinkResult<Unit>

    /**
     * Uploads one file with the device [token]. [onProgress] receives values in 0..100.
     * The returned attachment carries the CloudFront link to put in the email body.
     */
    suspend fun upload(
        token: String,
        request: MailLinkUploadRequest,
        onProgress: (Int) -> Unit,
    ): MailLinkResult<MailLinkAttachment>
}

/**
 * A file to upload. [openStream] is called once for the upload and must return a fresh stream each time
 * (for example from a `content://` URI). [sizeBytes] must match the stream length.
 */
class MailLinkUploadRequest(
    val fileName: String,
    val mimeType: String,
    val sizeBytes: Long,
    val openStream: () -> java.io.InputStream,
)

sealed interface MailLinkResult<out T> {
    data class Success<T>(val value: T) : MailLinkResult<T>
    data class Failure(val reason: MailLinkFailureReason) : MailLinkResult<Nothing>
}

/** Stores the device token. Implementations must keep it encrypted at rest and out of backups. */
interface MailLinkTokenStore {
    fun getToken(): String?
    fun saveToken(token: String)
    fun clearToken()
}
