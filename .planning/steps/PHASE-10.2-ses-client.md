# Phase 10.2 — SES email client (`integration/aws/`)

> **Status: ✅ DONE (2026-06-06).**
> Part of [../plans/phase-10-notifications.md](../plans/phase-10-notifications.md). Second step of Phase 10.

---

## 1. Goal & why
Add the outbound-email seam the notification service (10.3) will call. Mirrors the Phase 6/8 AWS-client
pattern (interface + `Default*` over the SDK + a typed access exception + a LocalStack-gated IT). No firing
or gating logic here — that lives in the service.

## 2. What was built

| File | Role |
|---|---|
| `integration/aws/SesClient` | Interface: `void send(String to, String subject, String body)` — sends from the configured verified sender. |
| `integration/aws/DefaultSesClient` | `@Component` over AWS SDK v2 `SesV2Client`; resolves `from` from `goaml.notifications.email.from` (eager fail-fast); all SDK failures → `SesAccessException`. |
| `integration/aws/SesAccessException` | Typed failure (unconfigured sender, blank recipient, SDK error). |
| `config/aws/AwsConfig` | `+ @Bean SesV2Client` — regional + default-creds in prod; endpoint-override + static dummy creds under LocalStack (same shape as the S3/Secrets beans). |
| `build.gradle` | `+ implementation 'software.amazon.awssdk:sesv2'` (BOM-pinned). |

## 3. Key understanding / decisions
- **SESv2 (modern API), not v1.** `DefaultSesClient` uses `SesV2Client.sendEmail` — the recommended API.
  Kept even though community LocalStack can't run it (below), because the production client must be
  prod-correct; the unit test covers every branch deterministically.
- **The bean exists regardless of the `email.enabled` flag.** Gating lives in the service (10.3) so it can
  still write in-app rows when email is off. `DefaultSesClient` only fails fast if asked to send with an
  unconfigured `from`.
- **`from` comes from `NotificationProperties`, not `AwsProperties`** — it's a notification concern, kept
  with the other `goaml.notifications.*` settings.
- **Eager `from()` resolution** — the Phase 8 lesson (resolve config before the SDK lambda so a mock/fail
  path is actually exercised).

## 4. Tests
- **`DefaultSesClientTest`** (mocked `SesV2Client`): captures the `SendEmailRequest` and asserts
  from/to/subject/body; blank recipient → `SesAccessException`; unconfigured sender → `SesAccessException`
  ("from"); an `SdkClientException` maps to `SesAccessException`.
- **`SesClientIT`** (`@Tag("localstack")`): verifies an identity then sends via LocalStack. **`sesv2` is a
  LocalStack Pro feature** — community returns *"API for service 'sesv2' not yet implemented or pro
  feature"*. The IT detects that `SesV2Exception` in `@BeforeAll` and **skips cleanly** (`assumeTrue(false)`),
  alongside the socket-reachability skip — so it runs only where SESv2 genuinely exists (LocalStack Pro /
  real AWS) and never reds the suite on community LocalStack.

## 5. Verification
`./gradlew test --tests DefaultSesClientTest --tests SesClientIT` → **BUILD SUCCESSFUL**; 4 unit tests pass,
the IT skips (community LocalStack has no SESv2). `git status` scoped to Phase 10.2 files.

---

## Outcome
✅ The SES email seam is in place (prod-correct SESv2, deterministically tested; IT runs on Pro/real SES).
Next: **10.3** — `NotificationService`: resolve recipients (author + tenant MLROs), write in-app rows, and
dispatch gated email via this client.
