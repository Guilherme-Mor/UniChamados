package com.example.demandas.integration.telegram;

import com.example.demandas.Fixtures;
import com.example.demandas.entity.Chamado;
import com.example.demandas.entity.Notificacao;
import com.example.demandas.entity.TelegramMensagem;
import com.example.demandas.entity.TelegramVinculo;
import com.example.demandas.entity.Usuario;
import com.example.demandas.repository.NotificacaoRepository;
import com.example.demandas.repository.TelegramMensagemRepository;
import com.example.demandas.repository.TelegramVinculoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class NotificacaoProcessadorTest {

    @Mock NotificacaoRepository notificacaoRepo;
    @Mock TelegramVinculoRepository vinculoRepo;
    @Mock TelegramMensagemRepository mensagemRepo;
    @Mock TelegramClient telegram;

    private NotificacaoProcessador processador;
    private Usuario docente;
    private TelegramVinculo vinculo;
    private Notificacao notificacao;

    @BeforeEach
    void preparar() {
        processador = new NotificacaoProcessador(notificacaoRepo, vinculoRepo, mensagemRepo, telegram);
        docente = Fixtures.usuario(20, "Docente A", "a@t", "ATIVO", "DOCENTE");
        Usuario aluno = Fixtures.usuario(10, "Aluno", "aluno@t", "ATIVO", "DISCENTE");
        Chamado chamado = Fixtures.chamado(100L, "20261105-000001", aluno, docente, "ATRIBUIDO");

        vinculo = new TelegramVinculo();
        vinculo.setUsuario(docente);
        vinculo.setChatId(500L);
        vinculo.setStatus(TelegramVinculo.ATIVO);
        when(vinculoRepo.findByUsuarioIdAndStatusIn(eq(20), anyCollection())).thenReturn(Optional.of(vinculo));

        notificacao = new Notificacao();
        notificacao.setUsuario(docente);
        notificacao.setChamado(chamado);
        notificacao.setTipo(Notificacao.TIPO_ATRIBUICAO);
        notificacao.setTexto("texto do aviso");
        notificacao.setProximoEnvio(LocalDateTime.now());
        when(notificacaoRepo.reservarProxima(any())).thenReturn(Optional.of(notificacao));
    }

    private void telegramFalhaCom(Integer codigo, Integer espera) {
        when(telegram.enviarMensagem(eq(500L), eq("texto do aviso"), any()))
                .thenThrow(new TelegramException("falha simulada", codigo, espera));
    }

    // ===== Sucesso =====

    @Test
    void semNotificacaoPendenteNaoFazNada() {
        when(notificacaoRepo.reservarProxima(any())).thenReturn(Optional.empty());

        assertThat(processador.processarProxima()).isFalse();
        verifyNoInteractions(telegram);
    }

    @Test
    void envioComSucessoMarcaComoEnviadaERegistraAMensagem() {
        when(telegram.enviarMensagem(eq(500L), eq("texto do aviso"), any())).thenReturn(777L);

        assertThat(processador.processarProxima()).isTrue();

        assertThat(notificacao.getStatus()).isEqualTo(Notificacao.ENVIADA);
        assertThat(notificacao.getEnviadoEm()).isNotNull();
        assertThat(notificacao.getTentativas()).isEqualTo(1);
        ArgumentCaptor<TelegramMensagem> captor = ArgumentCaptor.forClass(TelegramMensagem.class);
        verify(mensagemRepo).save(captor.capture());
        assertThat(captor.getValue().getChatId()).isEqualTo(500L);
        assertThat(captor.getValue().getMessageId()).isEqualTo(777L);
        assertThat(captor.getValue().getChamado().getId()).isEqualTo(100L);
        assertThat(captor.getValue().getDirecao()).isEqualTo(TelegramMensagem.ENVIADA);
    }

    @SuppressWarnings("unchecked")
    @Test
    void oAvisoSaiComOsBotoesDoChamado() {
        when(telegram.enviarMensagem(eq(500L), eq("texto do aviso"), any())).thenReturn(777L);

        processador.processarProxima();

        ArgumentCaptor<Map<String, Object>> teclado = ArgumentCaptor.forClass(Map.class);
        verify(telegram).enviarMensagem(eq(500L), eq("texto do aviso"), teclado.capture());
        assertThat(Fixtures.callbacks(teclado.getValue())).contains("ver:100", "resp:100", "st:100");
    }

    // ===== Sem destino =====

    @Test
    void semVinculoAtivoNaoEnviaEMarcaSemVinculo() {
        when(vinculoRepo.findByUsuarioIdAndStatusIn(eq(20), anyCollection())).thenReturn(Optional.empty());

        processador.processarProxima();

        assertThat(notificacao.getStatus()).isEqualTo(Notificacao.SEM_VINCULO);
        verifyNoInteractions(telegram);
    }

    @Test
    void docenteDesativadoNaoRecebe() {
        docente.setStatus("INATIVO");

        processador.processarProxima();

        assertThat(notificacao.getStatus()).isEqualTo(Notificacao.SEM_VINCULO);
        verifyNoInteractions(telegram);
    }

    // ===== Falhas =====

    @Test
    void botBloqueadoRevogaOVinculoENaoInsiste() {
        telegramFalhaCom(403, null);

        processador.processarProxima();

        assertThat(notificacao.getStatus()).isEqualTo(Notificacao.FALHA);
        assertThat(vinculo.getStatus()).isEqualTo(TelegramVinculo.REVOGADO);
        assertThat(vinculo.getMotivoRevogacao()).isEqualTo("BOT_BLOQUEADO");
    }

    @Test
    void erroPermanenteDoTelegramNaoEhReenviado() {
        telegramFalhaCom(400, null);

        processador.processarProxima();

        assertThat(notificacao.getStatus()).isEqualTo(Notificacao.FALHA);
        assertThat(vinculo.getStatus()).isEqualTo(TelegramVinculo.ATIVO);
        assertThat(notificacao.getErro()).contains("falha simulada");
    }

    @Test
    void erroDeRedeFicaPendenteComEsperaDe30Segundos() {
        telegramFalhaCom(null, null);

        LocalDateTime antes = LocalDateTime.now();
        processador.processarProxima();
        LocalDateTime depois = LocalDateTime.now();

        assertThat(notificacao.getStatus()).isEqualTo(Notificacao.PENDENTE);
        assertThat(notificacao.getTentativas()).isEqualTo(1);
        assertThat(notificacao.getProximoEnvio()).isBetween(antes.plusSeconds(30), depois.plusSeconds(30));
    }

    @Test
    void erroDoServidorDoTelegramTambemEhReenviado() {
        telegramFalhaCom(500, null);

        processador.processarProxima();

        assertThat(notificacao.getStatus()).isEqualTo(Notificacao.PENDENTE);
    }

    @Test
    void aEsperaCresceAcadaTentativa() {
        notificacao.setTentativas(1);   // esta será a segunda tentativa
        telegramFalhaCom(null, null);

        LocalDateTime antes = LocalDateTime.now();
        processador.processarProxima();
        LocalDateTime depois = LocalDateTime.now();

        assertThat(notificacao.getTentativas()).isEqualTo(2);
        assertThat(notificacao.getProximoEnvio()).isBetween(antes.plusSeconds(60), depois.plusSeconds(60));
    }

    @Test
    void respeitaOTempoPedidoPeloTelegramNoLimiteDeEnvio() {
        telegramFalhaCom(429, 120);

        LocalDateTime antes = LocalDateTime.now();
        processador.processarProxima();
        LocalDateTime depois = LocalDateTime.now();

        assertThat(notificacao.getStatus()).isEqualTo(Notificacao.PENDENTE);
        assertThat(notificacao.getProximoEnvio()).isBetween(antes.plusSeconds(120), depois.plusSeconds(120));
    }

    @Test
    void aEsperaNuncaPassaDeUmaHora() {
        telegramFalhaCom(429, 99999);

        LocalDateTime antes = LocalDateTime.now();
        processador.processarProxima();
        LocalDateTime depois = LocalDateTime.now();

        assertThat(notificacao.getProximoEnvio()).isBetween(antes.plusSeconds(3600), depois.plusSeconds(3600));
    }

    @Test
    void naQuintaTentativaDesiste() {
        notificacao.setTentativas(4);
        telegramFalhaCom(null, null);

        processador.processarProxima();

        assertThat(notificacao.getTentativas()).isEqualTo(5);
        assertThat(notificacao.getStatus()).isEqualTo(Notificacao.FALHA);
    }
}