# Testing guide

## Existing command

```powershell
mvn test
```

This compiles all modules and runs coordinator, client SDK, and sample UUIDv7 unit tests.

The SDK unit suite covers unique generation, configured bit fields, Base62 alphabet and conversion, sequence overflow
waiting, exact expiry fencing, stale epochs, explicit fencing/shutdown, and clock rollback. The clock and sleeper are
injectable so overflow and rollback are deterministic.

The sample storage layer requires PostgreSQL for integration testing. Use a database named `paymentsDB` with the
configured `postgres` credentials, then start the sample so Flyway creates `payments`. Verify that a new UUIDv7 key
returns `201` with a numeric `id`, and replaying the same key returns `200` with the same payment ID.

## Required integration coverage

Add Testcontainers PostgreSQL tests for:

- Flyway migration against PostgreSQL 18.
- Foreign keys, namespace primary key, and lease-history epoch uniqueness.
- Acquire returns a leased slot and records history.
- Re-acquire by the same instance returns its valid lease.
- Renewal retains epoch; release retains slot epoch; reacquisition increments epoch.
- Invalid hierarchy, disabled worker types, stale epoch, and stale instance errors.

## Required concurrency coverage

Use multiple concurrent acquisition requests against the same namespace. Assert that active leases have distinct worker
IDs and that no lease-history ownership generation is duplicated. Repeat at and beyond configured slot capacity.

## Manual smoke test

1. Start PostgreSQL and the coordinator.
2. Start two sample clients with unique IDs.
3. Confirm they log different worker IDs.
4. Stop one client and confirm its graceful release.
5. Stop the coordinator or database; confirm a client logs lease loss after renewal cannot succeed and does not retain
   its in-memory lease.
6. Confirm the sample logs a payment ID only after lease acquisition and that generation is fenced after renewal failure
   or shutdown. These checks do not establish cross-process uniqueness beyond coordinator slot ownership and local
   validity checks.
7. Submit the same payment request twice and confirm `client_idempotency_key` is unique while `payments.id` remains the
   generated `BIGINT` identity.
