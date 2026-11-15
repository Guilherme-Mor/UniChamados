package com.example.demandas.integration.telegram;

import com.example.demandas.entity.Chamado;
import com.example.demandas.entity.Interacao;
import com.example.demandas.entity.Perfil;
import com.example.demandas.entity.TelegramMensagem;
import com.example.demandas.entity.TelegramVinculo;
import com.example.demandas.repository.ChamadoRepository;
import com.example.demandas.repository.InteracaoRepository;
import com.example.demandas.repository.TelegramMensagemRepository;
import com.example.demandas.repository.TelegramVinculoRepository;
import com.example.demandas.service.PoliticaStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static com.example.demandas.integration.telegram.TelegramTexto.escapar;
import static com.example.demandas.integration.telegram.TelegramTexto.escaparCortando;
import static com.example.demandas.integration.telegram.TelegramTexto.truncar;

/** Consultas do Telegram sobre chamados. Cada método confere se o chat é de um docente ativo e responsável. */
@Service
public class TelegramChamadoDados {

    /** Status que o docente pode escolher pelo Telegram (cancelar e encerrar ficam só no portal). */
    public static final Set<String> STATUS_PELO_TELEGRAM =
            Set.of("EM_ATENDIMENTO", "AGUARDANDO_DISCENTE", "RESOLVIDO");

    static final String SEM_VINCULO =
            "Este Telegram não está vinculado a uma conta de docente ativa. "
                    + "Gere um código no portal (menu Telegram).";
    static final String NAO_ENCONTRADO =
            "Chamado não encontrado ou não atribuído a você (ele pode ter sido reatribuído).";

    private static final List<String> SO_ATIVO = List.of(TelegramVinculo.ATIVO);
    private static final DateTimeFormatter DATA_HORA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final DateTimeFormatter DATA_CURTA = DateTimeFormatter.ofPattern("dd/MM HH:mm");

    public record Docente(Integer id, String email, String nome) {}

    private final TelegramVinculoRepository vinculoRepo;
    private final ChamadoRepository chamadoRepo;
    private final InteracaoRepository interacaoRepo;
    private final TelegramMensagemRepository mensagemRepo;

    public TelegramChamadoDados(TelegramVinculoRepository vinculoRepo,
                                ChamadoRepository chamadoRepo,
                                InteracaoRepository interacaoRepo,
                                TelegramMensagemRepository mensagemRepo) {
        this.vinculoRepo = vinculoRepo;
        this.chamadoRepo = chamadoRepo;
        this.interacaoRepo = interacaoRepo;
        this.mensagemRepo = mensagemRepo;
    }

    // =====================================================================
    // Identidade e autorização
    // =====================================================================

    /** O docente dono deste chat: vínculo ATIVO, usuário ativo e com perfil DOCENTE. */
    @Transactional(readOnly = true)
    public Optional<Docente> docente(Long chatId) {
        return vinculoRepo.findByChatIdAndStatusIn(chatId, SO_ATIVO)
                .map(TelegramVinculo::getUsuario)
                .filter(u -> "ATIVO".equals(u.getStatus()))
                .filter(u -> u.getPerfis().stream().map(Perfil::getNome).anyMatch("DOCENTE"::equals))
                .map(u -> new Docente(u.getId(), u.getEmail(), u.getNome()));
    }

    @Transactional(readOnly = true)
    public boolean ehResponsavel(Integer docenteId, Long chamadoId) {
        return chamadoRepo.findById(chamadoId)
                .map(c -> responsavelDe(c, docenteId))
                .orElse(false);
    }

    @Transactional(readOnly = true)
    public String protocolo(Long chamadoId) {
        return chamadoRepo.findById(chamadoId).map(Chamado::getProtocolo).orElse("?");
    }

    /** Descobre a qual chamado pertence uma mensagem do bot (usado ao responder por reply). */
    @Transactional(readOnly = true)
    public Optional<Long> chamadoDaMensagem(Long chatId, Long messageId) {
        return mensagemRepo.findByChatIdAndMessageId(chatId, messageId)
                .filter(m -> TelegramMensagem.ENVIADA.equals(m.getDirecao()) && m.getChamado() != null)
                .map(m -> m.getChamado().getId());
    }

