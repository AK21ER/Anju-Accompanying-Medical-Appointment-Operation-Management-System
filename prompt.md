## Anju Medical Appointment System

The **Anju Accompanying Medical Appointment Operation Management System** is designed to provide backend API capabilities for operation administrators, reviewers, scheduling dispatchers, financial personnel, and frontline service personnel, focusing on the offline operational closed-loop of "property management + accompanying medical appointment service scheduling."

**Property Domain** supports property creation, editing, listing/delisting, association of materials (images/videos), configuration of rent and deposit rules, management of available rental periods and vacancy periods, and provides compliance field validation and review workflows.

**Appointment Domain** offers maintenance of available time slots, configuration of service types and standard durations (15/30/60/90 minutes), conflict detection (the same accompanying medical staff cannot accept overlapping appointments in the same time slot, and the same resource cannot be double-booked), rescheduling/cancellation rules (latest 24 hours in advance, maximum of 2 reschedules per order, cancellation penalty capped at 10% of the order amount or 50 RMB), and lifecycle management driven by a state machine. Appointments not confirmed within 15 minutes are automatically released.

**Financial Domain** records multi-channel payment transactions (for bookkeeping only, no integration with external payment systems), refunds (original/non-original payment methods), and settlement details. Daily statements are generated with support for exception flagging, export, and tracking of invoice requests and issuance status.

**File Domain** provides chunked upload, resumable upload, hash-based deduplication for instant uploads, concurrency and bandwidth throttling, preview capabilities for common documents/images/audio-video files, multi-version rollback, and a recycle bin policy (retained for 30 days).

The backend is implemented as a monolithic Java service (using Spring ecosystem components) running in a Docker offline environment, with configuration and registration managed solely by a locally deployed Nacos. Business data is stored in MySQL.

API capabilities are organized by resource domains into interface families such as authentication and permissions, property management, appointment scheduling, financial reconciliation, file and version management, audit and export, etc. Each interface type supports semantics like create/query/update/submit for review/approve/invalidate, with role-based access control (minimum privileges, sensitive operations requiring secondary password verification).

Core data models include:

- Property master table (unique property code, status enumeration, rent/deposit Decimal, available rental start/end dates, compliance validation results).
- Appointment table (unique appointment number, start/end times, status enumeration, penalty Decimal, reschedule count Int).
- Transaction and settlement table (unique transaction number, channel enumeration, refundable flag).
- File table (unique index on content hash, chunk information, version number, deletion flag, and expiration time).

Key fields are indexed with composite indexes to support queries by status and time range.

**Security and Compliance Requirements:**

- Username and password are validated locally, with passwords stored using strong hashing (minimum 8 characters, including letters and numbers).
- Sensitive fields (e.g., ID numbers, contact information) are encrypted in the database or output with masking.
- Full-chain audit logs record the operator, timestamp, and summaries of key field changes.
- Import/export functionality supports field mapping and type validation (required fields, length, enumeration, date format) and includes idempotency key rules to prevent duplicate bookkeeping and appointment creation.
