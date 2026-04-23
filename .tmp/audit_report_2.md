# Delivery Acceptance and Project Architecture Audit

**Verdict: Partial Pass**

The delivered project provides a solid, highly complete backend implementation of the Anju Medical Appointment System. It strictly adheres to the stated technology stack (Spring Boot, Nacos, MySQL, Docker) and fulfills the core business domains (Property, Appointment, Finance, File) well. The project demonstrates professional-level engineering with robust object-level authorization (IDOR prevention), secondary password validation, full-chain auditing, and well-designed concurrency controls (file upload throttling, idempotency keys). 

However, it receives a **Partial Pass** due to critical missing unit tests in two major domains (Property and File) and a potential memory exhaustion risk in the chunked upload implementation.


**Reviewed:**
- Project structure, configuration (`pom.xml`, `.env`, `docker-compose.yml`, `README.md`)
- Core domain logic (`PropertyService`, `AppointmentService`, `TransactionService`, `FileService`)
- Security configurations (`SecurityConfig`, `AuthController`)
- Controller layer routing and validation logic
- Test suite (`AppointmentServiceTest`, `TransactionServiceTest`, `ApiFunctionalTest`)

**Not Reviewed / Executed:**
- Actual Nacos configuration bootstrap behavior at runtime
- Real Docker network routing and container startup
- Manual verification of the Swagger documentation output

**Limitations (Cannot Confirm Statistically):**
- The exact query logic constraint inside Spring Data Repositories for double-booking prevention (`existsConflictExcludingId`) cannot be fully proven without inspecting the `@Query` annotation implementations.
- Compatibility of the `byte[]` payload in the API layer for high-concurrency large file uploads, as heap behavior needs runtime profiling.

## 3. Repository / Requirement Mapping Summary
**Primary Objective mapped to Code:**
- **Property Domain:** `PropertyService.java` implements lifecycle tracking, rent/deposit rules, and compliance review with secondary auth.
- **Appointment Domain:** `AppointmentService.java` implements a 24-hour rescheduling rule, 2 max reschedules, 10% or 50 RMB penalty capping, and conflict validation. A 15-minute auto-release is implemented via `@Scheduled`.
- **Financial Domain:** `TransactionService.java` tracks multi-channel bookkeeping, idempotency import, partial refunds, and daily statement aggregation.
- **File Domain:** `FileService.java` provides chunk assembly, SHA-256 deduplication, 30-day recycle bin (`@Scheduled`), and throttling. 
- **Security:** Spring Security + JWT, with `@PreAuthorize` role validations and manual `X-Secondary-Password` header checks.

## 4. Section-by-section Review

### 4.1 Hard Gates
- **1.1 Documentation:** **Pass**. `README.md` clearly outlines `docker compose up -d`, exposed ports, Postman collection usage, and test runner instructions.
- **1.2 Prompt Alignment:** **Pass**. The implementation precisely maps to the requested offline medical scheduling service without any unnecessary generic scaffolding or scope creep.

### 4.2 Delivery Completeness
- **2.1 Core Requirements Covered:** **Pass**. All features, including the 15-minute auto release, max penalty cap, file version rollover, and idempotency, are statically present.
- **2.2 Baseline 0-to-1:** **Pass**. This is a complete project with database integrations, proper DTOs, custom exception handling (`GlobalExceptionHandler`), and Docker containerization.

### 4.3 Engineering and Architecture Quality
- **3.1 Structural Decomposition:** **Pass**. Clean Domain-Driven Design (DDD) lite structure. Domains (auth, property, appointment, finance, file) are properly isolated.
- **3.2 Maintainability:** **Pass**. Standard Java/Spring interfaces are used. Service limits are externalized (e.g., `@Value("${file.upload.max-concurrent-per-user:3}")`).

### 4.4 Engineering Details and Professionalism
- **4.1 Detail Quality:** **Partial Pass**. Error handling is encapsulated in a central `GlobalExceptionHandler`. The auditing aspect (`@Auditable`) logs lifecycle events properly. However, the file chunking system accepts `byte[]` in the DTO which is risky for large payloads.
- **4.2 Product Realism:** **Pass**. The use of idempotency keys, throttling (`activeUploadsByUser`), and manual settlement flags represents enterprise-grade realism.

### 4.5 Prompt Understanding
- **5.1 Goal Alignment:** **Pass**. The strict enforcement of `STAFF` limitations (staff can only view/book their own appointments/transactions) proves deep understanding of the multi-role offline operational context.

### 4.6 Aesthetics
- **6.1 UI Quality:** **Not Applicable**. Backend-only task.

## 5. Issues / Suggestions (Severity-Rated)

### [High] In-Memory Chunk Upload Exposes OOM Risk
- **Conclusion:** Fail
- **Evidence:** `FileService.java:112` (`boolean hasChunkPayload = request.getChunkData() != null && request.getChunkData().length > 0;`)
- **Impact:** The `ChunkUploadRequest` DTO appears to map the chunk data to a `byte[]`. Since chunks could theoretically be up to 50MB, high concurrency will quickly exhaust heap memory, leading to OutOfMemoryError. 
- **Minimum Actionable Fix:** Refactor `ChunkUploadRequest` to use Spring's `MultipartFile` instead of `byte[]` to leverage disk buffering (staging), or stream the `HttpServletRequest` input directly to disk.