    @Transactional
    public void registrarMensagem(Long chatId, Long messageId, Long chamadoId, String direcao, String tipo) {
        Optional<Chamado> chamado = chamadoRepo.findById(chamadoId);
        if (chamado.isEmpty()) return;
        TelegramMensagem m = new TelegramMensagem();
        m.setChatId(chatId);
        m.setMessageId(messageId);
        m.setChamado(chamado.get());
        m.setDirecao(direcao);
        m.setTipo(tipo);
        mensagemRepo.save(m);
    }

    // =====================================================================
    // Consultas
    // =====================================================================

    @Transactional(readOnly = true)
    public RespostaTelegram listar(Long chatId) {
        Optional<Docente> docente = docente(chatId);
        if (docente.isEmpty()) return RespostaTelegram.simples(SEM_VINCULO);

        Integer id = docente.get().id();
        List<Chamado> lista = chamadoRepo
                .findTop5ByResponsavelIdAndStatusNomeNotInOrderByCriadoEmDesc(id, PoliticaStatus.FINAIS);
        if (lista.isEmpty()) return RespostaTelegram.simples("Você não tem chamados em aberto. 🎉");
        long total = chamadoRepo.countByResponsavelIdAndStatusNomeNotIn(id, PoliticaStatus.FINAIS);

        StringBuilder sb = new StringBuilder("<b>Seus chamados em aberto</b> (" + total + ")\n\n");
        List<TelegramTeclados.Item> itens = new ArrayList<>();
        for (Chamado c : lista) {
            sb.append("• <b>").append(escapar(c.getProtocolo())).append("</b> · ")
                    .append(escaparCortando(c.getTitulo(), 60))
                    .append(" (").append(escapar(TelegramTeclados.rotuloStatus(c.getStatus().getNome()))).append(")\n");
            itens.add(new TelegramTeclados.Item(c.getId(), c.getProtocolo() + " · " + truncar(c.getTitulo(), 22)));
        }
        if (total > lista.size()) {
            sb.append("\nMostrando os ").append(lista.size()).append(" mais recentes. Veja todos no portal.");
        }
        return new RespostaTelegram(sb.toString(), TelegramTeclados.lista(itens), null, null);
    }

    @Transactional(readOnly = true)
    public RespostaTelegram porProtocolo(Long chatId, String protocolo) {
        Optional<Docente> docente = docente(chatId);
        if (docente.isEmpty()) return RespostaTelegram.simples(SEM_VINCULO);

        Optional<Chamado> chamado = chamadoRepo.findByProtocolo(protocolo.trim());
        if (chamado.isEmpty() || !responsavelDe(chamado.get(), docente.get().id())) {
            return RespostaTelegram.simples(NAO_ENCONTRADO);
        }
        return montarDetalhe(chamado.get());
    }

    @Transactional(readOnly = true)
    public RespostaTelegram detalhe(Long chatId, Long chamadoId) {
        Optional<Docente> docente = docente(chatId);
        if (docente.isEmpty()) return RespostaTelegram.simples(SEM_VINCULO);

        Optional<Chamado> chamado = chamadoRepo.findById(chamadoId);
        if (chamado.isEmpty() || !responsavelDe(chamado.get(), docente.get().id())) {
            return RespostaTelegram.simples(NAO_ENCONTRADO);
        }
        return montarDetalhe(chamado.get());
    }

    @Transactional(readOnly = true)
    public RespostaTelegram pedirResposta(Long chatId, Long chamadoId) {
        Optional<Docente> docente = docente(chatId);
        if (docente.isEmpty()) return RespostaTelegram.simples(SEM_VINCULO);

        Optional<Chamado> chamado = chamadoRepo.findById(chamadoId);
        if (chamado.isEmpty() || !responsavelDe(chamado.get(), docente.get().id())) {
            return RespostaTelegram.simples(NAO_ENCONTRADO);
        }
        Chamado c = chamado.get();
        if (PoliticaStatus.FINAIS.contains(c.getStatus().getNome())) {
            return RespostaTelegram.simples("Este chamado está "
                    + escapar(c.getStatus().getNome().toLowerCase()) + " e não aceita novas mensagens.");
        }
        return new RespostaTelegram(
                "✍️ Escreva a sua resposta para o chamado <b>" + escapar(c.getProtocolo()) + "</b>.\n"
                        + "Responda a <b>esta mensagem</b>: o texto será registrado no histórico e o discente o verá no portal.",
                TelegramTeclados.respostaForcada(), c.getId(), "PEDIDO_RESPOSTA");
    }

