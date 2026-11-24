package net.thunderbird.android.mailink

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import app.k9mail.core.android.common.camera.io.CaptureImageFileWriter
import net.thunderbird.android.BuildConfig
import net.thunderbird.components.ui.bolt.theme.thunderbird.ThunderbirdBoltTheme
import net.thunderbird.feature.mail.mailink.api.MailLinkClient
import net.thunderbird.feature.mail.mailink.api.MailLinkTokenStore
import net.thunderbird.feature.mail.mailink.internal.MailLinkBodyBuilder
import net.thunderbird.feature.mail.mailink.internal.ui.MailLinkScreen
import org.koin.android.ext.android.inject

/**
 * Entry point for the S3 MailLink screen (enrolment, upload, copy link) in IR Mail.
 * The composer hook that inserts links into outgoing emails is a separate step (ADR irmail-0002).
 */
class MailLinkActivity : ComponentActivity() {

    private val client: MailLinkClient by inject()
    private val tokenStore: MailLinkTokenStore by inject()
    private val captureImageFileWriter: CaptureImageFileWriter by inject()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Opened from the composer: return the link text so the composer can insert it into the body.
        val pickMode = intent.getBooleanExtra(EXTRA_PICK_MODE, false)
        setContent {
            ThunderbirdBoltTheme {
                MailLinkScreen(
                    client = client,
                    tokenStore = tokenStore,
                    serverUrl = BuildConfig.MAILINK_API_BASE_URL,
                    onInsertLink = if (pickMode) ::returnTextToComposer else null,
                    createCameraUri = { captureImageFileWriter.getFileUri() },
                )
            }
        }
    }

    private fun returnTextToComposer(text: String) {
        setResult(Activity.RESULT_OK, Intent().putExtra(EXTRA_TEXT, text))
        finish()
    }

    private companion object {
        // Must match MessageCompose.EXTRA_MAILINK_PICK_MODE / EXTRA_MAILINK_TEXT.
        const val EXTRA_PICK_MODE = "net.thunderbird.android.mailink.PICK_MODE"
        const val EXTRA_TEXT = "net.thunderbird.android.mailink.TEXT"
    }
}
