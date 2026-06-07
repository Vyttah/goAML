# Phase 14.3 — Helm chart (`helm/goaml/`)

> **Status: ✅ DONE (2026-06-07).**
> Part of [../plans/phase-14-infra.md](../plans/phase-14-infra.md). Third step of Phase 14 — the Kubernetes
> deployment chart for the goAML image (EKS target).

---

## 1. Goal & why
Package the Phase-14.2 image for Kubernetes: a full Helm chart so an operator can `helm install`/`upgrade`
goAML with environment-specific values (image, ingress, autoscaling, AWS access, secrets).

## 2. What was built (`helm/goaml/`)
`Chart.yaml`, `values.yaml`, `.helmignore`, and `templates/`:
- `_helpers.tpl` — name/fullname/labels/selectorLabels/serviceAccountName/secretName.
- `serviceaccount.yaml` — optional SA with **IRSA** annotation slot (AWS access without static keys).
- `configmap.yaml` — non-secret env (datasource URL/user, redis host/port, AWS region/endpoint/bucket,
  allowed-origins, `SPRING_PROFILES_ACTIVE=prod`).
- `secret.yaml` / `externalsecret.yaml` — sensitive env via one of three strategies (below).
- `deployment.yaml` — probes, security context, `envFrom` configmap+secret, resources, `/tmp` emptyDir.
- `service.yaml`, `ingress.yaml` (TLS), `hpa.yaml` (CPU), `NOTES.txt`.

## 3. Key understanding / decisions
- **Probes hit the health groups** — liveness=`/actuator/health/liveness`, readiness=`/actuator/health/readiness`
  (so Redis being down doesn't fail readiness), plus a generous `startupProbe` for the Flyway-migrate first
  boot.
- **Three secret strategies** (D7): `externalSecret.enabled` (External Secrets Operator → a Secret),
  `secret.create` (chart-managed, dev/test only), or `secret.existingSecret` (out-of-band). The Deployment
  `envFrom` references the resolved secret name with `optional: true` so a no-secret render still applies.
- **Hardened by default** — `runAsNonRoot`, `readOnlyRootFilesystem: true` (with a writable `/tmp` emptyDir
  for the JVM + multipart), dropped capabilities, `RuntimeDefault` seccomp.
- **IRSA over static keys** — the ServiceAccount carries the AWS role annotation; the image's AWS SDK uses
  it (no access keys in the cluster).
- **Autoscaling on by default** (HPA, CPU 75%, 2–6 replicas); `replicaCount` applies only when HPA is off.
- **Config drift restart** — a `checksum/config` pod annotation rolls pods when the ConfigMap changes.

## 4. Verification
With a standalone `helm` v3.16.2 binary (helm isn't installed on this machine — fetched, used, removed):
- `helm lint helm/goaml` → **0 failures** (only the cosmetic "icon is recommended" info).
- `helm template` (defaults) renders ConfigMap, Deployment, HPA, Service, ServiceAccount.
- `helm template --set ingress.enabled --set externalSecret.enabled --set serviceAccount.annotations.<irsa>`
  → all 7 kinds incl. Ingress + ExternalSecret.
- `--set secret.create` renders a chart-managed Secret; probes/`readOnlyRootFilesystem`/`runAsNonRoot`
  confirmed present in the rendered Deployment.
- `Chart.yaml` + `values.yaml` parse as YAML.

## 5. Outcome
✅ A full, lint-clean Helm chart. Next: **14.4** — GitHub Actions (`ci.yml` backend+frontend gates;
`cd.yml` image build + secret-gated ECR push / `helm upgrade`).