    @Transactional(readOnly = true)
    public RespostaTelegram opcoesStatus(Long chatId, Long chamadoId) {
        Optional<Docente> docente = docente(chatId);
        if (docente.isEmpty()) return RespostaTelegram.simples(SEM_VINCULO);

        Optional<Chamado> chamado = chamadoRepo.findById(chamadoId);
        if (chamado.isEmpty() || !responsavelDe(chamado.get(), docente.get().id())) {
            return RespostaTelegram.simples(NAO_ENCONTRADO);
        }
        Chamado c = chamado.get();
        String atual = c.getStatus().getNome();
        List<String> opcoes = PoliticaStatus.proximos(atual).stream()
                .filter(STATUS_PELO_TELEGRAM::contains)
                .toList();
        if (opcoes.isEmpty()) {
            return RespostaTelegram.simples("Nenhuma mudança de status disponível pelo Telegram para este chamado ("
                    + escapar(TelegramTeclados.rotuloStatus(atual)) + "). Cancelar e encerrar só no portal.");
        }
        return new RespostaTelegram(
                "Escolha o novo status do chamado <b>" + escapar(c.getProtocolo()) + "</b> (atual: <b>"
                        + escapar(TelegramTeclados.rotuloStatus(atual)) + "</b>):",
                TelegramTeclados.status(c.getId(), opcoes), c.getId(), "STATUS");
    }

    // =====================================================================
    // Apoio
    // =====================================================================

    private static boolean responsavelDe(Chamado c, Integer docenteId) {
        return c.getResponsavel() != null && c.getResponsavel().getId().equals(docenteId);
    }

    private RespostaTelegram montarDetalhe(Chamado c) {
        List<Interacao> ultimas = new ArrayList<>(
                interacaoRepo.findTop3ByChamadoIdOrderByCriadoEmDescIdDesc(c.getId()));
        Collections.reverse(ultimas);   // da mais antiga para a mais nova
        boolean encerrado = PoliticaStatus.FINAIS.contains(c.getStatus().getNome());
        return new RespostaTelegram(
                formatarDetalhe(c, ultimas, encerrado),
                encerrado ? null : TelegramTeclados.chamado(c.getId()),
                c.getId(), "DETALHE");
    }

    private String formatarDetalhe(Chamado c, List<Interacao> ultimas, boolean encerrado) {
        StringBuilder sb = new StringBuilder();
        sb.append("📄 <b>Chamado ").append(escapar(c.getProtocolo())).append("</b>\n");
        sb.append("<b>").append(escaparCortando(c.getTitulo(), 150)).append("</b>\n\n");
        sb.append("Status: <b>").append(escapar(TelegramTeclados.rotuloStatus(c.getStatus().getNome()))).append("</b>\n");
        sb.append("Categoria: ").append(escaparCortando(c.getCategoria().getNome(), 100)).append('\n');
        if (c.getDisciplina() != null) {
            sb.append("Disciplina: ").append(escaparCortando(c.getDisciplina().getNome(), 100)).append('\n');
        }
        sb.append("Solicitante: ").append(escaparCortando(c.getUsuario().getNome(), 100)).append('\n');
        sb.append("Aberto em: ").append(DATA_HORA.format(c.getCriadoEm())).append("\n\n");

        sb.append("<b>Descrição</b>\n").append(escaparCortando(c.getDescricao(), 1000)).append("\n\n");

        sb.append("<b>Últimas interações</b>\n");
        for (Interacao i : ultimas) {
            sb.append("• ").append(DATA_CURTA.format(i.getCriadoEm())).append(" · ")
                    .append(escaparCortando(i.getUsuario().getNome(), 60));
            if ("TELEGRAM".equals(i.getCanal())) sb.append(" ✈️");
            sb.append(": ").append(escaparCortando(i.getMensagem(), 250)).append('\n');
        }
        if (encerrado) {
            sb.append("\nEste chamado está <b>").append(escapar(c.getStatus().getNome().toLowerCase()))
                    .append("</b> e não aceita novas ações.");
        }
        return sb.toString();
    }
}