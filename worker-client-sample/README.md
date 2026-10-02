# Worker client sample

This executable client registers its configured hierarchy, acquires a worker lease, renews it every 10 seconds, and
attempts release on graceful shutdown. It also persists sample payments in PostgreSQL. Payment-specific naming stays in
this sample while ID generation is provided by the reusable `worker-id-client` module. IDs are never generated without a
currently valid lease. Renewal failure, expiry, stale epoch, shutdown, explicit fencing, and clock rollback fence
generation.

The default signed 64-bit layout is timestamp/region/worker/sequence `41/4/10/8`, with epoch `2024-01-01T00:00:00Z`.
Configure it with `ID_TIMESTAMP_BITS`, `ID_REGION_BITS`, `ID_WORKER_BITS`, `ID_SEQUENCE_BITS`, and `ID_EPOCH_MILLIS`.
Base62 output uses `0-9`, `a-z`, `A-Z`.

This is local lease-aware generation only. It does not claim cross-process uniqueness or complete self-fencing
correctness beyond the coordinator invariants and implemented local checks.

## Payment identity model

The sample payment table is documented in [Payment sample schema](../docs/PAYMENT_SAMPLE_SCHEMA.md). Its two identity
fields are deliberately different:

```text
UUIDv7                    → client_idempotency_key / request identity
Snowflake-generated BIGINT → payments.id / payment identity
```

The UUIDv7 is unique for idempotency and request replay; it is never used as the payment ID. The generated numeric ID
uses the currently valid leased `regionId` and `workerId`.

Start the coordinator, then launch multiple processes with distinct instance UUIDs:

```powershell
mvn -pl worker-client-sample spring-boot:run
$env:WORKER_INSTANCE_ID=[guid]::NewGuid().ToString(); mvn -pl worker-client-sample spring-boot:run
```

The sample defaults to non-web mode, since it only needs to call the coordinator and obtain its worker ID at startup. To
expose an HTTP server (for example, an Actuator endpoint) for each client process, override it to servlet mode and
assign a distinct port:

```powershell
mvn -pl worker-client-sample spring-boot:run "-Dspring-boot.run.arguments=--spring.main.web-application-type=servlet --server.port=8083"
```

Run another instance on a different port (and give it a unique instance identity):

```powershell
mvn -pl worker-client-sample spring-boot:run "-Dspring-boot.run.arguments=--spring.main.web-application-type=servlet --server.port=8084 --worker-client.instance-id=22222222-2222-2222-2222-222222222222"
```

Set `COORDINATOR_URL`, `PRODUCT_ID`, `SERVICE_ID`, `WORKER_TYPE_ID`, `REGION_ID`, and `WORKER_INSTANCE_ID` through the
environment as needed.

## PostgreSQL payment storage

Create the database before starting the sample:

```sql
CREATE DATABASE "paymentsDB";
```

The default connection is `jdbc:postgresql://localhost:5432/paymentsDB` with username `postgres` and password
`postgres`.
Override `PAYMENTS_DB_URL`, `PAYMENTS_DB_USERNAME`, or `PAYMENTS_DB_PASSWORD` when needed. Flyway creates the
`payments` table and JPA runs with `ddl-auto=validate`.

Expose the payment endpoint by running the sample in servlet mode:

```powershell
mvn -pl worker-client-sample spring-boot:run "-Dspring-boot.run.arguments=--spring.main.web-application-type=servlet --server.port=8083"
```

Create a payment with a UUIDv7 idempotency key:

```powershell
curl.exe -X POST http://localhost:8083/api/v1/payments `
  -H "Content-Type: application/json" `
  -d '{"clientIdempotencyKey":"0199b1a4-7d2e-7c11-8b8f-6e0d3e4e0a01","amount": "12.3400"}'
```

The first request returns `201 Created` and a numeric `id`. Repeating the same UUIDv7 key returns the existing payment
with `200 OK`; the UUID is never used as the payment ID.
