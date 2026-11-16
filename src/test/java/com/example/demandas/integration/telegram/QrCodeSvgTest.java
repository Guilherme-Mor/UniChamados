package com.example.demandas.integration.telegram;

import com.google.zxing.BinaryBitmap;
import com.google.zxing.LuminanceSource;
import com.google.zxing.common.HybridBinarizer;
import com.google.zxing.qrcode.QRCodeReader;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

class QrCodeSvgTest {

    private static final Pattern CAIXA = Pattern.compile("viewBox=\"0 0 (\\d+) (\\d+)\"");
    private static final Pattern RETANGULO = Pattern.compile("M(\\d+) (\\d+)h(\\d+)v1h-\\d+z");

    @Test
    void oQrCodeLidoDevolveExatamenteOLink() throws Exception {
        String link = "https://t.me/unichamados_dev_bot?start=ABCDEFGHJK";

        assertThat(decodificar(QrCodeSvg.svg(link))).isEqualTo(link);
    }

    @Test
    void funcionaTambemComOutroConteudo() throws Exception {
        String link = "https://t.me/unichamados_bot?start=Z9Y8X7W6V5";

        assertThat(decodificar(QrCodeSvg.svg(link))).isEqualTo(link);
    }

    @Test
    void dataUrlContemOMesmoSvg() {
        String link = "https://t.me/bot?start=ABCDEFGHJK";

        String dataUrl = QrCodeSvg.dataUrl(link);

        assertThat(dataUrl).startsWith("data:image/svg+xml;base64,");
        String svg = new String(Base64.getDecoder().decode(dataUrl.substring(dataUrl.indexOf(',') + 1)),
                StandardCharsets.UTF_8);
        assertThat(svg).isEqualTo(QrCodeSvg.svg(link));
    }

    @Test
    void oSvgNaoTemScriptsNemEventos() {
        String svg = QrCodeSvg.svg("https://t.me/bot?start=ABCDEFGHJK").toLowerCase();

        assertThat(svg).doesNotContain("<script").doesNotContain("onload").doesNotContain("href");
    }

    @Test
    void conteudosDiferentesGeramImagensDiferentes() {
        assertThat(QrCodeSvg.svg("https://t.me/bot?start=AAAAAAAAAA"))
                .isNotEqualTo(QrCodeSvg.svg("https://t.me/bot?start=BBBBBBBBBB"));
    }

    /** Reconstrói a imagem a partir do SVG (cada módulo vira 8x8 pontos) e a lê como uma câmera leria. */
    private static String decodificar(String svg) throws Exception {
        Matcher caixa = CAIXA.matcher(svg);
        assertThat(caixa.find()).isTrue();
        int largura = Integer.parseInt(caixa.group(1));
        int altura = Integer.parseInt(caixa.group(2));

        boolean[][] preto = new boolean[altura][largura];
        Matcher retangulo = RETANGULO.matcher(svg);
        while (retangulo.find()) {
            int x = Integer.parseInt(retangulo.group(1));
            int y = Integer.parseInt(retangulo.group(2));
            int tamanho = Integer.parseInt(retangulo.group(3));
            for (int i = 0; i < tamanho; i++) preto[y][x + i] = true;
        }

        int escala = 8;
        int w = largura * escala;
        int h = altura * escala;
        byte[] pontos = new byte[w * h];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                pontos[y * w + x] = preto[y / escala][x / escala] ? (byte) 0 : (byte) 255;
            }
        }

        LuminanceSource fonte = new LuminanceSource(w, h) {
            @Override
            public byte[] getRow(int y, byte[] linha) {
                if (linha == null || linha.length < w) linha = new byte[w];
                System.arraycopy(pontos, y * w, linha, 0, w);
                return linha;
            }

            @Override
            public byte[] getMatrix() {
                return pontos;
            }
        };
        return new QRCodeReader().decode(new BinaryBitmap(new HybridBinarizer(fonte))).getText();
    }
}