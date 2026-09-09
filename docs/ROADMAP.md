# Roadmap

## Completed

- Generic product → service → worker-type hierarchy and registration APIs.
- Flyway PostgreSQL schema, including durable worker slots and lease history.
- PostgreSQL worker allocation using transactions and `FOR UPDATE SKIP LOCKED`.
- Lease acquire, renew, and release APIs with epoch validation.
- Multi-module Maven build with a coordinator service and runnable worker client sample.
- PostgreSQL 18-compatible Flyway configuration.
- Reusable lease-aware client SDK with configurable Snowflake-style IDs, optional Base62, and local fencing tests.
- Payment sample PostgreSQL storage with Flyway, JPA validation, UUIDv7 idempotency, and generated numeric payment IDs.

## Current phase

The service can allocate and manage basic leases. The next priority is proving and strengthening its runtime behavior rather than adding business-domain features.

## Next priorities

1. Add PostgreSQL Testcontainers integration tests for migration, allocation, renewal, release, and epoch increments.
2. Add concurrent acquisition tests across coordinator instances and verify no duplicate active slot ownership.
3. Persist and enforce request idempotency using `idempotency_records` and `registrationId`.
4. Add worker-slot and lease-history inspection APIs.
5. Add metrics, structured audit events, and operational dashboards.
6. Add SDK integration/concurrency tests around lease renewal races and application shutdown.

## Explicitly deferred

- Lease-expiry scheduler and expiry auditing.
- Cross-process uniqueness claims and complete distributed self-fencing correctness.
- Authentication, authorization, mTLS, leader election, and cross-region coordination.
- Kubernetes/cloud deployment automation and administrative reassignment workflows.
