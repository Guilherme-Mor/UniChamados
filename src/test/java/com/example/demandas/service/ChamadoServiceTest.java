package com.example.demandas.service;

import com.example.demandas.Fixtures;
import com.example.demandas.dto.AtribuicaoRequest;
import com.example.demandas.dto.ChamadoRequest;
import com.example.demandas.dto.ChamadoResumo;
import com.example.demandas.dto.MensagemRequest;
import com.example.demandas.dto.StatusRequest;
import com.example.demandas.entity.Categoria;
import com.example.demandas.entity.Chamado;
import com.example.demandas.entity.Disciplina;
import com.example.demandas.entity.Interacao;
import com.example.demandas.entity.Usuario;
import com.example.demandas.exception.RegraNegocioException;
import com.example.demandas.integration.telegram.TelegramNotificacoes;
import com.example.demandas.repository.CategoriaRepository;
import com.example.demandas.repository.ChamadoRepository;
import com.example.demandas.repository.DisciplinaRepository;
import com.example.demandas.repository.InteracaoRepository;
import com.example.demandas.repository.StatusChamadoRepository;
import com.example.demandas.repository.UsuarioRepository;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ChamadoServiceTest {

    @Mock ChamadoRepository chamadoRepo;
    @Mock InteracaoRepository interacaoRepo;
    @Mock CategoriaRepository categoriaRepo;
    @Mock DisciplinaRepository disciplinaRepo;
    @Mock StatusChamadoRepository statusRepo;
    @Mock UsuarioRepository usuarioRepo;
    @Mock ProtocoloGenerator protocoloGenerator;
    @Mock TelegramNotificacoes telegramNotificacoes;

    private ChamadoService servico;
    private Usuario admin;
    private Usuario aluno;
    private Usuario outroAluno;
    private Usuario docenteA;
    private Usuario docenteB;

    @BeforeEach
    void preparar() {
        servico = new ChamadoService(chamadoRepo, interacaoRepo, categoriaRepo, disciplinaRepo,
                statusRepo, usuarioRepo, protocoloGenerator, telegramNotificacoes);
        admin = Fixtures.usuario(1, "Admin", "admin@t", "ATIVO", "ADMIN");
        aluno = Fixtures.usuario(10, "Aluno", "aluno@t", "ATIVO", "DISCENTE");
        outroAluno = Fixtures.usuario(11, "Outro Aluno", "aluno2@t", "ATIVO", "DISCENTE");
        docenteA = Fixtures.usuario(20, "Docente A", "a@t", "ATIVO", "DOCENTE");
        docenteB = Fixtures.usuario(21, "Docente B", "b@t", "ATIVO", "DOCENTE");
        for (Usuario u : List.of(admin, aluno, outroAluno, docenteA, docenteB)) {
            when(usuarioRepo.findByEmailIgnoreCase(u.getEmail())).thenReturn(Optional.of(u));
            when(usuarioRepo.findById(u.getId())).thenReturn(Optional.of(u));
        }
        for (String s : List.of("ABERTO", "ATRIBUIDO", "EM_ATENDIMENTO", "AGUARDANDO_DISCENTE",
                "RESOLVIDO", "ENCERRADO", "CANCELADO")) {
            when(statusRepo.findByNome(s)).thenReturn(Optional.of(Fixtures.status(s)));
        }
        when(protocoloGenerator.proximo()).thenReturn("20261105-000001");
    }

    // ===== Apoio =====

    private Chamado chamado(String status, Usuario responsavel) {
        Chamado c = Fixtures.chamado(100L, "20261105-000001", aluno, responsavel, status);
        when(chamadoRepo.findById(100L)).thenReturn(Optional.of(c));
        return c;
    }

    private Interacao ultimaInteracao() {
        ArgumentCaptor<Interacao> captor = ArgumentCaptor.forClass(Interacao.class);
        verify(interacaoRepo).save(captor.capture());
        return captor.getValue();
    }

    private static void esperarErro(ThrowingCallable acao, HttpStatus status) {
        assertThatThrownBy(acao)
                .isInstanceOf(RegraNegocioException.class)
                .satisfies(e -> assertThat(((RegraNegocioException) e).getStatus()).isEqualTo(status));
    }

    private StatusRequest pedido(String status) {
        return new StatusRequest(status, null);
    }

    // ===== Mudança de status =====

    @Test
    void responsavelMudaOStatusEGravaHistorico() {
        Chamado c = chamado("ATRIBUIDO", docenteA);

        servico.alterarStatus("a@t", 100L, pedido("EM_ATENDIMENTO"));

        assertThat(c.getStatus().getNome()).isEqualTo("EM_ATENDIMENTO");
        assertThat(c.getEncerradoEm()).isNull();
        Interacao i = ultimaInteracao();
        assertThat(i.getTipo()).isEqualTo("MUDANCA_STATUS");
        assertThat(i.getCanal()).isEqualTo("PORTAL");
        assertThat(i.getMensagem()).contains("ATRIBUIDO", "EM_ATENDIMENTO");
    }

    @Test
    void mudancaPeloTelegramFicaRegistradaComOCanal() {
        chamado("ATRIBUIDO", docenteA);

        servico.alterarStatus("a@t", 100L, pedido("EM_ATENDIMENTO"), "TELEGRAM");

        assertThat(ultimaInteracao().getCanal()).isEqualTo("TELEGRAM");
    }

    @Test
    void observacaoEntraNoHistorico() {
        chamado("EM_ATENDIMENTO", docenteA);

        servico.alterarStatus("a@t", 100L, new StatusRequest("RESOLVIDO", "Revisão feita"));

        assertThat(ultimaInteracao().getMensagem()).contains("Revisão feita");
    }

    @Test
    void transicaoForaDaPoliticaEhRecusadaEMantemOStatus() {
        Chamado c = chamado("ATRIBUIDO", docenteA);

        esperarErro(() -> servico.alterarStatus("a@t", 100L, pedido("RESOLVIDO")), HttpStatus.UNPROCESSABLE_ENTITY);

        assertThat(c.getStatus().getNome()).isEqualTo("ATRIBUIDO");
        verify(interacaoRepo, never()).save(any());
    }

    @Test
    void adminTambemSegueAPolitica() {
        chamado("ATRIBUIDO", docenteA);

        esperarErro(() -> servico.alterarStatus("admin@t", 100L, pedido("ENCERRADO")), HttpStatus.UNPROCESSABLE_ENTITY);
    }

    @Test
    void resolverNaoPreencheEncerradoEm() {
        Chamado c = chamado("EM_ATENDIMENTO", docenteA);

        servico.alterarStatus("a@t", 100L, pedido("RESOLVIDO"));

        assertThat(c.getEncerradoEm()).isNull();
    }

    @Test
    void reabrirUmChamadoResolvidoEhPermitido() {
        Chamado c = chamado("RESOLVIDO", docenteA);

        servico.alterarStatus("a@t", 100L, pedido("EM_ATENDIMENTO"));

        assertThat(c.getStatus().getNome()).isEqualTo("EM_ATENDIMENTO");
        assertThat(c.getEncerradoEm()).isNull();
    }

    @Test
    void encerrarECancelarPreenchemEncerradoEm() {
        Chamado encerrado = chamado("RESOLVIDO", docenteA);
        servico.alterarStatus("admin@t", 100L, pedido("ENCERRADO"));
        assertThat(encerrado.getEncerradoEm()).isNotNull();

        Chamado cancelado = chamado("ATRIBUIDO", docenteA);
        servico.alterarStatus("a@t", 100L, pedido("CANCELADO"));
        assertThat(cancelado.getEncerradoEm()).isNotNull();
    }

    @Test
    void chamadoFinalizadoNaoMudaMais() {
        chamado("ENCERRADO", docenteA);

        esperarErro(() -> servico.alterarStatus("admin@t", 100L, pedido("EM_ATENDIMENTO")), HttpStatus.CONFLICT);
    }

    @Test
    void mesmoStatusEhConflito() {
        chamado("ATRIBUIDO", docenteA);

        esperarErro(() -> servico.alterarStatus("a@t", 100L, pedido("ATRIBUIDO")), HttpStatus.CONFLICT);
    }

    @Test
    void statusInexistenteEhRecusado() {
        chamado("ATRIBUIDO", docenteA);

        esperarErro(() -> servico.alterarStatus("a@t", 100L, pedido("INEXISTENTE")), HttpStatus.UNPROCESSABLE_ENTITY);
    }

    @Test
    void solicitanteSoConsegueCancelar() {
        Chamado c = chamado("ATRIBUIDO", docenteA);

        esperarErro(() -> servico.alterarStatus("aluno@t", 100L, pedido("RESOLVIDO")), HttpStatus.FORBIDDEN);
        assertThat(c.getStatus().getNome()).isEqualTo("ATRIBUIDO");

        servico.alterarStatus("aluno@t", 100L, pedido("CANCELADO"));
        assertThat(c.getStatus().getNome()).isEqualTo("CANCELADO");
    }

    @Test
    void solicitanteNaoCancelaDepoisDeResolvido() {
        chamado("RESOLVIDO", docenteA);

        esperarErro(() -> servico.alterarStatus("aluno@t", 100L, pedido("CANCELADO")), HttpStatus.FORBIDDEN);
    }

    // ===== Isolamento entre usuários =====

    @Test
    void outroDocenteNaoAlteraOChamado() {
        Chamado c = chamado("ATRIBUIDO", docenteA);

        esperarErro(() -> servico.alterarStatus("b@t", 100L, pedido("EM_ATENDIMENTO")), HttpStatus.FORBIDDEN);

        assertThat(c.getStatus().getNome()).isEqualTo("ATRIBUIDO");
        verify(interacaoRepo, never()).save(any());
    }

    @Test
    void outroAlunoNaoVeNemEscreveNoChamado() {
        chamado("ATRIBUIDO", docenteA);

        esperarErro(() -> servico.detalhe("aluno2@t", 100L), HttpStatus.FORBIDDEN);
        esperarErro(() -> servico.adicionarMensagem("aluno2@t", 100L, new MensagemRequest("oi")), HttpStatus.FORBIDDEN);
        esperarErro(() -> servico.transicoes("aluno2@t", 100L), HttpStatus.FORBIDDEN);
        verify(interacaoRepo, never()).save(any());
    }

    @Test
    void chamadoInexistenteDa404() {
        esperarErro(() -> servico.detalhe("admin@t", 999L), HttpStatus.NOT_FOUND);
    }

    @Test
    void transicoesDependemDeQuemPergunta() {
        chamado("ATRIBUIDO", docenteA);

        assertThat(servico.transicoes("a@t", 100L)).containsExactlyInAnyOrder("EM_ATENDIMENTO", "CANCELADO");
        assertThat(servico.transicoes("aluno@t", 100L)).containsExactly("CANCELADO");
    }

    // ===== Mensagens =====

    @Test
    void mensagemFicaNoHistoricoEAvisaOResponsavel() {
        Chamado c = chamado("EM_ATENDIMENTO", docenteA);

        servico.adicionarMensagem("aluno@t", 100L, new MensagemRequest("  Preciso de uma resposta  "));

        Interacao i = ultimaInteracao();
        assertThat(i.getTipo()).isEqualTo("MENSAGEM");
        assertThat(i.getCanal()).isEqualTo("PORTAL");
        assertThat(i.getMensagem()).isEqualTo("Preciso de uma resposta");
        verify(telegramNotificacoes).avisarNovaMensagem(c, aluno);
    }

    @Test
    void mensagemPeloTelegramGravaOCanal() {
        chamado("EM_ATENDIMENTO", docenteA);

        servico.adicionarMensagem("a@t", 100L, new MensagemRequest("Resposta"), "TELEGRAM");

        assertThat(ultimaInteracao().getCanal()).isEqualTo("TELEGRAM");
    }

    @Test
    void chamadoFinalizadoNaoAceitaMensagem() {
        chamado("ENCERRADO", docenteA);

        esperarErro(() -> servico.adicionarMensagem("a@t", 100L, new MensagemRequest("oi")), HttpStatus.CONFLICT);
        verify(interacaoRepo, never()).save(any());
    }

    // ===== Atribuição =====

    @Test
    void atribuirUmChamadoAbertoMudaParaAtribuido() {
        Chamado c = chamado("ABERTO", null);

        servico.atribuir("admin@t", 100L, new AtribuicaoRequest(20));

        assertThat(c.getResponsavel()).isSameAs(docenteA);
        assertThat(c.getStatus().getNome()).isEqualTo("ATRIBUIDO");
        assertThat(ultimaInteracao().getMensagem()).contains("atribuído a Docente A");
        verify(telegramNotificacoes).avisarAtribuicao(c, docenteA, false);
    }

    @Test
    void reatribuirMantemOStatusEAvisaComoReatribuicao() {
        Chamado c = chamado("EM_ATENDIMENTO", docenteA);

        servico.atribuir("admin@t", 100L, new AtribuicaoRequest(21));

        assertThat(c.getResponsavel()).isSameAs(docenteB);
        assertThat(c.getStatus().getNome()).isEqualTo("EM_ATENDIMENTO");
        assertThat(ultimaInteracao().getMensagem()).contains("reatribuído de Docente A para Docente B");
        verify(telegramNotificacoes).avisarAtribuicao(c, docenteB, true);
    }

    @Test
    void naoReatribuiParaOMesmoDocente() {
        chamado("EM_ATENDIMENTO", docenteA);

        esperarErro(() -> servico.atribuir("admin@t", 100L, new AtribuicaoRequest(20)), HttpStatus.CONFLICT);
    }

    @Test
    void docenteInativoNaoRecebeChamado() {
        Usuario inativo = Fixtures.usuario(22, "Docente Inativo", "i@t", "INATIVO", "DOCENTE");
        when(usuarioRepo.findById(22)).thenReturn(Optional.of(inativo));
        chamado("ABERTO", null);

        esperarErro(() -> servico.atribuir("admin@t", 100L, new AtribuicaoRequest(22)), HttpStatus.UNPROCESSABLE_ENTITY);
    }

    @Test
    void soDocenteRecebeChamado() {
        chamado("ABERTO", null);

        esperarErro(() -> servico.atribuir("admin@t", 100L, new AtribuicaoRequest(10)), HttpStatus.UNPROCESSABLE_ENTITY);
        esperarErro(() -> servico.atribuir("admin@t", 100L, new AtribuicaoRequest(999)), HttpStatus.UNPROCESSABLE_ENTITY);
    }

    @Test
    void chamadoFinalizadoNaoEhAtribuido() {
        chamado("CANCELADO", docenteA);

        esperarErro(() -> servico.atribuir("admin@t", 100L, new AtribuicaoRequest(21)), HttpStatus.CONFLICT);
    }

    // ===== Abertura e atribuição automática =====

    private Categoria categoria(boolean exigeDisciplina) {
        Categoria c = new Categoria();
        ReflectionTestUtils.setField(c, "id", 5);
        c.setNome("Revisão de nota");
        c.setExigeDisciplina(exigeDisciplina);
        when(categoriaRepo.findById(5)).thenReturn(Optional.of(c));
        return c;
    }

    private void disciplina(Usuario docente) {
        Disciplina d = new Disciplina();
        ReflectionTestUtils.setField(d, "id", 7);
        d.setNome("Matemática");
        d.setDocente(docente);
        when(disciplinaRepo.findById(7)).thenReturn(Optional.of(d));
    }

    @Test
    void aberturaAtribuiAutomaticamenteAoDocenteDaDisciplina() {
        categoria(true);
        disciplina(docenteA);

        ChamadoResumo r = servico.abrir("aluno@t", new ChamadoRequest(5, 7, "Título", "Descrição"));

        assertThat(r.status()).isEqualTo("ATRIBUIDO");
        assertThat(r.responsavel()).isEqualTo("Docente A");
        verify(telegramNotificacoes).avisarAtribuicao(any(Chamado.class), eq(docenteA), eq(false));
        ArgumentCaptor<Interacao> captor = ArgumentCaptor.forClass(Interacao.class);
        verify(interacaoRepo, atLeastOnce()).save(captor.capture());
        assertThat(captor.getAllValues()).extracting(Interacao::getTipo).containsExactly("ABERTURA", "ATRIBUICAO");
    }

    @Test
    void semDocenteNaDisciplinaOChamadoFicaAberto() {
        categoria(true);
        disciplina(null);

        ChamadoResumo r = servico.abrir("aluno@t", new ChamadoRequest(5, 7, "Título", "Descrição"));

        assertThat(r.status()).isEqualTo("ABERTO");
        assertThat(r.responsavel()).isNull();
        verify(telegramNotificacoes, never()).avisarAtribuicao(any(), any(), eq(false));
    }

    @Test
    void docenteInativoNaDisciplinaNaoRecebeAutomaticamente() {
        categoria(true);
        disciplina(Fixtures.usuario(22, "Docente Inativo", "i@t", "INATIVO", "DOCENTE"));

        ChamadoResumo r = servico.abrir("aluno@t", new ChamadoRequest(5, 7, "Título", "Descrição"));

        assertThat(r.status()).isEqualTo("ABERTO");
    }

    @Test
    void categoriaQueExigeDisciplinaSemDisciplinaEhRecusada() {
        categoria(true);

        esperarErro(() -> servico.abrir("aluno@t", new ChamadoRequest(5, null, "Título", "Descrição")),
                HttpStatus.UNPROCESSABLE_ENTITY);
    }

    @Test
    void categoriaComSubcategoriasNaoAbreChamado() {
        categoria(false);
        when(categoriaRepo.existsByCategoriaPaiIdAndAtivoTrue(5)).thenReturn(true);

        esperarErro(() -> servico.abrir("aluno@t", new ChamadoRequest(5, null, "Título", "Descrição")),
                HttpStatus.UNPROCESSABLE_ENTITY);
    }
}