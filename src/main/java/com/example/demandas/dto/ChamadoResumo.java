package com.example.demandas.dto;

import java.time.LocalDateTime;

public record ChamadoResumo(
        Long id,
        String protocolo,
        String titulo,
        String categoria,
        String disciplina,
        String status,
        String solicitante,
        String responsavel,
        LocalDateTime criadoEm
) {}