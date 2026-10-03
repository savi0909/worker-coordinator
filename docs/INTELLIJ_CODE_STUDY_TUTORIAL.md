# Study worker leases and local ID generation in IntelliJ

Updated: 2026-10-03. This tutorial explains the code currently in this project.
Runtime exercises below are instructions and predictions, not results from this
documentation session. Existing execution evidence remains in the linked project
documents. Reading or passing a test does not establish learner mastery.

## 1. Prepare the study session

Open `D:/java-projects/pom.xml` as a Maven project, use JDK 21 for both the project
SDK and Maven runner, and reload Maven. The workspace aggregates independent
projects; it does not supply their inherited dependency configuration. On a fresh
clone, initialize the coordinator and monitor submodules before importing.

Focus on `distributed-coordinator`. First read [Technical specification](TECHNICAL_SPEC.md); [API examples](API_EXAMPLES.md).
Use the project's README for its exact infrastructure and startup commands.
Start only the selected lab when attempting live exercises. Import its supplied
Postman collection/environment where available; dependent requests need their
preceding fixture-creation steps. This tutorial does not require running all stacks.

In IntelliJ, use Navigate to Declaration, Find Usages, the Structure view, and the
test gutter. Inspect the call stack and variables at the boundaries below.
A breakpoint in the Spring main method explains startup; the interesting
correctness decisions happen in the request, persistence, or event paths.

## 2. Read these files in order

| File | What to find and explain |
| --- | --- |
| [RegistrationController.java](../coordinator-service/src/main/java/com/example/workercoordinator/api/controller/RegistrationController.java) | Follow generic product/service/worker-type registration. |
| [RegistrationServices.java](../coordinator-service/src/main/java/com/example/workercoordinator/infrastructure/persistence/RegistrationServices.java) | Inspect hierarchy validation and persistence without business-domain assumptions. |
| [AcquireWorkerRequest.java](../coordinator-service/src/main/java/com/example/workercoordinator/api/dto/AcquireWorkerRequest.java) | Distinguish namespace, instanceId and supplied registrationId. |
| [WorkerController.java](../coordinator-service/src/main/java/com/example/workercoordinator/api/controller/WorkerController.java) | Follow acquire 201, renew/release 200 into domain commands. |
| [WorkerCoordinator.java](../coordinator-service/src/main/java/com/example/workercoordinator/domain/service/WorkerCoordinator.java) | Read interface commands separately from storage implementation. |
| [PostgresWorkerCoordinator.java](../coordinator-service/src/main/java/com/example/workercoordinator/infrastructure/persistence/PostgresWorkerCoordinator.java) | Locate slot locks, epoch increment, live renewal predicate and history write. |
| [V1__create_coordinator_schema.sql](../coordinator-service/src/main/resources/db/migration/V1__create_coordinator_schema.sql) | Read composite slot key and monotonic ownership history. |
| [LeaseAwareIdGenerator.java](../worker-id-client/src/main/java/com/example/workeridclient/LeaseAwareIdGenerator.java) | Inspect local synchronization, lease checks, overflow and permanent fencing. |
| [IdLayout.java](../worker-id-client/src/main/java/com/example/workeridclient/IdLayout.java) | Decode timestamp/region/worker/sequence bits and epoch range. |
| [Base62.java](../worker-id-client/src/main/java/com/example/workeridclient/Base62.java) | Explain numeric encoding rather than hashing a random string. |
| [WorkerLeaseLifecycle.java](../worker-client-sample/src/main/java/com/example/workerclient/WorkerLeaseLifecycle.java) | Compare sample acquire/renew/release lifecycle with the shortener adapter. |

## 3. Trace the control plane before the data plane

There are three modules: coordinator-service stores generic hierarchy/leases;
worker-id-client generates local IDs; worker-client-sample demonstrates a domain
client. The server does not return each application's next ID.

Use the existing URL-shortener stack's coordinator 8120/PostgreSQL 5552 if it is
already running. For a standalone coordinator, follow its technical/API examples
and datasource configuration. Do not start the payment sample merely to study
leases.

Register your own generic study hierarchy before acquisition: POST
`/api/v1/products` with productId/productName; POST
`/api/v1/products/{productId}/services` with serviceId/serviceName; POST
`/api/v1/services/{serviceId}/worker-types` with workerTypeId/workerTypeName.
Then acquire a region 1 slot using unique process identity:

```json
{
  "productId":"study-product",
  "serviceId":"study-service",
  "workerTypeId":"study-worker",
  "regionId":1,
  "instanceId":"REPLACE_WITH_UUID",
  "registrationId":"REPLACE_WITH_UUID"
}
```

POST `/api/v1/workers/acquire` returns 201 and namespace/region/worker/epoch/expiry.
Use actual UUIDs and saved lease fields for renewal/release; do not guess a slot.
Read [API examples](API_EXAMPLES.md) for full request contracts.

## 4. Follow allocation in one PostgreSQL transaction

