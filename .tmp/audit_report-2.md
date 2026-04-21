# Delivery Acceptance and Project Architecture Audit
**Project:** Anju Accompanying Medical Appointment Operation Management System  
**Audit Date:** 2026-04-21  
**Auditor:** Static-only review — no code was executed, no Docker was started, no tests were run.

---

## 1. Verdict

**Overall Conclusion: Partial Pass**

The delivery demonstrates a structurally complete, professionally organized Spring Boot backend that directly addresses the Prompt's four business domains (Property, Appointment, Finance, File). Core flows—authentication, conflict detection, rescheduling rules, financial bookkeeping, file chunking, audit logging, and encryption—are implemented with real logic rather than stubs. Documentation is adequate for a human reviewer to attempt setup.

However, **two Blockers and three High-severity issues** prevent unconditional acceptance:

1. **Blocker — Open Role Escalation:** Any unauthenticated caller may self-register with `ADMIN` or any other privileged role, undermining all RBAC.
2. **Blocker — Unit Test Cannot Compile:** `unit_tests/pom.xml` points to a non-existent source path `../api/src/main/java`; the `api/` directory was previously deleted.
3. **High — API Test Assertion Mismatch:** The integration test asserts a CSV `Content-Disposition` response header that the actual endpoint never sets.
4. **High — Secondary Passwords Logged:** The audit log aspect serializes all method arguments, including `X-Secondary-Password` header values, into `request_payload`.
5. **High — Wrong Mock in Conflict Unit Test:** A reschedule-conflict test mocks `existsConflict` but the implementation calls `existsConflictExcludingId`; the test will fail under Mockito strict stubbing.

---

## 2. Scope and Static Verification Boundary

**What was reviewed:**
- `repo/README.md`, `.env.example`, `repo/docker-compose.yml`
- `repo/backend/pom.xml`, `repo/backend/Dockerfile`
- `repo/backend/src/main/resources/application.yml`
- `repo/backend/database/migrations/init.sql`
- All Java source files under `repo/backend/src/main/java/com/anju/`
- Unit tests: `repo/backend/unit_tests/src/test/java/com/anju/domain/**`
- API/integration tests: `repo/backend/API_tests/src/test/java/com/anju/integration/ApiFunctionalTest.java`
- Both test `pom.xml` files
- `docs/` directory (api-spec.md, design.md referenced but not deeply reviewed since code is authoritative)

**What was not reviewed:**
- Postman collection internals (referenced in README, not code)
- `trajectory.json`
- Previous audit reports in `.tmp/`

**Intentionally not executed:**
- Docker / Docker Compose
- Maven build
- Any test suite
- Nacos, MySQL, or any external service

**Claims requiring manual verification:**
- Runtime behaviour of JPA queries (conflict detection, soft-delete filter)
- AES/GCM encryption round-trip correctness
- Scheduled task (`@Scheduled`) firing behaviour
- Whether `NACOS_CONFIG_ENABLED=false` is stable across profile switches
- File upload storage on the container filesystem

---

## 3. Repository / Requirement Mapping Summary

**Core business goal (from Prompt):** Offline operational closed-loop backend for a medical appointment + property management service, supporting administrators, reviewers, schedulers, finance staff, and frontline staff.

**Major requirement areas extracted:**

| Prompt Domain | Key Requirements |
|---|---|
| Authentication | Local username/password, BCrypt, secondary password, JWT, RBAC (min-privilege), audit trail |
| Property | CRUD, unique code, status lifecycle (LISTED/DELISTED), compliance review, media association, rent/deposit Decimal |
| Appointment | Conflict detection (staff + resource), 15/30/60/90 min durations, rescheduling (≤2, ≥24h notice), cancellation penalty (10% or 50 RMB), auto-release PENDING after 15 min, state machine |
| Finance | Bookkeeping (no external payment integration), multi-channel, refunds, daily settlement, exception flag, invoice lifecycle, import with field-mapping and idempotency |
| File | Chunked upload, resumable, hash deduplication/instant-upload, concurrency throttle, preview, multi-version rollback, recycle bin (30 days) |
| Infrastructure | Spring Boot monolith, MySQL, Nacos for config/discovery, Docker offline |
| Security | BCrypt, AES-GCM sensitive field encryption, audit log (operator + timestamp + changes) |

**Mapped implementation areas:**
- Auth: `AuthController`, `AuthService`, `JwtService`, `JwtAuthenticationFilter`, `JwtTokenBlacklistService`, `SecurityConfig`
- Property: `PropertyController`, `PropertyService`, `Property` entity, `PropertyRepository`
- Appointment: `AppointmentController`, `AppointmentService`, `Appointment`, `AppointmentRepository`
- Finance: `TransactionController`, `TransactionService`, `Transaction`, `TransactionRepository`
- File: `FileController`, `FileService`, `SysFile`, `SysFileRepository`
- Cross-cutting: `AuditLogAspect`, `CryptoConverter`, `GlobalExceptionHandler`
- DB: `init.sql` (5 tables + indexes + seed data)
- Infra: `docker-compose.yml`, `Dockerfile`, `application.yml`

---

