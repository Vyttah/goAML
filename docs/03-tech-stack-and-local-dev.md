# 03 — Tech Stack & Local Dev

> Exact versions, every dependency, and how to build/run/test on your machine.

---

## 1. The stack at a glance

| Layer | Choice | Version |
|-------|--------|---------|
| Language / runtime | **Java** (LTS) | **21** |
| Framework | **Spring Boot** | **3.3.5** |
| Build tool | **Gradle** (Groovy DSL) | wrapper **8.10.2** |
| JDK provisioning | Foojay toolchain resolver | plugin 0.8.0 |
| Database | **PostgreSQL** | 16 (alpine, local) |
| Migrations | **Flyway** | (Spring-managed version) |
| ORM | Hibernate via **Spring Data JPA** | (Boot-managed) |
| Auth | Spring Security + **JJWT** (HS256) | jjwt 0.12.6 |
| XML binding | **Jakarta JAXB** | api 4.0.2 / runtime 4.0.5 |
| Domain codegen | **xjc** via the `com.github.bjornvester.xjc` plugin (generates the domain model from the goAML XSD) | plugin 1.9.0 / xjc 4.0.5 |
| Boilerplate / mapping | **Lombok** + **MapStruct** (JPA/web side only) | lombok (BOM-managed) / mapstruct 1.6.3 |
| AWS / cache | **AWS SDK v2** (Secrets Manager) + **Redis** (Spring Data Redis) | awssdk BOM 2.28.16 |
| Tests | JUnit 5, AssertJ, Mockito, Testcontainers, XMLUnit, **WireMock**, spring-security-test | wiremock 3.9.2 |
| Coverage | **JaCoCo** (≥90%/≥80% gate on Phase 6 packages) | plugin (Gradle-bundled) |
| Coordinates | group `com.vyttah`, name `goaml` | version `0.1.0-SNAPSHOT` |

The Spring Boot `io.spring.dependency-management` plugin supplies the Boot **platform BOM**, so most
Spring / Flyway / Postgres / Testcontainers artifacts are **unpinned** (versions inherited from Boot
3.3.5). Only the non-Boot libraries below are pinned explicitly.

---

## 2. Every dependency (from `build.gradle`)

Grouped by purpose. `(impl)` = `implementation`, `(rt)` = `runtimeOnly`, `(test)` = `testImplementation`,
`(ap)` = `annotationProcessor`, `(co)` = `compileOnly`.

**Boilerplate / mapping** (JPA/web side only — the generated domain + engine use neither)
- `org.projectlombok:lombok` (co + ap, plus `testCompileOnly`/`testAnnotationProcessor`) — version BOM-managed
- `org.mapstruct:mapstruct:1.6.3` (impl) — **pinned**
- `org.mapstruct:mapstruct-processor:1.6.3` (ap + test ap) — **pinned**
- `org.projectlombok:lombok-mapstruct-binding:0.2.0` (ap + test ap) — **pinned**; lets MapStruct see
  Lombok-generated accessors during annotation processing

**Web / validation**
- `spring-boot-starter-web` (impl)
- `spring-boot-starter-validation` (impl)

**Observability**
- `spring-boot-starter-actuator` (impl)

**Persistence / cache**
- `spring-boot-starter-data-jpa` (impl)
- `org.flywaydb:flyway-core` (impl)
- `org.flywaydb:flyway-database-postgresql` (impl)
- `org.postgresql:postgresql` (rt)
- `spring-boot-starter-data-redis` (impl) — Phase 6; caches per-tenant goAML B2B session tokens

**AWS (Phase 6)**
- `software.amazon.awssdk:bom:2.28.16` (platform) + `software.amazon.awssdk:secretsmanager` (impl) —
  per-tenant goAML credentials from Secrets Manager (KMS decryption is transparent on read)

**Security / JWT**
- `spring-boot-starter-security` (impl)
- `io.jsonwebtoken:jjwt-api:0.12.6` (impl) — **pinned**
- `io.jsonwebtoken:jjwt-impl:0.12.6` (rt) — **pinned**
- `io.jsonwebtoken:jjwt-jackson:0.12.6` (rt) — **pinned**

**XML / JAXB** (Jakarta namespace — `jakarta.xml.bind`, not legacy `javax`)
- `jakarta.xml.bind:jakarta.xml.bind-api:4.0.2` (impl) — **pinned**
- `org.glassfish.jaxb:jaxb-runtime:4.0.5` (impl) — **pinned**
- The **`com.github.bjornvester.xjc` plugin (1.9.0)** generates the domain model from the goAML XSD at
  build time (`xjcVersion 4.0.5`, `useJakarta`, `-extension`). Output → `build/generated/sources/xjc`,
  package `com.vyttah.goaml.domain.generated` — **not committed** (see [04 — Domain Model](04-domain-model.md)).

