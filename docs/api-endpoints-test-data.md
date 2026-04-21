# API Endpoints and Test Data

## Base Configuration

- Base URL: `http://localhost:8080`
- Default content type: `application/json`
- Protected endpoints require: `Authorization: Bearer <token>`
- Sensitive operations also require: `X-Secondary-Password: <secondaryPassword>`

## Quick Test Users

### Admin User (full capabilities)

```json
{
  "username": "admin_demo",
  "password": "Admin1234",
  "displayName": "Admin Demo",
  "email": "admin@anju.local",
  "phone": "+251911000000",
  "role": "ADMIN",
  "secondaryPassword": "Admin5678"
}
```

### Finance User

```json
{
  "username": "finance_demo",
  "password": "Finance1234",
  "displayName": "Finance Demo",
  "email": "finance@anju.local",
  "phone": "+251922000000",
  "role": "FINANCE",
  "secondaryPassword": "Finance5678"
}
```

### Staff User

```json
{
  "username": "staff_demo",
  "password": "Staff1234",
  "displayName": "Staff Demo",
  "email": "staff@anju.local",
  "phone": "+251933000000",
  "role": "STAFF",
  "secondaryPassword": "Staff5678"
}
```

## Authentication Endpoints

### POST /auth/register

- Auth: public
- Body:

```json
{
  "username": "admin_demo",
  "password": "Admin1234",
  "displayName": "Admin Demo",
  "email": "admin@anju.local",
  "phone": "+251911000000",
  "role": "ADMIN",
  "secondaryPassword": "Admin5678"
}
```

### POST /auth/login

- Auth: public
- Body:

```json
{
  "username": "admin_demo",
  "password": "Admin1234"
}
```

- Use response `data.token` as bearer token.

### POST /auth/logout

- Auth: bearer token
- Header: `Authorization: Bearer <token>`
- Body: none

### GET /auth/me

- Auth: bearer token
- Body: none

### POST /auth/verify-secondary

- Auth: bearer token
- Header: `X-Secondary-Password`
- Body: none

## Property Endpoints

### POST /api/properties

- Roles: ADMIN, OPERATOR
- Body:

```json
{
  "code": "PROP-ANJU-001",
  "status": "AVAILABLE",
  "rent": 1200.0,
  "deposit": 2400.0,
  "startDate": "2026-04-01T09:00:00",
  "endDate": "2026-12-31T18:00:00",
  "complianceStatus": "PENDING",
  "mediaRefs": [
    "https://example.com/property-1-photo.jpg",
    "https://example.com/property-1-video.mp4"
  ]
}
```

### GET /api/properties/{id}

- Roles: ADMIN, OPERATOR, REVIEWER, STAFF

### GET /api/properties/code/{code}

- Roles: ADMIN, OPERATOR, REVIEWER, STAFF
- Example: `/api/properties/code/PROP-ANJU-001`

### GET /api/properties

- Roles: ADMIN, OPERATOR, REVIEWER, STAFF
- Optional query: `status`, `complianceStatus`
- Example: `/api/properties?status=AVAILABLE&complianceStatus=PENDING`

### PUT /api/properties/{id}

- Roles: ADMIN, OPERATOR
- Body:

```json
{
  "status": "AVAILABLE",
  "rent": 1300.0,
  "deposit": 2600.0,
  "complianceStatus": "PENDING",
  "mediaRefs": ["https://example.com/property-1-photo-updated.jpg"]
}
```

### DELETE /api/properties/{id}

- Roles: ADMIN

### PATCH /api/properties/{id}/list

- Roles: ADMIN, OPERATOR

### PATCH /api/properties/{id}/delist

- Roles: ADMIN, OPERATOR

### PATCH /api/properties/{id}/compliance-review

- Roles: ADMIN, REVIEWER
- Header: `X-Secondary-Password`
- Body:

```json
{
  "complianceStatus": "APPROVED",
  "comment": "All required documents verified."
}
```

## Appointment Endpoints

### POST /appointment

- Roles: ADMIN, SCHEDULER, STAFF
- Optional header: `X-Idempotency-Key`
- Body:

```json
{
  "number": "APPT-DEMO-001",
  "staffId": 3,
  "resourceId": 1,
  "startTime": "2026-04-05T10:00:00",
  "endTime": "2026-04-05T10:30:00"
}
```

### PUT /appointment/reschedule

- Roles: ADMIN, SCHEDULER, STAFF
- Body:

```json
{
  "appointmentId": 1,
  "startTime": "2026-04-06T11:00:00",
  "endTime": "2026-04-06T11:30:00"
}
```

### PUT /appointment/{id}

- Roles: ADMIN, SCHEDULER
- Body:

