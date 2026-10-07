package com.example.workercoordinator.domain.model;

import java.util.List;

public record WorkerSlotPage(List<WorkerSlot> workers, int limit, int offset, boolean hasMore) {
}
