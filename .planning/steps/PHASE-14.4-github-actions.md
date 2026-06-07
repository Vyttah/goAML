# Phase 14.4 — GitHub Actions (CI + CD)

> **Status: ✅ DONE (2026-06-07).**
> Part of [../plans/phase-14-infra.md](../plans/phase-14-infra.md). Fourth step of Phase 14 — automated
> gates on every push and an image/deploy pipeline on main.

---

## 1. Goal & why
Run the same gates we run locally, automatically, and stand up a deploy pipeline that's ready the moment an
AWS account/cluster exists — without breaking when it doesn't.

## 2. What was built (`.github/workflows/`)
- **`ci.yml`** (push to any branch + PRs): two jobs.
  - **backend** — Temurin 21 + `gradle/actions/setup-gradle` → `./gradlew test jacocoTestCoverageVerification`
    (Testcontainers uses the runner's Docker); uploads test reports on failure.
  - **frontend** — Node 18 (npm cache) → `npm ci` → `typecheck` → `lint` → `test` → `build`.
- **`cd.yml`** (push to `main`): one job that **always builds** the production image (exercising the
  Dockerfile), then runs **secret-gated** deploy steps.

## 3. Key understanding / decisions
- **Gate parity** — the CI commands are exactly the local ones, so green-locally ⇒ green-in-CI (and vice
  versa). The frontend `npm test` is `vitest run` (per package.json).
- **Deploy is gated, not removed** (D5) — secrets can't be used directly in `if:`, so `cd.yml` maps
  `secrets.AWS_ROLE_ARN != ''` to a job-level `DEPLOY_ENABLED` env string; the AWS-creds / ECR-push /
  `helm upgrade` steps run only when it's `'true'`. With no secrets, it builds the image and prints a
  "deploy skipped" note — **inert but ready**.
- **OIDC, not static keys** — `permissions: id-token: write` + `aws-actions/configure-aws-credentials`
  assuming `AWS_ROLE_ARN` (matches the IRSA/no-static-keys posture).
- **Image always built on main** — validates the Dockerfile continuously even before a cluster exists.
- **Required secrets (documented in the workflow header)**: `AWS_ROLE_ARN`, `AWS_REGION`, `ECR_REPOSITORY`,
  `EKS_CLUSTER`.

## 4. Verification
- Both workflows parse as valid YAML (`ci.yml` → jobs backend/frontend; `cd.yml` → job image).
- Gate commands match the locally-verified ones (backend `test jacocoTestCoverageVerification`; frontend
  `typecheck`/`lint`/`test`/`build`).
- Actions can't be executed here (no remote/runner); execution will occur once the repo has a GitHub remote.

## 5. Outcome
✅ CI runs the real gates; CD builds the image and is wired (gated) for ECR + EKS. Next: **14.5** —
docs/planning sync + merge `phase-14/infra` → `main`.
