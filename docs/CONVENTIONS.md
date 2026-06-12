# goAML — Folder Structure & Conventions

This is the goAML-specific adaptation of **Vyttah's standard Spring Boot Service conventions**. It applies the
same organizing rules used across the Vyttah suite (vyttah-accounting, vyttah-masters, vyttah-auth, …) so an
engineer moving between services finds the same shape — **adapted to goAML's actual stack and the fact that
goAML is sold standalone.** Follow it for all new code; the existing tree is being migrated to it.

> **Authoritative source of the generic conventions:** the team's "Vyttah Spring Boot Service — Folder
> Structure & Conventions" doc. **This file records where goAML follows it and where it deliberately
> diverges** (decisions locked 2026-06-04). When the two disagree, **this file wins for goAML.**

---

## 0. Stack reality & the four divergence decisions

| Topic | Generic Vyttah convention | **goAML decision** | Why |
|---|---|---|---|
| Spring Boot / JDK | 2.7.8 / 17 | **3.3.5 / 21** | goAML chose the modern baseline (Jakarta namespace, records). |
| Shared library | All DTOs/enums/base entities in `com.vyttah:vth-common` | **Self-contained** — goAML keeps its own `model/base`, DTOs, enums in-repo, mirroring the conventions; **no `vth-common` dependency** | goAML is **sold standalone**; a hard `vth-common` dep breaks standalone shipping. (May extract to vth-common later for the suite build.) |
| DB migrations | Liquibase, `changelogs/VTH-<ticket>/` | **Flyway**, `src/main/resources/db/migration/{shared,tenant}/` | Already in use, Spring Boot 3 native. The shared/tenant split mirrors the doc's public/tenant changelog split. |
| Lombok + MapStruct | Mandatory everywhere | **Adopted on the JPA/web side only** | The JAXB-generated `domain.generated.*` and the `engine/` package stay plain (codegen / hand-tuned). |
| Inter-service clients (`api/`, Retrofit) | Present | **Not yet** — arrives with Phase 1.5 (accounting/screening integration). | No remote calls in the standalone core. |

Everything else (layer-first packaging, feature slices, interface+`Default*` services, entity/repo separation,
thin controllers, constructor injection, DTO naming, no entities out of controllers) **applies unchanged**.

---

## 1. The two product-core packages (goAML-specific — outside the CRUD layout)

goAML is not a pure CRUD service; its heart is a goAML-XML report engine. Two packages are **product-core**
and are organized by their own internal structure, **not** the generic `controller/service/repository` layers:

- **`domain/`** — the goAML report data model.
  - `domain/generated/**` — **xjc-generated JAXB** types from `goAMLSchema.xsd` (5.0.2). **Never hand-edit;**
    regenerated each build into `build/` (not committed). Bindings: `src/main/jaxb/goaml-bindings.xjb`.
  - `domain/adapter/` — JAXB `XmlAdapter`s reused by the generated model (e.g. `GoamlDateTimeAdapter`).
- **`engine/`** — build / validate / marshal / package goAML reports:
  `engine/{build,validation,marshal,packaging,jurisdiction,lookup}/`. Pure library code (no web, no JPA).

These keep their current shape. The conventions below govern **everything else** (the multi-tenant web/JPA app
around the engine).

---

## 2. Java package layout — layer-first, then feature

Base package `com.vyttah.goaml`. The **only** class at the root is `GoamlApplication`.

```
com/vyttah/goaml/
├── GoamlApplication.java
│
│   ── product core (see §1) ──
├── domain/{generated,adapter}/        # goAML JAXB model (generated) + adapters
├── engine/{build,validation,marshal,packaging,jurisdiction,lookup}/
│
│   ── application (layer-first, feature second) ──
├── config/                            # one @Configuration per concern
├── controller/<feature>/              # thin REST controllers (§5.1)
├── exception/                         # GlobalExceptionHandler (@RestControllerAdvice)
├── model/
│   ├── base/                          # self-contained base classes (BaseEntity, …) — our vth-common stand-in
│   ├── entity/<feature>/              # JPA @Entity — never leaves the service boundary
│   ├── dto/<feature>/                 # request/response/filter DTOs (naming carries direction — §5 / §8)
│   └── mapper/<feature>/              # MapStruct entity<->DTO mappers
├── repository/<feature>/              # Spring Data repositories (+ custom impls)
├── security/                          # self-managed JWT/RBAC (standalone — stays in-repo, not vth-common)
├── service/<feature>/                 # <Feature>Service interface + Default<Feature>Service impl
│   └── tenant/                        # tenant provisioning, etc.
└── util/                              # stateless <Thing>Util(s)
```

