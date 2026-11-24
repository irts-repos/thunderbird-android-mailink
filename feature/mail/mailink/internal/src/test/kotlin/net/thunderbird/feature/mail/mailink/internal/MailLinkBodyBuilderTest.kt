package net.thunderbird.feature.mail.mailink.internal

import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.doesNotContain
import assertk.assertions.isEqualTo
import kotlin.test.Test
import net.thunderbird.feature.mail.mailink.api.MailLinkAttachment

class MailLinkBodyBuilderTest {

    private val testSubject = MailLinkBodyBuilder

    private val link = MailLinkAttachment(
        fileName = "Jadwal Migrasi.xlsx",
        sizeBytes = 10_650L,
        url = "https://d16k5yh5wbwlib.cloudfront.net/attachments/x/Jadwal.xlsx",
    )

    @Test
    fun `no links leaves the body unchanged`() {
        assertThat(testSubject.appendToPlainText("Halo", emptyList())).isEqualTo("Halo")
        assertThat(testSubject.appendToHtml("<p>Halo</p>", emptyList())).isEqualTo("<p>Halo</p>")
    }

    @Test
    fun `plain text gets name, size and url`() {
        val result = testSubject.appendToPlainText("Halo", listOf(link))

        assertThat(result).isEqualTo(
            "Halo\n\nFile dikirim lewat S3 MailLink:\n" +
                "- Jadwal Migrasi.xlsx (10 KB): https://d16k5yh5wbwlib.cloudfront.net/attachments/x/Jadwal.xlsx",
        )
    }

    @Test
    fun `plain text with empty body does not start with blank lines`() {
        val result = testSubject.appendToPlainText("", listOf(link))

        assertThat(result.startsWith("File dikirim lewat S3 MailLink:")).isEqualTo(true)
    }

    @Test
    fun `html block is inserted before closing body tag`() {
        val html = "<html><body><p>Halo</p></body></html>"

        val result = testSubject.appendToHtml(html, listOf(link))

        assertThat(result).contains("<ul><li>Jadwal Migrasi.xlsx (10 KB): ")
        assertThat(result.indexOf("</body>")).isEqualTo(result.lastIndexOf("</body>"))
        assertThat(result.endsWith("</body></html>")).isEqualTo(true)
    }

    @Test
    fun `html without body tag appends the block`() {
        val result = testSubject.appendToHtml("<p>Halo</p>", listOf(link))

        assertThat(result.startsWith("<p>Halo</p><div>")).isEqualTo(true)
    }

    @Test
    fun `file name and url are escaped in html`() {
        val hostile = MailLinkAttachment(
            fileName = "<img src=x onerror=alert(1)>.pdf",
            sizeBytes = 1L,
            url = "https://x.example/a?b=1&c=\"2\"",
        )

        val result = testSubject.appendToHtml("", listOf(hostile))

        assertThat(result).doesNotContain("<img")
        assertThat(result).contains("&lt;img src=x onerror=alert(1)&gt;.pdf")
        assertThat(result).contains("href=\"https://x.example/a?b=1&amp;c=&quot;2&quot;\"")
    }

    @Test
    fun `sizes are shown in KB below one MB and MB with one decimal above`() {
        assertThat(testSubject.formatSize(0L)).isEqualTo("1 KB")
        assertThat(testSubject.formatSize(10_650L)).isEqualTo("10 KB")
        assertThat(testSubject.formatSize(3_565_158L)).isEqualTo("3.4 MB")
        assertThat(testSubject.formatSize(52_428_800L)).isEqualTo("50.0 MB")
    }
}
