# Step R — Restructure to the Vyttah folder conventions

> **Status: ✅ DONE (2026-06-04) — R1 (moves) + R2 (Lombok) + R3 (MapStruct) all landed; full suite green.**
> Reference: [../../docs/CONVENTIONS.md](../../docs/CONVENTIONS.md) (the goAML-adapted conventions).
> This is a **structural refactor** (no feature behaviour change) done **before Step 5**, while the codebase
> is still small (~40 files). Proposed as **three reviewable sub-commits R1 → R2 → R3.**

---

## 1. What & why

The current layout mixes concerns the conventions separate — most visibly **JPA entities and repositories
live together** in `persistence/{shared,tenant}/`, services have **no interface + `Default*` split**, and
controllers carry **inline DTOs** under `web/`. This step reorganizes the multi-tenant web/JPA app around the
report engine into the standard **layer-first, feature-second** layout, adopts **Lombok** on the JPA/web side,
and wires **MapStruct** infrastructure. The product-core `domain/` (JAXB) and `engine/` packages are **left
as-is** (see CONVENTIONS §1). Decisions locked 2026-06-04: self-contained (no `vth-common`), keep Flyway,
Lombok+MapStruct on JPA/web only, full structural move now.

## 2. Target layout (the moves)

| From | To |
|---|---|
| `web/auth/AuthController.java` | `controller/auth/AuthController.java` |
| `web/admin/AdminController.java` | `controller/admin/AdminController.java` |
| `web/me/MeController.java` | `controller/me/MeController.java` |
| `web/GlobalExceptionHandler.java` | `exception/GlobalExceptionHandler.java` |
| `web/auth/{LoginRequest,LoginResponse}.java` | `model/dto/auth/` |
| `web/me/MeResponse` (inline/record) | `model/dto/me/MeResponse.java` |
| `service/tenant/TenantProvisioningRequest.java` | `model/dto/tenant/` |
| `persistence/shared/AppUserEntity.java` | `model/entity/appuser/AppUser.java` *(drop `Entity` suffix)* |
| `persistence/shared/TenantEntity.java` | `model/entity/tenant/Tenant.java` |
| `persistence/shared/RoleEntity.java` | `model/entity/role/Role.java` |
| `persistence/shared/JurisdictionEntity.java` | `model/entity/jurisdiction/Jurisdiction.java` |
| `persistence/tenant/AuditLogEntity.java` | `model/entity/audit/AuditLog.java` |
| `persistence/shared/*Repository.java` | `repository/{appuser,tenant,role,jurisdiction}/` |
| `persistence/tenant/AuditLogRepository.java` | `repository/audit/` |
| `tenant/{MultiTenancyHibernateConfig,SchemaMultiTenantConnectionProvider,TenantContext,TenantIdentifierResolver}.java` | `config/tenant/` |
| `service/audit/AuditService.java` (concrete) | `service/audit/AuditService.java` (**interface**) + `DefaultAuditService.java` |
| `service/tenant/TenantProvisioningService.java` (concrete) | `TenantProvisioningService` (**interface**) + `DefaultTenantProvisioningService.java` |

Unchanged: `domain/**`, `engine/**`, `security/**`, `config/{SecurityConfig,SecurityCryptoConfig,JwtProperties}`,
`GoamlApplication`. Test tree mirrors every move.

> **Schema tier preserved** via `@Table(schema="public")` / no-schema + Flyway `db/migration/{shared,tenant}/`
> (unchanged) — the `shared`/`tenant` package split goes away in favour of feature packages (CONVENTIONS §2).

## 3. Sub-steps (each its own commit, each compiles)

**R1 — Package moves + entity rename + service extraction (structure; minimal behaviour move).**
- Move files per §2 (fix `package` declarations + every import; mirror in `src/test`).
- **Rename entities to drop the `Entity` suffix** (`AppUserEntity`→`AppUser`, `TenantEntity`→`Tenant`,
  `RoleEntity`→`Role`, `JurisdictionEntity`→`Jurisdiction`, `AuditLogEntity`→`AuditLog`) across all call sites.
