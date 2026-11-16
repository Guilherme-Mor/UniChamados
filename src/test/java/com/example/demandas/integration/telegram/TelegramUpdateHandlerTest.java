package com.example.demandas.integration.telegram;

import com.example.demandas.repository.TelegramUpdateProcessadoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TelegramUpdateHandlerTest {

    private static final TelegramTipos.TgUser MARIA = new TelegramTipos.TgUser(555L, false, "Maria", "Silva", "maria");

    @Mock TelegramUpdateProcessadoRepository processados;
    @Mock TelegramClient telegram;
    @Mock TelegramVinculoService vinculos;
    @Mock TelegramChamadoDados dados;
    @Mock TelegramChamadoAcoes acoes;

    private TelegramUpdateHandler handler;

    @BeforeEach
    void preparar() {
        handler = new TelegramUpdateHandler(processados, telegram, vinculos, dados, acoes);
        when(processados.registrar(anyLong())).thenReturn(1);   // evento novo
    }

    private static TelegramTipos.Update mensagem(long updateId, String tipoChat, String texto,
                                                 TelegramTipos.Message respondida) {
        TelegramTipos.Message m = new TelegramTipos.Message(9L, MARIA, new TelegramTipos.Chat(100L, tipoChat),
                texto, respondida);
        return new TelegramTipos.Update(updateId, m, null);
    }

    private static TelegramTipos.Update botao(long updateId, String dadosDoBotao) {
        TelegramTipos.Message origem = new TelegramTipos.Message(1L, null, new TelegramTipos.Chat(100L, "private"), null, null);
        return new TelegramTipos.Update(updateId, null,
                new TelegramTipos.CallbackQuery("cb1", MARIA, origem, dadosDoBotao));
    }

    @Test
    void eventoRepetidoNaoEhProcessadoDeNovo() {
        when(processados.registrar(1L)).thenReturn(0);

        handler.tratar(mensagem(1, "private", "/ajuda", null));

        verifyNoInteractions(telegram, vinculos, dados, acoes);
    }

    @Test
    void ajudaListaOsComandos() {
        handler.tratar(mensagem(2, "private", "/ajuda", null));

        verify(telegram).enviarMensagem(eq(100L), contains("Comandos"));
    }

    @Test
    void comandoComNomeDoBotEhReconhecido() {
        handler.tratar(mensagem(3, "private", "/ajuda@unichamados_bot", null));

        verify(telegram).enviarMensagem(eq(100L), contains("Comandos"));
    }

    @Test
    void grupoEhRecusado() {
        handler.tratar(mensagem(4, "group", "/chamados", null));

        verify(telegram).enviarMensagem(eq(100L), contains("conversa privada"));
        verifyNoInteractions(dados);
    }

    @Test
    void startComCodigoVaiParaOServicoDeVinculo() {
        when(vinculos.iniciarVinculo(100L, MARIA, "ABCDEFGHJK")).thenReturn("Pedido recebido.");

        handler.tratar(mensagem(5, "private", "/start ABCDEFGHJK", null));

        verify(telegram).enviarMensagem(100L, "Pedido recebido.");
    }

    @Test
    void protocoloMalFormadoNaoConsultaNada() {
        handler.tratar(mensagem(6, "private", "/chamado abc", null));

        verify(telegram).enviarMensagem(eq(100L), contains("Informe o protocolo"));
        verifyNoInteractions(dados);
    }


    @Test
    void textoSoltoRecebeOrientacao() {
        handler.tratar(mensagem(7, "private", "olá", null));

        verify(telegram).enviarMensagem(eq(100L), contains("Responder"));
        verifyNoInteractions(acoes);
    }

    @Test
    void replyViraRespostaAoChamado() {
        TelegramTipos.Message respondida = new TelegramTipos.Message(55L, null, new TelegramTipos.Chat(100L, "private"), "aviso", null);
        when(acoes.responder(100L, 55L, 9L, "Minha resposta")).thenReturn(RespostaTelegram.simples("ok"));

        handler.tratar(mensagem(8, "private", "Minha resposta", respondida));

        verify(acoes).responder(100L, 55L, 9L, "Minha resposta");
        verify(telegram).enviarMensagem(eq(100L), eq("ok"), any());
    }

    @Test
    void botaoDeDetalhesConsultaEGuardaALigacaoMensagemChamado() {
        when(dados.detalhe(100L, 100L)).thenReturn(new RespostaTelegram("detalhe", Map.of("k", "v"), 100L, "DETALHE"));
        when(telegram.enviarMensagem(eq(100L), eq("detalhe"), any())).thenReturn(77L);

        handler.tratar(botao(9, "ver:100"));

        verify(telegram).responderCallback("cb1", null);
        verify(dados).registrarMensagem(100L, 77L, 100L, "ENVIADA", "DETALHE");
    }

    @Test
    void botaoComDadosInvalidosEhIgnoradoComAviso() {
        handler.tratar(botao(10, "xx"));

        verify(telegram).enviarMensagem(100L, "Ação inválida.");
        verifyNoInteractions(dados, acoes);
    }

    @Test
    void erroInesperadoNaoDerrubaOBotEAvisaOUsuario() {
        when(dados.listar(100L)).thenThrow(new IllegalStateException("falha simulada"));

        handler.tratar(mensagem(11, "private", "/chamados", null));

        verify(telegram).enviarMensagem(eq(100L), contains("Algo deu errado"));
        verify(telegram, never()).enviarMensagem(eq(100L), contains("falha simulada"));
    }
}