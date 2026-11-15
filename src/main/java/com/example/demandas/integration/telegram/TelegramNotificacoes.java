package com.example.demandas.integration.telegram;

import com.example.demandas.entity.Chamado;
import com.example.demandas.entity.Notificacao;
import com.example.demandas.entity.TelegramVinculo;
import com.example.demandas.entity.Usuario;
import com.example.demandas.repository.NotificacaoRepository;
import com.example.demandas.repository.TelegramVinculoRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;

/** Enfileira avisos para o docente. Deve ser chamada dentro da transação da mudança no chamado. */
@Component
public class TelegramNotificacoes {

    private static final List<String> SO_ATIVO = List.of(TelegramVinculo.ATIVO);

    private final TelegramProperties props;
    private final TelegramVinculoRepository vinculoRepo;
    private final NotificacaoRepository notificacaoRepo;
    private final TelegramDespachante despachante;

    public TelegramNotificacoes(TelegramProperties props,
                                TelegramVinculoRepository vinculoRepo,
                                NotificacaoRepository notificacaoRepo,
                                TelegramDespachante despachante) {
        this.props = props;
        this.vinculoRepo = vinculoRepo;
        this.notificacaoRepo = notificacaoRepo;
        this.despachante = despachante;
    }

    public void avisarAtribuicao(Chamado chamado, Usuario docente, boolean reatribuicao) {
        String cabecalho = reatribuicao
                ? "🔁 <b>Chamado reatribuído a você</b>"
                : "🔔 <b>Novo chamado atribuído</b>";
        enfileirar(docente, chamado, Notificacao.TIPO_ATRIBUICAO, cabecalho + "\n\n" + resumo(chamado));
    }

    /** Avisa o responsável quando outra pessoa escreve no chamado. */
    public void avisarNovaMensagem(Chamado chamado, Usuario autor) {
        Usuario responsavel = chamado.getResponsavel();
        if (responsavel == null || responsavel.getId().equals(autor.getId())) return;

        String texto = "💬 <b>Nova mensagem</b> em " + TelegramTexto.escapar(chamado.getProtocolo()) + "\n\n"
                + resumo(chamado) + "\nDe: " + TelegramTexto.escapar(autor.getNome());
        enfileirar(responsavel, chamado, Notificacao.TIPO_NOVA_MENSAGEM, texto);
    }

    private String resumo(Chamado c) {
        StringBuilder sb = new StringBuilder();
        sb.append("Protocolo: <b>").append(TelegramTexto.escapar(c.getProtocolo())).append("</b>\n");
        sb.append("Título: ").append(TelegramTexto.escapar(TelegramTexto.truncar(c.getTitulo(), 150))).append('\n');
        sb.append("Categoria: ").append(TelegramTexto.escapar(c.getCategoria().getNome())).append('\n');
        if (c.getDisciplina() != null) {
            sb.append("Disciplina: ").append(TelegramTexto.escapar(c.getDisciplina().getNome())).append('\n');
        }
        sb.append("Solicitante: ").append(TelegramTexto.escapar(c.getUsuario().getNome())).append('\n');
        return sb.toString();
    }

    private void enfileirar(Usuario docente, Chamado chamado, String tipo, String texto) {
        if (!props.ativo()) return;
        if (vinculoRepo.findByUsuarioIdAndStatusIn(docente.getId(), SO_ATIVO).isEmpty()) return;

        Notificacao n = new Notificacao();
        n.setUsuario(docente);
        n.setChamado(chamado);
        n.setTipo(tipo);
        n.setTexto(TelegramTexto.truncar(texto, TelegramTexto.LIMITE));
        notificacaoRepo.save(n);

        // O envio só é acionado depois que a transação foi confirmada
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    despachante.acionar();
                }
            });
        } else {
            despachante.acionar();
        }
    }
}