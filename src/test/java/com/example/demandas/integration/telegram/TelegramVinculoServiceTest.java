package com.example.demandas.integration.telegram;

import com.example.demandas.Fixtures;
import com.example.demandas.dto.TelegramCodigoResponse;
import com.example.demandas.dto.TelegramEstadoResponse;
import com.example.demandas.entity.TelegramCodigoVinculo;
import com.example.demandas.entity.TelegramVinculo;
import com.example.demandas.entity.Usuario;
import com.example.demandas.exception.RegraNegocioException;
import com.example.demandas.repository.TelegramCodigoVinculoRepository;
import com.example.demandas.repository.TelegramVinculoRepository;
import com.example.demandas.repository.UsuarioRepository;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TelegramVinculoServiceTest {

    private static final String EMAIL = "d1@teste.local";
    private static final String CODIGO = "ABCDEFGHJK";
    private static final TelegramTipos.TgUser MARIA = new TelegramTipos.TgUser(555L, false, "Maria", "Silva", "maria");

    @Mock TelegramVinculoRepository vinculoRepo;
    @Mock TelegramCodigoVinculoRepository codigoRepo;
    @Mock UsuarioRepository usuarioRepo;
    @Mock TelegramLimites limites;

    private TelegramVinculoService servico;
    private Usuario docente;

    @BeforeEach
    void preparar() {
        servico = criar(true, "unichamados_dev_bot");
        docente = Fixtures.usuario(1, "Docente Um", EMAIL, "ATIVO", "DOCENTE");
        when(usuarioRepo.findByEmailIgnoreCase(EMAIL)).thenReturn(Optional.of(docente));
    }

    private TelegramVinculoService criar(boolean ativo, String bot) {
        TelegramProperties props = new TelegramProperties(ativo, "token", bot, "segredo", "polling", "", 10, 10, 5, 5);
        return new TelegramVinculoService(props, vinculoRepo, codigoRepo, usuarioRepo, limites);
    }

    private TelegramVinculo vinculo(String status, long chatId, int id) {
        TelegramVinculo v = new TelegramVinculo();
        ReflectionTestUtils.setField(v, "id", id);
        v.setUsuario(docente);
        v.setChatId(chatId);
        v.setStatus(status);
        return v;
    }

    private TelegramCodigoVinculo codigo(Usuario dono, LocalDateTime expira, LocalDateTime usado) {
        TelegramCodigoVinculo c = new TelegramCodigoVinculo();
        c.setUsuario(dono);
        c.setCodigoHash(TelegramVinculoService.hash(CODIGO));
        c.setExpiraEm(expira);
        c.setUsadoEm(usado);
        when(codigoRepo.findByCodigoHash(TelegramVinculoService.hash(CODIGO))).thenReturn(Optional.of(c));
        return c;
    }

    private TelegramCodigoVinculo codigoValido() {
        return codigo(docente, LocalDateTime.now().plusMinutes(5), null);
    }

    private void vinculoDoDocente(TelegramVinculo v) {
        when(vinculoRepo.findByUsuarioIdAndStatusIn(eq(1), anyCollection())).thenReturn(Optional.of(v));
    }

    private void vinculoDoChat(TelegramVinculo v) {
        when(vinculoRepo.findByChatIdAndStatusIn(eq(100L), anyCollection())).thenReturn(Optional.of(v));
    }

    private static void esperarErro(ThrowingCallable acao, HttpStatus status) {
        assertThatThrownBy(acao)
                .isInstanceOf(RegraNegocioException.class)
                .satisfies(e -> assertThat(((RegraNegocioException) e).getStatus()).isEqualTo(status));
    }

    // ===== Hash =====

    @Test
    void hashEhSha256EmHexadecimal() {
        // vetor de teste conhecido do SHA-256
        assertThat(TelegramVinculoService.hash("abc"))
                .isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
    }

    // ===== Geração do código =====

    @Test
    void geraCodigoGuardandoSoOHash() {
        TelegramCodigoResponse r = servico.gerarCodigo(EMAIL);

        assertThat(r.codigo()).matches("[ABCDEFGHJKMNPQRSTUVWXYZ23456789]{10}");
        assertThat(r.link()).isEqualTo("https://t.me/unichamados_dev_bot?start=" + r.codigo());
        assertThat(r.qrCode()).startsWith("data:image/svg+xml;base64,");
        assertThat(r.validadeSegundos()).isEqualTo(600);

        ArgumentCaptor<TelegramCodigoVinculo> captor = ArgumentCaptor.forClass(TelegramCodigoVinculo.class);
        verify(codigoRepo).save(captor.capture());
        TelegramCodigoVinculo salvo = captor.getValue();
        assertThat(salvo.getCodigoHash()).isEqualTo(TelegramVinculoService.hash(r.codigo())).isNotEqualTo(r.codigo());
        assertThat(salvo.getUsuario()).isSameAs(docente);
        assertThat(salvo.getExpiraEm()).isBetween(LocalDateTime.now().plusMinutes(9), LocalDateTime.now().plusMinutes(11));
    }

    @Test
    void codigosGeradosSaoDiferentes() {
        assertThat(servico.gerarCodigo(EMAIL).codigo()).isNotEqualTo(servico.gerarCodigo(EMAIL).codigo());
    }

    @Test
    void gerarNovoCodigoInvalidaOsAnteriores() {
        TelegramCodigoVinculo antigo = codigoValido();
        when(codigoRepo.findByUsuarioIdAndUsadoEmIsNullAndExpiraEmAfter(eq(1), any()))
                .thenReturn(java.util.List.of(antigo));

        servico.gerarCodigo(EMAIL);

        assertThat(antigo.getExpiraEm()).isBeforeOrEqualTo(LocalDateTime.now());
    }

    @Test
    void limiteDeCodigosPorHoraBloqueia() {
        when(limites.codigosNaJanela(eq(1), any())).thenReturn(5L);

        esperarErro(() -> servico.gerarCodigo(EMAIL), HttpStatus.TOO_MANY_REQUESTS);
        verify(codigoRepo, never()).save(any());
    }

    @Test
    void naoGeraCodigoSeJaHaTelegramVinculado() {
        vinculoDoDocente(vinculo(TelegramVinculo.ATIVO, 100L, 7));

        esperarErro(() -> servico.gerarCodigo(EMAIL), HttpStatus.CONFLICT);
        verify(codigoRepo, never()).save(any());
    }

    @Test
    void pedidoPendenteEhSubstituidoAoGerarOutroCodigo() {
        TelegramVinculo pendente = vinculo(TelegramVinculo.PENDENTE, 100L, 7);
        vinculoDoDocente(pendente);

        servico.gerarCodigo(EMAIL);

        assertThat(pendente.getStatus()).isEqualTo(TelegramVinculo.REVOGADO);
        assertThat(pendente.getMotivoRevogacao()).isEqualTo("SUBSTITUIDO");
    }

    @Test
    void integracaoDesligadaNaoGeraCodigo() {
        esperarErro(() -> criar(false, "bot").gerarCodigo(EMAIL), HttpStatus.SERVICE_UNAVAILABLE);
        verifyNoInteractions(codigoRepo);
    }

    @Test
    void semUsernameDoBotNaoGeraCodigo() {
        esperarErro(() -> criar(true, "  ").gerarCodigo(EMAIL), HttpStatus.SERVICE_UNAVAILABLE);
    }

    @Test
    void soDocenteAtivoGeraCodigo() {
        Usuario aluno = Fixtures.usuario(2, "Aluno", "aluno@t", "ATIVO", "DISCENTE");
        Usuario inativo = Fixtures.usuario(3, "Inativo", "inativo@t", "INATIVO", "DOCENTE");
        when(usuarioRepo.findByEmailIgnoreCase("aluno@t")).thenReturn(Optional.of(aluno));
        when(usuarioRepo.findByEmailIgnoreCase("inativo@t")).thenReturn(Optional.of(inativo));

        esperarErro(() -> servico.gerarCodigo("aluno@t"), HttpStatus.FORBIDDEN);
        esperarErro(() -> servico.gerarCodigo("inativo@t"), HttpStatus.FORBIDDEN);
    }

    // ===== /start CÓDIGO =====

    @Test
    void codigoValidoCriaPedidoPendenteEConsomeOCodigo() {
        TelegramCodigoVinculo c = codigoValido();

        String resposta = servico.iniciarVinculo(100L, MARIA, "abcde-fghjk");   // minúsculas e hífen são aceitos

        assertThat(resposta).contains("Pedido recebido");
        ArgumentCaptor<TelegramVinculo> captor = ArgumentCaptor.forClass(TelegramVinculo.class);
        verify(vinculoRepo).saveAndFlush(captor.capture());
        TelegramVinculo novo = captor.getValue();
        assertThat(novo.getStatus()).isEqualTo(TelegramVinculo.PENDENTE);
        assertThat(novo.getChatId()).isEqualTo(100L);
        assertThat(novo.getUsername()).isEqualTo("maria");
        assertThat(novo.getNomeExibicao()).isEqualTo("Maria Silva");
        assertThat(novo.getUsuario()).isSameAs(docente);
        assertThat(novo.getExpiraEm()).isAfter(LocalDateTime.now());
        assertThat(c.getUsadoEm()).isNotNull();
        verify(limites, never()).registrarFalha(any(), any());
    }

    @Test
    void codigoDeFormatoInvalidoContaComoTentativaErrada() {
        String resposta = servico.iniciarVinculo(100L, MARIA, "abc");

        assertThat(resposta).startsWith("Código inválido ou expirado");
        verify(limites).registrarFalha(eq(100L), any());
        verify(vinculoRepo, never()).saveAndFlush(any());
    }

    @Test
    void codigoInexistenteContaComoTentativaErrada() {
        String resposta = servico.iniciarVinculo(100L, MARIA, "ZZZZZZZZZZ");

        assertThat(resposta).startsWith("Código inválido ou expirado");
        verify(limites).registrarFalha(eq(100L), any());
    }

    @Test
    void codigoExpiradoOuJaUsadoEhRecusadoComAMesmaMensagem() {
        codigo(docente, LocalDateTime.now().minusMinutes(1), null);
        String expirado = servico.iniciarVinculo(100L, MARIA, CODIGO);

        codigo(docente, LocalDateTime.now().plusMinutes(5), LocalDateTime.now().minusMinutes(1));
        String usado = servico.iniciarVinculo(100L, MARIA, CODIGO);

        assertThat(expirado).startsWith("Código inválido ou expirado").isEqualTo(usado);
        verify(vinculoRepo, never()).saveAndFlush(any());
    }

    @Test
    void codigoDeDocenteInativoEhRecusado() {
        Usuario inativo = Fixtures.usuario(1, "Docente", EMAIL, "INATIVO", "DOCENTE");
        codigo(inativo, LocalDateTime.now().plusMinutes(5), null);

        assertThat(servico.iniciarVinculo(100L, MARIA, CODIGO)).startsWith("Código inválido ou expirado");
        verify(vinculoRepo, never()).saveAndFlush(any());
    }

    @Test
    void chatBloqueadoNemChegaAAvaliarOCodigo() {
        when(limites.chatBloqueado(eq(100L), any())).thenReturn(true);

        String resposta = servico.iniciarVinculo(100L, MARIA, CODIGO);

        assertThat(resposta).contains("Muitas tentativas");
        verifyNoInteractions(codigoRepo);
        verify(limites, never()).registrarFalha(any(), any());
    }

    @Test
    void docenteJaVinculadoNaoConsomeOCodigo() {
        TelegramCodigoVinculo c = codigoValido();
        vinculoDoDocente(vinculo(TelegramVinculo.ATIVO, 999L, 7));

        String resposta = servico.iniciarVinculo(100L, MARIA, CODIGO);

        assertThat(resposta).contains("já tem um Telegram vinculado");
        assertThat(c.getUsadoEm()).isNull();
        verify(vinculoRepo, never()).saveAndFlush(any());
    }

    @Test
    void chatJaVinculadoAOutraContaEhRecusado() {
        TelegramCodigoVinculo c = codigoValido();
        vinculoDoChat(vinculo(TelegramVinculo.ATIVO, 100L, 8));

        String resposta = servico.iniciarVinculo(100L, MARIA, CODIGO);

        assertThat(resposta).contains("já está vinculado a uma conta");
        assertThat(c.getUsadoEm()).isNull();
    }

    @Test
    void pedidoPendenteAnteriorEhRevogadoAntesDoNovoInsert() {
        codigoValido();
        TelegramVinculo pendente = vinculo(TelegramVinculo.PENDENTE, 100L, 9);
        vinculoDoDocente(pendente);
        vinculoDoChat(pendente);

        servico.iniciarVinculo(100L, MARIA, CODIGO);

        assertThat(pendente.getStatus()).isEqualTo(TelegramVinculo.REVOGADO);
        assertThat(pendente.getMotivoRevogacao()).isEqualTo("SUBSTITUIDO");
        // o UPDATE tem de sair antes do INSERT, por causa dos índices únicos parciais
        InOrder ordem = inOrder(vinculoRepo);
        ordem.verify(vinculoRepo).flush();
        ordem.verify(vinculoRepo).saveAndFlush(any(TelegramVinculo.class));
    }

    // ===== Confirmar, recusar, desvincular =====

    @Test
    void confirmarTornaOVinculoAtivo() {
        TelegramVinculo pendente = vinculo(TelegramVinculo.PENDENTE, 100L, 9);
        pendente.setExpiraEm(LocalDateTime.now().plusMinutes(5));
        vinculoDoDocente(pendente);

        AvisoTelegram aviso = servico.confirmar(EMAIL);

        assertThat(pendente.getStatus()).isEqualTo(TelegramVinculo.ATIVO);
        assertThat(pendente.getConfirmadoEm()).isNotNull();
        assertThat(pendente.getExpiraEm()).isNull();
        assertThat(aviso.chatId()).isEqualTo(100L);
        assertThat(aviso.texto()).contains("Vínculo confirmado");
    }

    @Test
    void confirmarSemPedidoPendenteEhConflito() {
        esperarErro(() -> servico.confirmar(EMAIL), HttpStatus.CONFLICT);
    }

    @Test
    void recusarRevogaOPedido() {
        TelegramVinculo pendente = vinculo(TelegramVinculo.PENDENTE, 100L, 9);
        vinculoDoDocente(pendente);

        AvisoTelegram aviso = servico.recusar(EMAIL);

        assertThat(pendente.getStatus()).isEqualTo(TelegramVinculo.REVOGADO);
        assertThat(pendente.getMotivoRevogacao()).isEqualTo("RECUSADO");
        assertThat(aviso.chatId()).isEqualTo(100L);
    }

    @Test
    void desvincularRevogaOVinculoAtivo() {
        TelegramVinculo ativo = vinculo(TelegramVinculo.ATIVO, 100L, 9);
        vinculoDoDocente(ativo);

        servico.desvincular(EMAIL);

        assertThat(ativo.getStatus()).isEqualTo(TelegramVinculo.REVOGADO);
        assertThat(ativo.getMotivoRevogacao()).isEqualTo("DESVINCULADO");
    }

    @Test
    void desvincularSemVinculoEhConflito() {
        esperarErro(() -> servico.desvincular(EMAIL), HttpStatus.CONFLICT);
    }

    @Test
    void desvincularPeloTelegram() {
        assertThat(servico.desvincularPorChat(100L)).contains("não está vinculado");

        TelegramVinculo ativo = vinculo(TelegramVinculo.ATIVO, 100L, 9);
        vinculoDoChat(ativo);
        assertThat(servico.desvincularPorChat(100L)).contains("desvinculado");
        assertThat(ativo.getStatus()).isEqualTo(TelegramVinculo.REVOGADO);
        assertThat(ativo.getMotivoRevogacao()).isEqualTo("DESVINCULADO_TELEGRAM");
    }

    // ===== Estado =====

    @Test
    void estadoComIntegracaoDesligada() {
        TelegramEstadoResponse e = criar(false, "bot").estado(EMAIL);

        assertThat(e.habilitado()).isFalse();
        assertThat(e.estado()).isEqualTo("NENHUM");
    }

    @Test
    void estadoDoPedidoPendenteTrazOTempoRestante() {
        TelegramVinculo pendente = vinculo(TelegramVinculo.PENDENTE, 100L, 9);
        pendente.setExpiraEm(LocalDateTime.now().plusMinutes(5));
        vinculoDoDocente(pendente);

        TelegramEstadoResponse e = servico.estado(EMAIL);

        assertThat(e.estado()).isEqualTo("PENDENTE");
        assertThat(e.restanteSegundos()).isBetween(280L, 300L);
    }
}