# ADR irmail-0002: MailLink in the Android composer (S3 attachments)

- Status: **Proposed** (no implementation yet; decisions D1–D4 below are open)
- Applies to: `app-thunderbird` build of IR Mail
- Depends on: `irmail-0001-password-only-login-disable-oauth.md`
- Server side: MailLink backend (enrolment OTP, device tokens, `/v1/uploads`) in the `s3-maillink-handoff` repository

## Context

IR Mail should let a user attach files that are uploaded to S3 instead of being sent as MIME attachments. The email
then contains a CloudFront download link. Roadmap EPIC D.2 lists the required behaviour: a choice between a normal
attachment and an S3 MailLink, upload with progress, retry and cancel, the link inserted into the HTML and plain-text
body, and no send while an upload is still running.

Where the composer lives today:

- The compose screen is `legacy/ui/legacy/.../activity/MessageCompose.java` (Java, legacy module).
- Attachment handling is `legacy/ui/legacy/.../activity/compose/AttachmentPresenter.java` (Java, legacy).
- Message building is `legacy/core/.../message/SimpleMessageBuilder.java` and `PgpMessageBuilder.java` (Java, legacy).
- `feature/mail/message/composer` contains only helpers (signature sanitiser, HTML settings, a dialog). It does not
  contain the compose screen.

`AGENTS.md` says: do not add new logic to `legacy:*`, use Kotlin, Compose, Koin and MVI for new code, and do not change
module structure or dependency graphs without explicit approval.

Enrolment on the device is not implemented yet. The server already supports it: `POST /v1/enroll/request` and
`POST /v1/enroll/verify` return a device token, and `/v1/uploads` accepts that token.

## Decisions needed

### D1. Where the MailLink logic lives

- **Option A: inside the legacy composer.** Add the choice and upload to `AttachmentPresenter` and the send path in
  `MessageCompose`. Smallest change, fastest to ship. It adds new logic to `legacy:*`, which `AGENTS.md` discourages.
- **Option B: a new Kotlin module** (for example `feature/mail/mailink`, with `:api` and `:internal`) with a Compose
  UI and an MVI view model. The legacy composer calls it through a small interface. Follows `AGENTS.md`, but adds a
  module, so it needs an ADR and approval.

Recommendation: **B**, with the legacy composer changed only at the hook points.

### D2. Device enrolment and token storage

- Enrolment screen (email, then OTP code), calling `/v1/enroll/request` and `/v1/enroll/verify`. The same flow as the
  desktop add-on.
- Store the device token with Android Keystore encryption (for example `EncryptedSharedPreferences` or an equivalent
  Keystore-backed store). Never write the token to logs, analytics, or backups.
- Check which libraries upstream already uses for encrypted storage, and do not add a new one without approval.

### D3. Upload client

- Use OkHttp (already used by autodiscovery) for `/v1/uploads` and the presigned POST to S3.
- Progress and cancel through the request body. Retry only for network errors, with a small fixed limit.
- Files come from `content://` URIs through the Storage Access Framework. The file is streamed, not loaded into memory.
- The send button is blocked while any MailLink upload is running. On failure, the user sees a clear message and can
  retry or switch that file to a normal attachment.

### D4. Scope of the first delivery

- Single file per send first, or multiple files?
- Insert the links as a list in both HTML and plain text, or only plain text?
- Size threshold: suggest asking the user for files above a fixed size (for example 5 MB, the Thunderbird default), but
  let the user choose. This is not a company policy yet (roadmap section 3).

## Constraints (from AGENTS.md and the roadmap)

- Do not put tokens or email addresses in logs.
- Do not add permissions without justification. Network access already exists. Notifications are already in the add-on
  desktop, not in the app.
- Do not send a MIME attachment for a file that the user chose as MailLink.
- Download links are public bearer links until the access policy in roadmap section F.1 is decided. Do not use MailLink
  for sensitive files in pilot.

## Next steps (after decisions)

1. Write tests first for the upload state machine (idle, uploading, done, failed, cancelled) and for link insertion.
2. Implement enrolment (D2) and the upload client (D3) in the chosen module.
3. Connect the composer hook points (D1) and the send gate.
4. Build `assembleFullDebug` and run `test lint detekt spotlessCheck`, then test on device.