**Multi-tenancy infra** (the current `tenant/` package: `MultiTenancyHibernateConfig`,
`SchemaMultiTenantConnectionProvider`, `TenantContext`, `TenantIdentifierResolver`) lives under **`config/`**
(it is Spring/Hibernate configuration), except `TenantContext` which is a request-scoped holder — keep it with
the multi-tenancy config classes under `config/` (sub-package `config/tenant/` is acceptable to group them).

**The two organizing rules** (unchanged from the generic doc):
1. **Layer first, feature second.** Top level = architectural layer; one sub-package per business feature inside.
2. **Feature package names are all-lowercase, no separators**, and the *same* feature name is reused across
   every layer so a feature reads as a vertical slice.

### goAML features (current)

| Feature pkg | Schema tier | Entity | Repository | Notes |
|---|---|---|---|---|
| `tenant` | public (shared) | `Tenant` | `TenantRepository` | + `service/tenant` provisioning |
| `appuser` | public (shared) | `AppUser` | `AppUserRepository` | login identity |
| `role` | public (shared) | `Role` | `RoleRepository` | fixed RBAC roles |
| `jurisdiction` | public (shared) | `Jurisdiction` | `JurisdictionRepository` | per-FIU config row |
| `audit` | per-tenant | `AuditLog` | `AuditLogRepository` | tenant-schema table |

> **Schema tier is carried by `@Table(schema="public")` (shared) or no schema (per-tenant, resolved by the
> connection provider) + the Flyway location** (`db/migration/shared` vs `db/migration/tenant`) — **not** by the
> package. Packages are feature-first per the convention. The table above is the source-of-truth mapping.

---

## 3. Per-layer conventions (goAML)

### 3.1 Controllers — `controller/<feature>/`
`@RestController` + `@RequestMapping("<resource>")` + Lombok `@RequiredArgsConstructor` (constructor injection,
**no field `@Autowired`**). Thin: map DTO→entity via the mapper, delegate to the service, map entity→DTO back,
return `ResponseEntity<XDto>`. **No business logic, never return a JPA entity, and NEVER inject a repository —
all persistence access goes through a `service/<feature>/` service** (e.g. login logic lives in `AuthService`,
not the controller).

### 3.2 Services — `service/<feature>/`
Always **interface + `Default*` impl** in the same package: `AuditService` + `DefaultAuditService`,
`TenantProvisioningService` + `DefaultTenantProvisioningService`. Services speak in entities (CRUD) and DTOs
(queries/commands).

### 3.3 Repositories — `repository/<feature>/`
`<Entity>Repository extends JpaRepository<…>` (goAML's self-contained base, e.g. `BaseRepository`, may be
introduced in `model/base` / a `repository/base`). Derived queries + parameterized `@Query`. **Never
string-concatenate SQL.** Dynamic queries use a `Custom<Feature>Repository` + `…Impl` pair in the same package.

### 3.4 Entities — `model/entity/<feature>/`
Named `<Name>` with **no suffix** (`AppUser`, `Tenant`, `Role`, `Jurisdiction`, `AuditLog`). Extend a goAML
base (`model/base/BaseEntity`, …). Lombok `@Getter @Setter @NoArgsConstructor @AllArgsConstructor`, `@Entity`,
`@Table(...)`. Soft-delete / audit annotations where applicable. Entities never cross the service boundary —
always map to a DTO.

