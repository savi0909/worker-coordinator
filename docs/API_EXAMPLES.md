# API examples

Assume the coordinator is available at `$base = 'http://localhost:8080/api/v1'`.

```powershell
$base = 'http://localhost:8080/api/v1'
Invoke-RestMethod "$base/products" -Method Post -ContentType 'application/json' -Body '{"productId":"orders","productName":"Orders"}'
Invoke-RestMethod "$base/products/orders/services" -Method Post -ContentType 'application/json' -Body '{"serviceId":"order-processing","serviceName":"Order Processing"}'
Invoke-RestMethod "$base/services/order-processing/worker-types" -Method Post -ContentType 'application/json' -Body '{"workerTypeId":"order-id-generator","workerTypeName":"Order ID Generator"}'
```

Acquire a worker identity:

```powershell
$instance = [guid]::NewGuid().ToString()
$request = @{ productId='orders'; serviceId='order-processing'; workerTypeId='order-id-generator'; regionId=1; instanceId=$instance; registrationId=([guid]::NewGuid().ToString()) } | ConvertTo-Json
$lease = Invoke-RestMethod "$base/workers/acquire" -Method Post -ContentType 'application/json' -Body $request
$lease
```

Renew the returned lease:

```powershell
$renew = @{ productId=$lease.productId; serviceId=$lease.serviceId; workerTypeId=$lease.workerTypeId; regionId=$lease.regionId; workerId=$lease.workerId; epoch=$lease.epoch; instanceId=$instance; registrationId=([guid]::NewGuid().ToString()) } | ConvertTo-Json
Invoke-RestMethod "$base/workers/renew" -Method Post -ContentType 'application/json' -Body $renew
```

Release it with the same namespace, worker ID, epoch, and instance ID. A stale epoch or different instance must be
rejected.

## Read the hierarchy and worker slots

Walk the hierarchy top-down: product → service → worker type → worker slots.

```powershell
Invoke-RestMethod "$base/products"
Invoke-RestMethod "$base/products/orders/services"
Invoke-RestMethod "$base/services/order-processing/worker-types"
```

List slots of a worker type, optionally filtered by region and effective status, and page with `limit`/`offset`:

```powershell
Invoke-RestMethod "$base/worker-types/order-id-generator/workers?regionId=1&status=LEASED&limit=50"
Invoke-RestMethod "$base/worker-types/order-id-generator/regions/1/workers/$($lease.workerId)"
```

A lease that passed its expiry without renewal shows `status = EXPIRED` and keeps its last `ownerInstanceId` until
another instance acquires the slot.

## Sample payment API

The sample client runs on its own port in servlet mode and connects to `paymentsDB`:

```powershell
$paymentKey = '0199b1a4-7d2e-7c11-8b8f-6e0d3e4e0a01' # UUIDv7
$payment = @{ clientIdempotencyKey=$paymentKey; amount='12.3400' } | ConvertTo-Json
Invoke-RestMethod 'http://localhost:8083/api/v1/payments' -Method Post -ContentType 'application/json' -Body $payment
```

The response `id` is the lease-aware Snowflake-generated `BIGINT` payment identity. Repeating the request with the
same UUIDv7 key returns the existing payment; the UUID is the idempotency/request identity, not the payment ID.
