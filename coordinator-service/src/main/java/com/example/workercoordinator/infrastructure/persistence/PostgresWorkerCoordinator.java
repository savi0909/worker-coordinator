package com.example.workercoordinator.infrastructure.persistence;

import com.example.workercoordinator.config.CoordinatorProperties;
import com.example.workercoordinator.domain.exception.CoordinatorException;
import com.example.workercoordinator.domain.model.ReleaseResult;
import com.example.workercoordinator.domain.model.Status;
import com.example.workercoordinator.domain.model.WorkerLease;
import com.example.workercoordinator.domain.service.WorkerCoordinator;
import com.example.workercoordinator.repository.springdata.ProductRepository;
import com.example.workercoordinator.repository.springdata.ServiceRepository;
import com.example.workercoordinator.repository.springdata.WorkerTypeRepository;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.sql.Timestamp;
import java.util.Map;
import java.util.UUID;

/** PostgreSQL is the concurrency authority. A slot is locked until its ownership update and audit write commit. */
@Service
public class PostgresWorkerCoordinator implements WorkerCoordinator {
    private final NamedParameterJdbcTemplate jdbc;
    private final ProductRepository products;
    private final ServiceRepository services;
    private final WorkerTypeRepository workerTypes;
    private final CoordinatorProperties properties;

    public PostgresWorkerCoordinator(NamedParameterJdbcTemplate jdbc, ProductRepository products, ServiceRepository services,
                                     WorkerTypeRepository workerTypes, CoordinatorProperties properties) {
        this.jdbc = jdbc; this.products = products; this.services = services; this.workerTypes = workerTypes; this.properties = properties;
    }

    @Override
    @Transactional
    public WorkerLease acquire(AcquireWorkerCommand request) {
        validateNamespace(request.productId(), request.serviceId(), request.workerTypeId(), request.regionId());
        seedSlots(request.productId(), request.serviceId(), request.workerTypeId(), request.regionId());

        MapSqlParameterSource p = namespace(request.productId(), request.serviceId(), request.workerTypeId(), request.regionId())
                .addValue("instanceId", request.instanceId());
        Slot existing = jdbc.query("""
                SELECT worker_id, current_epoch, lease_expiry FROM workers
                WHERE product_id=:productId AND service_id=:serviceId AND worker_type_id=:workerTypeId AND region_id=:regionId
                  AND owner_instance_id=:instanceId AND status='LEASED' AND lease_expiry > now()
                FOR UPDATE
                """, p, rs -> rs.next() ? new Slot(rs.getInt(1), rs.getLong(2), rs.getTimestamp(3).toInstant()) : null);
        if (existing != null) return lease(request, existing);

        Slot candidate = jdbc.query("""
                SELECT worker_id, current_epoch, lease_expiry FROM workers
                WHERE product_id=:productId AND service_id=:serviceId AND worker_type_id=:workerTypeId AND region_id=:regionId
                  AND (status <> 'LEASED' OR lease_expiry <= now())
                ORDER BY worker_id
                FOR UPDATE SKIP LOCKED LIMIT 1
                """, p, rs -> rs.next() ? new Slot(rs.getInt(1), rs.getLong(2), null) : null);
        if (candidate == null) throw new CoordinatorException("WORKER_NOT_AVAILABLE", "No currently available worker slot in this namespace");

        p.addValue("workerId", candidate.workerId()).addValue("leaseSeconds", properties.leaseDuration().toSeconds());
        Slot lease = jdbc.queryForObject("""
                UPDATE workers SET current_epoch=current_epoch+1, owner_instance_id=:instanceId, status='LEASED',
                  lease_expiry=now() + (:leaseSeconds * interval '1 second'), updated_at=now()
                WHERE product_id=:productId AND service_id=:serviceId AND worker_type_id=:workerTypeId AND region_id=:regionId AND worker_id=:workerId
                RETURNING worker_id, current_epoch, lease_expiry
                """, p, (rs, row) -> new Slot(rs.getInt(1), rs.getLong(2), rs.getTimestamp(3).toInstant()));
        jdbc.update("""
                INSERT INTO worker_lease_history(product_id,service_id,worker_type_id,region_id,worker_id,epoch,owner_instance_id,acquired_at,expires_at,reason)
                VALUES (:productId,:serviceId,:workerTypeId,:regionId,:workerId,:epoch,:instanceId,now(),:expiry,'ACQUIRED')
                """, p.addValue("epoch", lease.epoch()).addValue("expiry", Timestamp.from(lease.expiry())));
        return lease(request, lease);
    }