### 3.5 DTOs — `model/dto/<feature>/`
Self-contained (NOT vth-common). Naming carries direction — **do not create `request/`/`response/`
sub-packages.** Entity DTOs are `<Entity>Dto`; command/result DTOs that don't map to an entity keep an intent
name (e.g. `LoginRequest`/`LoginResponse`, `TenantProvisioningRequest`). Filters: `<Feature>FilterDto`.

### 3.6 Mappers — `model/mapper/<feature>/`
MapStruct `@Mapper(componentModel = "spring")` named `<Feature>Mapper`. Mappers own **all** entity↔DTO
conversion (no hand-written conversion in controllers/services). Generated sources go to `build/generated/`.

### 3.7 config / exception / util
`config/` — one class per concern (`SecurityConfig`, `SecurityCryptoConfig`, `JwtProperties`, the multi-tenancy
Hibernate classes, the scheduler/integration `*Properties`/`*ApiConfig`). *(An early draft listed a
`RabbitMQConfig` — the accounting integration is **REST, not RabbitMQ**, so there is none; see PROJECT.md.)*
`exception/GlobalExceptionHandler`
(`@RestControllerAdvice`). `util/` — stateless `<Thing>Util(s)`.

### 3.8 security
goAML keeps its **own** JWT/RBAC under `security/` (the generic doc puts security in vth-common, but goAML is
self-contained and is the identity authority — see the unified-auth plan).

---

## 4. Resources — `src/main/resources/`

```
resources/
├── application.yml (+ profile variants)
├── jurisdictions/<code>.yml        # per-FIU jurisdiction config (engine)
├── lookups/<code>/*.json           # per-jurisdiction lookup sets (engine)
├── xsd/goaml/5.0.2/goAMLSchema.xsd  # authoritative schema (xjc + XSD gate)
└── db/migration/
    ├── shared/   V*__*.sql          # public-schema migrations (Flyway)
    └── tenant/   V*__*.sql          # per-tenant-schema migrations (Flyway)
```

**Migration rules:** every schema change is a new `V<n>__<desc>.sql` in `shared/` (public) or `tenant/`
(per-tenant). Never edit an applied migration; never reuse a version number. (This is the Flyway equivalent of
the generic doc's Liquibase `VTH-<ticket>` rule.)

---

## 5. Tests — `src/test/java/`
Mirror the main tree exactly. The product-core engine tests live under `engine/` and `domain/generated/`.

---

## 6. Naming cheat-sheet (goAML)

| Thing | Pattern | Example |
|---|---|---|
| Main class | `GoamlApplication` | — |
| Feature package | all-lowercase, no separators | `jurisdiction`, `appuser`, `audit` |
| Controller | `<Feature>Controller` | `AuthController`, `AdminController` |
| Service iface / impl | `<Feature>Service` / `Default<Feature>Service` | `AuditService` / `DefaultAuditService` |
| Repository | `<Entity>Repository` | `AppUserRepository` |
| Entity | `<Name>` (no suffix) | `AppUser`, `Tenant`, `AuditLog` |
| Mapper | `<Feature>Mapper` | `TenantMapper` |
| DTO | `<Entity>Dto` / intent name / `<Feature>FilterDto` | `LoginRequest`, `TenantProvisioningRequest` |
| Util | `<Thing>Util(s)` | `ReportUtils` |
| Flyway migration | `V<n>__<desc>.sql` under `shared/`|`tenant/` | `V2__shared_core.sql` |

---

## 7. Hard rules (do / don't) — goAML

**DO:** keep the same feature-package name across every layer; interface + `Default*` for every service;
constructor injection via `@RequiredArgsConstructor`; MapStruct for entity↔DTO; one Flyway migration per schema
change in the right tier; keep `domain/`+`engine/` as product-core.

**DON'T:** return entities from controllers; **inject repositories into controllers** (go through a service);
put business logic in controllers or mappers; create `request/`/`response/` sub-packages; hand-write entity↔DTO
conversion; field `@Autowired`; string-concatenated SQL; edit applied Flyway migrations; **add a `vth-common`
dependency** (goAML stays self-contained); hand-edit `domain/generated/**`; add an `Entity` suffix to entity
class names.
