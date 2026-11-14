package com.example.demandas.integration.telegram;

import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;

/** Modo de desenvolvimento: consulta o Telegram em segundo plano, sem precisar de endereço público. */
@Component
public class TelegramPolling {

    private static final Logger log = LoggerFactory.getLogger(TelegramPolling.class);

    private final TelegramClient telegram;
    private final TelegramUpdateHandler handler;
    private volatile boolean executando;
    private Thread thread;

    public TelegramPolling(TelegramClient telegram, TelegramUpdateHandler handler) {
        this.telegram = telegram;
        this.handler = handler;
    }

    public synchronized void iniciar() {
        if (executando) return;
        executando = true;
        thread = new Thread(this::laco, "telegram-polling");
        thread.setDaemon(true);
        thread.start();
    }

    @PreDestroy
    public synchronized void parar() {
        executando = false;
        if (thread != null) thread.interrupt();
    }

    private void laco() {
        try {
            telegram.removerWebhook();   // com webhook registrado, a consulta não funciona
        } catch (TelegramException e) {
            log.error("Polling: não foi possível remover o webhook ({}).", e.getMessage());
        }

        long offset = 0;
        long espera = 1000;
        while (executando) {
            try {
                List<TelegramTipos.Update> eventos = telegram.obterAtualizacoes(offset, 25);
                espera = 1000;
                for (TelegramTipos.Update evento : eventos) {
                    try {
                        handler.tratar(evento);
                    } catch (RuntimeException e) {
                        log.error("Polling: erro ao tratar o evento {}.", evento.updateId(), e);
                    }
                    // Avança mesmo com erro, para um evento problemático não travar a fila.
                    if (evento.updateId() != null) offset = Math.max(offset, evento.updateId() + 1);
                }
            } catch (TelegramException e) {
                log.warn("Polling: {} (nova tentativa em {} s).", e.getMessage(), espera / 1000);
                if (!dormir(espera)) return;
                espera = Math.min(espera * 2, 30_000);
            }
        }
    }

    private boolean dormir(long milissegundos) {
        try {
            Thread.sleep(milissegundos);
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            executando = false;
            return false;
        }
    }
}