package com.example.demandas.integration.telegram;

import com.example.demandas.dto.ChamadoResumo;
import com.example.demandas.dto.MensagemRequest;
import com.example.demandas.dto.StatusRequest;
import com.example.demandas.exception.RegraNegocioException;
import com.example.demandas.service.ChamadoService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpStatus;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TelegramChamadoAcoesTest {

    @Mock TelegramChamadoDados dados;
    @Mock ChamadoService chamadoService;

    private TelegramChamadoAcoes acoes;

    @BeforeEach
    void preparar() {
        acoes = new TelegramChamadoAcoes(dados, chamadoService);
        when(dados.docente(100L)).thenReturn(Optional.of(new TelegramChamadoDados.Docente(20, "a@t", "Docente A")));
        when(dados.protocolo(100L)).thenReturn("20261105-000001");
    }

    private void respostaAoChamado100() {
        when(dados.chamadoDaMensagem(100L, 55L)).thenReturn(Optional.of(100L));
    }

    // ===== Responder =====

    @Test
    void semVinculoNaoEscreveNada() {
        RespostaTelegram r = acoes.responder(999L, 55L, 56L, "Olá");

        assertThat(r.texto()).isEqualTo(TelegramChamadoDados.SEM_VINCULO);
        verifyNoInteractions(chamadoService);
    }

    @Test
    void replyAUmaMensagemDesconhecidaNaoEscreveNada() {
        RespostaTelegram r = acoes.responder(100L, 55L, 56L, "Olá");

        assertThat(r.texto()).contains("Não consegui identificar a qual chamado");
        verifyNoInteractions(chamadoService);
    }

    @Test
    void quemNaoEhOResponsavelNaoEscreve() {
        respostaAoChamado100();
        when(dados.ehResponsavel(20, 100L)).thenReturn(false);

        RespostaTelegram r = acoes.responder(100L, 55L, 56L, "Olá");

        assertThat(r.texto()).isEqualTo(TelegramChamadoDados.NAO_ENCONTRADO);
        verifyNoInteractions(chamadoService);
        verify(dados, never()).registrarMensagem(any(), any(), any(), any(), any());
    }

    @Test
    void textoVazioOuGrandeDemaisEhRecusado() {
        respostaAoChamado100();
        when(dados.ehResponsavel(20, 100L)).thenReturn(true);

        assertThat(acoes.responder(100L, 55L, 56L, "   ").texto()).contains("Envie um texto");
        assertThat(acoes.responder(100L, 55L, 56L, "x".repeat(5001)).texto()).contains("longa demais");
        verifyNoInteractions(chamadoService);
    }

    @Test
    void respostaDoResponsavelEhRegistradaPeloMesmoServicoDoPortal() {
        respostaAoChamado100();
        when(dados.ehResponsavel(20, 100L)).thenReturn(true);

        RespostaTelegram r = acoes.responder(100L, 55L, 56L, "  Revisão feita  ");

        assertThat(r.texto()).contains("Mensagem registrada", "20261105-000001");
        verify(chamadoService).adicionarMensagem(eq("a@t"), eq(100L),
                argThat((MensagemRequest m) -> m.mensagem().equals("Revisão feita")), eq("TELEGRAM"));
        verify(dados).registrarMensagem(100L, 56L, 100L, "RECEBIDA", "RESPOSTA");
        assertThat(r.chamadoId()).isEqualTo(100L);
    }

    @Test
    void recusaDoServicoViraAvisoEnaoGravaARecepcao() {
        respostaAoChamado100();
        when(dados.ehResponsavel(20, 100L)).thenReturn(true);
        doThrow(new RegraNegocioException("Chamado encerrado não aceita novas mensagens.", HttpStatus.CONFLICT))
                .when(chamadoService).adicionarMensagem(any(), any(), any(), any());

        RespostaTelegram r = acoes.responder(100L, 55L, 56L, "Olá");

        assertThat(r.texto()).contains("⚠️", "não aceita novas mensagens");
        verify(dados, never()).registrarMensagem(any(), any(), any(), any(), any());
    }

    // ===== Status =====

    @Test
    void soOsStatusPermitidosPeloTelegramPassam() {
        when(dados.ehResponsavel(20, 100L)).thenReturn(true);

        assertThat(acoes.aplicarStatus(100L, 100L, "CANCELADO").texto()).contains("só pode ser alterado no portal");
        assertThat(acoes.aplicarStatus(100L, 100L, "ENCERRADO").texto()).contains("só pode ser alterado no portal");
        assertThat(acoes.aplicarStatus(100L, 100L, null).texto()).contains("só pode ser alterado no portal");
        assertThat(acoes.aplicarStatus(100L, 100L, "QUALQUER").texto()).contains("só pode ser alterado no portal");
        verifyNoInteractions(chamadoService);
    }

    @Test
    void quemNaoEhOResponsavelNaoMudaOStatus() {
        when(dados.ehResponsavel(20, 100L)).thenReturn(false);

        RespostaTelegram r = acoes.aplicarStatus(100L, 100L, "RESOLVIDO");

        assertThat(r.texto()).isEqualTo(TelegramChamadoDados.NAO_ENCONTRADO);
        verifyNoInteractions(chamadoService);
    }

    @Test
    void semVinculoNaoMudaOStatus() {
        assertThat(acoes.aplicarStatus(999L, 100L, "RESOLVIDO").texto()).isEqualTo(TelegramChamadoDados.SEM_VINCULO);
        verifyNoInteractions(chamadoService);
    }

    @Test
    void mudancaValidaPassaPeloServicoComOCanalTelegram() {
        when(dados.ehResponsavel(20, 100L)).thenReturn(true);
        when(chamadoService.alterarStatus(eq("a@t"), eq(100L), any(StatusRequest.class), eq("TELEGRAM")))
                .thenReturn(new ChamadoResumo(100L, "20261105-000001", "T", "Nota", null,
                        "EM_ATENDIMENTO", "Aluno", "Docente A", null));

        RespostaTelegram r = acoes.aplicarStatus(100L, 100L, "EM_ATENDIMENTO");

        assertThat(r.texto()).contains("alterado para", "EM ATENDIMENTO");
        assertThat(r.teclado()).isNotNull();
        verify(chamadoService).alterarStatus(eq("a@t"), eq(100L),
                argThat((StatusRequest s) -> s.status().equals("EM_ATENDIMENTO")), eq("TELEGRAM"));
    }

    @Test
    void transicaoRecusadaPelaPoliticaViraAviso() {
        when(dados.ehResponsavel(20, 100L)).thenReturn(true);
        when(chamadoService.alterarStatus(any(), any(), any(), any())).thenThrow(new RegraNegocioException(
                "Não é possível mudar o chamado de ATRIBUIDO para RESOLVIDO.", HttpStatus.UNPROCESSABLE_ENTITY));

        RespostaTelegram r = acoes.aplicarStatus(100L, 100L, "RESOLVIDO");

        assertThat(r.texto()).contains("⚠️", "Não é possível mudar o chamado");
    }
}