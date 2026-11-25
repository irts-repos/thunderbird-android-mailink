package net.thunderbird.feature.mail.mailink.internal.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import net.thunderbird.components.ui.bolt.atom.button.ButtonFilled
import net.thunderbird.components.ui.bolt.atom.button.ButtonOutlined
import net.thunderbird.components.ui.bolt.atom.button.ButtonText
import net.thunderbird.components.ui.bolt.atom.text.TextBodyMedium
import net.thunderbird.components.ui.bolt.molecule.input.TextInput
import net.thunderbird.components.ui.bolt.organism.TopAppBar
import net.thunderbird.components.ui.bolt.template.Scaffold
import net.thunderbird.feature.mail.mailink.api.MailLinkAttachment
import net.thunderbird.feature.mail.mailink.api.MailLinkClient
import net.thunderbird.feature.mail.mailink.api.MailLinkTokenStore
import net.thunderbird.feature.mail.mailink.api.MailLinkUploadState
import net.thunderbird.feature.mail.mailink.internal.MailLinkBodyBuilder
import net.thunderbird.feature.mail.mailink.internal.R

/**
 * MailLink screen: enrol with the emailed code, pick one file, upload to S3, copy the link.
 * Logs nothing: no email, code, token, file name, or URL is written to logcat.
 *
 * [onInsertLink] is set only when the screen was opened from the composer. It receives the uploaded file,
 * so the caller can build the plain text and HTML.
 */
@Composable
fun MailLinkScreen(
    client: MailLinkClient,
    tokenStore: MailLinkTokenStore,
    modifier: Modifier = Modifier,
    serverUrl: String = "",
    onInsertLink: ((MailLinkAttachment) -> Unit)? = null,
    createCameraUri: (() -> Uri)? = null,
) {
    val scope = rememberCoroutineScope()
    val model = remember { MailLinkScreenModel(client = client, tokenStore = tokenStore, scope = scope) }
    val state by model.state.collectAsState()
    val context = LocalContext.current
    var showSettings by remember { mutableStateOf(false) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) model.pickFile(pickedFileFrom(context, uri))
    }
    var pendingCameraUri by remember { mutableStateOf<Uri?>(null) }
    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { saved ->
        val uri = pendingCameraUri
        pendingCameraUri = null
        if (saved && uri != null) model.pickFile(pickedFileFrom(context, uri))
    }

    // Scaffold keeps the content below the status bar and gives the screen a Bolt app bar.
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = { MailLinkTopBar(showSettings = showSettings, onToggleSettings = { showSettings = !showSettings }) },
    ) { paddingValues ->
        if (showSettings) {
            MailLinkSettingsContent(
                enrolled = state.enrolled,
                serverUrl = serverUrl,
                onSignOut = model::signOut,
                modifier = Modifier.padding(paddingValues),
            )
        } else {
            Column(
                modifier = Modifier.fillMaxWidth().padding(paddingValues).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                state.message?.let { message ->
                    TextBodyMedium(text = stringResource(messageRes(message)))
                }

                if (state.enrolled) {
                    FileSection(
                        state = state,
                        model = model,
                        onPickFile = { picker.launch(arrayOf("*/*")) },
                        onTakePhoto = createCameraUri?.let { create ->
                            {
                                val uri = create()
                                pendingCameraUri = uri
                                cameraLauncher.launch(uri)
                            }
                        },
                        onInsertLink = onInsertLink,
                    )
                } else {
                    EnrollSection(state = state, model = model)
                }
            }
        }
    }
}

@Composable
private fun MailLinkTopBar(showSettings: Boolean, onToggleSettings: () -> Unit) {
    TopAppBar(
        title = stringResource(R.string.mailink_title),
        actions = {
            ButtonText(
                text = stringResource(if (showSettings) R.string.mailink_back else R.string.mailink_settings),
                onClick = onToggleSettings,
            )
        },
    )
}

@Composable
private fun EnrollSection(state: MailLinkUiState, model: MailLinkScreenModel) {
    TextBodyMedium(text = stringResource(R.string.mailink_enroll_intro))
    TextInput(
        text = state.email,
        onTextChange = model::onEmailChanged,
        label = stringResource(R.string.mailink_email_label),
        isEnabled = !state.busy,
    )
    ButtonFilled(
        text = stringResource(R.string.mailink_send_code),
        onClick = model::requestCode,
        enabled = !state.busy,
    )
    if (state.codeSent) {
        TextInput(
            text = state.code,
            onTextChange = model::onCodeChanged,
            label = stringResource(R.string.mailink_code_label),
            isEnabled = !state.busy,
        )
        ButtonFilled(
            text = stringResource(R.string.mailink_verify_code),
            onClick = model::verifyCode,
            enabled = !state.busy && state.code.length == CODE_LENGTH,
        )
    }
}

