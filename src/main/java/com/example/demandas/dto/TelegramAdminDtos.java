package com.example.demandas.dto;

import java.time.LocalDateTime;
import java.util.List;

public final class TelegramAdminDtos {

    private TelegramAdminDtos() {}

    /** estado: NENHUM, PENDENTE ou ATIVO. */
    public record Docente(Integer id, String nome, String email, String estado, String username,
                          long codigosNaHora) {}

    public record Bloqueio(Long chatId, String nome, String username, long tentativas,
                           LocalDateTime ultimaTentativa) {}

    public record Painel(boolean habilitado, int limiteCodigos, int limiteTentativas,
                         List<Docente> docentes, List<Bloqueio> bloqueios) {}
}