**Test**
- `spring-boot-starter-test` (test)
- `spring-boot-testcontainers` (test)
- `spring-security-test` (test)
- `org.testcontainers:junit-jupiter` (test)
- `org.testcontainers:postgresql` (test)
- `org.xmlunit:xmlunit-core:2.10.0` (test) — **pinned**
- `org.xmlunit:xmlunit-assertj3:2.10.0` (test) — **pinned**
- `org.wiremock:wiremock-standalone:3.9.2` (test) — **pinned**; stubs the goAML B2B REST API
- Mockito (via `spring-boot-starter-test`) — unit tests mock the AWS SDK / Redis / collaborators

**Coverage**
- The **`jacoco`** plugin: `jacocoTestReport` (after `test`) + `jacocoTestCoverageVerification` — a
  **≥90% instruction / ≥80% branch** gate scoped to the Phase 6 packages (`b2b`, `integration.aws`,
  `config.aws`); generated domain excluded. Report at `build/reports/jacoco/test/html/index.html`.

⚠️ **Not present yet** (deferred to later phases, noted as comments in `build.gradle`): Spring AI MCP,
picocli (CLI), the Node/Vite frontend build plugin, and the S3 / SES AWS clients (Phases 8 / 10).

---

## 3. Prerequisites

You do **not** need to install Java 21 manually — Gradle auto-provisions it via the Foojay toolchain.
You need:
- **Docker** (for Postgres via `docker-compose`).
- A network connection on first build (Gradle downloads the JDK + dependencies).

---

## 4. Build, run, test

```bash
# 1. Start the dev dependencies. Postgres uses Testcontainers for tests, but the Phase 6
#    LocalStack + Redis integration tests run against these compose services (they skip cleanly
#    if absent). Start all three for the complete suite:
docker compose up -d postgres localstack redis

# 2. Run the full test suite (auto-provisions Java 21; spins Testcontainers Postgres)
./gradlew test
#   With coverage gate: ./gradlew test jacocoTestCoverageVerification

# 3. Run the app
./gradlew bootRun
#   → boots on http://localhost:8080
#   → health probe: http://localhost:8080/actuator/health

# Build the deployable jar
./gradlew bootJar           # output: build/libs/goaml-0.1.0-SNAPSHOT.jar

# Build the Docker image (multi-stage; builds the jar with tests skipped)
docker build -t goaml:dev .
```

### Regenerating golden XML files
The engine tests compare marshalled XML against committed "golden" files. After an *intentional*
schema/fixture change, regenerate them:

```bash
./gradlew test -Dgoaml.golden.regenerate=true
```

This overwrites `src/test/resources/golden/*.xml` with current engine output instead of asserting.
Review the diff before committing. (More in [08 — Testing](08-testing.md).)

---

## 5. Configuration (`src/main/resources/application.yml`)

Every key currently in the config, with its env override and default:

**Application**
```yaml
spring.application.name: goaml
```

**Datasource** (all override-able via env)
```yaml
spring.datasource.url:      ${SPRING_DATASOURCE_URL:jdbc:postgresql://localhost:5432/goaml}
spring.datasource.username: ${SPRING_DATASOURCE_USERNAME:goaml}
spring.datasource.password: ${SPRING_DATASOURCE_PASSWORD:goaml}
```

**JPA / Hibernate**
```yaml
spring.jpa.hibernate.ddl-auto: none      # Flyway owns the schema; 'validate' is avoided on purpose
spring.jpa.open-in-view: false
spring.jpa.properties.hibernate.jdbc.time_zone: UTC
spring.jpa.properties.hibernate.multiTenancy: SCHEMA   # schema-per-tenant
```
> `ddl-auto: none` (not `validate`) is deliberate: tenant-scoped entity tables live only inside
> `tenant_<id>` schemas, never in `public`, so a startup `validate` against `public` would fail.

**Flyway**
```yaml
spring.flyway.enabled:   true
spring.flyway.locations: classpath:db/migration/shared
spring.flyway.schemas:   public
```
> Note: only the **shared** migrations run at startup. The **tenant** migrations
> (`classpath:db/migration/tenant`) are run *programmatically* during tenant provisioning — see
> [07 — Persistence & Migrations](07-persistence-and-migrations.md).

