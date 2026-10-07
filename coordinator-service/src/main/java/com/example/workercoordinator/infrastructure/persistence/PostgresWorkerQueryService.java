package com.example.workercoordinator.infrastructure.persistence;

import com.example.workercoordinator.domain.exception.CoordinatorException;
import com.example.workercoordinator.domain.model.Status;
import com.example.workercoordinator.domain.model.WorkerSlot;
import com.example.workercoordinator.domain.model.WorkerSlotPage;
import com.example.workercoordinator.domain.service.WorkerQueryService;
import com.example.workercoordinator.repository.springdata.ServiceRepository;
import com.example.workercoordinator.repository.springdata.WorkerTypeRepository;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Reads worker slots without locking. Status is derived with the database clock, matching how acquire and renew decide
 * expiry, so a LEASED row past its expiry is reported as EXPIRED.
 */
@Service
public class PostgresWorkerQueryService implements WorkerQueryService {
    private static final Set<Status> SLOT_STATUSES = EnumSet.of(Status.AVAILABLE, Status.LEASED, Status.EXPIRED);
    private static final String EFFECTIVE_STATUS =
            "CASE WHEN status='LEASED' AND lease_expiry <= now() THEN 'EXPIRED' ELSE status END";
    private static final String SELECT_SLOT = """
            SELECT product_id, service_id, worker_type_id, region_id, worker_id, current_epoch, owner_instance_id,
                   %s AS effective_status, lease_expiry
            FROM workers
            WHERE product_id=:productId AND service_id=:serviceId AND worker_type_id=:workerTypeId
            """.formatted(EFFECTIVE_STATUS);
    private static final RowMapper<WorkerSlot> SLOT = (rs, row) -> {
        Timestamp expiry = rs.getTimestamp("lease_expiry");
        return new WorkerSlot(rs.getString("product_id"), rs.getString("service_id"), rs.getString("worker_type_id"),
                rs.getInt("region_id"), rs.getInt("worker_id"), rs.getLong("current_epoch"),
                rs.getObject("owner_instance_id", UUID.class), Status.valueOf(rs.getString("effective_status")),
                expiry == null ? null : expiry.toInstant());
    };

    private final NamedParameterJdbcTemplate jdbc;
    private final ServiceRepository services;
    private final WorkerTypeRepository workerTypes;

    public PostgresWorkerQueryService(NamedParameterJdbcTemplate jdbc, ServiceRepository services, WorkerTypeRepository workerTypes) {
        this.jdbc = jdbc;
        this.services = services;
        this.workerTypes = workerTypes;
    }

    @Override
    @Transactional(readOnly = true)
    public WorkerSlotPage listWorkers(String workerTypeId, Integer regionId, Status status, int limit, int offset) {
        if (status != null && !SLOT_STATUSES.contains(status))
            throw new CoordinatorException("INVALID_STATUS_FILTER", "Worker status filter must be one of " + SLOT_STATUSES);
        MapSqlParameterSource p = namespace(workerTypeId).addValue("limit", limit + 1).addValue("offset", offset);
        StringBuilder sql = new StringBuilder(SELECT_SLOT);
        if (regionId != null) {
            sql.append(" AND region_id=:regionId");
            p.addValue("regionId", regionId);
        }
        if (status != null) {
            sql.append(" AND ").append(EFFECTIVE_STATUS).append("=:status");
            p.addValue("status", status.name());
        }
        sql.append(" ORDER BY region_id, worker_id LIMIT :limit OFFSET :offset");
        List<WorkerSlot> rows = jdbc.query(sql.toString(), p, SLOT);
        boolean hasMore = rows.size() > limit;
        return new WorkerSlotPage(hasMore ? rows.subList(0, limit) : rows, limit, offset, hasMore);
    }

    @Override
    @Transactional(readOnly = true)
    public WorkerSlot getWorker(String workerTypeId, int regionId, int workerId) {
        MapSqlParameterSource p = namespace(workerTypeId).addValue("regionId", regionId).addValue("workerId", workerId);
        return jdbc.query(SELECT_SLOT + " AND region_id=:regionId AND worker_id=:workerId", p, SLOT).stream().findFirst()
                .orElseThrow(() -> new CoordinatorException("WORKER_NOT_FOUND",
                        "No worker slot %d in region %d for worker type %s".formatted(workerId, regionId, workerTypeId)));
    }

    /**
     * Resolves the full primary-key prefix so slot queries seek the workers primary-key index.
     */
    private MapSqlParameterSource namespace(String workerTypeId) {
        var type = workerTypes.findById(workerTypeId)
                .orElseThrow(() -> new CoordinatorException("WORKER_TYPE_NOT_FOUND", "No resource registered with ID: " + workerTypeId));
        var service = services.findById(type.serviceId)
                .orElseThrow(() -> new CoordinatorException("SERVICE_NOT_FOUND", "No resource registered with ID: " + type.serviceId));
        return new MapSqlParameterSource()
                .addValue("productId", service.productId)
                .addValue("serviceId", service.id)
                .addValue("workerTypeId", workerTypeId);
    }
}