### [High] Incomplete Test Coverage for Core Domains (Property & File)
- **Conclusion:** Fail
- **Evidence:** `unit_tests/src/test/java/com/anju/domain/` directory contains tests for `appointment`, `auth`, `finance`, but lacks `property` and `file`.
- **Impact:** Changes to the Property compliance workflow or the highly complex File Chunking/Recycle Bin algorithms lack regressions safety nets.
- **Minimum Actionable Fix:** Add JUnit test classes covering the `PropertyService` validation states and the `FileService` assembly logic.

### [Medium] Database Queries for Conflict Resolution Cannot Be Verified
- **Conclusion:** Cannot Confirm Statistically
- **Evidence:** `AppointmentService.java:116` (`appointmentRepository.existsConflictExcludingId(...)`)
- **Impact:** If the underlying JPQL/SQL query uses an `AND` instead of an `OR` between `staffId` and `resourceId`, structural double-booking loopholes may exist.
- **Minimum Actionable Fix:** Ensure the query checks `(staffId = :staffId OR resourceId = :resourceId)` to guarantee that neither the resource nor the staff member is double-booked.

## 6. Security Review Summary

- **Authentication Entry Points:** **Pass**. `SecurityConfig.java:41` safely exposes `POST /auth/login` and `POST /auth/register`. Filter chains enforce stateless JWT.
- **Route-level Authorization:** **Pass**. Exhaustive use of `@PreAuthorize("hasAnyRole(...)")` across all controllers to gate operations.
- **Object-level Authorization (IDOR):** **Pass**. Verified heavily in `enforceAppointmentAccess`, `enforceTransactionAccess`, and `enforceFileAccess`. Validated by `ApiFunctionalTest.java:311` (testFileOwnership_idorReturns403).
- **Function-level Authorization:** **Pass**. High-risk methods (Compliance Review, Manual Finance tracking) require `X-Secondary-Password`, correctly verified via `authService.verifySecondaryPassword`.
- **Tenant / User Isolation:** **Pass**. User role logic (`STAFF` vs `ADMIN`) handles implicit filtering of retrieved lists in service layers (`AppointmentService.java:214`).
- **Admin Protection:** **Pass**. Covered by preventing users from self-promoting during registration (`ApiFunctionalTest.java:458`), downgrading invalid roles automatically.

## 7. Tests and Logging Review

- **Unit Tests:** **Partial Pass**. Good logic validation for Appointment/Finance/Auth, but Property/File domains are missing tests.
- **API / Integration Tests:** **Pass**. `ApiFunctionalTest` checks true endpoint behaviors, including Idempotency and IDOR edge cases.
- **Logging Categories:** **Pass**. Dedicated system-wide `@Auditable` aspect intercepts controller actions to write `SysAuditLog` entries automatically.
- **Leakage Risk:** **Pass**. Passwords and secondary passwords are mathematically hashed using `BCryptPasswordEncoder`.

## 8. Test Coverage Assessment (Static Audit)

### 8.1 Test Overview
- **Framework:** JUnit 5, Mockito, RestAssured.
- **Entry Points:** Provided via Docker script (`run_tests.sh:30`).
- **Evidence:** `repo/backend/unit_tests/src/test/java/com/anju/domain/` and `repo/backend/API_tests/src/test/java/com/anju/integration/ApiFunctionalTest.java`.

### 8.2 Coverage Mapping Table

| Requirement / Risk Point | Mapped Test Case(s) | Key Assertion / Fixture | Coverage Assessment | Gap / Minimum Addition |
|--------------------------|---------------------|------------------------|---------------------|----------------------|
| **Appt: Max 2 Reschedules** | `testReschedule_exceedingMaxReschedules_throwsException` | `assertEquals(4011, exception.getCode())` | Sufficient | None |
| **Appt: Cancel Penalty Cap** | `testCancel_withinDeadline_penaltyCappedAt50` | `assertEquals(0, expected.compareTo(appt.getPenalty()))` | Sufficient | None |
| **Finance: Import Idempotency**| `importBookkeepingProcessesValidRowsAndCollectsErrors` | `transactionRepository.findByIdempotencyKey(...)` | Sufficient | None |
| **Finance: 2nd Password** | `testFinanceCreate_withInvalidSecondaryPassword_returns403` | `statusCode(403)` | Sufficient | None |
| **File: Chunk Assembly** | _None_ | N/A | **Missing** | Add unit test mocking chunk uploads and verifying `assembleChunkedUpload` bounds. |
| **Property: Compliance Review**| _None_ | N/A | **Missing** | Add unit test verifying status transition. |

### 8.3 Security Coverage Audit
- **Authentication:** Sufficient. Covered by functional tests preventing generic bypasses.
- **Route / Object Authorization:** Sufficient. Thoroughly tested using IDOR API test simulating cross-tenant file access.
- **Data Isolation:** Sufficient. Current User injected through Context propagation and statically verified against resource Owner IDs. 
- **Admin Protection:** Sufficient (Role downgrade on Registration explicitly covered).

### 8.4 Final Coverage Judgment
**Conclusion: Partial Pass**

**Boundary Explanation:** The core mechanical risks surrounding concurrency (appointment conflicts, finance idempotency) and security (IDOR, Role restrictions) are well-covered by both unit and functional tests. However, the tests could theoretically pass while a severe defect exists within the File uploading or Property compliance validation logic, due to the total absence of test files covering those two domains.
