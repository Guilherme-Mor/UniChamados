package com.example.demandas.integration.telegram;

/** Erro na comunicação com o Telegram. A mensagem nunca contém o token. */
public class TelegramException extends RuntimeException {

    private final Integer codigo;
    private final Integer retryAfter;

    public TelegramException(String mensagem, Integer codigo, Integer retryAfter) {
        super(mensagem);
        this.codigo = codigo;
        this.retryAfter = retryAfter;
    }

    public Integer getCodigo() { return codigo; }
    public Integer getRetryAfter() { return retryAfter; }

    /** O usuário bloqueou o bot ou removeu a conversa. */
    public boolean botBloqueado() {
        return codigo != null && codigo == 403;
    }
}