@Composable
private fun FileSection(
    state: MailLinkUiState,
    model: MailLinkScreenModel,
    onPickFile: () -> Unit,
    onTakePhoto: (() -> Unit)?,
    onInsertLink: ((MailLinkAttachment) -> Unit)?,
) {
    val context = LocalContext.current
    ButtonOutlined(
        text = stringResource(R.string.mailink_pick_file),
        onClick = onPickFile,
        enabled = state.upload !is MailLinkUploadState.Uploading,
    )
    onTakePhoto?.let { takePhoto ->
        ButtonOutlined(
            text = stringResource(R.string.mailink_take_photo),
            onClick = takePhoto,
            enabled = state.upload !is MailLinkUploadState.Uploading,
        )
    }
    state.file?.let { file ->
        TextBodyMedium(text = "${file.name} (${MailLinkBodyBuilder.formatSize(file.sizeBytes)})")
    }
    UploadSection(state.upload, state.file != null, model)
    state.link?.let { link ->
        TextBodyMedium(text = link.url)
        onInsertLink?.let { insert ->
            ButtonFilled(
                text = stringResource(R.string.mailink_insert_into_email),
                onClick = { insert(link) },
            )
        }
        ButtonOutlined(
            text = stringResource(R.string.mailink_copy_link),
            onClick = { copyToClipboard(context, link.url) },
        )
    }
}

@Composable
private fun UploadSection(upload: MailLinkUploadState, hasFile: Boolean, model: MailLinkScreenModel) {
    when (upload) {
        is MailLinkUploadState.Idle -> ButtonFilled(
            text = stringResource(R.string.mailink_upload),
            onClick = model::startUpload,
            enabled = hasFile,
        )

        is MailLinkUploadState.Uploading -> {
            TextBodyMedium(text = stringResource(R.string.mailink_uploading, upload.progressPercent))
            ButtonOutlined(text = stringResource(R.string.mailink_cancel), onClick = model::cancelUpload)
        }

        is MailLinkUploadState.Failed -> ButtonFilled(
            text = stringResource(R.string.mailink_retry),
            onClick = model::retryUpload,
        )

        is MailLinkUploadState.Completed -> TextBodyMedium(text = stringResource(R.string.mailink_upload_done))

        is MailLinkUploadState.Cancelled -> ButtonFilled(
            text = stringResource(R.string.mailink_upload),
            onClick = model::startUpload,
            enabled = hasFile,
        )
    }
}

private fun messageRes(message: MailLinkMessage): Int = when (message) {
    MailLinkMessage.EMAIL_REQUIRED -> R.string.mailink_msg_email_required
    MailLinkMessage.CODE_SENT -> R.string.mailink_msg_code_sent
    MailLinkMessage.CODE_INVALID -> R.string.mailink_msg_code_invalid
    MailLinkMessage.ENROLLED -> R.string.mailink_msg_enrolled
    MailLinkMessage.NOT_ENROLLED -> R.string.mailink_msg_not_enrolled
    MailLinkMessage.NETWORK -> R.string.mailink_msg_network
    MailLinkMessage.TOO_LARGE -> R.string.mailink_msg_too_large
    MailLinkMessage.REJECTED -> R.string.mailink_msg_rejected
    MailLinkMessage.UPLOAD_DONE -> R.string.mailink_msg_upload_done
}

private fun pickedFileFrom(context: Context, uri: Uri): PickedFile {
    var name = "file"
    var size = 0L
    context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
        if (cursor.moveToFirst()) {
            cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME).takeIf { it >= 0 }?.let { name = cursor.getString(it) }
            cursor.getColumnIndex(OpenableColumns.SIZE).takeIf { it >= 0 }?.let { size = cursor.getLong(it) }
        }
    }
    val mime = context.contentResolver.getType(uri) ?: "application/octet-stream"
    return PickedFile(
        name = name,
        sizeBytes = size,
        mimeType = mime,
        openStream = { context.contentResolver.openInputStream(uri) ?: error("cannot open picked file") },
    )
}

private fun copyToClipboard(context: Context, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("S3 MailLink", text))
}

private const val CODE_LENGTH = 6