- Extract `AuditService`/`TenantProvisioningService` interfaces with `Default*` impls (`@Service` on the impl).
- **Extract `AuthService` + `DefaultAuthService`** and move `AuthController`'s login logic (repo access, schema
  resolution, token issue, audit) into it — the controller must not inject `AppUserRepository`/`TenantRepository`
  (per your directive: controllers go through services). `MeController`/`AdminController` already touch no repo;
  only their inline DTOs move (`MeResponse` → `model/dto/me/`).
- Delete the now-empty `web/`, `persistence/`, top-level `tenant/` packages. Behaviour is preserved (login
  flow identical, just relocated behind `AuthService`).

**R2 — Adopt Lombok on the JPA/web side.**
Add Lombok to `build.gradle`. Convert the 5 entities + `UserPrincipal` + DTO classes to Lombok
(`@Getter/@Setter/@NoArgsConstructor/@AllArgsConstructor` for entities; `@RequiredArgsConstructor` for
constructor-injected components, replacing explicit constructors). Leave `domain.generated.*` and `engine/`
untouched.

**R3 — Wire MapStruct infrastructure.**
Add MapStruct to `build.gradle` (annotation processor + `build/generated` source set) and a base mapper
contract under `model/mapper/`. **No feature mappers yet** — there are no entity↔DTO CRUD endpoints today
(auth/me are command-style); mappers arrive with report CRUD (Phase 7). This step only makes MapStruct ready
so feature work follows the convention from day one. *(If you'd rather not add an unused dependency yet, R3
can be skipped and folded into Phase 7 — your call; default = wire it now.)*

## 4. Honest caveats / risks

1. **Verification needs Docker.** The code being moved (auth, persistence, tenant provisioning) is covered by
   **Testcontainers tests** (`AuthFlowTest`, `RbacTest`, `SharedSchemaTest`, `TenantProvisioningServiceTest`,
   `TenantIsolationTest`) that need Postgres. I can guarantee **compile** + the non-DB suite at each sub-step;
   to confirm runtime behaviour I need **`docker compose up -d postgres`** running. Please start it before I
   execute, or accept compile-only verification until you can run the DB suite.
2. **Big import churn, low semantic risk.** R1 is moves + import rewrites; mechanical but wide. Mitigation:
   separate commits, compile after each, `git mv` so history follows.
3. **`AuthController` thinning is now IN scope** (per your directive). Its login logic moves verbatim into
   `DefaultAuthService`; the controller calls `authService.login(request)`. Behaviour is unchanged — the
   Testcontainers `AuthFlowTest`/`RbacTest` guard it. (Docker baseline confirmed green before starting.)
4. **Lombok on entities** changes how getters/setters are generated. If anything relied on a specific
   constructor signature, R2 surfaces it at compile time. Low risk for 5 entities.

## 5. Done criteria

- Layout matches CONVENTIONS §2; no `web/`, `persistence/`, or top-level `tenant/` packages remain; entities
  in `model/entity/<feature>/`, repositories in `repository/<feature>/`, DTOs in `model/dto/<feature>/`,
  controllers in `controller/<feature>/`, `GlobalExceptionHandler` in `exception/`.
- Every service is an interface + `Default*` impl.
- Lombok in use on the JPA/web side; `domain.generated`/`engine` untouched. MapStruct wired (R3).
- `./gradlew compileJava compileTestJava` clean; non-DB suite green; **full suite green with Docker up.**

## 6. What this step does NOT do

No new feature behaviour, no `vth-common` dependency, no Flyway→Liquibase change, no touching
`domain.generated`/`engine`. (The login flow is relocated behind `AuthService` but functionally unchanged.)
Step 5 (goldens / report-type expansion) resumes after this.

---

## Outcome

### R1 — Package moves + entity rename + service extraction ✅ (2026-06-04)

