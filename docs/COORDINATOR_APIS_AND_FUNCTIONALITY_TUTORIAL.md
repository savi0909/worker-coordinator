# Distributed coordinator: APIs and functionality

Staff-level tutorial · Updated 2026-10-12 · Java 21 / Spring Boot 3.

**Reading path:** section 1 gives the core model. Sections 2–4 explain the API;
sections 5–8 explain correctness; sections 9–12 cover capacity and failure.
**Implemented** means verified in this repository's source. **Extension** means
a proposed production capability. Examples are study instructions, not recorded
runtime results. This guide adds no application behavior.

## 1. The core model in five minutes

The coordinator grants a process temporary ownership of a worker slot. PostgreSQL
is the ownership authority; every coordinator replica uses that same database.

```text
Product → Service → Worker Type → Worker Instance → Worker Lease
                                  process UUID      slot + epoch + expiry

Process startup -> register hierarchy -> acquire -> periodically renew -> release
Process crash   -> no release -> expiry -> another instance may acquire the slot
```

The hierarchy defines scope. A durable **slot** is the reusable resource; a
**lease** is one generation of ownership. A slot retains its epoch after release
or expiry. A new allocation increments that epoch; renewal does not.

| Mechanism | Purpose | Boundary |
| --- | --- | --- |
| Namespace | Isolate worker allocation between products/services/types/regions | Worker number alone is not globally unique. |
| Row lock | Serialize ownership transitions for one slot | Does not lock the whole namespace. |
| Transaction | Commit ownership, epoch and acquisition history together | A lost HTTP response still leaves an unknown client outcome. |
| Epoch and owner predicate | Reject obsolete renew/release requests | Does not automatically fence writes to another system. |
| Expiry | Make abandoned slots eligible for reuse | No background expiry scheduler is required for allocation. |
| Read API | Inspect hierarchy and effective slot state | Observation does not grant ownership. |

The coordinator does not issue individual IDs. The optional `worker-id-client`
SDK generates IDs locally using leased identity. Its local validity checks are
separate from the database's ownership guarantees.

**Invariant:** for a slot, one committed current-owner tuple exists; new
ownership generations strictly increase its retained epoch. Never interpret this
as proof that an isolated old process has stopped executing.

## 2. Identity: distinguish namespace, slot, process and generation

```text
namespace  = (productId, serviceId, workerTypeId, regionId)
slot       = namespace + workerId
ownership  = slot + instanceId + epoch
validity   = ownership + leaseExpiry
```

| Field | Meaning | How to use it |
| --- | --- | --- |
| Product / service / worker-type IDs | Stable hierarchy identities | Register before acquisition; names are metadata. |
| `regionId` | Namespace partition, accepted range `0..15` | Use the same value throughout a lease lifecycle. |
| `workerId` | Reusable slot number within that namespace | Save the returned value; do not choose it by reading availability. |
| `instanceId` | UUID identifying the running process incarnation | Give separate live processes distinct identities. |
| `epoch` | PostgreSQL `BIGINT` ownership generation for the slot | Echo the returned value on renew/release. |
| `registrationId` | Required request UUID | Accepted but currently not used for durable operation replay. |
| `leaseExpiry` / `leaseDuration` | Absolute deadline / configured duration | Java types are `Instant` / `Duration`. |

**Implemented:** service IDs and worker-type IDs are globally unique within their
tables, rather than parent-local names. This lets a worker-type read resolve its
product/service prefix. Slot uniqueness still uses the full composite key.

Two worker types can both own worker `7` in region `1`. That is valid namespace
isolation. It does not establish disjoint numeric ID ranges if client generators
omit namespace information from their ID layout.

**Checkpoint:** identify the full slot key before reasoning about uniqueness.
Mistake: treating `workerId=7` or `epoch=3` as a global identity.

## 3. API map: all paths start with `/api/v1`

### Register and inspect the hierarchy

