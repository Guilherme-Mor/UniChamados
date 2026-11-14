package com.example.demandas.integration.telegram;

import com.example.demandas.repository.TelegramUpdateProcessadoRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/** Recebe cada evento do Telegram (webhook ou polling) e decide a resposta. */
@Service
public class TelegramUpdateHandler {

    private static final Logger log = LoggerFactory.getLogger(TelegramUpdateHandler.class);

    private static final String BOAS_VINDAS = """
            <b>UniChamados</b>
            Olá! Este bot avisa os docentes sobre chamados atribuídos a eles.

            Para vincular a sua conta, gere um código no portal (menu Telegram) e abra o link ou escaneie o QR Code.

            Use /ajuda para ver os comandos.""";

    private static final String AJUDA = """
            <b>Comandos</b>
            /start - apresentação
            /ajuda - esta lista

            Os demais comandos ficam disponíveis depois que a sua conta for vinculada.""";

    private final TelegramUpdateProcessadoRepository processados;
    private final TelegramClient telegram;

    public TelegramUpdateHandler(TelegramUpdateProcessadoRepository processados, TelegramClient telegram) {
        this.processados = processados;
        this.telegram = telegram;
    }

    public void tratar(TelegramTipos.Update update) {
        if (update == null || update.updateId() == null) return;

        // O Telegram pode reenviar o mesmo evento: só o primeiro é processado.
        if (processados.registrar(update.updateId()) == 0) {
            log.debug("Evento {} repetido, ignorado.", update.updateId());
            return;
        }

        try {
            if (update.message() != null) {
                tratarMensagem(update.message());
            } else if (update.callbackQuery() != null) {
                tratarBotao(update.callbackQuery());
            }
        } catch (TelegramException e) {
            log.warn("Não foi possível responder no Telegram: {}", e.getMessage());
        }
    }

    private void tratarMensagem(TelegramTipos.Message mensagem) {
        if (mensagem.chat() == null || mensagem.chat().id() == null) return;
        Long chatId = mensagem.chat().id();

        if (!"private".equals(mensagem.chat().type())) {
            telegram.enviarMensagem(chatId, "Use este bot em uma conversa privada.");
            return;
        }

        String texto = mensagem.text() == null ? "" : mensagem.text().trim();
        if (texto.isEmpty()) {
            telegram.enviarMensagem(chatId, "Envie um texto ou use /ajuda.");
            return;
        }

        String[] partes = texto.split("\\s+", 2);
        String comando = partes[0].toLowerCase();
        int arroba = comando.indexOf('@');
        if (arroba > 0) comando = comando.substring(0, arroba);   // /ajuda@nome_do_bot
        String argumento = partes.length > 1 ? partes[1].trim() : "";

        switch (comando) {
            case "/start" -> {
                if (argumento.isEmpty()) {
                    telegram.enviarMensagem(chatId, BOAS_VINDAS);
                } else {
                    // Etapa 5 troca este trecho pela vinculação de verdade.
                    telegram.enviarMensagem(chatId, "Código recebido. A vinculação ainda não está habilitada nesta versão.");
                }
            }
            case "/ajuda", "/help" -> telegram.enviarMensagem(chatId, AJUDA);
            default -> telegram.enviarMensagem(chatId, "Não entendi. Use /ajuda para ver os comandos.");
        }
    }

    private void tratarBotao(TelegramTipos.CallbackQuery botao) {
        // As ações dos botões entram nas próximas etapas.
        telegram.responderCallback(botao.id(), "Ação ainda não disponível.");
    }
}