package com.example.demandas.integration.telegram;

import com.example.demandas.repository.TelegramTentativaInvalidaRepository;
import com.example.demandas.repository.TelegramUpdateProcessadoRepository;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.locks.ReentrantLock;

/** Esvazia a fila de notificações: logo após um commit e, por segurança, a cada 20 segundos. */
@Component
public class TelegramDespachante {

    private static final Logger log = LoggerFactory.getLogger(TelegramDespachante.class);

    private final TelegramProperties props;
    private final NotificacaoProcessador processador;
    private final TelegramUpdateProcessadoRepository processados;
    private final TelegramTentativaInvalidaRepository tentativas;

    private final ReentrantLock trava = new ReentrantLock();
    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "telegram-despacho");
        t.setDaemon(true);
        return t;
    });

    public TelegramDespachante(TelegramProperties props,
                               NotificacaoProcessador processador,
                               TelegramUpdateProcessadoRepository processados,
                               TelegramTentativaInvalidaRepository tentativas) {
        this.props = props;
        this.processador = processador;
        this.processados = processados;
        this.tentativas = tentativas;
    }

    /** Aciona o envio em segundo plano, sem atrasar a resposta da requisição que gerou o aviso. */
    public void acionar() {
        if (!props.ativo()) return;
        executor.execute(this::processar);
    }

    @Scheduled(fixedDelay = 20000L, initialDelay = 20000L)
    public void agendado() {
        processar();
    }

    /** Limpeza: eventos do Telegram com mais de 7 dias e tentativas inválidas com mais de 1 dia. */
    @Scheduled(fixedDelay = 3600000L, initialDelay = 60000L)
    public void manutencao() {
        try {
            processados.apagarAnteriores(LocalDateTime.now().minusDays(7));
            tentativas.apagarAnteriores(LocalDateTime.now().minusDays(1));
        } catch (RuntimeException e) {
            log.warn("Manutenção do Telegram falhou: {}", e.getMessage());
        }
    }

    void processar() {
        if (!props.ativo()) return;
        if (!trava.tryLock()) return;   // já há um envio em andamento
        try {
            int limite = 50;
            while (limite-- > 0 && processador.processarProxima()) {
                // segue enquanto houver notificação vencida
            }
        } catch (RuntimeException e) {
            log.error("Erro ao despachar notificações do Telegram.", e);
        } finally {
            trava.unlock();
        }
    }

    @PreDestroy
    public void parar() {
        executor.shutdown();
    }
}