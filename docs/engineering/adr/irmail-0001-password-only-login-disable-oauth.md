# ADR irmail-0001: Password-only login for IR Mail (OAuth disabled)

- Status: **Accepted** (fork-specific; not part of upstream numbering)
- Applies to: `app-thunderbird` build of IR Mail (`applicationId` `id.indoretreading.mail`)
- Upstream baseline: commit `8ddc69d148e4bbefc5bb504a2342a0195647acd2`

## Context

IR Mail is used to sign in to the company mail server `mail.indoretreading.id` (Mailcow). Users do not use Google or
Microsoft accounts for this app. The upstream OAuth flow (Google, Microsoft, AOL, Fastmail, Yahoo) is configured by
`TbOAuthConfigurationFactory` and is bound to the upstream application ID and the upstream OAuth client registrations.
With the new `applicationId`, those registrations no longer match this app, so OAuth sign-in cannot work.

## Decision

1. **Disable OAuth, do not delete it.** `ThunderbirdKoinModule` binds `OAuthConfigurationFactory` to a factory that
   returns no configurations when `OAUTH_ENABLED = false`. The OAuth feature modules and the per-flavor
   `TbOAuthConfigurationFactory` files stay in the tree.
2. **Why this works:** `GetAutoDiscovery` removes `AuthenticationType.OAuth2` from the discovered options when
   `OAuthConfigurationProvider.getConfiguration(hostname)` returns `null`. With no configurations, OAuth2 is never
   offered, and the password (IMAP/SMTP) flow is used.
3. **Re-enable path:** set `OAUTH_ENABLED = true` and register OAuth clients for the new application ID before use.
4. Module structure, dependency graph, and API/internal boundaries are unchanged (see `AGENTS.md`).

## Consequences

- Positive: small, reversible change; upstream merges stay practical; OAuth code and tests keep compiling.
- Negative: the disabled OAuth code is still shipped in the APK (dead code, no runtime path). Upstream OAuth tests
  still run.
- Not covered: OAuth redirect URIs and client IDs in `TbOAuthConfigurationFactory` still reference upstream IDs. They are
  not used while `OAUTH_ENABLED = false`.
- Accounts that are already configured with OAuth2 in a previous install are not migrated by this ADR. A new install is
  the expected path for IR Mail.

## Related

- Server preset for `indoretreading.id` (IMAP 993 SSL/TLS, SMTP 465 SSL/TLS; verified reachable): planned separately.
- MailLink integration (S3 attachments): see the MailLink handoff repository.
