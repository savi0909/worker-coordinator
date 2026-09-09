# Generic Worker-ID Coordinator

This is a **generic Worker-ID Coordinator intended to provide worker identity and lease management for distributed
services across arbitrary business and technical domains**.

It manages the control-plane hierarchy `Product → Service → Worker Type → Worker Instance → Worker Lease`. A running
instance acquires a `(regionId, workerId)` slot and uses its epoch and lease to establish ownership. The worker—not this
service—generates local Snowflake-style IDs (`timestamp | region | worker | sequence`). Regions and worker namespaces
are isolated by product, service, worker type, and region.

For example, `Payments / Clearing / Payment ID Generator` and `Orders / Order Processing / Order ID Generator` can both
own worker 17 in region 1 without conflict.

## API

- `POST/GET /api/v1/products`
- `POST /api/v1/products/{productId}/services`, `GET /api/v1/services/{serviceId}`
- `POST /api/v1/services/{serviceId}/worker-types`, `GET /api/v1/worker-types/{workerTypeId}`
- `POST /api/v1/workers/acquire|renew|release`

## Local development

Provide PostgreSQL via `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, and `SPRING_DATASOURCE_PASSWORD`, then run
`mvn spring-boot:run`. Flyway owns migrations and JPA validates the schema. Run tests with `mvn test`; Testcontainers
integration tests require Docker.

## Current limitations

Registration, hierarchy persistence, and basic worker allocation are implemented. Slot rows are retained, seeded on
first acquisition, and allocated in a PostgreSQL transaction with `FOR UPDATE SKIP LOCKED`; each ownership transition
increments the epoch and records lease history. Lease expiry processing, end-to-end self-fencing, ID generation/Base62,
SDK, and production authentication remain deferred.

See [the technical reference](docs/TECHNICAL_SPEC.md) for the data model, API contracts, locking algorithm, invariants, and future-work boundaries.

## Modules

- `coordinator-service`: Spring Boot coordinator and PostgreSQL migrations.
- `worker-client-sample`: standalone registration/acquisition/renewal/release client. See its [run guide](worker-client-sample/README.md).
