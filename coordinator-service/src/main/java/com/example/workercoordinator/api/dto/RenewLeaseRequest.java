package com.example.workercoordinator.api.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record RenewLeaseRequest(@NotBlank String productId, @NotBlank String serviceId, @NotBlank String workerTypeId,
                                @Min(0) @Max(15) int regionId, @Min(0) int workerId, @Min(1) long epoch,
                                @NotNull UUID instanceId, @NotNull UUID registrationId) {
}
