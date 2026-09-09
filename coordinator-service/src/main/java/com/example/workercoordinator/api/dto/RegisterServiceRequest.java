package com.example.workercoordinator.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterServiceRequest(@NotBlank @Size(max = 100) String serviceId,
                                     @NotBlank @Size(max = 255) String serviceName) {
}
