package com.example.demandas.integration.telegram;

import com.example.demandas.Fixtures;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TelegramTecladosTest {

    @Test
    void chamadoTemOsTresBotoes() {
        assertThat(Fixtures.callbacks(TelegramTeclados.chamado(42L)))
                .containsExactlyInAnyOrder("ver:42", "resp:42", "st:42");
    }

    @Test
    void osDadosDosBotoesCabemNoLimiteDoTelegram() {
        long maior = Long.MAX_VALUE;
        List<String> todos = new java.util.ArrayList<>();
        todos.addAll(Fixtures.callbacks(TelegramTeclados.chamado(maior)));
        todos.addAll(Fixtures.callbacks(TelegramTeclados.status(maior,
                List.of("EM_ATENDIMENTO", "AGUARDANDO_DISCENTE", "RESOLVIDO"))));
        todos.addAll(Fixtures.callbacks(TelegramTeclados.lista(List.of(new TelegramTeclados.Item(maior, "x")))));

        for (String dado : todos) {
            assertThat(dado.getBytes(StandardCharsets.UTF_8).length).isLessThanOrEqualTo(64);
        }
    }

    @Test
    void statusOferecaApenasAsOpcoesPedidasEOVoltar() {
        List<String> dados = Fixtures.callbacks(TelegramTeclados.status(7L, List.of("RESOLVIDO")));

        assertThat(dados).containsExactly("s:7:RESOLVIDO", "ver:7");
    }

    @Test
    void listaTemUmBotaoPorChamado() {
        List<String> dados = Fixtures.callbacks(TelegramTeclados.lista(
                List.of(new TelegramTeclados.Item(1L, "A"), new TelegramTeclados.Item(2L, "B"))));

        assertThat(dados).containsExactly("ver:1", "ver:2");
    }

    @Test
    void respostaForcadaPedeUmReply() {
        assertThat(TelegramTeclados.respostaForcada()).containsEntry("force_reply", true);
    }
}