package com.example.demandas.integration.telegram;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TelegramTextoTest {

    @Test
    void escaparNeutralizaHtml() {
        assertThat(TelegramTexto.escapar("<b>a & b</b>")).isEqualTo("&lt;b&gt;a &amp; b&lt;/b&gt;");
        assertThat(TelegramTexto.escapar(null)).isEmpty();
    }

    @Test
    void truncarRespeitaOLimite() {
        assertThat(TelegramTexto.truncar("abc", 10)).isEqualTo("abc");
        assertThat(TelegramTexto.truncar("abcdefghij", 5)).hasSize(5).endsWith("…");
        assertThat(TelegramTexto.truncar(null, 5)).isEmpty();
    }

    @Test
    void escaparCortandoNaoCortaQuandoCabe() {
        assertThat(TelegramTexto.escaparCortando("a<b", 50)).isEqualTo("a&lt;b");
        assertThat(TelegramTexto.escaparCortando(null, 10)).isEmpty();
    }

    @Test
    void escaparCortandoNaoPartePorEntidade() {
        String r = TelegramTexto.escaparCortando("&".repeat(100), 20);

        assertThat(r.length()).isLessThanOrEqualTo(20);
        assertThat(r).endsWith("…");
        // sem a reticência, só podem sobrar entidades completas
        assertThat(r.substring(0, r.length() - 1).replace("&amp;", "")).isEmpty();
    }

    @Test
    void escaparCortandoNaoPartePares() {
        String emojis = "\uD83D\uDE00".repeat(50);

        String r = TelegramTexto.escaparCortando(emojis, 11);

        String semReticencias = r.substring(0, r.length() - 1);
        assertThat(r.length()).isLessThanOrEqualTo(11);
        assertThat(semReticencias.codePointCount(0, semReticencias.length()) * 2).isEqualTo(semReticencias.length());
    }
}