```json
{
  "staffId": 3,
  "resourceId": 1,
  "startTime": "2026-04-06T14:00:00",
  "endTime": "2026-04-06T14:30:00"
}
```

### PUT /appointment/{id}/cancel

- Roles: ADMIN, SCHEDULER, STAFF

### DELETE /appointment/{id}

- Roles: ADMIN, SCHEDULER

### GET /appointment/{id}

- Roles: ADMIN, SCHEDULER, STAFF

### GET /appointment

- Roles: ADMIN, SCHEDULER, STAFF

## Finance Endpoints

### POST /finance/bookkeeping

- Roles: ADMIN, FINANCE
- Optional header: `X-Idempotency-Key`
- Body:

```json
{
  "transactionNumber": "TX-DEMO-1001",
  "appointmentId": 1,
  "payerId": 3,
  "amount": 120.5,
  "type": "PAYMENT",
  "currency": "USD",
  "remark": "Initial appointment payment",
  "channel": "MANUAL",
  "refundable": true
}
```

### POST /finance/import/bookkeeping

- Roles: ADMIN, FINANCE
- Body:

```json
{
  "fieldMapping": {
    "transactionNumber": "tx_no",
    "amount": "amt",
    "type": "kind",
    "occurredAt": "occurred_at"
  },
  "idempotencyKeyPrefix": "import-demo-20260325",
  "rows": [
    {
      "tx_no": "TX-IMP-2001",
      "amt": "95.75",
      "kind": "PAYMENT",
      "occurred_at": "2026-03-25T10:15:30",
      "currency": "USD"
    }
  ]
}
```

### POST /finance

- Roles: ADMIN, FINANCE
- Required header: `X-Secondary-Password`
- Optional header: `X-Idempotency-Key`
- Body: same as `/finance/bookkeeping`

### GET /finance/{transactionNo}

- Roles: ADMIN, FINANCE, AUDITOR

### GET /finance

- Roles: ADMIN, FINANCE, AUDITOR

### POST /finance/{transactionNo}/refund

- Roles: ADMIN, FINANCE
- Body:

```json
{
  "amount": 20.0,
  "reason": "Customer requested partial refund"
}
```

### POST /finance/settlements/daily

- Roles: ADMIN, FINANCE
- Body:

```json
{
  "date": "2026-03-25"
}
```

### GET /finance/statements/daily?date=YYYY-MM-DD

- Roles: ADMIN, FINANCE, AUDITOR
- Example: `/finance/statements/daily?date=2026-03-25`

### GET /finance/statements/daily/export?date=YYYY-MM-DD

- Roles: ADMIN, FINANCE, AUDITOR
- Returns JSON summary
- Example: `/finance/statements/daily/export?date=2026-03-25`

### POST /finance/{transactionNo}/exception

- Roles: ADMIN, FINANCE
- Body:

```json
{
  "reason": "Manual reconciliation mismatch"
}
```

### POST /finance/{transactionNo}/invoice/request

- Roles: ADMIN, FINANCE

### POST /finance/{transactionNo}/invoice/issue

- Roles: ADMIN, FINANCE
- Required header: `X-Secondary-Password`
- Body:

```json
{
  "invoiceNo": "INV-2026-0001"
}
```

## File Endpoints

### GET /file/check?hash={sha256}

- Roles: ADMIN, STAFF, OPERATOR
- Example: `/file/check?hash=abc123...`

### POST /file/upload-file (New: Real File Upload)

- Roles: ADMIN, STAFF, OPERATOR
- Content-Type: `multipart/form-data`
- Form Data:
  - `file`: (The actual standard PDF or file upload)
  - `hash` (optional): file hash string
  - `expiresAt` (optional): ISO-8601 date string e.g. "2026-04-20T10:00:00"

### GET /file/{id}

- Roles: ADMIN, STAFF, OPERATOR

### GET /file

- Roles: ADMIN, STAFF, OPERATOR

### DELETE /file/{id}

- Roles: ADMIN, STAFF, OPERATOR

### POST /file/{id}/restore

- Roles: ADMIN, STAFF, OPERATOR

### GET /file/recycle-bin

- Roles: ADMIN, STAFF, OPERATOR

### POST /file/{hash}/rollback/{targetVersion}

- Roles: ADMIN, STAFF, OPERATOR

### GET /file/{id}/preview

- Roles: ADMIN, STAFF, OPERATOR

### GET /file/{id}/content

- Roles: ADMIN, STAFF, OPERATOR

## Suggested End-to-End Flow

1. Register admin user.
2. Login and save `data.token`.
3. Create a property.
4. Create an appointment for that property.
5. Record finance transaction for the appointment.
6. Fetch daily statement and daily export JSON.
7. Mark transaction exception and run invoice request/issue.
8. Upload and preview a file.
9. Logout and verify old token no longer works.
