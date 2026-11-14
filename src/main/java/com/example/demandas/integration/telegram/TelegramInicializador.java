package com.example.demandas.integration.telegram;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

/** Ao subir: valida o token e liga o modo escolhido (polling ou webhook). Nunca derruba a aplicação. */
@Component
public class TelegramInicializador {

    private static final Logger log = LoggerFactory.getLogger(TelegramInicializador.class);
    private static final Pattern SEGREDO_VALIDO = Pattern.compile("[A-Za-z0-9_-]{1,256}");

    private final TelegramProperties props;
    private final TelegramClient telegram;
    private final TelegramPolling polling;

    public TelegramInicializador(TelegramProperties props, TelegramClient telegram, TelegramPolling polling) {
        this.props = props;
        this.telegram = telegram;
        this.polling = polling;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void iniciar() {
        if (!props.enabled()) {
            log.info("Telegram desativado (TELEGRAM_ENABLED não é true).");
            return;
        }
        if (props.botToken().isBlank()) {
            log.error("Telegram: TELEGRAM_ENABLED=true, mas TELEGRAM_BOT_TOKEN está vazio. Integração inativa.");
            return;
        }

        try {
            TelegramTipos.TgUser bot = telegram.obterBot();
            log.info("Telegram conectado como @{} (modo {}).", bot.username(), props.modo());
            if (!props.botUsername().isBlank() && !props.botUsername().equalsIgnoreCase(bot.username())) {
                log.warn("Telegram: TELEGRAM_BOT_USERNAME ({}) difere do bot do token (@{}).",
                        props.botUsername(), bot.username());
            }
        } catch (TelegramException e) {
            log.error("Telegram: não foi possível validar o token ({}). Integração inativa.", e.getMessage());
            return;
        }

        if (props.modoWebhook()) {
            registrarWebhook();
        } else {
            polling.iniciar();
        }
    }

    private void registrarWebhook() {
        String base = props.appPublicUrl().replaceAll("/+$", "");
        if (!base.startsWith("https://")) {
            log.error("Telegram: APP_PUBLIC_URL deve começar com https:// para o modo webhook.");
            return;
        }
        if (!SEGREDO_VALIDO.matcher(props.webhookSecret()).matches()) {
            log.error("Telegram: TELEGRAM_WEBHOOK_SECRET ausente ou inválido (1 a 256 caracteres: letras, números, _ e -).");
            return;
        }
        try {
            telegram.registrarWebhook(base + "/api/telegram/webhook", props.webhookSecret());
            log.info("Telegram: webhook registrado em {}/api/telegram/webhook", base);
        } catch (TelegramException e) {
            log.error("Telegram: falha ao registrar o webhook ({}).", e.getMessage());
        }
    }
}