package com.example.demandas.integration.telegram;

/** Utilitários para montar textos seguros (formato HTML do Telegram). */
public final class TelegramTexto {

    /** Limite de caracteres de uma mensagem no Telegram. */
    public static final int LIMITE = 4096;

    private TelegramTexto() {}

    /** Escapa conteúdo vindo de usuários para não ser interpretado como HTML. */
    public static String escapar(String texto) {
        if (texto == null) return "";
        return texto.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    /** Corta o texto no limite informado, acrescentando reticências. */
    public static String truncar(String texto, int max) {
        if (texto == null) return "";
        if (texto.length() <= max) return texto;
        return texto.substring(0, Math.max(0, max - 1)) + "…";
    }

    /** Escapa e corta no limite de caracteres já escapados, sem partir uma entidade (como &amp;). */
    public static String escaparCortando(String texto, int maxEscapado) {
        String completo = escapar(texto);
        if (completo.length() <= maxEscapado) return completo;

        StringBuilder sb = new StringBuilder();
        int i = 0;
        while (i < texto.length()) {
            int ponto = texto.codePointAt(i);
            String parte = switch (ponto) {
                case '&' -> "&amp;";
                case '<' -> "&lt;";
                case '>' -> "&gt;";
                default -> new String(Character.toChars(ponto));
            };
            if (sb.length() + parte.length() > maxEscapado - 1) break;
            sb.append(parte);
            i += Character.charCount(ponto);
        }
        return sb.append('…').toString();
    }
}