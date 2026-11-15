package com.example.demandas.integration.telegram;

import com.example.demandas.entity.TelegramLiberacao;
import com.example.demandas.entity.TelegramTentativaInvalida;
import com.example.demandas.repository.TelegramCodigoVinculoRepository;
import com.example.demandas.repository.TelegramLiberacaoRepository;
import com.example.demandas.repository.TelegramTentativaInvalidaRepository;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Optional;

/** Limites de 1 hora do Telegram: códigos gerados por docente e tentativas inválidas por chat. */
@Component
public class TelegramLimites {

    private final TelegramProperties props;
    private final TelegramCodigoVinculoRepository codigoRepo;
    private final TelegramTentativaInvalidaRepository tentativaRepo;
    private final TelegramLiberacaoRepository liberacaoRepo;

    public TelegramLimites(TelegramProperties props,
                           TelegramCodigoVinculoRepository codigoRepo,
                           TelegramTentativaInvalidaRepository tentativaRepo,
                           TelegramLiberacaoRepository liberacaoRepo) {
        this.props = props;
        this.codigoRepo = codigoRepo;
        this.tentativaRepo = tentativaRepo;
        this.liberacaoRepo = liberacaoRepo;
    }

    /** Códigos gerados na última hora, considerando só o que veio depois da última liberação do admin. */
    public long codigosNaJanela(Integer usuarioId, LocalDateTime agora) {
        LocalDateTime desde = agora.minusHours(1);
        Optional<TelegramLiberacao> liberacao =
                liberacaoRepo.findFirstByTipoAndUsuarioIdOrderByCriadoEmDesc(TelegramLiberacao.DOCENTE, usuarioId);
        if (liberacao.isPresent() && liberacao.get().getCriadoEm().isAfter(desde)) {
            desde = liberacao.get().getCriadoEm();
        }
        return codigoRepo.countByUsuarioIdAndCriadoEmAfter(usuarioId, desde);
    }

    public boolean chatBloqueado(Long chatId, LocalDateTime agora) {
        return tentativaRepo.countByChatIdAndCriadoEmAfter(chatId, agora.minusHours(1))
                >= props.tentativasLimiteHora();
    }

    public void registrarFalha(Long chatId, TelegramTipos.TgUser from) {
        TelegramTentativaInvalida t = new TelegramTentativaInvalida();
        t.setChatId(chatId);
        if (from != null) {
            t.setUsername(from.username() == null ? null : TelegramTexto.truncar(from.username(), 64));
            String nome = ((from.firstName() == null ? "" : from.firstName()) + " "
                    + (from.lastName() == null ? "" : from.lastName())).trim();
            t.setNomeExibicao(nome.isEmpty() ? null : TelegramTexto.truncar(nome, 200));
        }
        tentativaRepo.save(t);
    }
}