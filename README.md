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
increments the epoch and records lease history. The `worker-id-client` SDK provides optional local lease-aware
Snowflake-style IDs and numeric Base62. The server does not issue individual IDs. Lease-expiration operations,
end-to-end distributed self-fencing and production authentication remain deferred.

See [the technical reference](docs/TECHNICAL_SPEC.md) for the data model, API contracts, locking algorithm, invariants,
and future-work boundaries.

## Modules

- `coordinator-service`: Spring Boot coordinator and PostgreSQL migrations.
- `worker-id-client`: reusable local lease-aware generator and Base62 encoder.
- `worker-client-sample`: standalone registration/acquisition/renewal/release client. See
  its [run guide](worker-client-sample/README.md).

## URL shortener Docker integration

The independent [URL shortener lab](../url-shortener-lab/README.md) uses this
coordinator and SDK for short codes. Its [Compose file](../url-shortener-lab/compose.yml)
builds this project's Dockerfile and runs the coordinator on loopback8120 with
its own PostgreSQL5552/retained volume; it also runs two shortener APIs and their
separate storage/cache. Build with `mvn -B -ntp install` here first, then follow
the shortener guide. This does not start the payment sample or change generic
coordinator allocation. No production/global-ID uniqueness claim is implied.
