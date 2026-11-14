package com.example.demandas.integration.telegram;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "telegram")
public record TelegramProperties(
        @DefaultValue("false") boolean enabled,
        @DefaultValue("") String botToken,
        @DefaultValue("") String botUsername,
        @DefaultValue("") String webhookSecret,
        @DefaultValue("polling") String modo,
        @DefaultValue("") String appPublicUrl,
        @DefaultValue("10") int codigoValidadeMinutos,
        @DefaultValue("10") int pedidoValidadeMinutos,
        @DefaultValue("5") int codigoLimiteHora,
        @DefaultValue("5") int tentativasLimiteHora) {

    /** Integração ligada e com token informado. */
    public boolean ativo() {
        return enabled && !botToken.isBlank();
    }

    public boolean modoWebhook() {
        return "webhook".equalsIgnoreCase(modo);
    }

    public boolean modoPolling() {
        return !modoWebhook();
    }
}