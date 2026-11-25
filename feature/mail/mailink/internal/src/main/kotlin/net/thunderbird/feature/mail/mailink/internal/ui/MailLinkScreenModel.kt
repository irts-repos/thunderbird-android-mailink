package net.thunderbird.feature.mail.mailink.internal.ui

import java.io.InputStream
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import net.thunderbird.feature.mail.mailink.api.MailLinkAttachment
import net.thunderbird.feature.mail.mailink.api.MailLinkClient
import net.thunderbird.feature.mail.mailink.api.MailLinkFailureReason
import net.thunderbird.feature.mail.mailink.api.MailLinkResult
import net.thunderbird.feature.mail.mailink.api.MailLinkTokenStore
import net.thunderbird.feature.mail.mailink.api.MailLinkUploadRequest
import net.thunderbird.feature.mail.mailink.api.MailLinkUploadState
import net.thunderbird.feature.mail.mailink.internal.MailLinkUploadStateMachine

/** A file the user picked. [openStream] must return a fresh stream for each call. */
class PickedFile(
    val name: String,
    val sizeBytes: Long,
    val mimeType: String,
    val openStream: () -> InputStream,
)

/** Messages shown to the user. The screen maps each one to a string resource. */
enum class MailLinkMessage {
    EMAIL_REQUIRED,
    CODE_SENT,
    CODE_INVALID,
    ENROLLED,
    NOT_ENROLLED,
    NETWORK,
    TOO_LARGE,
    REJECTED,
    UPLOAD_DONE,
}

data class MailLinkUiState(
    val enrolled: Boolean,
    val email: String = "",
    val codeSent: Boolean = false,
    val code: String = "",
    val busy: Boolean = false,
    val message: MailLinkMessage? = null,
    val file: PickedFileInfo? = null,
    val upload: MailLinkUploadState = MailLinkUploadState.Idle,
    val link: MailLinkAttachment? = null,
)

/** The part of [PickedFile] the screen needs. */
data class PickedFileInfo(val name: String, val sizeBytes: Long)

/**
 * State and actions for the MailLink screen (enrolment, pick one file, upload, show link).
 * Contains no Android types so it can be unit tested.
 */
class MailLinkScreenModel(
    private val client: MailLinkClient,
    private val tokenStore: MailLinkTokenStore,
    private val scope: CoroutineScope,
) {
    private val _state = MutableStateFlow(MailLinkUiState(enrolled = tokenStore.getToken() != null))
    val state: StateFlow<MailLinkUiState> = _state.asStateFlow()

    private var picked: PickedFile? = null
    private var uploadJob: Job? = null

    fun onEmailChanged(email: String) = _state.update { it.copy(email = email.trim()) }

    fun onCodeChanged(code: String) = _state.update { it.copy(code = code.filter(Char::isDigit).take(CODE_LENGTH)) }

    fun requestCode() {
        val email = _state.value.email
        if (email.isBlank()) return _state.update { it.copy(message = MailLinkMessage.EMAIL_REQUIRED) }
        _state.update { it.copy(busy = true, message = null) }
        scope.launch {
            val result = client.requestEnrollmentCode(email)
            _state.update { current ->
                when (result) {
                    is MailLinkResult.Success -> current.copy(
                        busy = false,
                        codeSent = true,
                        message = MailLinkMessage.CODE_SENT,
                    )

                    is MailLinkResult.Failure -> current.copy(busy = false, message = failureMessage(result.reason))
                }
            }
        }
    }

    fun verifyCode() {
        val current = _state.value
        if (current.code.length != CODE_LENGTH) return
        _state.update { it.copy(busy = true, message = null) }
        scope.launch {
            when (val result = client.verifyEnrollmentCode(current.email, current.code)) {
                is MailLinkResult.Success -> {
                    tokenStore.saveToken(result.value)
                    _state.update {
                        it.copy(
                            busy = false,
                            enrolled = true,
                            code = "",
                            codeSent = false,
                            message = MailLinkMessage.ENROLLED,
                        )
                    }
                }

                is MailLinkResult.Failure -> _state.update {
                    it.copy(busy = false, message = failureMessage(result.reason))
                }
            }
        }
    }

    /**
     * Clears the token on this device at once, then revokes it on the server. A failed revoke does not keep the
     * user signed in here. The server token then expires after its idle period.
     */
    fun signOut() {
        val token = tokenStore.getToken()
        tokenStore.clearToken()
        if (token != null) {
            scope.launch { client.revokeDeviceToken(token) }
        }
        cancelUpload()
        picked = null
        _state.update { MailLinkUiState(enrolled = false) }
    }

    fun pickFile(file: PickedFile) {
        cancelUpload()
        picked = file
        _state.update {
            it.copy(
                file = PickedFileInfo(file.name, file.sizeBytes),
                upload = MailLinkUploadState.Idle,
                link = null,
                message = null,
            )
        }
    }

    fun startUpload() {
        val file = picked ?: return
        val token = tokenStore.getToken()
        if (token == null) {
            _state.update { it.copy(enrolled = false, message = MailLinkMessage.NOT_ENROLLED) }
            return
        }
        _state.update { it.copy(upload = MailLinkUploadStateMachine.start(file.name), message = null, link = null) }
        uploadJob = scope.launch {
            val request = MailLinkUploadRequest(file.name, file.mimeType, file.sizeBytes, file.openStream)
            val result = client.upload(token, request) { percent ->
                _state.update { it.copy(upload = MailLinkUploadStateMachine.onProgress(it.upload, percent)) }
            }
            _state.update { current -> applyUploadResult(current, result) }
        }
    }

    fun cancelUpload() {
        uploadJob?.cancel()
        uploadJob = null
        _state.update { it.copy(upload = MailLinkUploadStateMachine.onCancelled(it.upload)) }
    }

    fun retryUpload() {
        _state.update { it.copy(upload = MailLinkUploadStateMachine.retry(it.upload)) }
        startUpload()
    }

    private fun applyUploadResult(
        current: MailLinkUiState,
        result: MailLinkResult<MailLinkAttachment>,
    ): MailLinkUiState = when (result) {
        is MailLinkResult.Success -> {
            val upload = MailLinkUploadStateMachine.onCompleted(current.upload, result.value.url)
            if (upload is MailLinkUploadState.Completed) {
                current.copy(upload = upload, link = result.value, message = MailLinkMessage.UPLOAD_DONE)
            } else {
                current.copy(upload = upload, message = MailLinkMessage.REJECTED)
            }
        }

        is MailLinkResult.Failure -> {
            val upload = MailLinkUploadStateMachine.onFailed(current.upload, result.reason)
            current.copy(upload = upload, message = failureMessage(result.reason))
        }
    }

    private fun failureMessage(reason: MailLinkFailureReason): MailLinkMessage = when (reason) {
        MailLinkFailureReason.NOT_ENROLLED -> MailLinkMessage.NOT_ENROLLED
        MailLinkFailureReason.NETWORK -> MailLinkMessage.NETWORK
        MailLinkFailureReason.TOO_LARGE -> MailLinkMessage.TOO_LARGE
        MailLinkFailureReason.INVALID_CODE -> MailLinkMessage.CODE_INVALID
        MailLinkFailureReason.REJECTED -> MailLinkMessage.REJECTED
    }

    private companion object {
        const val CODE_LENGTH = 6
    }
}
