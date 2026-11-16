package com.example.demandas.integration.telegram;

import com.example.demandas.Fixtures;
import com.example.demandas.entity.Chamado;
import com.example.demandas.entity.Interacao;
import com.example.demandas.entity.TelegramMensagem;
import com.example.demandas.entity.TelegramVinculo;
import com.example.demandas.entity.Usuario;
import com.example.demandas.repository.ChamadoRepository;
import com.example.demandas.repository.InteracaoRepository;
import com.example.demandas.repository.TelegramMensagemRepository;
import com.example.demandas.repository.TelegramVinculoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TelegramChamadoDadosTest {

    private static final String PROTOCOLO = "20261105-000001";

    @Mock TelegramVinculoRepository vinculoRepo;
    @Mock ChamadoRepository chamadoRepo;
    @Mock InteracaoRepository interacaoRepo;
    @Mock TelegramMensagemRepository mensagemRepo;

    private TelegramChamadoDados dados;
    private Usuario aluno;
    private Usuario docenteA;
    private Usuario docenteB;

    @BeforeEach
    void preparar() {
        dados = new TelegramChamadoDados(vinculoRepo, chamadoRepo, interacaoRepo, mensagemRepo);
        aluno = Fixtures.usuario(10, "Aluno", "aluno@t", "ATIVO", "DISCENTE");
        docenteA = Fixtures.usuario(20, "Docente A", "a@t", "ATIVO", "DOCENTE");
        docenteB = Fixtures.usuario(21, "Docente B", "b@t", "ATIVO", "DOCENTE");
        vincularChat(100L, docenteA);   // o chat 100 é do docente A
    }

    private void vincularChat(long chatId, Usuario dono) {
        TelegramVinculo v = new TelegramVinculo();
        v.setUsuario(dono);
        v.setChatId(chatId);
        v.setStatus(TelegramVinculo.ATIVO);
        when(vinculoRepo.findByChatIdAndStatusIn(eq(chatId), anyCollection())).thenReturn(Optional.of(v));
    }

    private Chamado chamado(String status, Usuario responsavel) {
        Chamado c = Fixtures.chamado(100L, PROTOCOLO, aluno, responsavel, status);
        when(chamadoRepo.findById(100L)).thenReturn(Optional.of(c));
        when(chamadoRepo.findByProtocolo(PROTOCOLO)).thenReturn(Optional.of(c));
        return c;
    }

    // ===== Identidade =====

    @Test
    void reconheceODocenteDoChat() {
        assertThat(dados.docente(100L)).isPresent();
        assertThat(dados.docente(100L).get().email()).isEqualTo("a@t");
        assertThat(dados.docente(999L)).isEmpty();   // chat sem vínculo
    }

    @Test
    void docenteInativoOuSemPerfilDeDocenteNaoEhReconhecido() {
        Usuario inativo = Fixtures.usuario(22, "Inativo", "i@t", "INATIVO", "DOCENTE");
        Usuario virouAluno = Fixtures.usuario(23, "Ex-docente", "x@t", "ATIVO", "DISCENTE");
        vincularChat(200L, inativo);
        vincularChat(300L, virouAluno);

        assertThat(dados.docente(200L)).isEmpty();
        assertThat(dados.docente(300L)).isEmpty();
        assertThat(dados.listar(200L).texto()).isEqualTo(TelegramChamadoDados.SEM_VINCULO);
    }

    // ===== Isolamento =====

    @Test
    void chamadoDeOutroDocenteEChamadoInexistenteDaoARespostaIdentica() {
        chamado("ATRIBUIDO", docenteB);   // o chamado é do docente B; quem pergunta é o A

        String deOutro = dados.porProtocolo(100L, PROTOCOLO).texto();
        String inexistente = dados.porProtocolo(100L, "20000101-000001").texto();

        assertThat(deOutro).isEqualTo(TelegramChamadoDados.NAO_ENCONTRADO).isEqualTo(inexistente);
    }

    @Test
    void naoVazaDadosPeloBotaoDeDetalhes() {
        chamado("ATRIBUIDO", docenteB);

        RespostaTelegram r = dados.detalhe(100L, 100L);

        assertThat(r.texto()).isEqualTo(TelegramChamadoDados.NAO_ENCONTRADO);
        assertThat(r.teclado()).isNull();
        assertThat(r.texto()).doesNotContain(PROTOCOLO);
    }

    @Test
    void pedirRespostaEMudarStatusTambemSaoNegadosParaOutroDocente() {
        chamado("ATRIBUIDO", docenteB);

        assertThat(dados.pedirResposta(100L, 100L).texto()).isEqualTo(TelegramChamadoDados.NAO_ENCONTRADO);
        assertThat(dados.opcoesStatus(100L, 100L).texto()).isEqualTo(TelegramChamadoDados.NAO_ENCONTRADO);
    }

    @Test
    void semVinculoNadaEhConsultado() {
        chamado("ATRIBUIDO", docenteA);

        assertThat(dados.porProtocolo(999L, PROTOCOLO).texto()).isEqualTo(TelegramChamadoDados.SEM_VINCULO);
        assertThat(dados.detalhe(999L, 100L).texto()).isEqualTo(TelegramChamadoDados.SEM_VINCULO);
        assertThat(dados.listar(999L).texto()).isEqualTo(TelegramChamadoDados.SEM_VINCULO);
    }

    @Test
    void ehResponsavelSoParaOResponsavelAtual() {
        chamado("ATRIBUIDO", docenteA);

        assertThat(dados.ehResponsavel(20, 100L)).isTrue();
        assertThat(dados.ehResponsavel(21, 100L)).isFalse();
        assertThat(dados.ehResponsavel(20, 999L)).isFalse();
    }

    // ===== Detalhe =====

    @Test
    void detalheMostraOsDadosEOsBotoes() {
        chamado("ATRIBUIDO", docenteA);
        Interacao antiga = interacao("Primeira", LocalDateTime.of(2026, 11, 5, 10, 0), "PORTAL");
        Interacao nova = interacao("Segunda", LocalDateTime.of(2026, 11, 5, 11, 0), "TELEGRAM");
        when(interacaoRepo.findTop3ByChamadoIdOrderByCriadoEmDescIdDesc(100L)).thenReturn(List.of(nova, antiga));

        RespostaTelegram r = dados.detalhe(100L, 100L);

        assertThat(r.texto()).contains(PROTOCOLO, "Revisão da nota", "ATRIBUIDO", "Solicitante: Aluno",
                "A nota da prova não confere.");
        assertThat(r.texto().indexOf("Primeira")).isLessThan(r.texto().indexOf("Segunda"));   // da mais antiga para a mais nova
        assertThat(Fixtures.callbacks(r.teclado())).contains("ver:100", "resp:100", "st:100");
        assertThat(r.chamadoId()).isEqualTo(100L);
        assertThat(r.tipo()).isEqualTo("DETALHE");
    }

    @Test
    void detalheEscapaHtmlVindoDosUsuarios() {
        Chamado c = chamado("ATRIBUIDO", docenteA);
        c.setTitulo("<b>Nota</b> & revisão");

        String texto = dados.detalhe(100L, 100L).texto();

        assertThat(texto).contains("&lt;b&gt;Nota&lt;/b&gt; &amp; revisão").doesNotContain("<b>Nota</b>");
    }

    @Test
    void chamadoEncerradoNaoOferecaBotoes() {
        chamado("ENCERRADO", docenteA);

        RespostaTelegram r = dados.detalhe(100L, 100L);

        assertThat(r.teclado()).isNull();
        assertThat(r.texto()).contains("não aceita novas ações");
    }

    private Interacao interacao(String mensagem, LocalDateTime quando, String canal) {
        Interacao i = new Interacao();
        ReflectionTestUtils.setField(i, "criadoEm", quando);
        i.setUsuario(aluno);
        i.setMensagem(mensagem);
        i.setCanal(canal);
        return i;
    }

    // ===== Lista =====

    @Test
    void listaMostraOsChamadosEOTotal() {
        Chamado c = chamado("ATRIBUIDO", docenteA);
        when(chamadoRepo.findTop5ByResponsavelIdAndStatusNomeNotInOrderByCriadoEmDesc(eq(20), anyCollection()))
                .thenReturn(List.of(c));
        when(chamadoRepo.countByResponsavelIdAndStatusNomeNotIn(eq(20), anyCollection())).thenReturn(7L);

        RespostaTelegram r = dados.listar(100L);

        assertThat(r.texto()).contains("(7)", PROTOCOLO, "Mostrando os 1 mais recentes");
        assertThat(Fixtures.callbacks(r.teclado())).containsExactly("ver:100");
    }

    @Test
    void listaVaziaAvisaQueNaoHaChamados() {
        assertThat(dados.listar(100L).texto()).contains("não tem chamados em aberto");
    }

    // ===== Resposta e status =====

    @Test
    void pedirRespostaUsaRespostaForcadaEGuardaOChamado() {
        chamado("EM_ATENDIMENTO", docenteA);

        RespostaTelegram r = dados.pedirResposta(100L, 100L);

        assertThat(r.teclado()).containsEntry("force_reply", true);
        assertThat(r.chamadoId()).isEqualTo(100L);
        assertThat(r.tipo()).isEqualTo("PEDIDO_RESPOSTA");
    }

    @Test
    void chamadoFinalizadoNaoPedeResposta() {
        chamado("CANCELADO", docenteA);

        assertThat(dados.pedirResposta(100L, 100L).texto()).contains("não aceita novas mensagens");
    }

    @Test
    void opcoesDeStatusSeguemAPoliticaESoOferecemOsPermitidosPeloTelegram() {
        Chamado c = chamado("ATRIBUIDO", docenteA);
        assertThat(Fixtures.callbacks(dados.opcoesStatus(100L, 100L).teclado()))
                .containsExactlyInAnyOrder("s:100:EM_ATENDIMENTO", "ver:100");

        c.setStatus(Fixtures.status("EM_ATENDIMENTO"));
        assertThat(Fixtures.callbacks(dados.opcoesStatus(100L, 100L).teclado()))
                .containsExactlyInAnyOrder("s:100:AGUARDANDO_DISCENTE", "s:100:RESOLVIDO", "ver:100");

        c.setStatus(Fixtures.status("RESOLVIDO"));
        assertThat(Fixtures.callbacks(dados.opcoesStatus(100L, 100L).teclado()))
                .containsExactlyInAnyOrder("s:100:EM_ATENDIMENTO", "ver:100");
    }

    @Test
    void semOpcoesPeloTelegramAvisaQueEhSoNoPortal() {
        chamado("ABERTO", docenteA);   // só CANCELADO é possível, e isso fica no portal

        RespostaTelegram r = dados.opcoesStatus(100L, 100L);

        assertThat(r.texto()).contains("Nenhuma mudança de status disponível pelo Telegram");
        assertThat(r.teclado()).isNull();
    }

    // ===== Ligação mensagem → chamado =====

    @Test
    void respostaPorReplyDescobreOChamadoSoParaMensagensDoBot() {
        Chamado c = chamado("ATRIBUIDO", docenteA);
        when(mensagemRepo.findByChatIdAndMessageId(100L, 55L)).thenReturn(Optional.of(mensagem(c, "ENVIADA")));
        when(mensagemRepo.findByChatIdAndMessageId(100L, 56L)).thenReturn(Optional.of(mensagem(c, "RECEBIDA")));
        when(mensagemRepo.findByChatIdAndMessageId(100L, 57L)).thenReturn(Optional.of(mensagem(null, "ENVIADA")));

        assertThat(dados.chamadoDaMensagem(100L, 55L)).contains(100L);
        assertThat(dados.chamadoDaMensagem(100L, 56L)).isEmpty();   // mensagem do próprio docente
        assertThat(dados.chamadoDaMensagem(100L, 57L)).isEmpty();   // mensagem sem chamado
        assertThat(dados.chamadoDaMensagem(100L, 58L)).isEmpty();   // desconhecida
    }

    private TelegramMensagem mensagem(Chamado c, String direcao) {
        TelegramMensagem m = new TelegramMensagem();
        m.setChamado(c);
        m.setDirecao(direcao);
        return m;
    }
}