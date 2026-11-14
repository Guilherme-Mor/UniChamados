package com.example.demandas.service;

import java.util.List;
import java.util.Map;
import java.util.Set;

/** Regra única das mudanças de status, usada pelo portal (e, depois, pelo Telegram). */
public final class PoliticaStatus {

    public static final Set<String> FINAIS = Set.of("ENCERRADO", "CANCELADO");

    // ABERTO -> ATRIBUIDO não está aqui: acontece pela atribuição do responsável.
    private static final Map<String, List<String>> TRANSICOES = Map.of(
            "ABERTO",              List.of("CANCELADO"),
            "ATRIBUIDO",           List.of("EM_ATENDIMENTO", "CANCELADO"),
            "EM_ATENDIMENTO",      List.of("AGUARDANDO_DISCENTE", "RESOLVIDO", "CANCELADO"),
            "AGUARDANDO_DISCENTE", List.of("EM_ATENDIMENTO", "RESOLVIDO", "CANCELADO"),
            "RESOLVIDO",           List.of("ENCERRADO", "EM_ATENDIMENTO"),
            "ENCERRADO",           List.of(),
            "CANCELADO",           List.of());

    private PoliticaStatus() {}

    /** Próximos status permitidos para quem gerencia o chamado (admin ou responsável). */
    public static List<String> proximos(String atual) {
        return TRANSICOES.getOrDefault(atual, List.of());
    }

    /** O solicitante só cancela enquanto o chamado não estiver resolvido nem finalizado. */
    public static boolean cancelavelPeloSolicitante(String atual) {
        return !atual.equals("RESOLVIDO") && !FINAIS.contains(atual);
    }
}