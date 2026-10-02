# Architecture decisions

## ADR-001: PostgreSQL is the concurrency authority

The coordinator permits multiple service instances. PostgreSQL transactions, constraints, and row locks—not application
leader election—coordinate worker-slot ownership.

## ADR-002: Slots are durable namespace records

The `workers` row is never deleted when a lease ends. It transitions through AVAILABLE, LEASED, and expired states while
retaining its epoch. This preserves ownership history and prevents epoch reuse.

## ADR-003: Allocation uses `FOR UPDATE SKIP LOCKED`

Acquisition selects one available or expired row in a transaction using `FOR UPDATE SKIP LOCKED`. A competing
coordinator skips a slot already being allocated instead of waiting or allocating it twice.

## ADR-004: Epoch fences ownership generations

Every new slot ownership generation increments `current_epoch`. Renewal retains the epoch; release retains it in the
slot. Requests with stale owner/epoch data fail. Epoch is coordinator metadata, not part of the initial Snowflake
layout.

## ADR-005: Worker IDs are namespace-local

Worker ID uniqueness is scoped to product, service, worker type, and region. No global worker-ID sequence exists; the
same numeric ID may appear in unrelated namespaces.

## ADR-006: The worker client self-fences locally

The reusable client SDK makes the generation boundary lease-aware. It clears the active lease and permanently fences on
renewal failure, expiry, stale epoch, shutdown, explicit fencing, or clock rollback. The sample adapts coordinator
leases into the SDK and owns payment-specific naming only in its demonstration wrapper. This is local fail-closed
behavior, not a claim of complete distributed self-fencing.

## ADR-007: IDs are locally generated from a configurable layout

The SDK uses the leased region and worker fields in a positive signed 64-bit Snowflake-style layout. The initial default
is `41/4/10/8` bits for timestamp/region/worker/sequence and a configurable epoch. Base62 encoding is an opt-in
representation and does not change the numeric ID. Clock rollback fails closed; sequence exhaustion waits for the next
millisecond.

## ADR-008: Sample payment identity is separate from request identity

The payment sample stores the lease-aware Snowflake value as `payments.id BIGINT`. A client-provided UUIDv7 is stored
as the unique `client_idempotency_key`. Repeating that key returns the existing payment and never treats the UUID as
the payment ID. Flyway owns the sample schema and JPA uses `ddl-auto=validate`; the coordinator schema remains
business-domain-neutral.
