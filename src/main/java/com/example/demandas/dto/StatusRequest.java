package com.example.demandas.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record StatusRequest(
        @NotBlank String status,
        @Size(max = 2000) String mensagem
) {}