| Method and path | Request or result |
| --- | --- |
| `POST /products` | `{productId, productName}` → product definition, `201` |
| `GET /products` | Product definitions sorted by ID, `200` |
| `GET /products/{productId}` | One product definition, `200` |
| `POST /products/{productId}/services` | `{serviceId, serviceName}` → service definition, `201` |
| `GET /products/{productId}/services` | Child services sorted by ID, `200` |
| `GET /services/{serviceId}` | One service definition, `200` |
| `POST /services/{serviceId}/worker-types` | `{workerTypeId, workerTypeName}` → worker-type definition, `201` |
| `GET /services/{serviceId}/worker-types` | Child worker types sorted by ID, `200` |
| `GET /worker-types/{workerTypeId}` | One worker-type definition, `200` |

Definitions return identity, name and status; service/type definitions also return
their parent ID. New registrations are `ACTIVE`. Existing parents with no children
return `[]`; unknown parents return `404`. Hierarchy lists have no pagination.

**Implemented registration behavior:** sequentially repeating the same ID,
parent and name returns the stored definition, still with `201`. Different
metadata conflicts: product uses `IDEMPOTENCY_CONFLICT`, service uses
`SERVICE_ALREADY_EXISTS`, worker type uses `WORKER_TYPE_ALREADY_EXISTS`.
Registration does not rename or move resources. IDs are nonblank, at most 100
characters; names are nonblank, at most 255.

The implementation checks existence then saves through JPA. Sequential replay
does not establish a complete concurrent-registration conflict protocol; race
handling needs dedicated database tests. There is no update/delete/disable API.

### Mutate ownership

| Method and path | Input | Success |
| --- | --- | --- |
| `POST /workers/acquire` | Namespace, instance UUID, registration UUID | `WorkerLease`, `201`, including same-instance reuse |
| `POST /workers/renew` | Acquire fields plus returned worker ID and epoch | Updated `WorkerLease`, `200` |
| `POST /workers/release` | Same identity fields as renew | `{released:true, epoch:<retained epoch>}`, `200` |

`WorkerLease` returns `productId`, `serviceId`, `workerTypeId`, `regionId`,
`workerId`, `epoch`, `instanceId`, `leaseExpiry` and `leaseDuration`.
Renew/release require `workerId >= 0` and `epoch >= 1`. Send every identity field
explicitly; primitive numeric DTO fields are not presence-validated.

### Inspect slots

| Method and path | Result |
| --- | --- |
| `GET /worker-types/{workerTypeId}/workers` | `{workers, limit, offset, hasMore}` |
| `GET /worker-types/{workerTypeId}/regions/{regionId}/workers/{workerId}` | One slot or `404 WORKER_NOT_FOUND` |

List parameters: optional `regionId=0..15`, optional
`status=AVAILABLE|LEASED|EXPIRED`, `limit=1..1000` (default 100), and `offset>=0`
(default 0). Results are ordered by `regionId, workerId`. Each slot returns its
namespace, worker ID, `epoch`, `ownerInstanceId`, effective status and expiry.
Available slots have null owner/expiry. Section 7 explains effective status.

## 4. One complete API lifecycle

Start the coordinator against PostgreSQL using the [runbook](RUNBOOK.md).
The default HTTP port is 8080; use your actual port if overridden. Run this
PowerShell study sequence promptly: the default lease lasts 30 seconds.

```powershell
$base = 'http://localhost:8080/api/v1'
function Send-CoordinatorPost($path, $payload) {
    Invoke-RestMethod "$base$path" -Method Post -ContentType 'application/json' `
        -Body ($payload | ConvertTo-Json)
}

Send-CoordinatorPost '/products' @{
    productId='study-product'; productName='Study Product'
}
Send-CoordinatorPost '/products/study-product/services' @{
    serviceId='study-service'; serviceName='Study Service'
}
Send-CoordinatorPost '/services/study-service/worker-types' @{
    workerTypeId='study-worker'; workerTypeName='Study Worker'
}

$instance = [guid]::NewGuid().ToString()
$acquire = @{
    productId='study-product'; serviceId='study-service'
    workerTypeId='study-worker'; regionId=1
    instanceId=$instance; registrationId=([guid]::NewGuid().ToString())
}
$lease = Send-CoordinatorPost '/workers/acquire' $acquire
$lease

