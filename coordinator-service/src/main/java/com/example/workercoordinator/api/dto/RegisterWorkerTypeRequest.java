package com.example.workercoordinator.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterWorkerTypeRequest(@NotBlank @Size(max = 100) String workerTypeId,
                                        @NotBlank @Size(max = 255) String workerTypeName) {
}
