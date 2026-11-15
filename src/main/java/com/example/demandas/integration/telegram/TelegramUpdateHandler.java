package com.example.demandas.integration.telegram;

import com.example.demandas.entity.TelegramMensagem;
import com.example.demandas.repository.TelegramUpdateProcessadoRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.regex.Pattern;

/** Recebe cada evento do Telegram (webhook ou polling) e decide a resposta. */
@Service
public class TelegramUpdateHandler {

    private static final Logger log = LoggerFactory.getLogger(TelegramUpdateHandler.class);
    private static final Pattern PROTOCOLO = Pattern.compile("\\d{8}-\\d{6}");

    private static final String BOAS_VINDAS = """
            <b>UniChamados</b>
            Olá! Este bot avisa os docentes sobre chamados atribuídos a eles.

            Para vincular a sua conta, gere um código no portal (menu Telegram) e abra o link ou escaneie o QR Code.

            Use /ajuda para ver os comandos.""";

    private static final String AJUDA = """
            <b>Comandos</b>
            /chamados - seus chamados em aberto
            /chamado PROTOCOLO - detalhes de um chamado
            /desvincular - solta este Telegram da sua conta
            /start - apresentação
            /ajuda - esta lista

            <b>Nos avisos de chamado</b>
            📄 Ver detalhes · ✍️ Responder · 🔄 Status

            Para responder ao discente, toque em Responder ou responda (reply) a uma mensagem do bot sobre o chamado.""";

    private static final String ORIENTACAO_TEXTO =
            "Para responder a um chamado, toque em <b>Responder</b> na mensagem dele ou responda (reply) a essa mensagem.\n"
                    + "Use /chamados para ver os seus chamados ou /ajuda para os comandos.";

    private final TelegramUpdateProcessadoRepository processados;
    private final TelegramClient telegram;
    private final TelegramVinculoService vinculos;
    private final TelegramChamadoDados dados;
    private final TelegramChamadoAcoes acoes;

    public TelegramUpdateHandler(TelegramUpdateProcessadoRepository processados,
                                 TelegramClient telegram,
                                 TelegramVinculoService vinculos,
                                 TelegramChamadoDados dados,
                                 TelegramChamadoAcoes acoes) {
        this.processados = processados;
        this.telegram = telegram;
        this.vinculos = vinculos;
        this.dados = dados;
        this.acoes = acoes;
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

    // =====================================================================
    // Mensagens e comandos
    // =====================================================================

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

        if (texto.startsWith("/")) {
            tratarComando(chatId, mensagem, texto);
            return;
        }

        // Texto livre: só vale como resposta quando é um reply a uma mensagem do bot
        if (mensagem.replyToMessage() != null && mensagem.replyToMessage().messageId() != null) {
            enviar(chatId, acoes.responder(chatId, mensagem.replyToMessage().messageId(),
                    mensagem.messageId(), texto));
            return;
        }
        telegram.enviarMensagem(chatId, ORIENTACAO_TEXTO);
    }

    private void tratarComando(Long chatId, TelegramTipos.Message mensagem, String texto) {
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
            case "/chamados" -> enviar(chatId, dados.listar(chatId));
            case "/chamado" -> {
                if (!PROTOCOLO.matcher(argumento).matches()) {
                    telegram.enviarMensagem(chatId, "Informe o protocolo. Exemplo: /chamado 20261105-000012");
                } else {
                    enviar(chatId, dados.porProtocolo(chatId, argumento));
                }
            }
            case "/ajuda", "/help" -> telegram.enviarMensagem(chatId, AJUDA);
            default -> telegram.enviarMensagem(chatId, "Não entendi. Use /ajuda para ver os comandos.");
        }
    }

    // =====================================================================
    // Botões
    // =====================================================================

    private void tratarBotao(TelegramTipos.CallbackQuery botao) {
        Long chatId = chatDoBotao(botao);
        confirmarBotao(botao.id());   // fecha o "carregando" do botão
        if (chatId == null) return;

        String[] partes = (botao.data() == null ? "" : botao.data()).split(":");
        Long chamadoId = idDe(partes, 1);
        if (chamadoId == null) {
            telegram.enviarMensagem(chatId, "Ação inválida.");
            return;
        }

        switch (partes[0]) {
            case "ver" -> enviar(chatId, dados.detalhe(chatId, chamadoId));
            case "resp" -> enviar(chatId, dados.pedirResposta(chatId, chamadoId));
            case "st" -> enviar(chatId, dados.opcoesStatus(chatId, chamadoId));
            case "s" -> enviar(chatId, acoes.aplicarStatus(chatId, chamadoId, partes.length > 2 ? partes[2] : null));
            default -> telegram.enviarMensagem(chatId, "Ação inválida.");
        }
    }

    private void confirmarBotao(String callbackId) {
        try {
            telegram.responderCallback(callbackId, null);
        } catch (TelegramException e) {
            log.debug("Botão não confirmado: {}", e.getMessage());   // ex.: botão antigo demais
        }
    }

    private static Long chatDoBotao(TelegramTipos.CallbackQuery botao) {
        if (botao.message() != null && botao.message().chat() != null && botao.message().chat().id() != null) {
            return botao.message().chat().id();
        }
        return botao.from() == null ? null : botao.from().id();
    }

    private static Long idDe(String[] partes, int posicao) {
        if (partes.length <= posicao) return null;
        try {
            return Long.parseLong(partes[posicao]);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    // =====================================================================
    // Envio
    // =====================================================================

    /** Envia a resposta e guarda a ligação mensagem → chamado, para o "responder" por reply. */
    private void enviar(Long chatId, RespostaTelegram resposta) {
        Long messageId = telegram.enviarMensagem(chatId, resposta.texto(), resposta.teclado());
        if (messageId != null && resposta.chamadoId() != null) {
            try {
                dados.registrarMensagem(chatId, messageId, resposta.chamadoId(),
                        TelegramMensagem.ENVIADA, resposta.tipo());
            } catch (RuntimeException e) {
                log.warn("Não foi possível registrar a mensagem {} do chat {}: {}", messageId, chatId, e.getMessage());
            }
        }
    }

    private void responderErro(TelegramTipos.Update update) {
        Long chatId = null;
        if (update.message() != null && update.message().chat() != null) {
            chatId = update.message().chat().id();
        } else if (update.callbackQuery() != null) {
            chatId = chatDoBotao(update.callbackQuery());
        }
        if (chatId == null) return;
        try {
            telegram.enviarMensagem(chatId, "Algo deu errado. Tente novamente em instantes.");
        } catch (TelegramException ignorado) {
            // já registramos o erro original
        }
    }
}