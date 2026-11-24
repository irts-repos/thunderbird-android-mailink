package net.thunderbird.feature.mail.mailink.internal

import net.thunderbird.feature.mail.mailink.api.MailLinkAttachment

/**
 * Inserts MailLink download links into an outgoing message body.
 *
 * Plain text gets a simple list. HTML gets the same list with every value escaped, so a file name cannot inject markup.
 */
object MailLinkBodyBuilder {

    private const val HEADING = "File dikirim lewat S3 MailLink:"
    private const val BYTES_PER_MB = 1024L * 1024L
    private const val BYTES_PER_KB = 1024L
    private const val DECIMAL_BASE = 10L

    fun appendToPlainText(body: String, links: List<MailLinkAttachment>): String {
        if (links.isEmpty()) return body
        val lines = links.joinToString(separator = "\n") { link ->
            "- ${link.fileName} (${formatSize(link.sizeBytes)}): ${link.url}"
        }
        return buildString {
            append(body)
            if (body.isNotEmpty()) append("\n\n")
            append(HEADING)
            append("\n")
            append(lines)
        }
    }

    fun appendToHtml(html: String, links: List<MailLinkAttachment>): String {
        if (links.isEmpty()) return html
        val items = links.joinToString(separator = "") { link ->
            "<li>${escapeHtml(link.fileName)} (${formatSize(link.sizeBytes)}): " +
                "<a href=\"${escapeHtml(link.url)}\">${escapeHtml(link.url)}</a></li>"
        }
        val block = "<div><p>${escapeHtml(HEADING)}</p><ul>$items</ul></div>"
        val closingBody = html.lastIndexOf("</body>", ignoreCase = true)
        return if (closingBody >= 0) {
            html.substring(0, closingBody) + block + html.substring(closingBody)
        } else {
            html + block
        }
    }

    internal fun formatSize(bytes: Long): String {
        return when {
            bytes >= BYTES_PER_MB -> "${tenths(bytes, BYTES_PER_MB)} MB"
            else -> "${maxOf(1L, bytes / BYTES_PER_KB)} KB"
        }
    }

    /** One decimal place, rounded to nearest, without floating point. For example 3.4 MB. */
    private fun tenths(value: Long, unit: Long): String {
        val scaled = (value * DECIMAL_BASE + unit / 2) / unit
        return "${scaled / DECIMAL_BASE}.${scaled % DECIMAL_BASE}"
    }

    internal fun escapeHtml(value: String): String {
        val out = StringBuilder(value.length)
        for (c in value) {
            when (c) {
                '&' -> out.append("&amp;")
                '<' -> out.append("&lt;")
                '>' -> out.append("&gt;")
                '"' -> out.append("&quot;")
                '\'' -> out.append("&#39;")
                else -> out.append(c)
            }
        }
        return out.toString()
    }
}
