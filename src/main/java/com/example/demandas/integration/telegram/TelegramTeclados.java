package com.example.demandas.integration.telegram;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Monta os botões. Os dados de cada botão ficam em até 64 bytes (limite do Telegram). */
public final class TelegramTeclados {

    private TelegramTeclados() {}

    public record Item(Long id, String rotulo) {}

    private static Map<String, Object> botao(String texto, String dados) {
        return Map.<String, Object>of("text", texto, "callback_data", dados);
    }

    private static Map<String, Object> teclado(List<List<Map<String, Object>>> linhas) {
        return Map.<String, Object>of("inline_keyboard", linhas);
    }

    /** Botões de um chamado: ver detalhes, responder e mudar status. */
    public static Map<String, Object> chamado(Long id) {
        return teclado(List.of(
                List.of(botao("📄 Ver detalhes", "ver:" + id), botao("✍️ Responder", "resp:" + id)),
                List.of(botao("🔄 Status", "st:" + id))));
    }

    public static Map<String, Object> lista(List<Item> itens) {
        List<List<Map<String, Object>>> linhas = new ArrayList<>();
        for (Item item : itens) {
            linhas.add(List.of(botao("📄 " + item.rotulo(), "ver:" + item.id())));
        }
        return teclado(linhas);
    }

    public static Map<String, Object> status(Long id, List<String> opcoes) {
        List<List<Map<String, Object>>> linhas = new ArrayList<>();
        for (String s : opcoes) {
            linhas.add(List.of(botao(iconeStatus(s) + " " + rotuloStatus(s), "s:" + id + ":" + s)));
        }
        linhas.add(List.of(botao("↩️ Voltar", "ver:" + id)));
        return teclado(linhas);
    }

    /** Faz o Telegram abrir a resposta já vinculada à mensagem do bot. */
    public static Map<String, Object> respostaForcada() {
        return Map.<String, Object>of("force_reply", true, "input_field_placeholder", "Escreva a sua resposta");
    }

    public static String rotuloStatus(String status) {
        return status == null ? "" : status.replace('_', ' ');
    }

    private static String iconeStatus(String status) {
        return switch (status) {
            case "EM_ATENDIMENTO" -> "▶️";
            case "AGUARDANDO_DISCENTE" -> "⏳";
            case "RESOLVIDO" -> "✅";
            default -> "•";
        };
    }
}