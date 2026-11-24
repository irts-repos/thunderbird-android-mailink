package net.thunderbird.feature.mail.mailink.internal

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import kotlin.test.Test
import net.thunderbird.feature.mail.mailink.api.MailLinkFailureReason
import net.thunderbird.feature.mail.mailink.api.MailLinkUploadState

class MailLinkUploadStateMachineTest {

    private val testSubject = MailLinkUploadStateMachine

    @Test
    fun `start moves to uploading at zero percent`() {
        val state = testSubject.start("jadwal.xlsx")

        assertThat(state).isEqualTo(MailLinkUploadState.Uploading("jadwal.xlsx", 0))
    }

    @Test
    fun `progress is clamped and never goes backwards`() {
        val uploading = testSubject.start("a.pdf")

        val progressed = testSubject.onProgress(uploading, 40)
        val backwards = testSubject.onProgress(progressed, 10)
        val tooHigh = testSubject.onProgress(backwards, 250)

        assertThat(progressed).isEqualTo(MailLinkUploadState.Uploading("a.pdf", 40))
        assertThat(backwards).isEqualTo(MailLinkUploadState.Uploading("a.pdf", 40))
        assertThat(tooHigh).isEqualTo(MailLinkUploadState.Uploading("a.pdf", 100))
    }

    @Test
    fun `completed with https link`() {
        val uploading = testSubject.start("a.pdf")

        val result = testSubject.onCompleted(uploading, "https://d16k5yh5wbwlib.cloudfront.net/attachments/x/a.pdf")

        assertThat(result).isEqualTo(
            MailLinkUploadState.Completed("a.pdf", "https://d16k5yh5wbwlib.cloudfront.net/attachments/x/a.pdf"),
        )
    }

    @Test
    fun `completed with non https link is rejected`() {
        val uploading = testSubject.start("a.pdf")

        val result = testSubject.onCompleted(uploading, "http://example.com/a.pdf")

        assertThat(result).isEqualTo(MailLinkUploadState.Failed("a.pdf", MailLinkFailureReason.REJECTED))
    }

    @Test
    fun `link with whitespace or markup characters is rejected`() {
        val uploading = testSubject.start("a.pdf")

        for (url in listOf("https://x.example/a b", "https://x.example/\"><script>", "https://x.example/<a>")) {
            val result = testSubject.onCompleted(uploading, url)
            assertThat(result).isEqualTo(MailLinkUploadState.Failed("a.pdf", MailLinkFailureReason.REJECTED))
        }
    }

    @Test
    fun `failure keeps the file name and reason`() {
        val uploading = testSubject.start("a.pdf")

        val result = testSubject.onFailed(uploading, MailLinkFailureReason.NETWORK)

        assertThat(result).isEqualTo(MailLinkUploadState.Failed("a.pdf", MailLinkFailureReason.NETWORK))
    }

    @Test
    fun `cancel stops an upload`() {
        val uploading = testSubject.start("a.pdf")

        assertThat(testSubject.onCancelled(uploading)).isEqualTo(MailLinkUploadState.Cancelled)
    }

    @Test
    fun `late callbacks after cancel do not change the state`() {
        val cancelled = MailLinkUploadState.Cancelled

        assertThat(testSubject.onProgress(cancelled, 50)).isEqualTo(cancelled)
        assertThat(testSubject.onCompleted(cancelled, "https://x.example/a")).isEqualTo(cancelled)
        assertThat(testSubject.onFailed(cancelled, MailLinkFailureReason.NETWORK)).isEqualTo(cancelled)
    }

    @Test
    fun `retry only applies after a failure`() {
        val failed = MailLinkUploadState.Failed("a.pdf", MailLinkFailureReason.NETWORK)

        assertThat(testSubject.retry(failed)).isEqualTo(MailLinkUploadState.Uploading("a.pdf", 0))
        assertThat(testSubject.retry(MailLinkUploadState.Completed("a.pdf", "https://x.example/a")))
            .isEqualTo(MailLinkUploadState.Completed("a.pdf", "https://x.example/a"))
    }

    @Test
    fun `send is blocked while any upload is running`() {
        val running = listOf(MailLinkUploadState.Idle, MailLinkUploadState.Uploading("a.pdf", 10))
        val done = listOf(MailLinkUploadState.Completed("a.pdf", "https://x.example/a"), MailLinkUploadState.Idle)

        assertThat(testSubject.canSend(running)).isFalse()
        assertThat(testSubject.canSend(done)).isTrue()
        assertThat(testSubject.canSend(emptyList())).isTrue()
    }
}
