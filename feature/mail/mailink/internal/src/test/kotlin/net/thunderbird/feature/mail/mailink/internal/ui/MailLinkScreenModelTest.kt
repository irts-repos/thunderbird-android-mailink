package net.thunderbird.feature.mail.mailink.internal.ui

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import kotlin.test.Test
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import net.thunderbird.feature.mail.mailink.api.MailLinkAttachment
import net.thunderbird.feature.mail.mailink.api.MailLinkClient
import net.thunderbird.feature.mail.mailink.api.MailLinkFailureReason
import net.thunderbird.feature.mail.mailink.api.MailLinkResult
import net.thunderbird.feature.mail.mailink.api.MailLinkTokenStore
import net.thunderbird.feature.mail.mailink.api.MailLinkUploadRequest
import net.thunderbird.feature.mail.mailink.api.MailLinkUploadState

class MailLinkScreenModelTest {

    @Test
    fun `blank email shows message and sends nothing`() = runTest {
        val client = FakeClient()
        val testSubject = MailLinkScreenModel(client, FakeTokenStore(), this)

        testSubject.requestCode()
        advanceUntilIdle()

        assertThat(testSubject.state.value.message).isEqualTo(MailLinkMessage.EMAIL_REQUIRED)
        assertThat(client.requestedEmails).isEqualTo(emptyList())
    }

    @Test
    fun `request code success shows code step`() = runTest {
        val testSubject = MailLinkScreenModel(FakeClient(), FakeTokenStore(), this)

        testSubject.onEmailChanged("user@indoretreading.id")
        testSubject.requestCode()
        advanceUntilIdle()

        assertThat(testSubject.state.value.codeSent).isEqualTo(true)
        assertThat(testSubject.state.value.message).isEqualTo(MailLinkMessage.CODE_SENT)
    }

    @Test
    fun `verify success saves the device token and marks enrolled`() = runTest {
        val tokens = FakeTokenStore()
        val client = FakeClient(verifyResult = MailLinkResult.Success("device-token"))
        val testSubject = MailLinkScreenModel(client, tokens, this)

        testSubject.onEmailChanged("user@indoretreading.id")
        testSubject.onCodeChanged("123456")
        testSubject.verifyCode()
        advanceUntilIdle()

        assertThat(tokens.stored).isEqualTo("device-token")
        assertThat(testSubject.state.value.enrolled).isEqualTo(true)
        assertThat(testSubject.state.value.message).isEqualTo(MailLinkMessage.ENROLLED)
    }

    @Test
    fun `code input keeps only six digits`() = runTest {
        val testSubject = MailLinkScreenModel(FakeClient(), FakeTokenStore(), this)

        testSubject.onCodeChanged("12-34 5678")

        assertThat(testSubject.state.value.code).isEqualTo("123456")
    }

    @Test
    fun `upload without enrolment is refused`() = runTest {
        val testSubject = MailLinkScreenModel(FakeClient(), FakeTokenStore(), this)
        testSubject.pickFile(file())

        testSubject.startUpload()
        advanceUntilIdle()

        assertThat(testSubject.state.value.message).isEqualTo(MailLinkMessage.NOT_ENROLLED)
        assertThat(testSubject.state.value.upload).isEqualTo(MailLinkUploadState.Idle)
    }

    @Test
    fun `upload success stores the link and completes`() = runTest {
        val tokens = FakeTokenStore(stored = "device-token")
        val link = MailLinkAttachment("a.xlsx", 10L, "https://cdn.example/a.xlsx")
        val client = FakeClient(uploadResult = MailLinkResult.Success(link))
        val testSubject = MailLinkScreenModel(client, tokens, this)
        testSubject.pickFile(file())

        testSubject.startUpload()
        advanceUntilIdle()

        assertThat(testSubject.state.value.link).isEqualTo(link)
        assertThat(testSubject.state.value.upload)
            .isEqualTo(MailLinkUploadState.Completed("a.xlsx", "https://cdn.example/a.xlsx"))
        assertThat(testSubject.state.value.message).isEqualTo(MailLinkMessage.UPLOAD_DONE)
    }

    @Test
    fun `upload network failure can be retried`() = runTest {
        val tokens = FakeTokenStore(stored = "device-token")
        val client = FakeClient(uploadResult = MailLinkResult.Failure(MailLinkFailureReason.NETWORK))
        val testSubject = MailLinkScreenModel(client, tokens, this)
        testSubject.pickFile(file())

        testSubject.startUpload()
        advanceUntilIdle()

        assertThat(testSubject.state.value.upload)
            .isEqualTo(MailLinkUploadState.Failed("a.xlsx", MailLinkFailureReason.NETWORK))
        assertThat(testSubject.state.value.message).isEqualTo(MailLinkMessage.NETWORK)
    }

    @Test
    fun `picking a new file clears the previous link`() = runTest {
        val tokens = FakeTokenStore(stored = "device-token")
        val link = MailLinkAttachment("a.xlsx", 10L, "https://cdn.example/a.xlsx")
        val client = FakeClient(uploadResult = MailLinkResult.Success(link))
        val testSubject = MailLinkScreenModel(client, tokens, this)
        testSubject.pickFile(file())
        testSubject.startUpload()
        advanceUntilIdle()

        testSubject.pickFile(file(name = "b.pdf"))

        assertThat(testSubject.state.value.link).isNull()
        assertThat(testSubject.state.value.upload).isEqualTo(MailLinkUploadState.Idle)
    }

    @Test
    fun `sign out clears the token and resets the screen`() = runTest {
        val tokens = FakeTokenStore(stored = "device-token")
        val testSubject = MailLinkScreenModel(FakeClient(), tokens, this)

        testSubject.signOut()
        advanceUntilIdle()

        assertThat(tokens.stored).isNull()
        assertThat(testSubject.state.value.enrolled).isEqualTo(false)
    }

    private fun file(name: String = "a.xlsx") = PickedFile(
        name = name,
        sizeBytes = 10L,
        mimeType = "application/octet-stream",
        openStream = { "data".byteInputStream() },
    )

    private class FakeTokenStore(var stored: String? = null) : MailLinkTokenStore {
        override fun getToken(): String? = stored
        override fun saveToken(token: String) {
            stored = token
        }
        override fun clearToken() {
            stored = null
        }
    }

    private class FakeClient(
        private val requestResult: MailLinkResult<Unit> = MailLinkResult.Success(Unit),
        private val verifyResult: MailLinkResult<String> = MailLinkResult.Failure(MailLinkFailureReason.INVALID_CODE),
        private val uploadResult: MailLinkResult<MailLinkAttachment> =
            MailLinkResult.Failure(MailLinkFailureReason.NETWORK),
    ) : MailLinkClient {
        val requestedEmails = mutableListOf<String>()

        override suspend fun requestEnrollmentCode(email: String): MailLinkResult<Unit> {
            requestedEmails.add(email)
            return requestResult
        }

        override suspend fun verifyEnrollmentCode(email: String, code: String): MailLinkResult<String> = verifyResult

        override suspend fun upload(
            token: String,
            request: MailLinkUploadRequest,
            onProgress: (Int) -> Unit,
        ): MailLinkResult<MailLinkAttachment> {
            onProgress(50)
            return uploadResult
        }
    }
}
