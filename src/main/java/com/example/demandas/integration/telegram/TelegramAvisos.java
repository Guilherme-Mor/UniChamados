package com.example.demandas.integration.telegram;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** Envia avisos sem nunca quebrar a operação principal se o Telegram estiver fora do ar. */
@Component
public class TelegramAvisos {

    private static final Logger log = LoggerFactory.getLogger(TelegramAvisos.class);

    private final TelegramClient telegram;
    private final TelegramProperties props;

    public TelegramAvisos(TelegramClient telegram, TelegramProperties props) {
        this.telegram = telegram;
        this.props = props;
    }

    public void enviar(AvisoTelegram aviso) {
        if (aviso == null || !props.ativo()) return;
        try {
            telegram.enviarMensagem(aviso.chatId(), aviso.texto());
        } catch (TelegramException e) {
            log.warn("Aviso no Telegram não enviado: {}", e.getMessage());
        }
    }
}