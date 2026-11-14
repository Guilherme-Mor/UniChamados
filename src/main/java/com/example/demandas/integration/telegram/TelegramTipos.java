package com.example.demandas.integration.telegram;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/** Apenas os campos da API do Telegram que o sistema usa. */
public final class TelegramTipos {

    private TelegramTipos() {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Update(
            @JsonProperty("update_id") Long updateId,
            Message message,
            @JsonProperty("callback_query") CallbackQuery callbackQuery) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Message(
            @JsonProperty("message_id") Long messageId,
            TgUser from,
            Chat chat,
            String text,
            @JsonProperty("reply_to_message") Message replyToMessage) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Chat(Long id, String type) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record TgUser(
            Long id,
            @JsonProperty("is_bot") Boolean isBot,
            @JsonProperty("first_name") String firstName,
            @JsonProperty("last_name") String lastName,
            String username) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record CallbackQuery(String id, TgUser from, Message message, String data) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Parametros(@JsonProperty("retry_after") Integer retryAfter) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Resposta<T>(
            Boolean ok,
            T result,
            String description,
            @JsonProperty("error_code") Integer errorCode,
            Parametros parameters) {}
}