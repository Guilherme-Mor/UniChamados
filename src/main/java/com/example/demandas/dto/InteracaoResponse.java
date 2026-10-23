package com.example.demandas.dto;

import java.time.LocalDateTime;

public record InteracaoResponse(Long id, String autor, String tipo, String canal,
                                String mensagem, LocalDateTime criadoEm) {}