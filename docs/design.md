# System Design

## 1. Architectural Overview (Monolithic)

The Anju Medical Appointment System is built as a single, modular Spring Boot monolithic application. The monolithic architecture was chosen to accelerate initial development, simplify deployment via a single Docker container, and keep inter-domain communications overhead minimal. Given that the domain constraints require stringent checks (e.g., appointment scheduling conflicts and cross-module validations), synchronous inter-service communication would introduce unwanted latency and consistency challenges if a microservices architecture were used early on. Nacos is used for basic configuration/discovery to allow easy extraction of microservices if horizontal scaling becomes a priority in the future.

## 2. AOP Transaction Logging

Instead of cluttering business logic with explicit database inserts, system monitoring is accomplished through Aspect-Oriented Programming (AOP). Time, method signatures, operator identities, and incoming arguments are extracted by `AuditLogAspect`. Interception is managed at runtime for any method annotated with `@Auditable`. This decouples the core domain logic from horizontal "plumbing" requirements and guarantees that an audit trail is maintained across different domain logic boundaries.

## 3. PII DB Encryption

To adhere to strict industry compliance regarding Personally Identifiable Information (PII), we utilize a JPA `AttributeConverter` (`CryptoConverter`). This class intercepts fields marked with the converter annotation prior to writing data to MySQL. It applies AES encryption to the strings, ensuring data stored at rest on the disk is masked. When JPA retrieves the records, the converter transparently decrypts the ciphertext back into plain text, abstracting the security requirements out of the standard controller/service code.
