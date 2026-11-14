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
            /desvincular - solta este Telegram da sua conta

            Os demais comandos ficam disponíveis depois que a sua conta for vinculada.""";

    private final TelegramUpdateProcessadoRepository processados;
    private final TelegramClient telegram;
    private final TelegramVinculoService vinculos;

    public TelegramUpdateHandler(TelegramUpdateProcessadoRepository processados,
                                 TelegramClient telegram,
                                 TelegramVinculoService vinculos) {
        this.processados = processados;
        this.telegram = telegram;
        this.vinculos = vinculos;
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
        } catch (RuntimeException e) {
            log.error("Erro ao tratar o evento {}.", update.updateId(), e);
            responderErro(update);
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
                    telegram.enviarMensagem(chatId, vinculos.iniciarVinculo(chatId, mensagem.from(), argumento));
                }
            }
            case "/desvincular" -> telegram.enviarMensagem(chatId, vinculos.desvincularPorChat(chatId));
            case "/ajuda", "/help" -> telegram.enviarMensagem(chatId, AJUDA);
            default -> telegram.enviarMensagem(chatId, "Não entendi. Use /ajuda para ver os comandos.");
        }
    }

    private void tratarBotao(TelegramTipos.CallbackQuery botao) {
        // As ações dos botões entram nas próximas etapas.
        telegram.responderCallback(botao.id(), "Ação ainda não disponível.");
    }

    private void responderErro(TelegramTipos.Update update) {
        Long chatId = null;
        if (update.message() != null && update.message().chat() != null) {
            chatId = update.message().chat().id();
        } else if (update.callbackQuery() != null && update.callbackQuery().message() != null
                && update.callbackQuery().message().chat() != null) {
            chatId = update.callbackQuery().message().chat().id();
        }
        if (chatId == null) return;
        try {
            telegram.enviarMensagem(chatId, "Algo deu errado. Tente novamente em instantes.");
        } catch (TelegramException ignorado) {
            // já registramos o erro original
        }
    }
}