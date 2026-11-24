package net.thunderbird.feature.mail.mailink.internal

import java.io.InputStream
import okhttp3.MediaType
import okhttp3.RequestBody
import okio.BufferedSink
import okio.source

/**
 * Streams a file to the request body in chunks and reports progress as 0..100.
 * The file is never held in memory as a whole.
 */
internal class ProgressRequestBody(
    private val contentType: MediaType?,
    private val sizeBytes: Long,
    private val openStream: () -> InputStream,
    private val onProgress: (Int) -> Unit,
) : RequestBody() {

    override fun contentType(): MediaType? = contentType

    override fun contentLength(): Long = sizeBytes

    override fun writeTo(sink: BufferedSink) {
        openStream().use { input ->
            input.source().use { source ->
                var written = 0L
                var lastReported = -1
                while (true) {
                    val read = source.read(sink.buffer, CHUNK_BYTES)
                    if (read == -1L) break
                    written += read
                    sink.emit()
                    val percent = if (sizeBytes > 0) {
                        (written * PERCENT_MAX / sizeBytes).toInt().coerceIn(0, PERCENT_MAX.toInt())
                    } else {
                        PERCENT_MAX.toInt()
                    }
                    if (percent != lastReported) {
                        lastReported = percent
                        onProgress(percent)
                    }
                }
            }
        }
    }

    private companion object {
        const val CHUNK_BYTES = 8L * 1024L
        const val PERCENT_MAX = 100L
    }
}
