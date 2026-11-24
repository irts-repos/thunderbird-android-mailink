package net.thunderbird.android.autodiscovery

import app.k9mail.autodiscovery.api.AuthenticationType
import app.k9mail.autodiscovery.api.AutoDiscovery
import app.k9mail.autodiscovery.api.AutoDiscoveryResult
import app.k9mail.autodiscovery.api.AutoDiscoveryRunnable
import app.k9mail.autodiscovery.api.ConnectionSecurity
import app.k9mail.autodiscovery.api.ImapServerSettings
import app.k9mail.autodiscovery.api.SmtpServerSettings
import net.thunderbird.core.common.mail.EmailAddress
import net.thunderbird.core.common.net.Hostname
import net.thunderbird.core.common.net.Port

private const val PRESET_DOMAIN = "indoretreading.id"
private const val MAIL_HOST = "mail.indoretreading.id"
private const val IMAP_PORT = 993
private const val SMTP_PORT = 465

/**
 * Built-in server settings for the company mail server (`mail.indoretreading.id`, Mailcow).
 *
 * Used only for addresses in `indoretreading.id`, so the user does not need to enter server settings.
 * Ports were checked against the server: IMAP 993 and SMTP 465 both use implicit TLS.
 *
 * The result is marked trusted because the settings are part of the app, not fetched from the network.
 */
class IrMailPresetAutoDiscovery : AutoDiscovery {
    override fun initDiscovery(email: EmailAddress): List<AutoDiscoveryRunnable> {
        if (!email.domain.value.equals(PRESET_DOMAIN, ignoreCase = true)) {
            return emptyList()
        }
        return listOf(AutoDiscoveryRunnable { presetSettings(email) })
    }

    private fun presetSettings(email: EmailAddress): AutoDiscoveryResult.Settings {
        val host = Hostname(MAIL_HOST)
        val username = email.address
        return AutoDiscoveryResult.Settings(
            incomingServerSettings = ImapServerSettings(
                hostname = host,
                port = Port(IMAP_PORT),
                connectionSecurity = ConnectionSecurity.TLS,
                authenticationTypes = listOf(AuthenticationType.PasswordCleartext),
                username = username,
            ),
            outgoingServerSettings = SmtpServerSettings(
                hostname = host,
                port = Port(SMTP_PORT),
                connectionSecurity = ConnectionSecurity.TLS,
                authenticationTypes = listOf(AuthenticationType.PasswordCleartext),
                username = username,
            ),
            isTrusted = true,
            source = "app-preset:$PRESET_DOMAIN",
        )
    }
}