Invoke-RestMethod "$base/worker-types/study-worker/workers?regionId=1&status=LEASED&limit=10"
Invoke-RestMethod "$base/worker-types/study-worker/regions/1/workers/$($lease.workerId)"

$owned = @{
    productId=$lease.productId; serviceId=$lease.serviceId
    workerTypeId=$lease.workerTypeId; regionId=$lease.regionId
    workerId=$lease.workerId; epoch=$lease.epoch; instanceId=$lease.instanceId
    registrationId=([guid]::NewGuid().ToString())
}
$renewed = Send-CoordinatorPost '/workers/renew' $owned
$renewed # same worker/owner/epoch, recalculated expiry
$released = Send-CoordinatorPost '/workers/release' $owned
$released
```

Before the first acquisition, the registered type lists no slots. First acquisition
seeds the selected region's slots. After release, your slot is `AVAILABLE`, owner
and expiry are cleared, and epoch is retained. Repeating release conflicts;
release is not currently a durable replay operation.

**Checkpoint:** explain why `201` alone does not prove acquire created a new
ownership generation. Mistake: assuming HTTP status codes distinguish replay.

## 5. Acquisition: one locked slot, one atomic ownership transition

**Implemented** in [PostgresWorkerCoordinator.acquire()](../coordinator-service/src/main/java/com/example/workercoordinator/infrastructure/persistence/PostgresWorkerCoordinator.java):

```text
BEGIN
  validate hierarchy and region
  seed namespace/region slots using INSERT ... ON CONFLICT DO NOTHING
  lock an existing unexpired same-instance lease; return it if found
  otherwise lock one available/expired candidate using SKIP LOCKED
  increment epoch; assign owner and expiry; set LEASED
  insert ownership-generation history
COMMIT
return lease
```

The candidate query's essential shape is:

```sql
SELECT worker_id, current_epoch
FROM workers
WHERE product_id = :productId AND service_id = :serviceId
  AND worker_type_id = :workerTypeId AND region_id = :regionId
  AND (status <> 'LEASED' OR lease_expiry <= now())
