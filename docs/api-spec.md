# API Specifications

All endpoints return a standardized `Result<T>` JSON response format:

```json
{
  "code": 0,
  "message": "OK",
  "data": { ... }
}
```

Errors return non-zero `code` values with appropriate HTTP status mapping and an error `message`.

## Authentication API

### 1. Register

**Endpoint:** `POST /auth/register`
**Description:** Registers a user with a role and password policy enforcement.

### 2. Login

**Endpoint:** `POST /auth/login`
**Description:** Validates credentials and updates last-login timestamp. Returns a JWT bearer token for protected routes.

### 3. Verify Secondary Password

**Endpoint:** `POST /auth/verify-secondary`
**Description:** Validates the secondary password required by sensitive operations.

### 4. Logout

**Endpoint:** `POST /auth/logout`
**Description:** Revokes current bearer token so it cannot be reused.

---

## Property API

### 1. Create Property

**Endpoint:** `POST /api/properties`
**Description:** Creates a new property listing.
**Request Body (`CreatePropertyRequest`):**

```json
{
  "code": "PROP-1001",
  "status": "AVAILABLE",
  "rent": 1500.0,
  "deposit": 3000.0,
  "startDate": "2024-01-01T00:00:00",
  "endDate": "2025-01-01T00:00:00",
  "complianceStatus": "PENDING"
}
```

**Response Body:** Returns the created `PropertyResponse` wrapped in `Result<T>`.

### 2. Compliance Review

**Endpoint:** `PATCH /api/properties/{id}/compliance-review`
**Description:** Updates the compliance status of a property (requires role and secondary password).
**Request Body (`ComplianceReviewRequest`):**

```json
{
  "propertyId": 1,
  "complianceStatus": "APPROVED",
  "reviewerNotes": "Passed all checks."
}
```

---

## Appointment API

### 1. Create Appointment

**Endpoint:** `POST /appointment`
**Description:** Books a new medical appointment, validating against scheduling conflicts for the staff and resource.
**Headers:**

- Optional: `X-Idempotency-Key` for safe retries (same key returns the original appointment response).
  **Request Body (`CreateAppointmentRequest`):**

```json
{
  "staffId": 101,
  "resourceId": 201,
  "startTime": "2024-05-10T10:00:00",
  "endTime": "2024-05-10T11:00:00",
  "status": "SCHEDULED",
  "penalty": 0.0,
  "rescheduleCount": 0
}
```

**Response Body:** Returns the generated `AppointmentResponse` wrapped in `Result<T>`.

### 2. Reschedule Appointment

**Endpoint:** `PUT /appointment/reschedule`
**Description:** Reschedules an existing appointment. Enforces maximum 2 reschedules and 24-hour advance notice rules.
**Request Body (`RescheduleAppointmentRequest`):**

```json
{
  "appointmentId": 1,
  "startTime": "2024-05-12T10:00:00",
  "endTime": "2024-05-12T11:00:00"
}
```

### 3. Cancel Appointment

**Endpoint:** `PUT /appointment/{id}/cancel`
**Description:** Cancels appointment and applies late-cancel penalty cap when within 24 hours.

### 4. Update Appointment

**Endpoint:** `PUT /appointment/{id}`
**Description:** Updates appointment scheduling fields (staff/resource/time) with conflict checks.

### 5. Delete Appointment

**Endpoint:** `DELETE /appointment/{id}`
**Description:** Deletes an appointment record.

---

## Finance API

### 1. Bookkeeping Transaction (Primary Write Endpoint)

**Endpoint:** `POST /finance/bookkeeping`
**Description:** Records a transaction with request validation.
**Headers:**

- Optional: `X-Idempotency-Key` for replay-safe writes.

### 2. Create Transaction (Secondary Password Protected)

**Endpoint:** `POST /finance`
**Description:** Records a transaction and requires secondary password verification.
**Headers:**

- Required: `X-Secondary-Password`
- Optional: `X-Idempotency-Key`

### 3. Refund

**Endpoint:** `POST /finance/{transactionNo}/refund`
**Description:** Creates a linked refund with amount guardrails.

### 4. Daily Settlement

**Endpoint:** `POST /finance/settlements/daily`
**Description:** Settles successful daily payment transactions.

### 5. Daily Statement

**Endpoint:** `GET /finance/statements/daily?date=YYYY-MM-DD`
**Description:** Returns payment/refund/net summary for the day.

### 6. Daily Statement Export

**Endpoint:** `GET /finance/statements/daily/export?date=YYYY-MM-DD`
**Description:** Returns the daily statement export in JSON format.

### 7. Mark Transaction Exception

**Endpoint:** `POST /finance/{transactionNo}/exception`
**Description:** Marks a transaction as exception with a reason. Re-marking the same transaction returns conflict.
**Request Body (`ExceptionMarkRequest`):**

```json
{
  "reason": "manual reconciliation mismatch"
}
```

### 8. Invoice Lifecycle

**Endpoints:**

- `POST /finance/{transactionNo}/invoice/request`
- `POST /finance/{transactionNo}/invoice/issue`

### 9. Bookkeeping Import (Field Mapping + Type Validation)

**Endpoint:** `POST /finance/import/bookkeeping`
**Description:** Imports bookkeeping rows with optional field mapping, required/enum/number validation, ISO-8601 datetime validation (`occurredAt`), and idempotency-safe persistence by generated row keys.
**Request Body (`BookkeepingImportRequest`):**

```json
{
  "fieldMapping": {
    "transactionNumber": "tx_no",
    "amount": "amt",
    "type": "kind",
    "occurredAt": "occurred_at"
  },
  "idempotencyKeyPrefix": "import-batch-20260325",
  "rows": [
    {
      "tx_no": "TX-10001",
      "amt": "120.50",
      "kind": "PAYMENT",
      "occurred_at": "2026-03-25T10:15:30",
      "currency": "USD"
    }
  ]
}
```

**Response Body:** import summary (`totalRows`, `successCount`, `failureCount`, imported numbers, and row-level errors).

---

## File API

### 1. Resumable Upload and Fast Upload

**Endpoints:**

- `GET /file/check?hash=...`
- `POST /file/upload`

### 2. Lifecycle Management

**Endpoints:**

- `GET /file`
- `GET /file/{id}`
- `DELETE /file/{id}`
- `POST /file/{id}/restore`
- `GET /file/recycle-bin`
- `POST /file/{hash}/rollback/{targetVersion}`

### 3. Preview Flow

**Endpoints:**

- `GET /file/{id}/preview`
- `GET /file/{id}/content`
