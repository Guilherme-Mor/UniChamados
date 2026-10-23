package com.example.demandas.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ChamadoRequest(
        @NotNull Integer categoriaId,
        Integer disciplinaId,
        @NotBlank @Size(max = 150) String titulo,
        @NotBlank @Size(max = 5000) String descricao
) {}