package com.example.demandas.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record DisciplinaEdicaoRequest(
        @NotBlank @Size(max = 20) String codigo,
        @NotBlank @Size(max = 150) String nome,
        Integer docenteId
) {}