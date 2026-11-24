package net.thunderbird.feature.mail.mailink.api

/**
 * State of one S3 MailLink upload (one file per send, see ADR irmail-0002).
 *
 * The file name is kept for display only. It must never be logged together with a token.
 */
sealed interface MailLinkUploadState {
    /** No upload has started for the selected file. */
    data object Idle : MailLinkUploadState

    /** Upload is running. [progressPercent] is in 0..100. */
    data class Uploading(val fileName: String, val progressPercent: Int) : MailLinkUploadState

    /** Upload finished. [url] is the CloudFront link to insert into the email body. */
    data class Completed(val fileName: String, val url: String) : MailLinkUploadState

    /** Upload failed. The user can retry or switch the file to a normal attachment. */
    data class Failed(val fileName: String, val reason: MailLinkFailureReason) : MailLinkUploadState

    /** The user cancelled the upload. */
    data object Cancelled : MailLinkUploadState
}

/** Reasons shown to the user. Mapped to strings in the UI layer. */
enum class MailLinkFailureReason {
    /** Device is not enrolled, or the token was revoked or expired. */
    NOT_ENROLLED,

    /** Network error or timeout. Retry is allowed. */
    NETWORK,

    /** The file is larger than the server limit. */
    TOO_LARGE,

    /** The enrolment code is wrong, expired, or was already used. */
    INVALID_CODE,

    /** The server rejected the request for another reason. */
    REJECTED,
}