1. Validate product/service/type hierarchy and active policy.
2. Seed retained worker rows idempotently for the namespace/region.
3. Look for a live lease already held by this instance.
4. Otherwise select one available/expired slot with FOR UPDATE SKIP LOCKED.
5. Increment current_epoch, install owner/expiry and mark LEASED.
6. Write lease history in the same transaction.
7. Commit before responding.

The slot's composite key contains product, service, worker type, region and worker.
Different namespaces can reuse a worker number. The lock protects one slot while
epoch/ownership/history change together; another coordinator sharing this database
can skip it and select another available slot.

Renewal conditions match current owner, epoch, status and unexpired DB time.
Release matches owner and epoch, so a previous incarnation cannot free a
successor's slot. No expiry scheduler is necessary for acquisition to reuse an
expired row.

Do not assume registrationId provides durable acquisition-response replay.
Inspect the actual acquire implementation: it accepts that field, but the current
allocation path does not persist a registrationId-based replay record. A live
same-instance lookup is narrower than a durable request-key protocol. Concurrency
for identical instances and response-loss cases need evidence beyond this lookup.

## 5. Decode a generated ID and its limits

```text
timestamp delta | region | worker | sequence
41 bits           4        10       8
```

The default 2024 epoch layout permits 1024 worker numbers,16 regions and 256 sequence
values per millisecond per worker. `256 * 1000 = 256000`/s is a bit-space ceiling,
not tested throughput; synchronization, clock precision and overflow waits matter.

LeaseAwareIdGenerator nextLong() is local and synchronized. It checks lease
availability, detects rollback, advances sequence and waits on overflow. Expiry,
explicit loss and stale epoch can fence generation. Installing another lease
does not undo permanent fencing on that generator instance.

Neither namespace nor lease epoch is encoded in the numeric ID. Two namespaces
with identical layout/region/worker/time can generate overlapping numbers.
Base 62 encodes the same integer; it cannot add uniqueness. Downstream SQL
uniqueness remains the URL-shortener's final committed mapping authority.

For controlled stepping, use LeaseAwareIdGeneratorTest with its supplied clock
and sleeper. Live lease time keeps advancing during a debugger pause. The URL
shortener adds its own monotonic deadline and safety margin; do not attribute
all of that adapter's lifecycle policy to the generic server.

## 6. Experiments, tests and larger design questions

| Exercise | Evidence/expectation |
| --- | --- |
| Concurrent acquire through distinct coordinator replicas | Different live slots under common DB authority |
| Renew old epoch after successor takeover | Rejected |
| Release stale owner | Does not free successor |
| Next ID after local expiry/fence | Fails closed |
| Sequence capacity exhausted at one timestamp | Waits for later millisecond |
| Clock moves backward | Generator fences |
| Different namespace with same worker bits | No universal ID-range separation |

Break in WorkerController then PostgresWorkerCoordinator before lock/update;
inspect namespace plus instance/epoch, not worker number alone. Use short
thread-only pauses. Unit tests here validate registration and generator behavior;
the simple controller-presence test is not a database allocation race test.
Cross-coordinator runtime evidence lives in the URL-shortener three-coordinator
run guide and does not prove HA/failover.

Staff discussion: database slot contention, renew load, worker exhaustion and
namespace layout compatibility. Principal discussion: clock ownership, downstream
fencing, failover ambiguity and migration of IDs/layout/epoch. Proposed SLOs should
separate lease acquisition/renewal from local ID generation and downstream
commit. Changing bit widths without versioning can invalidate uniqueness and
decoding assumptions.

Exercises: decode one ID; explain why epoch and worker differ; draw a renewal
timeout timeline; identify which downstream operation must reject stale work.

## Read the tests as executable design notes

Open [LeaseAwareIdGeneratorTest.java](../worker-id-client/src/test/java/com/example/workeridclient/LeaseAwareIdGeneratorTest.java) and start with:

- `generatesUniqueIdsAndPreservesConfiguredFields`.
- `waitsForNextMillisecondOnSequenceOverflow`.
- `expiryAndExplicitFencingStopGeneration`.
- `staleEpochAndClockRollbackFenceGenerator`.

Open [RegistrationServicesTest.java](../coordinator-service/src/test/java/com/example/workercoordinator/infrastructure/persistence/RegistrationServicesTest.java) and start with:

- `sameProductRegistrationIsIdempotent`.
- `unknownParentIsRejected`.

For each test, identify its initial state, competing or failing action, asserted invariant, and enforcing code. Check whether it uses mocks, controlled time, real infrastructure, or multiple running JVMs; these establish different levels of evidence. Run individual deterministic tests from the test gutter before attempting a live failure exercise.

## Study record

Write a short trace in your own words: input identity, admission, authoritative state, atomic boundary, reply, and retry after a lost reply. Record predictions separately from observations. Explain one limitation and the evidence you would need to remove it. Retain your fixtures; do not run Maven clean, file deletion, data resets, or global Docker cleanup. Any live fault experiment should follow the project run guide and restore only its selected services.
