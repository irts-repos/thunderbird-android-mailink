package net.thunderbird.android.autodiscovery

import app.k9mail.autodiscovery.api.AuthenticationType
import app.k9mail.autodiscovery.api.AutoDiscoveryResult
import app.k9mail.autodiscovery.api.ConnectionSecurity
import app.k9mail.autodiscovery.api.ImapServerSettings
import app.k9mail.autodiscovery.api.SmtpServerSettings
import assertk.assertThat
import assertk.assertions.hasSize
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import kotlin.test.Test
import kotlinx.coroutines.runBlocking
import net.thunderbird.core.common.mail.toEmailAddressOrThrow
import net.thunderbird.core.common.net.Hostname
import net.thunderbird.core.common.net.Port

class IrMailPresetAutoDiscoveryTest {

    private val testSubject = IrMailPresetAutoDiscovery()

    @Test
    fun `returns preset settings for indoretreading id address`() = runBlocking {
        val email = "user@indoretreading.id".toEmailAddressOrThrow()

        val runnables = testSubject.initDiscovery(email)
        assertThat(runnables).hasSize(1)

        val result = runnables.single().run()

        assertThat(result).isInstanceOf(AutoDiscoveryResult.Settings::class)
        val settings = result as AutoDiscoveryResult.Settings
        assertThat(settings.isTrusted).isEqualTo(true)
        assertThat(settings.incomingServerSettings).isEqualTo(
            ImapServerSettings(
                hostname = Hostname("mail.indoretreading.id"),
                port = Port(993),
                connectionSecurity = ConnectionSecurity.TLS,
                authenticationTypes = listOf(AuthenticationType.PasswordCleartext),
                username = "user@indoretreading.id",
            ),
        )
        assertThat(settings.outgoingServerSettings).isEqualTo(
            SmtpServerSettings(
                hostname = Hostname("mail.indoretreading.id"),
                port = Port(465),
                connectionSecurity = ConnectionSecurity.TLS,
                authenticationTypes = listOf(AuthenticationType.PasswordCleartext),
                username = "user@indoretreading.id",
            ),
        )
    }

    @Test
    fun `domain match is case insensitive`() = runBlocking {
        val email = "User@INDORETREADING.ID".toEmailAddressOrThrow()

        val runnables = testSubject.initDiscovery(email)

        assertThat(runnables).hasSize(1)
    }

    @Test
    fun `provides nothing for other domains`() {
        val email = "user@example.com".toEmailAddressOrThrow()

        val runnables = testSubject.initDiscovery(email)

        assertThat(runnables).isEmpty()
    }

    @Test
    fun `does not match subdomains or lookalike domains`() {
        val lookalikes = listOf("user@mail.indoretreading.id", "user@indoretreading.id.example.com")

        for (address in lookalikes) {
            val runnables = testSubject.initDiscovery(address.toEmailAddressOrThrow())

            assertThat(runnables).isEmpty()
        }
    }
}
