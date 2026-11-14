package com.example.demandas.integration.telegram;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.EnumMap;
import java.util.Map;

/** Gera o QR Code como SVG (preto sobre branco, com a margem de 4 módulos que as câmeras exigem). */
public final class QrCodeSvg {

    private QrCodeSvg() {}

    public static String svg(String conteudo) {
        BitMatrix matriz = matriz(conteudo);
        int largura = matriz.getWidth();
        int altura = matriz.getHeight();

        StringBuilder caminho = new StringBuilder();
        for (int y = 0; y < altura; y++) {
            int x = 0;
            while (x < largura) {
                if (matriz.get(x, y)) {
                    int inicio = x;
                    while (x < largura && matriz.get(x, y)) x++;
                    int tamanho = x - inicio;
                    caminho.append('M').append(inicio).append(' ').append(y)
                            .append('h').append(tamanho).append("v1h-").append(tamanho).append('z');
                } else {
                    x++;
                }
            }
        }

        return "<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 " + largura + " " + altura
                + "\" shape-rendering=\"crispEdges\">"
                + "<rect width=\"" + largura + "\" height=\"" + altura + "\" fill=\"#fff\"/>"
                + "<path d=\"" + caminho + "\" fill=\"#000\"/></svg>";
    }

    /** Imagem pronta para o atributo src de uma tag img. Imagens SVG não executam scripts. */
    public static String dataUrl(String conteudo) {
        String base64 = Base64.getEncoder().encodeToString(svg(conteudo).getBytes(StandardCharsets.UTF_8));
        return "data:image/svg+xml;base64," + base64;
    }

    private static BitMatrix matriz(String conteudo) {
        Map<EncodeHintType, Object> dicas = new EnumMap<>(EncodeHintType.class);
        dicas.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M);
        dicas.put(EncodeHintType.CHARACTER_SET, "UTF-8");
        dicas.put(EncodeHintType.MARGIN, 4);
        try {
            // Dimensões 0: a matriz sai no tamanho mínimo (1 ponto por módulo), já com a margem
            return new QRCodeWriter().encode(conteudo, BarcodeFormat.QR_CODE, 0, 0, dicas);
        } catch (WriterException e) {
            throw new IllegalStateException("Não foi possível gerar o QR Code.", e);
        }
    }
}