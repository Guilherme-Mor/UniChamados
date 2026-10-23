package com.example.demandas.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record MensagemRequest(@NotBlank @Size(max = 5000) String mensagem) {}