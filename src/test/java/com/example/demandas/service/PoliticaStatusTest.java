package com.example.demandas.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PoliticaStatusTest {

    private static final List<String> TODOS = List.of("ABERTO", "ATRIBUIDO", "EM_ATENDIMENTO",
            "AGUARDANDO_DISCENTE", "RESOLVIDO", "ENCERRADO", "CANCELADO");

    @Test
    void abertoSoPodeSerCancelado() {
        assertThat(PoliticaStatus.proximos("ABERTO")).containsExactly("CANCELADO");
    }

    @Test
    void atribuido() {
        assertThat(PoliticaStatus.proximos("ATRIBUIDO")).containsExactlyInAnyOrder("EM_ATENDIMENTO", "CANCELADO");
    }

    @Test
    void emAtendimento() {
        assertThat(PoliticaStatus.proximos("EM_ATENDIMENTO"))
                .containsExactlyInAnyOrder("AGUARDANDO_DISCENTE", "RESOLVIDO", "CANCELADO");
    }

    @Test
    void aguardandoDiscente() {
        assertThat(PoliticaStatus.proximos("AGUARDANDO_DISCENTE"))
                .containsExactlyInAnyOrder("EM_ATENDIMENTO", "RESOLVIDO", "CANCELADO");
    }

    @Test
    void resolvidoPodeEncerrarOuReabrir() {
        assertThat(PoliticaStatus.proximos("RESOLVIDO")).containsExactlyInAnyOrder("ENCERRADO", "EM_ATENDIMENTO");
    }

    @Test
    void nenhumStatusVoltaParaAberto() {
        for (String s : TODOS) {
            assertThat(PoliticaStatus.proximos(s)).doesNotContain("ABERTO");
        }
    }

    @Test
    void naoSePulaEtapas() {
        assertThat(PoliticaStatus.proximos("ATRIBUIDO")).doesNotContain("RESOLVIDO", "ENCERRADO");
        assertThat(PoliticaStatus.proximos("EM_ATENDIMENTO")).doesNotContain("ENCERRADO");
    }

    @ParameterizedTest
    @ValueSource(strings = {"ENCERRADO", "CANCELADO"})
    void statusFinaisNaoTemSaida(String status) {
        assertThat(PoliticaStatus.proximos(status)).isEmpty();
        assertThat(PoliticaStatus.FINAIS).contains(status);
    }

    @Test
    void statusDesconhecidoNaoTemSaida() {
        assertThat(PoliticaStatus.proximos("INEXISTENTE")).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"ABERTO", "ATRIBUIDO", "EM_ATENDIMENTO", "AGUARDANDO_DISCENTE"})
    void solicitantePodeCancelarAteResolver(String status) {
        assertThat(PoliticaStatus.cancelavelPeloSolicitante(status)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"RESOLVIDO", "ENCERRADO", "CANCELADO"})
    void solicitanteNaoCancelaDepoisDeResolvido(String status) {
        assertThat(PoliticaStatus.cancelavelPeloSolicitante(status)).isFalse();
    }
}