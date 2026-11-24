package net.thunderbird.feature.mail.mailink.internal

import net.thunderbird.feature.mail.mailink.api.MailLinkFailureReason
import net.thunderbird.feature.mail.mailink.api.MailLinkUploadState

/**
 * Pure transitions for one MailLink upload. Invalid transitions return the current state unchanged,
 * so a late callback (for example a progress event after cancel) cannot move the state backwards.
 */
object MailLinkUploadStateMachine {

    fun start(fileName: String): MailLinkUploadState = MailLinkUploadState.Uploading(fileName, 0)

    fun onProgress(state: MailLinkUploadState, percent: Int): MailLinkUploadState {
        if (state !is MailLinkUploadState.Uploading) return state
        val clamped = percent.coerceIn(0, 100)
        return state.copy(progressPercent = maxOf(state.progressPercent, clamped))
    }

    /**
     * Completes the upload. The link must be HTTPS, otherwise the upload is marked as rejected.
     */
    fun onCompleted(state: MailLinkUploadState, url: String): MailLinkUploadState {
        if (state !is MailLinkUploadState.Uploading) return state
        return if (isAcceptableLink(url)) {
            MailLinkUploadState.Completed(state.fileName, url)
        } else {
            MailLinkUploadState.Failed(state.fileName, MailLinkFailureReason.REJECTED)
        }
    }

    fun onFailed(state: MailLinkUploadState, reason: MailLinkFailureReason): MailLinkUploadState {
        if (state !is MailLinkUploadState.Uploading) return state
        return MailLinkUploadState.Failed(state.fileName, reason)
    }

    fun onCancelled(state: MailLinkUploadState): MailLinkUploadState {
        if (state !is MailLinkUploadState.Uploading) return state
        return MailLinkUploadState.Cancelled
    }

    /** Retry is allowed only after a failure. */
    fun retry(state: MailLinkUploadState): MailLinkUploadState {
        if (state !is MailLinkUploadState.Failed) return state
        return MailLinkUploadState.Uploading(state.fileName, 0)
    }

    /** Sending is blocked while any upload is still running, so no email goes out with a missing link. */
    fun canSend(states: Collection<MailLinkUploadState>): Boolean {
        return states.none { it is MailLinkUploadState.Uploading }
    }

    internal fun isAcceptableLink(url: String): Boolean {
        return url.startsWith("https://") && url.length <= MAX_LINK_LENGTH &&
            url.none { it.isWhitespace() || it == '"' || it == '<' || it == '>' }
    }

    private const val MAX_LINK_LENGTH = 2048
}
