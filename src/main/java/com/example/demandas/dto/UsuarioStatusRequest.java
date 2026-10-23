package com.example.demandas.dto;

import jakarta.validation.constraints.NotNull;

public record UsuarioStatusRequest(@NotNull Boolean ativo) {}