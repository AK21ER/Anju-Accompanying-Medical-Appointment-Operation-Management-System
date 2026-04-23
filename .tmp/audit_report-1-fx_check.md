# Audit Fix Check — audit_report-1.md
- Check date: 2026-04-21
- Based on: `.tmp/audit_report-1.md` 

---

## Summary

| Finding | Count |
|---|---|
| FIXED | 7 |
| OPEN | 0 |

All gaps from the original audit are resolved.

---

## Fixes Applied

### 1 · docker-compose.yml broken paths

Four paths referenced the old flat `api/` structure and did not exist after reorganisation under `repo/backend/`:

| Old | Fixed |
|---|---|
| `context: ./api` | `context: ./backend` |
| `./init.sql` (MySQL volume) | `./backend/database/migrations/init.sql` |
| `cd unit_tests` | `cd backend/unit_tests` |
| `cd API_tests` | `cd backend/API_tests` |

### 2 · README run-tests command

"Run Tests In Docker" section now shows `./run_tests.sh` instead of the raw multi-step docker-compose commands.

### 3 · Validation 400 coverage — finance and property domains

Added two API tests covering validation failure across domains:
- `testFinanceBookkeeping_missingAmount_shouldReturn400` (`@Order(13)`) — `POST /finance/bookkeeping` with missing `amount` → 400
- `testCreateProperty_missingCode_shouldReturn400` (`@Order(14)`) — `POST /api/properties` with missing `code` → 400

Coverage upgraded from "Basic Coverage" (appointment only) to "Sufficient" (three domains).

### 4 · Secondary password missing-header variant

`POST /finance` uses `@RequestHeader("X-Secondary-Password")` (required by default). When the header was absent, the unhandled `MissingRequestHeaderException` fell through to the generic `Exception` handler and returned 500.

Two changes made:
- `GlobalExceptionHandler.java` — added explicit `@ExceptionHandler(MissingRequestHeaderException.class)` returning 400 / code 4004.
- `ApiFunctionalTest.java` — added `testFinanceCreate_missingSecondaryPasswordHeader_shouldReturn400` (`@Order(15)`) asserting 400 when header is omitted.

### 5 · Finance import unit edge cases

Added two unit tests to `TransactionServiceTest.java`:
- `importBookkeepingInvalidRefundableValueCollectsError` — row with `refundable: "yes"` (not `"true"`/`"false"`) is collected as an error row; success count = 0, failure count = 1.
- `importBookkeepingInvalidPayerIdFormatCollectsError` — row with `payerId: "not-a-number"` is collected as an error row; success count = 0, failure count = 1.

---

## Stale Evidence Paths in Audit Document

The audit was generated against the old flat repo structure. All `api/src/...` references are now at `backend/src/...`. The code exists intact at the current paths — documentation staleness only, no runtime impact.

| Audit path | Actual current path |
|---|---|
| `api/src/main/java/com/anju/...` | `repo/backend/src/main/java/com/anju/...` |
| `API_tests/src/test/java/...` | `repo/backend/API_tests/src/test/java/...` |
| `unit_tests/src/test/java/...` | `repo/backend/unit_tests/src/test/java/...` |
| `init.sql` | `repo/backend/database/migrations/init.sql` |

---

## Overall Re-Judgment

| Section | Original | Current |
|---|---|---|
| 1.1 Runnability | Pass | Pass ✅ |
| 1.2 Prompt-theme alignment | Pass | Pass ✅ |
| 2.1 Core requirements coverage | Pass | Pass ✅ |
| 2.2 0-to-1 completeness | Pass | Pass ✅ |
| 3.1–3.2 Architecture / maintainability | Pass | Pass ✅ |
| 4.1–4.2 Engineering details | Pass | Pass ✅ (missing-header now 400 not 500) |
| 5.1 Business goal fitness | Pass | Pass ✅ |
| 6 Aesthetics | N/A | N/A (backend-only deliverable) |
| Security audit | Pass | Pass ✅ |
| Test coverage | Pass | Pass ✅ (all gaps closed) |
