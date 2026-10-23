package com.example.demandas.dto;

import jakarta.validation.constraints.NotNull;

public record AtribuicaoRequest(@NotNull Integer responsavelId) {}