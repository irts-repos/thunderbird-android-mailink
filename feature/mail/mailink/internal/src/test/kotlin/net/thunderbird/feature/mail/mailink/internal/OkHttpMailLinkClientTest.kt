package net.thunderbird.feature.mail.mailink.internal

import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.doesNotContain
import assertk.assertions.isEqualTo
import assertk.assertions.isGreaterThan
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlinx.coroutines.runBlocking
import net.thunderbird.feature.mail.mailink.api.MailLinkFailureReason
import net.thunderbird.feature.mail.mailink.api.MailLinkResult
import net.thunderbird.feature.mail.mailink.api.MailLinkUploadRequest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer

class OkHttpMailLinkClientTest {

    private lateinit var server: MockWebServer
    private lateinit var testSubject: OkHttpMailLinkClient

    @BeforeTest
    fun setUp() {
        server = MockWebServer()
        server.start()
        val base = server.url("/").toString().trimEnd('/')
        testSubject = OkHttpMailLinkClient(baseUrl = base, httpClient = OkHttpClient(), requireHttps = false)
    }

    @AfterTest
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `enrollment request accepted returns success`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(202))

        val result = testSubject.requestEnrollmentCode("user@indoretreading.id")

        assertThat(result).isEqualTo(MailLinkResult.Success(Unit))
        val recorded = server.takeRequest()
        assertThat(recorded.path).isEqualTo("/v1/enroll/request")
        assertThat(recorded.body.readUtf8()).contains("\"email\":\"user@indoretreading.id\"")
    }

    @Test
    fun `enrollment request service down maps to network`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(503))

        val result = testSubject.requestEnrollmentCode("user@indoretreading.id")

        assertThat(result).isEqualTo(MailLinkResult.Failure(MailLinkFailureReason.NETWORK))
    }

    @Test
    fun `verify returns device token from response`() = runBlocking {
        server.enqueue(
            MockResponse().setResponseCode(200)
                .setBody("{\"device_token\":\"tok-123\",\"idle_expiry_days\":90}"),
        )

        val result = testSubject.verifyEnrollmentCode("user@indoretreading.id", "123456")

        assertThat(result).isEqualTo(MailLinkResult.Success("tok-123"))
    }

    @Test
    fun `verify wrong code maps to invalid code`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(401).setBody("{\"error\":\"invalid_code\"}"))

        val result = testSubject.verifyEnrollmentCode("user@indoretreading.id", "000000")

        assertThat(result).isEqualTo(MailLinkResult.Failure(MailLinkFailureReason.INVALID_CODE))
    }

    @Test
    fun `verify without token is rejected`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(200).setBody("{}"))

        val result = testSubject.verifyEnrollmentCode("user@indoretreading.id", "123456")

        assertThat(result).isEqualTo(MailLinkResult.Failure(MailLinkFailureReason.REJECTED))
    }

    @Test
    fun `upload sends token in header only and streams file after signed fields`() = runBlocking {
        val base = server.url("/").toString().trimEnd('/')
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                "{\"method\":\"POST\",\"uploadUrl\":\"$base/s3\",\"fileUrl\":\"https://cdn.example/a.xlsx\"," +
                    "\"fields\":{\"key\":\"attachments/u/a.xlsx\",\"policy\":\"p\",\"x-amz-signature\":\"s\"}}",
            ),
        )
        server.enqueue(MockResponse().setResponseCode(201))

        val progress = mutableListOf<Int>()
        val content = "hello mailink".toByteArray()
        val request = MailLinkUploadRequest(
            fileName = "a.xlsx",
            mimeType = "application/octet-stream",
            sizeBytes = content.size.toLong(),
            openStream = { content.inputStream() },
        )

        val result = testSubject.upload(token = "SECRET-TOKEN", request = request) { progress.add(it) }

        assertThat(result is MailLinkResult.Success).isEqualTo(true)
        val ticket = server.takeRequest()
        assertThat(ticket.path).isEqualTo("/v1/uploads")
        assertThat(ticket.getHeader("Authorization")).isEqualTo("Bearer SECRET-TOKEN")
        assertThat(ticket.body.readUtf8()).doesNotContain("SECRET-TOKEN")

        val s3 = server.takeRequest()
        val body = s3.body.readUtf8()
        assertThat(body.indexOf("name=\"key\"")).isGreaterThan(-1)
        assertThat(body.indexOf("name=\"x-amz-signature\"")).isGreaterThan(body.indexOf("name=\"key\""))
        assertThat(body.indexOf("name=\"file\"")).isGreaterThan(body.indexOf("name=\"x-amz-signature\""))
        assertThat(body).contains("hello mailink")
        assertThat(progress.last()).isEqualTo(100)
    }

    @Test
    fun `upload with expired token maps to not enrolled`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(401).setBody("{\"error\":\"invalid credentials\"}"))

        val result = testSubject.upload(
            token = "old",
            request = MailLinkUploadRequest("a.xlsx", "application/octet-stream", 3L) { "abc".byteInputStream() },
            onProgress = {},
        )

        assertThat(result).isEqualTo(MailLinkResult.Failure(MailLinkFailureReason.NOT_ENROLLED))
    }

    @Test
    fun `upload too large maps to too large`() = runBlocking {
        server.enqueue(
            MockResponse().setResponseCode(400).setBody("{\"error\":\"file size must be 1..52428800 bytes\"}"),
        )

        val result = testSubject.upload(
            token = "tok",
            request = MailLinkUploadRequest("a.zip", "application/zip", 999L) { "x".byteInputStream() },
            onProgress = {},
        )

        assertThat(result).isEqualTo(MailLinkResult.Failure(MailLinkFailureReason.TOO_LARGE))
    }

    @Test
    fun `ticket urls must be https when https is required`() {
        val strict = OkHttpMailLinkClient(baseUrl = "https://mail.example", httpClient = OkHttpClient())
        val lax =
            OkHttpMailLinkClient(baseUrl = "http://localhost:8080", httpClient = OkHttpClient(), requireHttps = false)

        assertThat(strict.isAcceptableTicketUrl("https://s3.example/bucket", requireHttps = true)).isEqualTo(true)
        assertThat(strict.isAcceptableTicketUrl("http://s3.example/bucket", requireHttps = true)).isEqualTo(false)
        assertThat(lax.isAcceptableTicketUrl("http://localhost/bucket", requireHttps = false)).isEqualTo(true)
        assertThat(lax.isAcceptableTicketUrl("", requireHttps = false)).isEqualTo(false)
    }

    @Test
    fun `logout sends the token in the header and succeeds on 200`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(200).setBody("{\"status\":\"logged_out\"}"))

        val result = testSubject.revokeDeviceToken("tok-123")

        assertThat(result).isEqualTo(MailLinkResult.Success(Unit))
        val recorded = server.takeRequest()
        assertThat(recorded.path).isEqualTo("/v1/enroll/logout")
        assertThat(recorded.getHeader("Authorization")).isEqualTo("Bearer tok-123")
    }

    @Test
    fun `constructor refuses http base url in production mode`() {
        val failed = runCatching {
            OkHttpMailLinkClient(baseUrl = "http://mail.example", httpClient = OkHttpClient())
        }.isFailure

        assertThat(failed).isEqualTo(true)
    }
}