ORDER BY worker_id
FOR UPDATE SKIP LOCKED LIMIT 1;
```

If A locks candidate 0, B can skip it and lock candidate 1. A's lock remains held
while epoch, ownership and history change. If history insertion fails, the
transaction rolls back the ownership update too. No process-local mutex could
provide that protection across separate coordinator JVMs.

`SKIP LOCKED` is appropriate for choosing an interchangeable resource, not for
obtaining a complete availability snapshot. An empty selection may mean every
eligible slot is temporarily locked, rather than permanently exhausted. It also
does not eliminate waits in seeding, hierarchy lookups or the same-instance
`FOR UPDATE` query. [PostgreSQL locking semantics](https://www.postgresql.org/docs/current/sql-select.html#SQL-FOR-UPDATE-SHARE).

**Implemented boundaries:** validation checks product active state, service-to-
product association, worker-type-to-service association and worker-type active
state. It does not check service active state. A disabled product currently emits
`SERVICE_DISABLED`. No public lifecycle-status mutation API exists.

### Persistence that preserves ownership generations

| Table | Durable responsibility |
| --- | --- |
| `products`, `services`, `worker_types` | Hierarchy definitions and lifecycle status |
| `workers` | Current slot state; composite primary key; retained `current_epoch` |
| `worker_lease_history` | One row per allocation generation; unique slot plus epoch |
| `idempotency_records` | Schema exists; lease operations do not use it |

There is no separate worker-instance table: current ownership stores its UUID
on the slot, and history records the owning instance per generation. History
records allocation expiry and explicit release; renew does not update its
`expires_at`, and passive expiry does not append an expiry event. It is therefore
not a complete chronological renewal/expiry audit stream.

Flyway owns schema changes; JPA uses `ddl-auto=validate`. Timestamps are
`TIMESTAMPTZ`, epochs `BIGINT`, owners UUID. Foreign keys establish existence;
the service code checks the supplied hierarchy associations. Do not assume the
individual foreign keys enforce every cross-table association themselves.

**Checkpoint:** the lock, epoch increment and history insert belong to the same
transaction. Mistake: claiming all acquisition work is nonblocking because its
candidate query uses `SKIP LOCKED`.

## 6. Renew, release and expiry: read the exact predicates

```text
AVAILABLE(epoch k) --acquire--> LEASED(epoch k+1)
LEASED(epoch k+1)  --renew----> LEASED(epoch k+1, new expiry)
LEASED(epoch k+1)  --release--> AVAILABLE(epoch k+1)
LEASED, past expiry ----------> effectively EXPIRED; stored row unchanged
EXPIRED           --acquire--> LEASED(epoch k+2, new owner)
```

**Renew** is one conditional update. Namespace, worker ID, owner UUID, epoch and
stored `LEASED` status must match, and `lease_expiry > now()`. Expiry becomes
`now() + configured lease duration`; it is not extended by adding duration to
the previous deadline. A nonmatching update returns `409 LEASE_EXPIRED`, which
also covers stale owner/epoch, not just elapsed time.

**Release** matches slot, owner, epoch and stored `LEASED` status. It clears
owner/expiry, retains epoch and marks the matching history row released. It does
not require unexpired time or run hierarchy validation. Consequently, an expired
owner may release its unreassigned slot, but cannot release a successor's slot.
Repeating an already successful release returns `409 STALE_EPOCH`.

### Concrete ownership timeline

Illustrative seconds on a database clock; assume each operation is short:

| Time | Operation | Stored slot 0 | Effective state / outcome |
| --- | --- | --- | --- |
| 0 | A acquires | A, epoch 1, expiry 30 | LEASED |
| 10 | A renews | A, epoch 1, expiry 40 | LEASED; history allocation expiry remains 30 |
| 41 | Read only | A, epoch 1, expiry 40, stored LEASED | EXPIRED |
| 42 | B acquires that slot | B, epoch 2, expiry 72 | LEASED; second history generation |
| 43 | A sends epoch-1 renew/release | Unchanged | LEASE_EXPIRED / STALE_EPOCH |
| 44 | B releases | No owner, epoch 2, no expiry | AVAILABLE |

No expiry scheduler is needed: acquisition recognizes eligibility from the
deadline. Expiry alone neither clears the owner nor increments epoch.

### Clock semantics are part of the contract

**Implemented:** acquire, renew and slot reads use PostgreSQL `now()`, fixed at
transaction start. Server responses may arrive with less remaining lifetime than
`leaseDuration`. A renewal waiting on a lock evaluates its expiry predicate
against that earlier transaction timestamp; do not interpret it as a guaranteed
wall-clock check at lock acquisition or commit.
[PostgreSQL time functions](https://www.postgresql.org/docs/current/functions-datetime.html#FUNCTIONS-DATETIME-CURRENT).

**Extension:** define transaction/lock timeouts, clock-error assumptions and
conservative client deadlines. Changing to another clock function requires
reviewing predicate and response semantics together. Larger leases improve outage
tolerance but delay reuse after crashes; shorter leases increase renewal demand
and sensitivity to pauses.

**Checkpoint:** expiry makes a slot reusable; reassignment advances epoch.
Mistake: assuming renewal advances epoch or expiry immediately changes storage.

## 7. Reads: effective status, pagination and ownership boundaries

**Implemented** in [PostgresWorkerQueryService](../coordinator-service/src/main/java/com/example/workercoordinator/infrastructure/persistence/PostgresWorkerQueryService.java):

```sql
CASE WHEN status = 'LEASED' AND lease_expiry <= now()
     THEN 'EXPIRED' ELSE status END