## 4. Section-by-Section Review

### 4.1 Hard Gates

#### 4.1.1 Documentation and static verifiability

**Conclusion: Pass**

- `repo/README.md` provides clear `docker compose up -d` startup command, exposed ports (8080, 3306, 8848), and test execution instructions (`./run_tests.sh`). (`README.md:1-54`)
- `.env.example` documents all required environment variables. (`../.env.example:1-14`)
- Postman collection referenced for interactive exploration. (`README.md:38-54`)
- Project structure is clear; entry point is `AnjuApplication.java`; route registration is standard Spring MVC annotations.
- One gap: `.env.example` does not include `SECURITY_JWT_SECRET`, so a reviewer following docs exactly would use the default insecure key. This is a documentation-security gap, not a blocking documentation failure.

**Manual verification note:** Runtime startup requires Docker; cannot be confirmed statically.

---

#### 4.1.2 Material deviation from Prompt

**Conclusion: Partial Pass**

The implementation is correctly centred on the Prompt's business goal. All four explicit domains are present. However, several Prompt-specified capabilities are absent or weakened:

- **Not Implemented — Available time slot management:** The Prompt specifies "maintenance of available time slots" as a distinct appointment-domain feature. There is no `TimeSlot` entity or endpoint. Appointment conflict detection is done reactively, not via a pre-declared slot grid.
- **Not Implemented — Service type configuration:** The Prompt specifies "configuration of service types and standard durations (15/30/60/90 minutes)." Duration validation exists (`AppointmentService.java:218`) but there is no `ServiceType` entity or admin configuration endpoint.
- **Not Implemented — Audit log query/export endpoint:** The Prompt mentions "audit and export" as an interface family. The `sys_audit_log` table is populated but no query or export API is provided.
- **Nacos effectively disabled by default:** `application.yml:27-28` defaults `NACOS_CONFIG_ENABLED:false` and `NACOS_DISCOVERY_ENABLED:false`. The `.env.example` also sets them false. The Prompt states "configuration and registration managed solely by a locally deployed Nacos" which implies active use. This is disabled in the default delivery.
- **Property vacancy periods:** The Prompt mentions "management of available rental periods and vacancy periods." The `Property` entity tracks `startDate`/`endDate` but there is no explicit vacancy-period sub-entity or endpoint.

---

### 4.2 Delivery Completeness

#### 4.2.1 Core requirements coverage

**Conclusion: Partial Pass**

