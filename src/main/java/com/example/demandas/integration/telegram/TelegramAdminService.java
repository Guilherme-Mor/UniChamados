package com.example.demandas.integration.telegram;

import com.example.demandas.dto.TelegramAdminDtos;
import com.example.demandas.entity.Perfil;
import com.example.demandas.entity.TelegramLiberacao;
import com.example.demandas.entity.TelegramTentativaInvalida;
import com.example.demandas.entity.TelegramVinculo;
import com.example.demandas.entity.Usuario;
import com.example.demandas.exception.RegraNegocioException;
import com.example.demandas.repository.TelegramLiberacaoRepository;
import com.example.demandas.repository.TelegramTentativaInvalidaRepository;
import com.example.demandas.repository.TelegramVinculoRepository;
import com.example.demandas.repository.UsuarioRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class TelegramAdminService {

    private static final List<String> VIGENTES = List.of(TelegramVinculo.PENDENTE, TelegramVinculo.ATIVO);

    private final TelegramProperties props;
    private final UsuarioRepository usuarioRepo;
    private final TelegramVinculoRepository vinculoRepo;
    private final TelegramTentativaInvalidaRepository tentativaRepo;
    private final TelegramLiberacaoRepository liberacaoRepo;
    private final TelegramLimites limites;

    public TelegramAdminService(TelegramProperties props,
                                UsuarioRepository usuarioRepo,
                                TelegramVinculoRepository vinculoRepo,
                                TelegramTentativaInvalidaRepository tentativaRepo,
                                TelegramLiberacaoRepository liberacaoRepo,
                                TelegramLimites limites) {
        this.props = props;
        this.usuarioRepo = usuarioRepo;
        this.vinculoRepo = vinculoRepo;
        this.tentativaRepo = tentativaRepo;
        this.liberacaoRepo = liberacaoRepo;
        this.limites = limites;
    }

    @Transactional(readOnly = true)
    public TelegramAdminDtos.Painel painel() {
        LocalDateTime agora = LocalDateTime.now();

        Map<Integer, TelegramVinculo> vigentes = new HashMap<>();
        for (TelegramVinculo v : vinculoRepo.findByStatusIn(VIGENTES)) {
            vigentes.put(v.getUsuario().getId(), v);
        }

        List<TelegramAdminDtos.Docente> docentes = usuarioRepo.findByPerfil("DOCENTE").stream()
                .map(u -> {
                    TelegramVinculo v = vigentes.get(u.getId());
                    return new TelegramAdminDtos.Docente(
                            u.getId(), u.getNome(), u.getEmail(),
                            v == null ? "NENHUM" : v.getStatus(),
                            v == null ? null : v.getUsername(),
                            limites.codigosNaJanela(u.getId(), agora));
                })
                .toList();

        List<TelegramAdminDtos.Bloqueio> bloqueios = new ArrayList<>();
        for (Object[] linha : tentativaRepo.bloqueados(agora.minusHours(1), props.tentativasLimiteHora())) {
            Long chatId = ((Number) linha[0]).longValue();
            TelegramTentativaInvalida ultima = tentativaRepo.findFirstByChatIdOrderByCriadoEmDesc(chatId).orElse(null);
            bloqueios.add(new TelegramAdminDtos.Bloqueio(
                    chatId,
                    ultima == null ? null : ultima.getNomeExibicao(),
                    ultima == null ? null : ultima.getUsername(),
                    ((Number) linha[1]).longValue(),
                    (LocalDateTime) linha[2]));
        }

        return new TelegramAdminDtos.Painel(props.ativo(), props.codigoLimiteHora(),
                props.tentativasLimiteHora(), docentes, bloqueios);
    }

    /** Zera o limite de códigos do docente e as tentativas inválidas dos chats que já foram dele. */
    @Transactional
    public void liberarDocente(String emailAdmin, Integer docenteId) {
        Usuario admin = buscarAdmin(emailAdmin);
        Usuario docente = usuarioRepo.findById(docenteId)
                .filter(u -> u.getPerfis().stream().map(Perfil::getNome).anyMatch("DOCENTE"::equals))
                .orElseThrow(() -> new RegraNegocioException("Docente não encontrado.", HttpStatus.NOT_FOUND));

        TelegramLiberacao liberacao = new TelegramLiberacao();
        liberacao.setTipo(TelegramLiberacao.DOCENTE);
        liberacao.setUsuario(docente);
        liberacao.setAdmin(admin);
        liberacaoRepo.save(liberacao);

        for (TelegramVinculo v : vinculoRepo.findByUsuarioId(docenteId)) {
            tentativaRepo.apagarDoChat(v.getChatId());
        }
    }

    /** Libera um chat bloqueado por tentativas inválidas (caso sem vínculo com docente). */
    @Transactional
    public void liberarChat(String emailAdmin, Long chatId) {
        Usuario admin = buscarAdmin(emailAdmin);

        TelegramLiberacao liberacao = new TelegramLiberacao();
        liberacao.setTipo(TelegramLiberacao.CHAT);
        liberacao.setChatId(chatId);
        liberacao.setAdmin(admin);
        liberacaoRepo.save(liberacao);

        tentativaRepo.apagarDoChat(chatId);
    }

    private Usuario buscarAdmin(String email) {
        return usuarioRepo.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new RegraNegocioException("Usuário não encontrado.", HttpStatus.NOT_FOUND));
    }
}