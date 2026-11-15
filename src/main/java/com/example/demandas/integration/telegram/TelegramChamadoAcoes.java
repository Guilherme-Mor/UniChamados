package com.example.demandas.integration.telegram;

import com.example.demandas.dto.ChamadoResumo;
import com.example.demandas.dto.MensagemRequest;
import com.example.demandas.dto.StatusRequest;
import com.example.demandas.entity.TelegramMensagem;
import com.example.demandas.exception.RegraNegocioException;
import com.example.demandas.service.ChamadoService;
import com.example.demandas.service.PoliticaStatus;
import org.springframework.stereotype.Service;

import java.util.Optional;

import static com.example.demandas.integration.telegram.TelegramTexto.escapar;

/**
 * Ações do docente pelo Telegram. Sem @Transactional aqui: as validações e a execução rodam em transações
 * separadas, e uma recusa esperada (ex.: "status não permitido") não pode marcar a transação como falha.
 */
@Service
public class TelegramChamadoAcoes {

    private static final int LIMITE_MENSAGEM = 5000;

    private final TelegramChamadoDados dados;
    private final ChamadoService chamadoService;

    public TelegramChamadoAcoes(TelegramChamadoDados dados, ChamadoService chamadoService) {
        this.dados = dados;
        this.chamadoService = chamadoService;
    }

    /** O docente respondeu (reply) a uma mensagem do bot: registra o texto no chamado daquela mensagem. */
    public RespostaTelegram responder(Long chatId, Long mensagemRespondidaId, Long mensagemDoDocenteId, String texto) {
        Optional<TelegramChamadoDados.Docente> docente = dados.docente(chatId);
        if (docente.isEmpty()) return RespostaTelegram.simples(TelegramChamadoDados.SEM_VINCULO);

        Optional<Long> chamadoId = dados.chamadoDaMensagem(chatId, mensagemRespondidaId);
        if (chamadoId.isEmpty()) {
            return RespostaTelegram.simples("Não consegui identificar a qual chamado esta resposta pertence. "
                    + "Toque em <b>Responder</b> na mensagem do chamado, ou use /chamados.");
        }
        Long id = chamadoId.get();
        if (!dados.ehResponsavel(docente.get().id(), id)) {
            return RespostaTelegram.simples(TelegramChamadoDados.NAO_ENCONTRADO);
        }

        String limpo = texto == null ? "" : texto.trim();
        if (limpo.isEmpty()) return RespostaTelegram.simples("Envie um texto para registrar no chamado.");
        if (limpo.length() > LIMITE_MENSAGEM) {
            return RespostaTelegram.simples("Mensagem longa demais (máximo de " + LIMITE_MENSAGEM + " caracteres).");
        }

        try {
            chamadoService.adicionarMensagem(docente.get().email(), id, new MensagemRequest(limpo), "TELEGRAM");
        } catch (RegraNegocioException e) {
            return RespostaTelegram.simples("⚠️ " + escapar(e.getMessage()));
        }

        if (mensagemDoDocenteId != null) {
            dados.registrarMensagem(chatId, mensagemDoDocenteId, id, TelegramMensagem.RECEBIDA, "RESPOSTA");
        }
        return new RespostaTelegram(
                "✅ Mensagem registrada no chamado <b>" + escapar(dados.protocolo(id)) + "</b>.",
                TelegramTeclados.chamado(id), id, "CONFIRMACAO");
    }

    /** O docente tocou em um status. Passa pelas mesmas regras do portal (política de transições). */
    public RespostaTelegram aplicarStatus(Long chatId, Long chamadoId, String status) {
        Optional<TelegramChamadoDados.Docente> docente = dados.docente(chatId);
        if (docente.isEmpty()) return RespostaTelegram.simples(TelegramChamadoDados.SEM_VINCULO);

        if (status == null || !TelegramChamadoDados.STATUS_PELO_TELEGRAM.contains(status)) {
            return RespostaTelegram.simples("Esse status só pode ser alterado no portal.");
        }
        if (!dados.ehResponsavel(docente.get().id(), chamadoId)) {
            return RespostaTelegram.simples(TelegramChamadoDados.NAO_ENCONTRADO);
        }

        ChamadoResumo resumo;
        try {
            resumo = chamadoService.alterarStatus(docente.get().email(), chamadoId, new StatusRequest(status, null), "TELEGRAM");
        } catch (RegraNegocioException e) {
            return RespostaTelegram.simples("⚠️ " + escapar(e.getMessage()));
        }

        boolean final_ = PoliticaStatus.FINAIS.contains(resumo.status());
        return new RespostaTelegram(
                "✅ Status do chamado <b>" + escapar(resumo.protocolo()) + "</b> alterado para <b>"
                        + escapar(TelegramTeclados.rotuloStatus(resumo.status())) + "</b>.",
                final_ ? null : TelegramTeclados.chamado(chamadoId), chamadoId, "CONFIRMACAO");
    }
}