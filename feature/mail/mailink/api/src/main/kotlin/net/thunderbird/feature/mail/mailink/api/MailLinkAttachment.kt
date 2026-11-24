package net.thunderbird.feature.mail.mailink.api

/**
 * A file that was uploaded through MailLink and is ready to be linked from the email body.
 */
data class MailLinkAttachment(
    val fileName: String,
    val sizeBytes: Long,
    val url: String,
)
