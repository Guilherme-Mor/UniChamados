package com.example.demandas.integration.telegram;

import java.util.Map;

/** Resposta pronta para enviar ao chat. chamadoId serve para ligar a mensagem ao chamado (responder por reply). */
public record RespostaTelegram(String texto, Map<String, Object> teclado, Long chamadoId, String tipo) {

    public static RespostaTelegram simples(String texto) {
        return new RespostaTelegram(texto, null, null, null);
    }
}