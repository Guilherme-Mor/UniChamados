package com.example.demandas.integration.telegram;

import com.example.demandas.entity.TelegramLiberacao;
import com.example.demandas.repository.TelegramCodigoVinculoRepository;
import com.example.demandas.repository.TelegramLiberacaoRepository;
import com.example.demandas.repository.TelegramTentativaInvalidaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TelegramLimitesTest {

    private static final LocalDateTime AGORA = LocalDateTime.of(2026, 11, 5, 12, 0);

    @Mock TelegramCodigoVinculoRepository codigoRepo;
    @Mock TelegramTentativaInvalidaRepository tentativaRepo;
    @Mock TelegramLiberacaoRepository liberacaoRepo;

    private TelegramLimites limites;

    @BeforeEach
    void preparar() {
        TelegramProperties props = new TelegramProperties(true, "t", "b", "s", "polling", "", 10, 10, 5, 5);
        limites = new TelegramLimites(props, codigoRepo, tentativaRepo, liberacaoRepo);
        when(codigoRepo.countByUsuarioIdAndCriadoEmAfter(eq(1), any())).thenReturn(3L);
    }

    private void liberadoEm(LocalDateTime quando) {
        TelegramLiberacao l = new TelegramLiberacao();
        ReflectionTestUtils.setField(l, "criadoEm", quando);
        when(liberacaoRepo.findFirstByTipoAndUsuarioIdOrderByCriadoEmDesc(TelegramLiberacao.DOCENTE, 1))
                .thenReturn(Optional.of(l));
    }

    private LocalDateTime inicioDaContagem() {
        ArgumentCaptor<LocalDateTime> captor = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(codigoRepo).countByUsuarioIdAndCriadoEmAfter(eq(1), captor.capture());
        return captor.getValue();
    }

    @Test
    void semLiberacaoContaAUltimaHora() {
        assertThat(limites.codigosNaJanela(1, AGORA)).isEqualTo(3L);

        assertThat(inicioDaContagem()).isEqualTo(AGORA.minusHours(1));
    }

    @Test
    void liberacaoRecenteReiniciaAContagem() {
        LocalDateTime liberacao = AGORA.minusMinutes(10);
        liberadoEm(liberacao);

        limites.codigosNaJanela(1, AGORA);

        assertThat(inicioDaContagem()).isEqualTo(liberacao);
    }

    @Test
    void liberacaoAntigaNaoAlteraAJanela() {
        liberadoEm(AGORA.minusHours(2));

        limites.codigosNaJanela(1, AGORA);

        assertThat(inicioDaContagem()).isEqualTo(AGORA.minusHours(1));
    }

    @Test
    void chatBloqueadoAoAtingirOLimite() {
        when(tentativaRepo.countByChatIdAndCriadoEmAfter(eq(100L), any())).thenReturn(5L);
        when(tentativaRepo.countByChatIdAndCriadoEmAfter(eq(200L), any())).thenReturn(4L);

        assertThat(limites.chatBloqueado(100L, AGORA)).isTrue();
        assertThat(limites.chatBloqueado(200L, AGORA)).isFalse();
    }
}