| Requirement | Implemented | Evidence |
|---|---|---|
| Property CRUD + code uniqueness + status lifecycle | Yes | `PropertyController`, `PropertyService` |
| Property compliance review workflow | Yes | `PATCH /api/properties/{id}/compliance-review` |
| Property media association | Partial | `mediaRefs` as newline-text VARCHAR(2000); no file-linked structured storage |
| Appointment conflict detection (staff + resource) | Yes | `AppointmentRepository.java:20-53` |
| Duration validation (15/30/60/90 min) | Yes | `AppointmentService.java:218` |
| Reschedule ≤2 / ≥24h | Yes | `AppointmentService.java:95-100` |
| Cancellation penalty (10% of order or 50 RMB) | Partial | Hardcoded `orderAmount = 500.00` instead of actual transaction amount (`AppointmentService.java:169`) |
| Auto-release PENDING after 15 min | Yes | `AppointmentService.java:179-187` (`@Scheduled`) |
| State machine lifecycle (PENDING/CONFIRMED/RESCHEDULED/CANCELLED/RELEASED) | Yes | `AppointmentService.java:28-29`, `ensureReschedulable`, `ensureCancellable` |
| Finance bookkeeping (no external payment) | Yes | `TransactionController.java:45-51` |
| Refunds (original / non-original channel) | Partial | Refund uses original channel; no explicit non-original-method path |
| Daily settlement + exception flag + invoice lifecycle | Yes | `TransactionService.java:173-264` |
| Finance import with field mapping + idempotency | Yes | `TransactionService.java:81-123` |
| File chunked + resumable upload | Yes | `FileService.java:106-145` |
| Hash deduplication / instant upload | Yes | `FileService.java:56-64` |
| Concurrency + bandwidth throttle | Yes | `FileService.java:282-305` |
| File preview (image/audio/video/PDF/document) | Yes | `FileService.java:227-259` |
| Multi-version rollback | Yes | `FileService.java:203-224` |
| Recycle bin (30 days) | Yes | `FileService.java:272-280`, `SysFileRepository.java:33-38` |
| BCrypt passwords + policy | Yes | `AuthService.java:20`, `SecurityConfig.java:53-55` |
| Sensitive field AES-GCM encryption | Yes | `CryptoConverter.java` on `User.email`, `User.phone` |
| Audit log (operator + timestamp + key fields) | Yes | `AuditLogAspect.java` |
| Secondary password for sensitive operations | Yes | `AuthController.java:74-81`, used on compliance review and invoice issue |
| Role-based access control | Partial — escalation via open registration (see Issue #1) |

---

#### 4.2.2 End-to-end deliverable

**Conclusion: Pass**

The project is not a partial demo. It contains:
- Complete multi-module Maven build (`pom.xml` + `Dockerfile`)
- Database DDL with seed data (`init.sql`)
- Docker Compose orchestrating MySQL, Nacos, backend, and test-runner
- Postman collection for interactive testing
- Unit + API test suites
- `README.md` with setup instructions

No purely hardcoded mock behavior replaces real business logic.

---

### 4.3 Engineering and Architecture Quality

#### 4.3.1 Structure and module decomposition

**Conclusion: Pass**

Domain packages (`auth`, `appointment`, `property`, `finance`, `file`) map cleanly to business areas. Cross-cutting concerns (AOP audit, global exception handler, crypto converter, security config) are in dedicated packages. No class has unbounded responsibility. The `HomeController` is a minimal status endpoint rather than a dumping ground.

No redundant files observed. The only anomaly is the unit test `pom.xml` source path (structural defect, not design defect).

---

#### 4.3.2 Maintainability and extensibility

**Conclusion: Pass (with noted limitations)**

- Constructor injection throughout; no `@Autowired` field injection.
- `@Transactional` boundaries are appropriate; read-only transactions use `TxType.SUPPORTS`.
- Business rules are co-located with their domain services, not scattered across controllers.
- `Result<T>` wrapper provides a consistent API response envelope.
- Status enumerations are string constants (not Java enums), which reduces compile-time safety but is an acceptable trade-off for a flexible domain.
- `mediaRefs` stored as newline-separated VARCHAR(2000) string in `Property` is a tight coupling that limits extension (e.g., adding per-media metadata).

---

### 4.4 Engineering Details and Professionalism

#### 4.4.1 Error handling, logging, validation, API design

**Conclusion: Pass**

- `GlobalExceptionHandler.java` handles 12+ exception types with appropriate HTTP status codes and structured `Result` envelopes.
- Business errors use structured `BusinessException(code, message)` with domain-coded integer error codes.
- Input validation uses Bean Validation (`@NotNull`, `@Valid`, `@Positive`, `@Size`) at DTO level with detailed error messages.
- Logging uses SLF4J with `log.warn` for business exceptions and `log.error` for system exceptions. Audit logging is persistent to DB.
- API design follows REST conventions (POST/PUT/PATCH/DELETE/GET semantics used correctly).
- `@Scheduled` auto-release task is present and meaningful.
- `JwtTokenBlacklistService` provides logout token revocation (though in-memory only — see Issue #6).

**Notable gap:** `Transaction` entity is serialized directly in API responses, exposing internal JPA structure (e.g., `idempotencyKey`, `createdAt`) without a dedicated DTO/response class.

---

#### 4.4.2 Real product vs. demo

**Conclusion: Pass**

Evidence of production-intent implementation:
- AES-256-GCM encryption with random IV per field (`CryptoConverter.java:32-43`)
- Strict key requirement with test-profile bypass (`CryptoConverter.java:78-83`)
- Idempotency keys on appointment creation and financial bookkeeping
- Conflict detection SQL accounts for both staff and resource overlap
- Soft-delete on `Property` with `@SQLDelete` + `@Where` Hibernate annotations
- Concurrency throttle using `ConcurrentHashMap<Long, AtomicInteger>` per user
- `@EnableMethodSecurity` + `@PreAuthorize` at method granularity
- Docker Compose readiness probe before test execution

---

### 4.5 Prompt Understanding and Requirement Fit

**Conclusion: Partial Pass**

Core business semantics are understood and correctly implemented:
- Accompanying medical appointment closed-loop with scheduler/staff/admin roles
- Offline financial bookkeeping (no external payment integration)
- Property compliance review workflow with secondary password
- File domain with deduplication optimizing repeated uploads

Deviations noted:
- Cancellation penalty uses hardcoded order amount rather than actual transaction amount
- Available time slot grid is absent; conflict detection is reactive not pre-declared
- Nacos is integrated as a dependency but functionally disabled in defaults
- No audit log query API (data is stored but not retrievable through the API)

---

### 4.6 Aesthetics (Frontend)

**Conclusion: Not Applicable**

This is a backend-only Java API service. No frontend is included or required.

---

## 5. Issues / Suggestions (Severity-Rated)

---

### ISSUE-01 — [BLOCKER] Open Role Escalation via Self-Registration

**Conclusion:** Any unauthenticated caller can self-register as `ADMIN`, `FINANCE`, `SCHEDULER`, `REVIEWER`, or any other privileged role.

**Evidence:**
- `repo/backend/src/main/java/com/anju/domain/auth/AuthController.java:32` — `/auth/register` is `@permitAll()` (no auth required)
- `repo/backend/src/main/java/com/anju/domain/auth/dto/RegisterRequest.java:26` — `role` field accepts any string up to 32 chars with no restriction
- `repo/backend/src/main/java/com/anju/domain/auth/AuthService.java:48` — `user.setRole(StringUtils.hasText(request.getRole()) ? request.getRole().trim().toUpperCase() : "STAFF")` applies the caller-supplied role without validation

**Impact:** The entire RBAC model is bypassed. An attacker can register with `ADMIN` role and gain full system access, create privileged transactions, override compliance reviews, and read all finance data.

**Minimum fix:** Restrict the `role` field to `STAFF` on public registration. Allow role promotion only through an authenticated, admin-only endpoint. Add an allowlist validation: reject any `role` other than `STAFF` in `AuthService.register`.

---

### ISSUE-02 — [BLOCKER] Unit Test `pom.xml` References Non-Existent Source Path

**Conclusion:** Unit tests cannot compile.

**Evidence:**
- `repo/backend/unit_tests/pom.xml:19` — `<sourceDirectory>../api/src/main/java</sourceDirectory>`
- The `api/` subdirectory was previously deleted (git status shows many `D api/src/main/java/com/anju/...` entries)
- Actual source is at `repo/backend/src/main/java/`, relative path should be `../src/main/java`

**Impact:** `mvn clean test` in `unit_tests/` will fail with a compilation error. The `run_tests.sh` / `docker-compose.yml` test pipeline will always report failure. All unit test coverage is unreachable.

**Minimum fix:** Change `pom.xml:19` to `<sourceDirectory>../src/main/java</sourceDirectory>`.

---

### ISSUE-03 — [HIGH] API Integration Test Asserts CSV Export That Endpoint Does Not Produce

**Conclusion:** Test assertion `testFinanceExceptionAndStatementExportFlows` (Order=6) will fail at runtime due to mismatch between test expectation and actual endpoint behaviour.

**Evidence:**
- `repo/backend/API_tests/src/test/java/com/anju/integration/ApiFunctionalTest.java:219-220`:
  ```java
  .header("Content-Disposition", containsString("statement-" + today + ".csv"))
  .body(containsString("exceptionCount"));
  ```
- `repo/backend/src/main/java/com/anju/domain/finance/TransactionController.java:117-119`:
  ```java
  public Result<Map<String, Object>> dailyStatementExport(...) {
      Map<String, Object> statement = transactionService.dailyStatement(date);
      return Result.success(statement);
  }
  ```
  The endpoint returns a JSON `Result` object; it never sets a `Content-Disposition` header and never produces CSV output.

**Impact:** API test suite Order=6 will fail. The CSV/file-download export described in the Prompt is unimplemented.

**Minimum fix:** Either implement CSV serialization with a `Content-Disposition` header in the endpoint, or correct the test to expect JSON response with no `Content-Disposition` header.

---

### ISSUE-04 — [HIGH] Secondary Passwords Captured in Plaintext Audit Log

**Conclusion:** The `X-Secondary-Password` header value is serialized into `sys_audit_log.request_payload` by the AOP aspect.

**Evidence:**
- `repo/backend/src/main/java/com/anju/aspect/AuditLogAspect.java:49` — `auditLog.setRequestPayload(serializeArguments(joinPoint.getArgs()))`
- `AuditLogAspect.java:115-132` — `isSerializableArgument` only excludes `ServletRequest`, `ServletResponse`, `MultipartFile`, and `BindingResult`. Plain `String` parameters are NOT excluded.
- `repo/backend/src/main/java/com/anju/domain/property/PropertyController.java:130` — `String secondaryPassword` is a `@RequestHeader` method parameter captured by the aspect
- `repo/backend/src/main/java/com/anju/domain/finance/TransactionController.java:68` — same pattern on `POST /finance`
- `repo/backend/database/migrations/init.sql:131` — `request_payload LONGTEXT`; the secondary password would be stored in plaintext

**Impact:** Any ADMIN or AUDITOR who can query `sys_audit_log` (or a DBA with DB access) can extract secondary passwords from the audit log, defeating the second-factor mechanism.

**Minimum fix:** Add `String.class` filter with parameter-name annotation awareness, or exclude any argument corresponding to a `@RequestHeader("X-Secondary-Password")` annotation by inspecting `MethodSignature` parameter annotations in the aspect. At minimum, mask or omit string arguments named `*password*` / `*secret*` from `serializeArguments`.

---

### ISSUE-05 — [HIGH] Reschedule Conflict Unit Test Mocks Wrong Repository Method

**Conclusion:** `testReschedule_overlappingTimeSlot_throwsException` will not function as intended and will likely fail under Mockito strict stubbing.

**Evidence:**
- `repo/backend/unit_tests/src/test/java/com/anju/domain/appointment/AppointmentServiceTest.java:111-113` stubs `appointmentRepository.existsConflict(eq(10L), eq(20L), eq(newStartTime), eq(newEndTime), eq("CANCELLED"))` to return `true`
- `repo/backend/src/main/java/com/anju/domain/appointment/AppointmentService.java:108` — the actual `reschedule` path calls `existsConflictExcludingId(...)`, not `existsConflict(...)`
- With `@ExtendWith(MockitoExtension.class)`, Mockito 5.x uses strict stubbing by default; an unused stub (`existsConflict`) will throw `UnnecessaryStubbingException`
- Even without strict stubbing, `existsConflictExcludingId` is not mocked so returns `false`; no conflict exception is thrown; the test assertion fails

**Impact:** The test does not verify the rescheduling conflict path. A regression in conflict detection during reschedule would not be caught.

**Minimum fix:** Replace the mock stub target from `existsConflict` to `existsConflictExcludingId` with matching parameters.

---

### ISSUE-06 — [HIGH] Hardcoded Order Amount in Cancellation Penalty Calculation

**Conclusion:** The cancellation penalty is computed against a hardcoded `500.00` order amount rather than the actual transaction amount.

**Evidence:**
- `repo/backend/src/main/java/com/anju/domain/appointment/AppointmentService.java:169`:
  ```java
  BigDecimal orderAmount = new BigDecimal("500.00"); // Standard fallback for calculation
  ```
- The Prompt states: "cancellation penalty capped at 10% of the order amount or 50 RMB"
- No lookup of the associated `Transaction` (via `appointmentId`) is performed

**Impact:** For orders not worth 500 RMB, the penalty is either over- or under-stated. The 10% capped at 50 RMB rule will only be correct when the actual order amount happens to be ≥500. This is a financial calculation defect.

**Minimum fix:** Query the linked `Transaction` record for the appointment at `cancel()` time and use `transaction.getAmount()` as the order amount. If no transaction exists, apply a configurable default or raise a specific error.

---

### ISSUE-07 — [MEDIUM] In-Memory JWT Token Blacklist Lost on Restart

**Conclusion:** Revoked (logged-out) JWT tokens become valid again after service restart.

**Evidence:**
- `repo/backend/src/main/java/com/anju/domain/auth/JwtTokenBlacklistService.java:12`:
  ```java
  private final Map<String, Instant> revokedTokens = new ConcurrentHashMap<>();
  ```
  This is a JVM heap map. A container restart clears it.

**Impact:** If a user logs out and the service then restarts before their token expiry (up to 24h default), their "revoked" token resumes being valid. Risk is moderate in a Docker deployment where restarts are routine.

**Minimum fix:** Persist revoked tokens to the MySQL `users` or a dedicated `revoked_tokens` table, or use Redis. A simple DB approach: store `(token_hash, expires_at)` rows and clean up expired entries.

---

### ISSUE-08 — [MEDIUM] Default JWT Secret Falls Back to Known Hardcoded String

**Conclusion:** If `SECURITY_JWT_SECRET` environment variable is not set, the service uses a publicly visible default secret.

**Evidence:**
- `repo/backend/src/main/resources/application.yml:60`:
  ```yaml
  secret: ${SECURITY_JWT_SECRET:anju-medical-appointment-system-secret-key-2026}
  ```
- `.env.example` does not include `SECURITY_JWT_SECRET`

**Impact:** Anyone who reads this repository can forge valid JWT tokens if the variable is not explicitly set. The secret is under 256 bits and is entirely predictable.

**Minimum fix:** Remove the default fallback (or change it to fail at startup). Add `SECURITY_JWT_SECRET=<strong-random-value>` to `.env.example` with a prominent note that it must be changed.

---

### ISSUE-09 — [MEDIUM] Client IP Spoofable via X-Forwarded-For in Audit Logs

**Conclusion:** Audit log IP attribution is unreliable without reverse proxy enforcement.

**Evidence:**
- `repo/backend/src/main/java/com/anju/aspect/AuditLogAspect.java:90-93`:
  ```java
  String forwardedFor = request.getHeader("X-Forwarded-For");
  if (StringUtils.hasText(forwardedFor)) {
      return forwardedParts[0].trim();
  }
  ```
  Any client can send `X-Forwarded-For: 127.0.0.1` and have that IP recorded in the audit log.

**Impact:** Audit trail IP addresses can be falsified, undermining forensic traceability.

**Minimum fix:** Only trust `X-Forwarded-For` when requests arrive via a known reverse-proxy CIDR. If running behind a trusted proxy, use the last untrusted hop; otherwise fall back to `request.getRemoteAddr()`.

---

### ISSUE-10 — [MEDIUM] Missing Nacos Active Configuration (Disabled by Default)

**Conclusion:** The Prompt specifies Nacos as the sole configuration and service registry. The delivered system disables both Nacos config and discovery by default.

**Evidence:**
- `repo/.env.example:12-13`: `NACOS_CONFIG_ENABLED=false` and `NACOS_DISCOVERY_ENABLED=false`
- `repo/backend/src/main/resources/application.yml:26-28`: Both default to `false`

**Impact:** The system runs as a standalone Spring Boot app reading only `application.yml`. Nacos is present as a dependency and in Docker Compose but is never activated. The Prompt's "configuration managed solely by Nacos" requirement is not met in the default delivery.

**Minimum fix:** Set `NACOS_CONFIG_ENABLED=true` and `NACOS_DISCOVERY_ENABLED=true` in `.env.example`, or document that the defaults are for local-only development and explain how to enable Nacos for real deployment. Provide a Nacos bootstrap config example.

---

### ISSUE-11 — [LOW] No Pagination on List Endpoints

**Conclusion:** All `list()` endpoints return unbounded result sets.

**Evidence:**
- `repo/backend/src/main/java/com/anju/domain/appointment/AppointmentController.java:90-98` — returns full list
- `repo/backend/src/main/java/com/anju/domain/finance/TransactionController.java:81-89` — `transactionRepository.findAll()`
- `repo/backend/src/main/java/com/anju/domain/property/PropertyController.java:72-83` — returns full list

**Impact:** On a production dataset, these endpoints will degrade under volume. Not a correctness defect but a scalability concern.

**Minimum fix:** Add `Pageable` support to list endpoints using `JpaRepository.findAll(Pageable)`.

---

### ISSUE-12 — [LOW] No Audit Log Query/Export API

**Conclusion:** `sys_audit_log` is populated but there is no query or export endpoint for administrators or auditors.

**Evidence:**
- No controller or service references `SysAuditLogRepository` for reads — only `AuditLogAspect.java:57` writes to it
- The Prompt describes "audit and export" as an interface family

**Impact:** Audit data is captured but inaccessible through the API. An auditor must query the database directly.

**Minimum fix:** Expose a `GET /audit/logs` endpoint (role: ADMIN, AUDITOR) with optional date-range and module filters, plus an export path.

---

## 6. Security Review Summary

### Authentication Entry Points
**Conclusion: Pass (with note)**

JWT authentication is implemented in `JwtAuthenticationFilter` (`config/JwtAuthenticationFilter.java`). HTTP Basic is also supported as fallback via `SecurityConfig.java:47`. JWT is validated against expiry and token blacklist. BCrypt password encoding is used.

Note: The default JWT secret fallback is a known string (Issue #8).

---

### Route-Level Authorization
**Conclusion: Partial Pass**

`SecurityConfig.java:40-46` enforces:
- `POST /auth/register`, `POST /auth/login` — public
- `GET /` — public
- `/v3/api-docs/**`, `/swagger-ui/**` — public (Swagger UI is unauthenticated; acceptable for internal deployment)
- `/finance/**` — requires `ADMIN`, `FINANCE`, or `AUDITOR` role at URL level
- `/actuator/health` — public
- All other requests — require authentication

Method-level `@PreAuthorize` further restricts individual endpoints. The combination is appropriate.

**Gap:** Swagger UI (`/swagger-ui.html`, `/v3/api-docs/**`) is publicly accessible and exposes the full API schema. For a system handling financial and medical data, this may be undesirable in production.

---

### Object-Level Authorization
**Conclusion: Pass**

- Appointments: `enforceAppointmentAccess` (`AppointmentService.java:206-211`) restricts STAFF to their own appointments
- Transactions: `enforceTransactionAccess` (`TransactionService.java:275-282`) restricts STAFF to their own payer transactions
- Files: `enforceFileAccess` (`FileService.java:327-334`) restricts non-privileged users to files they uploaded
- Property: No object-level ownership (properties are shared resources; access is role-based only — appropriate for the domain)

API integration test `testFileOwnership_idorReturns403` (Order=10) verifies cross-user file access returns 403. (`ApiFunctionalTest.java:299-346`)

---

### Function-Level Authorization
**Conclusion: Pass**

`@PreAuthorize` annotations enforce role requirements at the method level for every sensitive operation. Examples:
- `POST /appointment` — `ADMIN`, `SCHEDULER`, `STAFF` (`AppointmentController.java:35`)
- `DELETE /appointment/{id}` — `ADMIN`, `SCHEDULER` only (`AppointmentController.java:75`)
- `PATCH /api/properties/{id}/compliance-review` — `ADMIN`, `REVIEWER` (`PropertyController.java:125`)
- `POST /finance` — `ADMIN`, `FINANCE` + secondary password (`TransactionController.java:62-72`)
- `POST /{txNo}/invoice/issue` — `ADMIN`, `FINANCE` + secondary password (`TransactionController.java:141-151`)

---

### Tenant / User Data Isolation
**Conclusion: Pass (single-tenant system)**

The Prompt describes a single-organisation system. Multi-tenancy is not a stated requirement. Within the single tenant, user-level data isolation is enforced via object-level authorization (see above).

---

### Admin / Internal / Debug Endpoint Protection
**Conclusion: Partial Pass**

- Actuator is restricted to `health` and `info` only (`application.yml:30-36`) — no `env`, `beans`, or `shutdown` exposed.
- Swagger UI is publicly accessible without authentication. For a closed offline deployment this may be acceptable, but it leaks the full API schema to unauthenticated callers.
- No debug endpoints found.

---

## 7. Tests and Logging Review

### Unit Tests
**Conclusion: Partial Pass**

Three test classes exist across three service domains:
- `AppointmentServiceTest` — 6 tests covering reschedule rules, idempotency, lifecycle enforcement
- `AuthServiceTest` — 3 tests covering password validation, secondary password mismatch, login
- `TransactionServiceTest` — 8 tests covering idempotency, duplicate detection, refund rejection, bookkeeping import

Quality is good for the tests that exist. Critical defects:
1. **`unit_tests/pom.xml` source path broken** — tests cannot compile (Issue #2)
2. **`testReschedule_overlappingTimeSlot_throwsException` mocks wrong method** — test is broken (Issue #5)

---

### API / Integration Tests
**Conclusion: Partial Pass**

`ApiFunctionalTest.java` contains 15 ordered tests covering:
- Unauthenticated rejection (401), role rejection (403), not-found (404), duplicate exception (409)
- Idempotency replay, secondary password enforcement, IDOR protection
- Property and appointment validation

Critical defect: **Order=6 CSV export assertion** will fail against the actual endpoint (Issue #3).

---

### Logging Categories / Observability
**Conclusion: Pass**

- SLF4J used throughout with appropriate levels: `log.warn` for business exceptions, `log.error` for system exceptions and data integrity violations (`GlobalExceptionHandler.java:109`)
- `FileService` uses `log.info` + `log.error` for scheduled tasks and upload failures
- `AuditLogAspect` logs a warning on audit persistence failure without re-throwing
- MDC `traceId` is referenced (`AuditLogAspect.java:44`) though no trace-ID injection filter is evident in the reviewed code; `traceId` may always be `null`

---

### Sensitive Data Leakage Risk in Logs / Responses
**Conclusion: Partial Pass**

- `AuditLogAspect.java:104-113` — `sanitizeErrorMessage` truncates to 1000 chars, reducing stack trace leakage in the DB
- `CryptoConverter` encrypts `User.email` and `User.phone` before DB persistence
- `GlobalExceptionHandler.java:143-145` — generic `Exception` handler exposes the exception `getMessage()` in API response body. While this is sometimes useful, exception messages may contain internal state (table names, SQL) in the case of `DataIntegrityViolationException` (partially mitigated by the specific handler at line 107-113)
- **Critical risk:** `AuditLogAspect` serializes secondary passwords into `request_payload` (Issue #4)
- `Transaction` entity is returned directly in API responses, exposing `idempotencyKey` in plaintext

---

## 8. Test Coverage Assessment (Static Audit)

### 8.1 Test Overview

| Category | Details |
|---|---|
| Unit tests exist | Yes — 3 files, 17 tests total |
| API/integration tests exist | Yes — 1 file, 15 tests |
| Framework | JUnit 5, Mockito 5 (unit); JUnit 5, REST Assured 5.4 (API) |
| Unit test entry point | `repo/backend/unit_tests/` — `mvn clean test` |
| API test entry point | `repo/backend/API_tests/` — `mvn clean test` (requires live server) |
| Test commands documented | Yes (`README.md:28-33`, `run_tests.sh`) |
| Can unit tests currently compile | **No** — broken source path in `unit_tests/pom.xml:19` |

---

### 8.2 Coverage Mapping Table

| Requirement / Risk Point | Mapped Test(s) | Key Assertion | Coverage Assessment | Gap | Minimum Test Addition |
|---|---|---|---|---|---|
| Password policy enforcement | `AuthServiceTest.registerRejectsWeakPassword` | `exception.getCode() == 4003` | **Sufficient** | — | — |
| Secondary password mismatch | `AuthServiceTest.verifySecondaryPasswordRejectsMismatch` | `exception.getCode() == 4033` | **Sufficient** | — | — |
| Login updates last-login field | `AuthServiceTest.loginUpdatesLastLogin` | `result.getUsername()` matches | **Basically Covered** | Does not verify `lastLoginAt` was updated | Assert `result.getLastLoginAt() != null` |
| Reschedule ≥24h notice rule | `AppointmentServiceTest.testReschedule_insufficientAdvanceNotice_throwsException` | `code == 4010` | **Sufficient** | — | — |
| Reschedule ≤2 rule | `AppointmentServiceTest.testReschedule_exceedingMaxReschedules_throwsException` | `code == 4011` | **Sufficient** | — | — |
| Reschedule conflict detection | `AppointmentServiceTest.testReschedule_overlappingTimeSlot_throwsException` | `code == 4006` | **Insufficient (broken)** | Wrong mock method used — test will fail | Fix mock to use `existsConflictExcludingId` |
| Reschedule success path | `AppointmentServiceTest.testReschedule_success` | `rescheduleCount == 1`, status `RESCHEDULED` | **Sufficient** | — | — |
| Reschedule rejected on RELEASED status | `AppointmentServiceTest.testReschedule_releasedAppointment_throwsException` | `code == 4013` | **Sufficient** | — | — |
| Appointment idempotency replay | `AppointmentServiceTest.testCreate_withIdempotencyKey_returnsExistingAppointment` | no save called | **Sufficient** | — | — |
| Server controls lifecycle fields on create | `AppointmentServiceTest.testCreate_serverControlsLifecycleFields` | status=PENDING, penalty=0, rescheduleCount=0 | **Sufficient** | — | — |
| Appointment cancellation penalty calculation | **None** | — | **Missing** | Hardcoded amount + no test | Add test verifying penalty = min(amount × 0.10, 50) |
| Auto-release PENDING after 15 min | **None** | — | **Missing** | Scheduled task not tested | Add unit test with mocked repository verifying RELEASED status after threshold |
| Duplicate transaction no rejection | `TransactionServiceTest.recordTransactionRejectsDuplicateTransactionNo` | `code == 400` | **Sufficient** | — | — |
| Transaction idempotency replay | `TransactionServiceTest.recordTransactionWithIdempotencyKeyReturnsExistingTransaction` | no save called | **Sufficient** | — | — |
| Refund rejection on non-refundable | `TransactionServiceTest.createRefundRejectsNonRefundableTransaction` | `code == 4008` | **Sufficient** | — | — |
| Bookkeeping import field mapping + errors | `TransactionServiceTest.importBookkeepingProcessesValidRowsAndCollectsErrors` | 1 success, 1 error | **Sufficient** | — | — |
| Bookkeeping import `occurredAt` parsing | `TransactionServiceTest.importBookkeepingParsesOccurredAtIsoDate` | 1 success | **Basically Covered** | Does not verify stored `occurredAt` value | Assert `stored.getOccurredAt()` equals expected |
| Unauthenticated request rejection (401) | `ApiFunctionalTest.testGetProperties_requiresAuthentication` (Order=1) | statusCode=401 | **Sufficient** | — | — |
| Role-based access control (403) | `ApiFunctionalTest.testFinanceAccess_forbiddenForStaffRole` (Order=4) | statusCode=403 | **Basically Covered** | Only STAFF→finance tested; other role combos not covered | — |
| Object-level file authorization (IDOR) | `ApiFunctionalTest.testFileOwnership_idorReturns403` (Order=10) | statusCode=403, code=4034 | **Sufficient** | — | — |
| Secondary password invalid → 403 | `ApiFunctionalTest.testFinanceCreate_withInvalidSecondaryPassword_returns403` (Order=11) | statusCode=403, code=4033 | **Sufficient** | — | — |
| Input validation → 400 | Orders 3, 13, 14, 15 | statusCode=400 | **Basically Covered** | Some boundary cases untested | — |
| Daily statement export (CSV) | `ApiFunctionalTest.testFinanceExceptionAndStatementExportFlows` (Order=6) | `Content-Disposition` header | **Insufficient (broken assertion)** | Endpoint returns JSON, not CSV | Fix assertion or implement CSV export |
| Open self-registration with admin role | **None** | — | **Missing** | Critical security gap not tested | Add test: register with role=ADMIN, verify attempt is rejected or role is downgraded |
| Duplicate exception-flag mark → 409 | `ApiFunctionalTest.testFinanceMarkException_repeatReturns409` (Order=9) | statusCode=409, code=4092 | **Sufficient** | — | — |
| Finance idempotency replay | `ApiFunctionalTest.testFinanceBookkeeping_idempotencyReplayReturnsSameTransaction` (Order=5) | same `transactionNo` returned | **Sufficient** | — | — |

---

### 8.3 Security Coverage Audit

| Area | Coverage | Assessment |
|---|---|---|
| Authentication (401 on unauthenticated) | Tested — Order=1 asserts 401 on `/api/properties` without credentials | Adequately covered |
| Route authorization (role-based 403) | Tested — Order=4 asserts STAFF cannot access `/finance` | Basically covered; only one role/endpoint combination tested |
| Object-level authorization (IDOR) | Tested — Order=10 asserts different-user file access returns 403 | Good coverage for file domain; appointment and transaction IDOR not explicitly tested via API tests |
| Tenant / data isolation | Not applicable (single tenant) | — |
| Admin / internal endpoint protection | Not tested | API tests do not probe `/actuator/**` or Swagger without auth |
| Open role escalation (self-register as ADMIN) | **Not tested** | A critical defect (Issue #1) has no test coverage; it could remain undetected indefinitely |

---

### 8.4 Final Coverage Judgment

**Conclusion: Partial Pass**

**Major risks covered:**
- Rescheduling business rules (24h notice, max 2 reschedules)
- Idempotency (appointments and transactions)
- Refund rejection rules
- IDOR for files
- Secondary password enforcement
- Input validation boundary cases
- Role-based access control (partial)

**Uncovered risks where severe defects could pass undetected:**
1. **Open role escalation** — no test exists; an attacker can self-register as ADMIN and it would go undetected by the test suite
2. **Unit tests cannot compile** — the `../api/src/main/java` path breaks all 17 unit tests; the pipeline reports failure but for a build reason, not a logic reason
3. **Cancellation penalty hardcoded amount** — no test verifies correct penalty against actual order amount
4. **Secondary password logged in plaintext** — no test checks audit log content for credential leakage
5. **Reschedule conflict regression** — the dedicated conflict test is broken (wrong mock); a regression in `existsConflictExcludingId` would not be caught

---

## 9. Final Notes

The codebase represents a competent, professionally structured implementation of the described business system. The four business domains are implemented with real logic, appropriate validation, and meaningful security controls. The AES-GCM encryption, per-user concurrency throttling, idempotency keys, and AOP-based audit logging all demonstrate production-intent engineering.

The two Blockers must be resolved before the delivery can be accepted: the role escalation vulnerability is a material security defect that undermines the entire RBAC model, and the broken unit test source path means the declared test suite is non-functional. The HIGH-severity issues (secondary password logging, wrong mock in conflict test, hardcoded penalty amount, CSV assertion mismatch) should be addressed in the same pass. The MEDIUM issues (in-memory blacklist, JWT default secret, Nacos disabled) are acceptable to defer but should have a documented remediation plan.
