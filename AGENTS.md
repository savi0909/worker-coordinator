# Worker-ID Coordinator contribution guide

This repository is generic distributed-systems infrastructure. Preserve the hierarchy **Product → Service → Worker
Type → Worker Instance → Worker Lease**. Never introduce business-domain-specific names or assumptions.

- Java 21 and Spring Boot 3; use constructor injection and REST/domain/JPA DTO separation.
- PostgreSQL is authoritative. Evolve schema exclusively through Flyway migrations; keep `ddl-auto=validate`.
- Lease expiry is `Instant`, duration is `Duration`, and epochs are PostgreSQL `BIGINT` and strictly monotonic per
  worker slot.
- Allocation uses PostgreSQL transactions and `FOR UPDATE SKIP LOCKED`; preserve its single-slot lock, epoch increment,
  and lease-history write in one transaction. Do not assert end-to-end self-fencing beyond these database invariants.
- Do not introduce leader election, a lease-expiry scheduler, ID generation, or worker self-fencing without an explicit
  design/task.
- Do not expose entities or Java exception internals from API controllers. Add explicit error codes and validation.
- Run `mvn test` before handoff when the environment permits it.