**Done & green — full suite (incl. Testcontainers DB tests) passes.** The multi-tenant web/JPA app now follows
the layer-first layout:
`controller/{auth,admin,me}/`, `exception/`, `model/entity/{appuser,tenant,role,jurisdiction,audit}/`,
`model/dto/{auth,me,tenant}/`, `repository/{appuser,tenant,role,jurisdiction,audit}/`,
`service/{auth,audit,tenant}/`, `config/tenant/`. Product-core `domain/` + `engine/` untouched.

- **Entities renamed (dropped `Entity` suffix):** `AppUser`, `Tenant`, `Role`, `Jurisdiction`, `AuditLog`.
- **Services are now interface + `Default*`:** `AuditService`/`DefaultAuditService`,
  `TenantProvisioningService`/`DefaultTenantProvisioningService`, and a **new `AuthService`/`DefaultAuthService`**.
- **`AuthController` is now thin** — login logic (repo access, schema resolution, token issue, audit) moved into
  `DefaultAuthService`; the controller injects only `AuthService`. No controller touches a repository.
- **`MeResponse`** extracted from `MeController` to `model/dto/me/`.
- Empty `web/`, `persistence/`, top-level `tenant/` packages removed. `git mv` preserved history.

**Verify:** `docker info` (Docker Desktop up) → `./gradlew test` → BUILD SUCCESSFUL.

**Small remaining item (not blocking):** the **test tree** still uses some old package paths
(`...persistence.SharedSchemaTest`, `...tenant.TenantIsolationTest`, `...security.AuthFlowTest/RbacTest`).
They compile & pass; mirroring them to the new feature packages is a low-risk follow-up (can fold into R2 or a
later tidy). Noted so it's not silently skipped.

### R2 — Adopt Lombok (JPA/web side) ✅ (2026-06-04)

**Done & green.** Added Lombok (Spring-BOM-managed; `compileOnly` + `annotationProcessor`, test variants).
- Entities use `@Getter` (and field-level `@Setter` on `AuditLog`'s mutable columns), dropping ~50 lines of
  boilerplate getters/setters. Custom constructors, `@PrePersist`/`@PreUpdate`, and `AppUser.addRole` kept.
- Constructor-injected components now use `@RequiredArgsConstructor` (explicit constructors removed):
  `AuthController`, `DefaultAuthService`, `DefaultTenantProvisioningService`, `AppUserDetailsService`,
  `JwtAuthFilter`, `SecurityConfig`. `DefaultAuditService` + `JwtService` keep explicit constructors (they
  have construction logic — `TransactionTemplate` / key derivation). Generated `domain.generated.*` + `engine/`
  untouched.

### R3 — Wire MapStruct infrastructure ✅ (2026-06-04)

**Done & green — wiring verified end-to-end.** Added MapStruct `1.6.3` + `lombok-mapstruct-binding 0.2.0`
(so MapStruct sees Lombok accessors) to `build.gradle`. Seeded the first real mapper to prove the processor
runs and to satisfy "controllers never return entities": `model/dto/tenant/TenantDto` +
`model/mapper/tenant/TenantMapper` (`@Mapper(componentModel = "spring")`, `Tenant → TenantDto`). A pure-POJO
`TenantMapperTest` confirms the generated `TenantMapperImpl` maps correctly (no Spring/DB needed). Feature
mappers for report CRUD arrive in Phase 7.

---

## Step R — DONE (2026-06-04)

The app follows `docs/CONVENTIONS.md`: layer-first feature packages, entities separated from repositories
(no `Entity` suffix), interface + `Default*` services, thin controllers (no repo injection — login behind
`AuthService`), Lombok on the JPA/web side, MapStruct wired. Product-core `domain/` + `engine/` unchanged.
Full suite (incl. Testcontainers DB tests) green. **Next = Step 5** (XSD-first goldens / report-type expansion;
carry-overs: transmode lookup vs XSD enum reconciliation, then transaction goldens).
