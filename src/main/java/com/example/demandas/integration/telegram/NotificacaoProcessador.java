package com.example.demandas.integration.telegram;

import com.example.demandas.entity.Notificacao;
import com.example.demandas.entity.TelegramMensagem;
import com.example.demandas.entity.TelegramVinculo;
import com.example.demandas.entity.Usuario;
import com.example.demandas.repository.NotificacaoRepository;
import com.example.demandas.repository.TelegramMensagemRepository;
import com.example.demandas.repository.TelegramVinculoRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class NotificacaoProcessador {

    private static final Logger log = LoggerFactory.getLogger(NotificacaoProcessador.class);
    private static final List<String> SO_ATIVO = List.of(TelegramVinculo.ATIVO);
    private static final int MAX_TENTATIVAS = 5;

    private final NotificacaoRepository notificacaoRepo;
    private final TelegramVinculoRepository vinculoRepo;
    private final TelegramMensagemRepository mensagemRepo;
    private final TelegramClient telegram;

    public NotificacaoProcessador(NotificacaoRepository notificacaoRepo,
                                  TelegramVinculoRepository vinculoRepo,
                                  TelegramMensagemRepository mensagemRepo,
                                  TelegramClient telegram) {
        this.notificacaoRepo = notificacaoRepo;
        this.vinculoRepo = vinculoRepo;
        this.mensagemRepo = mensagemRepo;
        this.telegram = telegram;
    }

    /** Envia a próxima notificação vencida. Devolve false quando não há nenhuma. */
    @Transactional
    public boolean processarProxima() {
        LocalDateTime agora = LocalDateTime.now();
        Optional<Notificacao> reservada = notificacaoRepo.reservarProxima(agora);
        if (reservada.isEmpty()) return false;

        Notificacao n = reservada.get();
        Usuario docente = n.getUsuario();
        Optional<TelegramVinculo> vinculo = vinculoRepo.findByUsuarioIdAndStatusIn(docente.getId(), SO_ATIVO);

        if (!"ATIVO".equals(docente.getStatus()) || vinculo.isEmpty()) {
            n.setStatus(Notificacao.SEM_VINCULO);
            n.setErro("Docente inativo ou sem Telegram vinculado no momento do envio.");
            return true;
        }

        TelegramVinculo v = vinculo.get();
        n.setTentativas(n.getTentativas() + 1);
        try {
            Long messageId = telegram.enviarMensagem(v.getChatId(), n.getTexto(), teclado(n));
            if (messageId != null) {
                TelegramMensagem registro = new TelegramMensagem();
                registro.setChatId(v.getChatId());
                registro.setMessageId(messageId);
                registro.setChamado(n.getChamado());
                registro.setDirecao(TelegramMensagem.ENVIADA);
                registro.setTipo("NOTIFICACAO");
                mensagemRepo.save(registro);
            }
            n.setStatus(Notificacao.ENVIADA);
            n.setEnviadoEm(agora);
            n.setErro(null);
        } catch (TelegramException e) {
            tratarFalha(n, v, e, agora);
        }
        return true;
    }

    private void tratarFalha(Notificacao n, TelegramVinculo v, TelegramException e, LocalDateTime agora) {
        n.setErro(TelegramTexto.truncar(e.getMessage(), 500));
        Integer codigo = e.getCodigo();

        if (e.botBloqueado()) {
            v.setStatus(TelegramVinculo.REVOGADO);
            v.setMotivoRevogacao("BOT_BLOQUEADO");
            v.setExpiraEm(null);
            n.setStatus(Notificacao.FALHA);
            log.warn("Notificação {}: o usuário bloqueou o bot, vínculo revogado.", n.getId());
        } else if (codigo != null && codigo >= 400 && codigo < 500 && codigo != 429) {
            n.setStatus(Notificacao.FALHA);   // erro permanente: insistir não adianta
            log.warn("Notificação {}: recusada pelo Telegram ({}).", n.getId(), e.getMessage());
        } else if (n.getTentativas() >= MAX_TENTATIVAS) {
            n.setStatus(Notificacao.FALHA);
            log.warn("Notificação {}: desistiu após {} tentativas.", n.getId(), n.getTentativas());
        } else {
            long espera = e.getRetryAfter() != null
                    ? e.getRetryAfter()
                    : 30L * (1L << (n.getTentativas() - 1));
            n.setProximoEnvio(agora.plusSeconds(Math.min(espera, 3600)));
            log.info("Notificação {}: nova tentativa em {} s.", n.getId(), Math.min(espera, 3600));
        }
    }

    /** Botões da mensagem. Entram na Etapa 7. */
    private Map<String, Object> teclado(Notificacao n) {
        return null;
    }
}