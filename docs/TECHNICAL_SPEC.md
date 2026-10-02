# Worker-ID Coordinator: technical reference

## Purpose

The Worker-ID Coordinator is generic control-plane infrastructure for distributed services that need a unique local
ID-generation identity. It manages a leased `(regionId, workerId)` slot; it does **not** generate Snowflake IDs, encode
Base62, or participate in per-ID requests. The reusable `worker-id-client` module performs optional local
Snowflake-style generation after a client has obtained that lease.

## Domain model

```text
Product → Service → Worker Type → Worker Instance → Worker Lease
```

Stable IDs identify products, services, and worker types. Names are metadata. A worker namespace is
`(productId, serviceId, workerTypeId, regionId)`. A logical slot adds `workerId`; an ownership generation adds
monotonically increasing `epoch`.

Slots are namespace-local: unrelated worker types may both own `regionId=1, workerId=17`.

## HTTP API

| Endpoint                                         | Result                                                     |
|--------------------------------------------------|------------------------------------------------------------|
| `POST /api/v1/products`                          | Idempotently registers a product.                          |
| `POST /api/v1/products/{productId}/services`     | Registers a service belonging to the product.              |
| `POST /api/v1/services/{serviceId}/worker-types` | Registers a worker type belonging to the service.          |
| `POST /api/v1/workers/acquire`                   | Returns a new or still-valid lease for an instance.        |
| `POST /api/v1/workers/renew`                     | Extends a valid lease only when instance and epoch match.  |
| `POST /api/v1/workers/release`                   | Releases a valid lease only when instance and epoch match. |

The payment sample additionally exposes `POST /api/v1/payments`. It accepts a UUIDv7 `clientIdempotencyKey` and a
positive decimal `amount`. A new request returns `201 Created` with a generated numeric payment `id`; replaying the
same key returns the existing payment with `200 OK`.

Requests use UUID `instanceId` and UUID `registrationId`. The current allocator treats an existing unexpired lease for
the same instance as an acquisition retry; persistent request-key replay records are schema-ready but not yet wired into
lease operations.

Lease responses include `productId`, `serviceId`, `workerTypeId`, `regionId`, `workerId`, `epoch`, `instanceId`,
`leaseExpiry`, and `leaseDuration`.

## Allocation and locking

`PostgresWorkerCoordinator` is transactional. On acquisition it:

1. Validates the active product → service → worker-type hierarchy and region range (`0..15`).
2. Inserts the configured worker-ID range into `workers` with `INSERT ... ON CONFLICT DO NOTHING`; slots are never
   deleted.
3. Locks an existing unexpired slot held by the requesting instance, if any, and returns it.
4. Otherwise selects one AVAILABLE or expired slot using `FOR UPDATE SKIP LOCKED`, ordered by `worker_id`.
5. Updates the locked row to LEASED, increments `current_epoch`, assigns owner and database-calculated expiry, and
   inserts lease history in the same transaction.

`SKIP LOCKED` means concurrent coordinator instances do not wait on an already-selected candidate and cannot
intentionally allocate the same locked row. If all candidate rows are presently leased or locked, acquisition returns
`WORKER_NOT_AVAILABLE`.

Renewal is a conditional update requiring the same namespace, slot, epoch, owner instance, LEASED status, and unexpired
lease. Release similarly requires matching epoch and owner, clears ownership, retains the epoch, and marks the history
row released.

## Persistence

Flyway migration `V1__create_coordinator_schema.sql` owns these PostgreSQL tables:

- `products`, `services`, `worker_types`: registered hierarchy with lifecycle status.
- `workers`: durable current state keyed by `(product_id, service_id, worker_type_id, region_id, worker_id)`.
- `worker_lease_history`: one row per ownership generation, unique on slot plus epoch.
- `idempotency_records`: reserved persistent idempotency storage.

The payment sample owns a separate `payments` table with `id BIGINT`, timestamps, unique UUIDv7
`client_idempotency_key`, and `amount DECIMAL(19,4)`. This is sample-domain persistence and is not part of the
generic coordinator schema.

Lease timestamps use `TIMESTAMPTZ`; epochs use `BIGINT`; worker instances use UUID. JPA validation is configured with
`ddl-auto=validate` so the Flyway schema is authoritative.

## Configuration

| Property                             | Default | Meaning                                                                 |
|--------------------------------------|---------|-------------------------------------------------------------------------|
| `coordinator.lease-duration`         | `30s`   | Lease expiry period.                                                    |
| `coordinator.lease-renewal-interval` | `10s`   | Intended client renewal cadence.                                        |
| `coordinator.region-id`              | `1`     | Local deployment metadata; acquisition accepts a request region.        |
| `coordinator.worker-id-bits`         | `10`    | Creates `2^bits` slots per namespace; implementation permits 1–16 bits. |

The sample payment datasource defaults to `jdbc:postgresql://localhost:5432/paymentsDB`, username `postgres`, and
password `postgres`. Override it with `PAYMENTS_DB_URL`, `PAYMENTS_DB_USERNAME`, and `PAYMENTS_DB_PASSWORD`.

## Error codes

Key API errors are `PRODUCT_NOT_FOUND`, `SERVICE_NOT_FOUND`, `WORKER_TYPE_NOT_FOUND`, `WORKER_TYPE_DISABLED`,
`REGION_NOT_FOUND`, `WORKER_NOT_AVAILABLE`, `LEASE_EXPIRED`, `STALE_EPOCH`, and `IDEMPOTENCY_CONFLICT`.

## Non-goals and follow-up work

The current implementation is a foundation, not a complete distributed fencing protocol. Future work should add
persistent `registrationId` request replay, integration/concurrency tests using Testcontainers PostgreSQL,
lease-expiration operations/audit, SDK renewal-race integration tests, observability metrics,
authentication/authorization, and operational inspection APIs.

The `worker-id-client` generator refuses generation without a currently valid lease, treats expiry as invalid, and
fences on renewal failure, explicit shutdown/fencing, stale epoch updates, and clock rollback. Its default layout is 41
timestamp bits, 4 region bits, 10 worker bits, and 8 sequence bits in a positive signed 64-bit value; all widths and the
custom epoch are configurable. Sequence overflow waits for the next millisecond. Base62 is optional and numeric, using
`0-9`, `a-z`, `A-Z`.

These checks are local process safeguards. They do not claim cross-process uniqueness or complete distributed
self-fencing correctness; PostgreSQL coordinator invariants and downstream uniqueness constraints remain the system
boundary.

The payment sample follows the same separation: UUIDv7 is the client idempotency/request key, while the lease-aware
Snowflake-style signed `BIGINT` is the payment identity. See `docs/PAYMENT_SAMPLE_SCHEMA.md`; payment persistence is
implemented only in the sample, not in the generic coordinator.
