package com.example.workercoordinator.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterProductRequest(@NotBlank @Size(max = 100) String productId,
                                     @NotBlank @Size(max = 255) String productName) {
}
