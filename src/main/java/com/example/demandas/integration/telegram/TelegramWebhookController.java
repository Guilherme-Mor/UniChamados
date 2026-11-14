package com.example.demandas.integration.telegram;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@RestController
public class TelegramWebhookController {

    private static final Logger log = LoggerFactory.getLogger(TelegramWebhookController.class);
    private static final String CABECALHO_SEGREDO = "X-Telegram-Bot-Api-Secret-Token";

    private final TelegramProperties props;
    private final TelegramUpdateHandler handler;

    public TelegramWebhookController(TelegramProperties props, TelegramUpdateHandler handler) {
        this.props = props;
        this.handler = handler;
    }

    @PostMapping("/api/telegram/webhook")
    public ResponseEntity<Void> receber(
            @RequestHeader(value = CABECALHO_SEGREDO, required = false) String segredo,
            @RequestBody TelegramTipos.Update update) {

        // Integração desligada ou sem segredo configurado: o endpoint "não existe".
        if (!props.ativo() || props.webhookSecret().isBlank()) {
            return ResponseEntity.notFound().build();
        }
        if (!segredoValido(segredo)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        try {
            handler.tratar(update);
        } catch (RuntimeException e) {
            log.error("Erro ao processar evento do Telegram.", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
        return ResponseEntity.ok().build();
    }

    /** Comparação em tempo constante, para não revelar o segredo por diferença de tempo. */
    private boolean segredoValido(String recebido) {
        if (recebido == null) return false;
        return MessageDigest.isEqual(
                recebido.getBytes(StandardCharsets.UTF_8),
                props.webhookSecret().getBytes(StandardCharsets.UTF_8));
    }
}