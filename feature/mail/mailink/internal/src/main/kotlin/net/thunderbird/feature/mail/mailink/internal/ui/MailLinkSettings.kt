package net.thunderbird.feature.mail.mailink.internal.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import net.thunderbird.components.ui.bolt.atom.button.ButtonOutlined
import net.thunderbird.components.ui.bolt.atom.text.TextBodyMedium
import net.thunderbird.feature.mail.mailink.internal.R

/**
 * Settings content for S3 MailLink: enrolment status, server address and sign out.
 * Stateless. The caller decides what happens on sign out.
 */
@Composable
internal fun MailLinkSettingsContent(
    enrolled: Boolean,
    serverUrl: String,
    onSignOut: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        val status = if (enrolled) R.string.mailink_status_enrolled else R.string.mailink_status_not_enrolled
        TextBodyMedium(text = stringResource(status))
        TextBodyMedium(text = stringResource(R.string.mailink_server_label, serverUrl))
        if (enrolled) {
            TextBodyMedium(text = stringResource(R.string.mailink_sign_out_hint))
            ButtonOutlined(
                text = stringResource(R.string.mailink_sign_out),
                onClick = onSignOut,
            )
        }
    }
}