```

The same expression drives the returned status and status filter. An expired
slot keeps its last `ownerInstanceId` and expiry until release or reassignment.
`ACTIVE`, `DISABLED` and `RELEASED` are enum values but invalid slot filters;
they return `400 INVALID_STATUS_FILTER`. An unknown enum token returns
`400 INVALID_REQUEST` during binding.

The query resolves worker type → service → product, then uses that full prefix
against the slot primary key. It fetches `limit + 1` rows to calculate `hasMore`
without a count query. Offset pages across separate requests are not one frozen
snapshot; effective-status membership can change between pages.

Reads use no row locks. Seeing `AVAILABLE` does not reserve the slot, and seeing
your UUID does not renew your lease. A registered type with no successful
acquisition has an empty slot list; an unseeded individual slot returns `404`.

**Extension:** use keyset pagination for large inventories and explicitly define
snapshot requirements. Derived status filters and large offsets deserve query-
plan measurements; a primary-key prefix does not make every filter inexpensive.

**Checkpoint:** list/get is inspection, acquire is authority. Mistake: implementing
allocation as `GET available` followed by an unguarded assignment.

## 8. Retries and errors: guarantees stop at specific boundaries

```text
commit succeeds -> response lost -> client does not know outcome
                                  same-instance acquire may recover a live lease
                                  registrationId does not replay stored responses