**Server**
```yaml
server.port: 8080
server.forward-headers-strategy: framework
```

**Actuator (management)**
```yaml
management.endpoints.web.exposure.include: health,info,prometheus
management.endpoint.health.probes.enabled: true
management.endpoint.health.show-details: when-authorized
management.endpoint.health.group.readiness.include: db,readinessState
```

**JWT (custom `goaml.jwt.*` properties)**
```yaml
goaml.jwt.secret:                  ${GOAML_JWT_SECRET:dev-secret-please-replace-...min-256-bits}
goaml.jwt.issuer:                  goaml
goaml.jwt.access-token-ttl-minutes: 15
```
> The secret must be **≥ 32 bytes (256 bits)** for HS256 or the app fails to start. The dev fallback is
> only for local use; production sources it from AWS Secrets Manager.

**Logging** — console pattern includes a `correlationId` MDC field for request tracing.

There are **no Spring profiles** defined in this file yet (single baseline config).

---

## 6. Docker Compose services (`docker-compose.yml`)

**postgres**
- image `postgres:16-alpine`, container `goaml-postgres`
- `POSTGRES_DB/USER/PASSWORD = goaml/goaml/goaml`
- port `5432:5432`, volume `goaml-pg`, healthcheck via `pg_isready`

**localstack** (only used from Phase 6 onward — emulates AWS locally)
- image `localstack/localstack:3`, container `goaml-localstack`
- `SERVICES=s3,secretsmanager,kms,ses`, `AWS_DEFAULT_REGION=me-central-1`
- port `4566:4566`, volume `goaml-localstack`

> `me-central-1` is the AWS UAE (Middle East) region — chosen for data-residency alignment with the
> UAE FIU. For Phase 1–5 work you only need the `postgres` service.

---

## 7. Dockerfile (multi-stage)

- **Stage `build`** — `eclipse-temurin:21-jdk`: copies the Gradle wrapper + sources, runs
  `./gradlew --no-daemon bootJar -x test` (tests skipped in the image build).
- **Runtime stage** — `eclipse-temurin:21-jre`: copies `build/libs/*.jar` → `/app/app.jar`,
  `EXPOSE 8080`, `ENTRYPOINT ["java","-jar","/app/app.jar"]`.

⚠️ Layered image, non-root user, JVM tuning, and the React build stage are deferred to Phase 13/14.

---

## 8. The main class & config beans

- **`GoamlApplication`** — a vanilla `@SpringBootApplication`. **No** `@EnableScheduling` /
  `@EnableAsync` / CLI handling yet (scheduling arrives in Phase 9, CLI in Phase 12). Default boot mode
  is the web app.
- **`config/SecurityCryptoConfig`** — `@Configuration` defining a single `PasswordEncoder` bean
  (`BCryptPasswordEncoder`), used to hash admin passwords during tenant provisioning. The full security
  filter chain lives in `security/SecurityConfig` (see [06](06-multitenancy-and-security.md)).

---

## 9. Coding conventions you'll see

- **Records** for DTOs and value types (`LoginRequest`, `ReportHeader`, `Attachment`, `PackagingLimits`,
  `JurisdictionConfig`, `ValidationMessage`, `DpmsrReportInput`, `ValidatedReport`, etc.).
- **Constructor injection** everywhere (no field `@Autowired`); Lombok `@RequiredArgsConstructor` on the
  JPA/web side.
- **Layer-first packages** (`controller/<feature>`, `service/<feature>`, `repository/<feature>`,
  `model/{entity,dto,mapper}/<feature>`) per [`../docs/CONVENTIONS.md`](CONVENTIONS.md). Services are an
  **interface + `Default*` impl**; controllers are thin and **never inject repositories directly**.
- **Lombok** (`@Getter`/`@Setter`/`@RequiredArgsConstructor`) + **MapStruct** mappers on entities/DTOs —
  **not** on the generated domain or the engine, which stay plain.
- Entity classes carry **no `Entity` suffix** (`AppUser`, `Tenant`, `Role`, `Jurisdiction`, `AuditLog`).
- **`BigDecimal`** for all money/amounts/quantities — never `double`/`float`.
- **`OffsetDateTime`** for all timestamps, normalized to UTC.
- Status/role values are **plain Strings** in the DB (documented via SQL comments), not DB enums.
- Tests: JUnit 5 + AssertJ; integration tests use **real Postgres via Testcontainers** (no DB mocking).

---

**Next:** [04 — Domain Model](04-domain-model.md) — the JAXB POJOs behind the XML.