    @Override
    @Transactional
    public WorkerLease renew(RenewLeaseCommand request) {
        validateNamespace(request.productId(), request.serviceId(), request.workerTypeId(), request.regionId());
        MapSqlParameterSource p = namespace(request.productId(), request.serviceId(), request.workerTypeId(), request.regionId())
                .addValue("workerId", request.workerId()).addValue("epoch", request.epoch()).addValue("instanceId", request.instanceId())
                .addValue("leaseSeconds", properties.leaseDuration().toSeconds());
        Slot renewed = jdbc.query("""
                UPDATE workers SET lease_expiry=now() + (:leaseSeconds * interval '1 second'), updated_at=now()
                WHERE product_id=:productId AND service_id=:serviceId AND worker_type_id=:workerTypeId AND region_id=:regionId AND worker_id=:workerId
                  AND current_epoch=:epoch AND owner_instance_id=:instanceId AND status='LEASED' AND lease_expiry > now()
                RETURNING worker_id,current_epoch,lease_expiry
                """, p, rs -> rs.next() ? new Slot(rs.getInt(1),rs.getLong(2),rs.getTimestamp(3).toInstant()) : null);
        if (renewed == null) throw new CoordinatorException("LEASE_EXPIRED", "Lease cannot be renewed because ownership is no longer current");
        return new WorkerLease(request.productId(),request.serviceId(),request.workerTypeId(),request.regionId(),renewed.workerId(),renewed.epoch(),request.instanceId(),renewed.expiry(),properties.leaseDuration());
    }

    @Override
    @Transactional
    public ReleaseResult release(ReleaseWorkerCommand request) {
        MapSqlParameterSource p = namespace(request.productId(), request.serviceId(), request.workerTypeId(), request.regionId())
                .addValue("workerId",request.workerId()).addValue("epoch",request.epoch()).addValue("instanceId",request.instanceId());
        int changed = jdbc.update("""
                UPDATE workers SET status='AVAILABLE', owner_instance_id=NULL, lease_expiry=NULL, updated_at=now()
                WHERE product_id=:productId AND service_id=:serviceId AND worker_type_id=:workerTypeId AND region_id=:regionId AND worker_id=:workerId
                  AND current_epoch=:epoch AND owner_instance_id=:instanceId AND status='LEASED'
                """, p);
        if (changed == 0) throw new CoordinatorException("STALE_EPOCH", "Lease owner or epoch is no longer current");
        jdbc.update("""
                UPDATE worker_lease_history SET released_at=now(), reason='RELEASED'
                WHERE product_id=:productId AND service_id=:serviceId AND worker_type_id=:workerTypeId AND region_id=:regionId AND worker_id=:workerId AND epoch=:epoch
                """,p);
        return new ReleaseResult(true,request.epoch());
    }

    private void seedSlots(String productId,String serviceId,String workerTypeId,int regionId) {
        int capacity = capacity();
        jdbc.update("""
                INSERT INTO workers(product_id,service_id,worker_type_id,region_id,worker_id,current_epoch,status,created_at,updated_at)
                SELECT :productId,:serviceId,:workerTypeId,:regionId,generate_series(0,:capacity - 1),0,'AVAILABLE',now(),now()
                ON CONFLICT (product_id,service_id,worker_type_id,region_id,worker_id) DO NOTHING
                """,
                namespace(productId,serviceId,workerTypeId,regionId).addValue("capacity",capacity));
    }
    private int capacity() { if (properties.workerIdBits() < 1 || properties.workerIdBits() > 16) throw new IllegalStateException("coordinator.worker-id-bits must be between 1 and 16"); return 1 << properties.workerIdBits(); }
    private void validateNamespace(String productId,String serviceId,String typeId,int regionId) {
        if (regionId < 0 || regionId > 15) throw new CoordinatorException("REGION_NOT_FOUND","Unknown region: "+regionId);
        var product=products.findById(productId).orElseThrow(()->new CoordinatorException("PRODUCT_NOT_FOUND","Unknown product: "+productId));
        if (product.status != Status.ACTIVE) throw new CoordinatorException("SERVICE_DISABLED","Product is not active");
        var service=services.findById(serviceId).orElseThrow(()->new CoordinatorException("SERVICE_NOT_FOUND","Unknown service: "+serviceId));
        if (!service.productId.equals(productId)) throw new CoordinatorException("SERVICE_NOT_FOUND","Service does not belong to product");
        var type=workerTypes.findById(typeId).orElseThrow(()->new CoordinatorException("WORKER_TYPE_NOT_FOUND","Unknown worker type: "+typeId));
        if (!type.serviceId.equals(serviceId)) throw new CoordinatorException("WORKER_TYPE_NOT_FOUND","Worker type does not belong to service");
        if (type.status != Status.ACTIVE) throw new CoordinatorException("WORKER_TYPE_DISABLED","Worker type is not active");
    }
    private MapSqlParameterSource namespace(String p,String s,String t,int r) { return new MapSqlParameterSource(Map.of("productId",p,"serviceId",s,"workerTypeId",t,"regionId",r)); }
    private WorkerLease lease(AcquireWorkerCommand r,Slot slot) { return new WorkerLease(r.productId(),r.serviceId(),r.workerTypeId(),r.regionId(),slot.workerId(),slot.epoch(),r.instanceId(),slot.expiry(),properties.leaseDuration()); }
    private record Slot(int workerId,long epoch,Instant expiry) {}
}