```

**Implemented:** acquire first searches for an unexpired lease held by the same
instance in the namespace. Sequential retry while that lease remains valid
returns it without epoch increment or renewal. After expiry, retry may allocate
a new generation. A delayed acquire request can therefore create new ownership
after the prior attempt's lease has ended.

There is no durable `registrationId` fingerprint/response protocol and no unique
constraint limiting an instance to one live slot. Concurrent same-instance
requests that both observe no existing lease are not covered by a proven
single-lease-per-instance guarantee. Slot locking still protects each selected
slot. Default PostgreSQL Read Committed isolation does not make a multi-statement
absence check a namespace-wide reservation.
[PostgreSQL isolation semantics](https://www.postgresql.org/docs/current/transaction-iso.html#XACT-READ-COMMITTED).

Renew retries recompute expiry; release retries can conflict after success.
Timeouts must not be interpreted as proof of rollback. Use bounded retries with
backoff/jitter and retain identity; do not start a second active process using
the first process's UUID to recover an ambiguous outcome.

| HTTP / code | Interpretation |
| --- | --- |
| `400 INVALID_REQUEST` | Handled DTO/parameter validation or query-value conversion failure |
| `400 INVALID_STATUS_FILTER` | Recognized enum value unsupported for slot filtering |
| `404 PRODUCT_NOT_FOUND`, `SERVICE_NOT_FOUND`, `WORKER_TYPE_NOT_FOUND`, `WORKER_NOT_FOUND` | Missing resource, or rejected hierarchy association where applicable |
| `409 WORKER_NOT_AVAILABLE` | No selectable candidate at this attempt |
| `409 LEASE_EXPIRED` | Renewal predicate matched no current valid ownership |
| `409 STALE_EPOCH` | Release predicate matched no current ownership |
| `409 SERVICE_DISABLED`, `WORKER_TYPE_DISABLED` | Current product/type policy rejected acquire or renew |
| `409 IDEMPOTENCY_CONFLICT`, `SERVICE_ALREADY_EXISTS`, `WORKER_TYPE_ALREADY_EXISTS` | Registration identity has conflicting metadata or parent |

Domain errors expose `timestamp`, `code`, `message`, `path`; handled validation
errors expose an `errors` map instead of a domain message. DTOs, domain commands
and persistence entities stay distinct; controllers return domain records.
The custom handler does not map every malformed-body or database exception, so
do not assume all failures have this envelope or an explicit retry policy.

**Extension:** durable operation-key replay, concurrent-instance policy and
database-error mapping require explicit contracts and failure tests.

**Checkpoint:** same-instance reuse is narrower than durable request replay.
Mistake: inferring idempotency from the presence of a request UUID.

## 9. Capacity and client responsibility

**Implemented defaults:** 30-second lease, intended 10-second renewal cadence,
10 worker bits and region metadata 1. Requests choose their own region. The
server does not schedule client renewals; the sample has its own 10-second
scheduled renewal configuration.

```text
slots per namespace/region = 2^10 = 1,024
renewal request rate       = active processes / renewal interval
10,000 processes / 10 s    = 1,000 renewals/s across enough namespaces/regions
```

At least ten namespace/region pools are needed for 10,000 active owners with
1,024 slots each, before operational headroom. A region with 900 owners has 124
remaining slots; a rollout overlapping 150 replacement processes cannot acquire
all of them simultaneously. Process count, rather than generated-ID rate,
determines coordinator renewal load.

Acquisition attempts the full slot-range seed on **every** call using
`ON CONFLICT DO NOTHING`. At 100 acquires/s and 1,024 slots, that is 102,400
candidate seed rows/s, mostly conflicts in an initialized namespace. This is
not 102,400 inserted rows, but it is work to benchmark. Cold-start bursts can
combine seeding, hierarchy queries, slot scans and acquisition history writes.

**Extension:** stagger renewal timing, set bounded timeout/admission budgets and
measure transaction latency, lock waits, no-candidate errors, renewal rejection,
pool saturation and remaining client lease time. Estimate history growth from
new allocations, not renewals. Add API replicas when HTTP capacity is limiting;
database saturation or hot slot contention requires database/path analysis.

The optional SDK checks local lease expiry, clock rollback and sequence capacity.
Its default layout is `timestamp(41) | region(4) | worker(10) | sequence(8)`;
namespace and lease epoch are absent. The sample calls the SDK's permanent fence
on renewal failure and shutdown. These are local safeguards, not downstream
ownership validation. Increasing server worker bits beyond the client's layout
requires a coordinated compatibility design.

**Checkpoint:** calculate renewal load and rollout slot demand independently.
Mistake: sizing the coordinator from per-ID throughput.

## 10. End-to-end model and failure walkthroughs

```text
HTTP DTO -> domain command -> PostgreSQL transaction -> committed lease
client   -> local validity checks -> application work
GET      -> namespace resolution -> effective status -> observation
```

Each sequence is a source-derived prediction; proposed replication behavior is
labeled separately.

**Normal ownership write**

1. Register hierarchy, acquire a candidate and commit owner/epoch/history.
2. Return lease; the client renews using that exact ownership tuple.

**Normal inspection read**

1. Resolve worker type's full namespace prefix; read without slot locks.
2. Derive effective expiry status and return a page or individual slot.

**One coordinator node fails after commit**

1. The client loses the response; committed ownership remains in PostgreSQL.
2. A later same-instance acquire through another replica can reuse the live lease.
   This is conditional recovery, not durable request replay.

**Slow replica: proposed database architecture**

1. A read replica lags a committed acquisition or renewal.
2. Replica inspection may be stale; ownership mutations must use the authoritative
   writer. Replication lag cannot authorize ownership.

**Network partition**

1. A coordinator unable to reach the writer cannot commit ownership changes.
2. A client unable to renew must follow its local lease-loss policy. PostgreSQL
   cannot remotely stop a partitioned process's unrelated application writes.

**Node recovery**

1. Restore connectivity and inspect authoritative slot/epoch state.
2. Start a fresh incarnation if the client generator is permanently fenced;
   preserve mappings and retained epochs instead of resetting allocation state.

**Node addition**

1. Add a coordinator replica pointing to the same authoritative database.
2. It uses the same locks and constraints; no separate application leader is
   needed. Reassess aggregate connection demand and mixed-version configuration.

**Whole control-plane outage**

1. Acquisition and renewal become unavailable; leases continue approaching expiry.
2. Restore the database/API, verify current ownership and recover clients in
   stages. Database failover needs an explicit acknowledged-write durability
   policy; asynchronous promotion can lose ownership/epoch history.

## 11. Concepts to retain and staff-level questions

| Mechanism | Question it answers |
| --- | --- |
| Full namespace | Which allocation pool does this process belong to? |
| Durable slot / epoch | Which resource and ownership generation are current? |
| Lock plus transaction | How do competing coordinators serialize one transition? |
| Owner/epoch predicate | Can this caller mutate the current lease? |
| Effective status | What does expiry mean for inspection without a scheduler? |
| Operation replay | Can a retry recover a prior result? Currently only limited reuse exists. |
| Local validity / downstream fencing | Must this process stop, and can the protected resource reject stale work? |

Retain three principles: **ownership is namespace-scoped; inspection is not
authority; database ownership protection is not end-to-end fencing.**

| Question | What a strong answer addresses |
| --- | --- |
| Could same-instance concurrent acquires occupy two slots? | Absence-check race, missing uniqueness/replay protocol, required concurrent test |
| Why can no-candidate errors be transient? | Eligible slots may be locked; other acquisition stages may still wait |
| Can renewal accept an expired-at-commit lease? | Transaction-start `now()`, lock delay, exact predicate and client deadline |
| Does history show the latest renewed expiry? | Allocation history differs from current slot state |
| What protects after database failover? | Replication durability, retained epoch continuity and obsolete clients |
| What would full fencing require? | The protected downstream operation must enforce ownership tokens |

**Explain-back:** trace acquire → lost response → retry → renewal → expiry →
reassignment → stale release. At each step name the full identity, authoritative
row, transaction boundary and enforcing predicate.

## 12. Source study and evidence

| Source | Read for |
| --- | --- |
| [RegistrationController](../coordinator-service/src/main/java/com/example/workercoordinator/api/controller/RegistrationController.java), [RegistrationServices](../coordinator-service/src/main/java/com/example/workercoordinator/infrastructure/persistence/RegistrationServices.java) | Registration replay and hierarchy reads |
| [WorkerController](../coordinator-service/src/main/java/com/example/workercoordinator/api/controller/WorkerController.java), [PostgresWorkerCoordinator](../coordinator-service/src/main/java/com/example/workercoordinator/infrastructure/persistence/PostgresWorkerCoordinator.java) | DTO/command mapping and ownership mutations |
| [WorkerQueryController](../coordinator-service/src/main/java/com/example/workercoordinator/api/controller/WorkerQueryController.java), [PostgresWorkerQueryService](../coordinator-service/src/main/java/com/example/workercoordinator/infrastructure/persistence/PostgresWorkerQueryService.java) | Filters, effective status and paging |
| [Flyway migration](../coordinator-service/src/main/resources/db/migration/V1__create_coordinator_schema.sql) | Slot key, history uniqueness and reserved replay schema |
| [ApiExceptionHandler](../coordinator-service/src/main/java/com/example/workercoordinator/api/controller/ApiExceptionHandler.java) | Implemented error envelope and status mapping |
| [LeaseAwareIdGenerator](../worker-id-client/src/main/java/com/example/workeridclient/LeaseAwareIdGenerator.java) | Local safeguards and their limits |

Existing tests cover mocked registration behavior, read-controller contracts,
PostgreSQL slot inspection/expiry/paging and deterministic local generation.
The PostgreSQL read suite exercises acquisition as setup; it does not establish
multi-coordinator allocation-race or failover correctness. `WorkerControllerTest`
only checks class presence. Those distinctions matter when assessing evidence.

Run `mvn test`; on the documented Docker API compatibility path, PowerShell uses
`mvn test '-Dapi.version=1.44'`. See the [testing guide](TESTING.md) for remaining
mutation/concurrency coverage and the [IntelliJ guide](INTELLIJ_CODE_STUDY_TUTORIAL.md)
for debugger exercises.

External references used: PostgreSQL [row locking](https://www.postgresql.org/docs/current/sql-select.html#SQL-FOR-UPDATE-SHARE),
[transaction isolation](https://www.postgresql.org/docs/current/transaction-iso.html#XACT-READ-COMMITTED),
[time functions](https://www.postgresql.org/docs/current/functions-datetime.html#FUNCTIONS-DATETIME-CURRENT)
and [conflict handling](https://www.postgresql.org/docs/current/sql-insert.html).
