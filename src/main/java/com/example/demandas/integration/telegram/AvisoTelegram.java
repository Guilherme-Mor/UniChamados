package com.example.demandas.integration.telegram;

/** Mensagem a enviar ao chat depois que a operação no banco foi concluída. */
public record AvisoTelegram(Long chatId, String texto) {}