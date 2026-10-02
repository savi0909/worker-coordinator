# Local runbook

## Prerequisites

- Java 21 and Maven.
- PostgreSQL 18 (or another supported PostgreSQL version).
- A database named `worker_coordinator` and a user permitted to create Flyway tables.

## Start the coordinator

```powershell
$env:SPRING_DATASOURCE_URL="jdbc:postgresql://localhost:5432/worker_coordinator"
$env:SPRING_DATASOURCE_USERNAME="worker_coordinator"
$env:SPRING_DATASOURCE_PASSWORD="worker_coordinator"
mvn -pl coordinator-service spring-boot:run
```

Flyway runs before JPA starts. The coordinator listens on port 8080 unless `SERVER_PORT` or `--server.port` is supplied.

## Start worker clients

The sample also requires a PostgreSQL database named `paymentsDB`; by default it uses username and password `postgres`.
Create it before starting the sample:

```sql
CREATE DATABASE "paymentsDB";
```

It contacts `http://localhost:8080` to register and acquire a lease. The default sample is non-web:

```powershell
mvn -pl worker-client-sample spring-boot:run
```

Start web-mode clients on unique ports when each client needs HTTP/Actuator exposure:

```powershell
mvn -pl worker-client-sample spring-boot:run "-Dspring-boot.run.arguments=--spring.main.web-application-type=servlet --server.port=8083 --worker-client.instance-id=11111111-1111-1111-1111-111111111111"
mvn -pl worker-client-sample spring-boot:run "-Dspring-boot.run.arguments=--spring.main.web-application-type=servlet --server.port=8084 --worker-client.instance-id=22222222-2222-2222-2222-222222222222"
```

In web mode, `POST /api/v1/payments` accepts a UUIDv7 `clientIdempotencyKey` and amount. Flyway creates the payment
table and JPA validates it with `ddl-auto=validate`. Override `PAYMENTS_DB_URL`, `PAYMENTS_DB_USERNAME`, and
`PAYMENTS_DB_PASSWORD` for a different database.

Each process must use a distinct `worker-client.instance-id`/`WORKER_INSTANCE_ID`. The coordinator logs or client logs
show the acquired `workerId` and epoch.

## Troubleshooting

- **Flyway says PostgreSQL is unsupported:** confirm Flyway 11.18.0 and `flyway-database-postgresql` are resolved; run
  `mvn dependency:tree`.
- **`WORKER_NOT_AVAILABLE`:** all slots in the configured namespace are actively leased; raise
  `coordinator.worker-id-bits`, wait for expiry, or release clients.
- **Lease lost:** stop local ID generation; inspect coordinator/database availability and start a fresh client instance
  if required.
