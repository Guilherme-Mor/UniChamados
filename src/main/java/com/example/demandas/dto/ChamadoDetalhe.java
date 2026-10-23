package com.example.demandas.dto;

import java.time.LocalDateTime;
import java.util.List;

public record ChamadoDetalhe(
        Long id,
        String protocolo,
        String titulo,
        String descricao,
        String categoria,
        String disciplina,
        String status,
        String prioridade,
        String solicitante,
        String responsavel,
        LocalDateTime criadoEm,
        LocalDateTime atualizadoEm,
        LocalDateTime encerradoEm,
        List<InteracaoResponse> historico
) {}