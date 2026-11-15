package com.example.demandas.integration.telegram;

import com.example.demandas.dto.TelegramCodigoResponse;
import com.example.demandas.dto.TelegramEstadoResponse;
import com.example.demandas.entity.Perfil;
import com.example.demandas.entity.TelegramCodigoVinculo;
import com.example.demandas.entity.TelegramVinculo;
import com.example.demandas.entity.Usuario;
import com.example.demandas.exception.RegraNegocioException;
import com.example.demandas.repository.TelegramCodigoVinculoRepository;
import com.example.demandas.repository.TelegramVinculoRepository;
import com.example.demandas.repository.UsuarioRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

@Service
public class TelegramVinculoService {

    private static final List<String> VIGENTES = List.of(TelegramVinculo.PENDENTE, TelegramVinculo.ATIVO);
    private static final List<String> SO_PENDENTE = List.of(TelegramVinculo.PENDENTE);

    // Sem 0, O, 1, I e L, que se confundem ao ler ou digitar
    private static final String ALFABETO = "ABCDEFGHJKMNPQRSTUVWXYZ23456789";
    private static final int TAMANHO_CODIGO = 10;
    private static final Pattern FORMATO_CODIGO = Pattern.compile("[ABCDEFGHJKMNPQRSTUVWXYZ23456789]{10}");

    private static final String CODIGO_INVALIDO =
            "Código inválido ou expirado. Gere um novo código no portal (menu Telegram).";
    private static final String CHAT_BLOQUEADO =
            "Muitas tentativas inválidas neste Telegram. Aguarde até 1 hora ou peça ao administrador "
                    + "para liberar o seu acesso.";

    private final SecureRandom random = new SecureRandom();

    private final TelegramProperties props;
    private final TelegramVinculoRepository vinculoRepo;
    private final TelegramCodigoVinculoRepository codigoRepo;
    private final UsuarioRepository usuarioRepo;
    private final TelegramLimites limites;

    public TelegramVinculoService(TelegramProperties props,
                                  TelegramVinculoRepository vinculoRepo,
                                  TelegramCodigoVinculoRepository codigoRepo,
                                  UsuarioRepository usuarioRepo,
                                  TelegramLimites limites) {
        this.props = props;
        this.vinculoRepo = vinculoRepo;
        this.codigoRepo = codigoRepo;
        this.usuarioRepo = usuarioRepo;
        this.limites = limites;
    }

    // =====================================================================
    // Lado do portal (docente logado)
    // =====================================================================

    @Transactional
    public TelegramEstadoResponse estado(String email) {
        if (!props.ativo()) {
            return new TelegramEstadoResponse(false, "NENHUM", null, null, null, null);
        }
        Usuario docente = buscarDocente(email);
        LocalDateTime agora = LocalDateTime.now();
        expirarPendentes(agora);

        Optional<TelegramVinculo> vigente = vinculoRepo.findByUsuarioIdAndStatusIn(docente.getId(), VIGENTES);
        if (vigente.isEmpty()) {
            return new TelegramEstadoResponse(true, "NENHUM", null, null, null, nomeBot());
        }
        TelegramVinculo v = vigente.get();
        Long restante = null;
        if (TelegramVinculo.PENDENTE.equals(v.getStatus()) && v.getExpiraEm() != null) {
            restante = Math.max(0L, Duration.between(agora, v.getExpiraEm()).getSeconds());
        }
        return new TelegramEstadoResponse(true, v.getStatus(), v.getUsername(), v.getNomeExibicao(),
                restante, nomeBot());
    }

    @Transactional
    public TelegramCodigoResponse gerarCodigo(String email) {
        if (!props.ativo()) {
            throw new RegraNegocioException(
                    "A integração com o Telegram não está habilitada.", HttpStatus.SERVICE_UNAVAILABLE);
        }
        String bot = nomeBot();
        if (bot.isEmpty()) {
            throw new RegraNegocioException(
                    "O username do bot não está configurado (TELEGRAM_BOT_USERNAME).", HttpStatus.SERVICE_UNAVAILABLE);
        }

        Usuario docente = buscarDocente(email);
        LocalDateTime agora = LocalDateTime.now();
        expirarPendentes(agora);

        Optional<TelegramVinculo> atual = vinculoRepo.findByUsuarioIdAndStatusIn(docente.getId(), VIGENTES);
        if (atual.isPresent() && TelegramVinculo.ATIVO.equals(atual.get().getStatus())) {
            throw new RegraNegocioException(
                    "Já existe um Telegram vinculado. Desvincule antes de vincular outro.", HttpStatus.CONFLICT);
        }

        if (limites.codigosNaJanela(docente.getId(), agora) >= props.codigoLimiteHora()) {
            throw new RegraNegocioException(
                    "Limite de códigos por hora atingido. Aguarde até 1 hora ou peça ao administrador "
                            + "para liberar o seu Telegram.",
                    HttpStatus.TOO_MANY_REQUESTS);
        }
        atual.ifPresent(pendente -> revogar(pendente, "SUBSTITUIDO"));

        // Só um código ativo por docente: os anteriores deixam de valer
        codigoRepo.findByUsuarioIdAndUsadoEmIsNullAndExpiraEmAfter(docente.getId(), agora)
                .forEach(c -> c.setExpiraEm(agora));

        String codigo;
        String hash;
        do {
            codigo = novoCodigo();
            hash = hash(codigo);
        } while (codigoRepo.findByCodigoHash(hash).isPresent());

        TelegramCodigoVinculo registro = new TelegramCodigoVinculo();
        registro.setUsuario(docente);
        registro.setCodigoHash(hash);
        registro.setExpiraEm(agora.plusMinutes(props.codigoValidadeMinutos()));
        codigoRepo.save(registro);

        String link = "https://t.me/" + bot + "?start=" + codigo;
        return new TelegramCodigoResponse(codigo, link, QrCodeSvg.dataUrl(link),
                props.codigoValidadeMinutos() * 60);
    }

    @Transactional
    public AvisoTelegram confirmar(String email) {
        Usuario docente = buscarDocente(email);
        LocalDateTime agora = LocalDateTime.now();
        expirarPendentes(agora);

        TelegramVinculo v = vinculoRepo.findByUsuarioIdAndStatusIn(docente.getId(), SO_PENDENTE)
                .orElseThrow(() -> new RegraNegocioException(
                        "Não há pedido de vínculo pendente (ele pode ter expirado).", HttpStatus.CONFLICT));

        v.setStatus(TelegramVinculo.ATIVO);
        v.setConfirmadoEm(agora);
        v.setExpiraEm(null);
        return new AvisoTelegram(v.getChatId(),
                "<b>Vínculo confirmado.</b>\nEste Telegram agora está vinculado à sua conta no UniChamados. "
                        + "Use /ajuda para ver os comandos.");
    }

    @Transactional
    public AvisoTelegram recusar(String email) {
        Usuario docente = buscarDocente(email);
        LocalDateTime agora = LocalDateTime.now();
        expirarPendentes(agora);

        TelegramVinculo v = vinculoRepo.findByUsuarioIdAndStatusIn(docente.getId(), SO_PENDENTE)
                .orElseThrow(() -> new RegraNegocioException(
                        "Não há pedido de vínculo pendente.", HttpStatus.CONFLICT));

        revogar(v, "RECUSADO");
        return new AvisoTelegram(v.getChatId(), "O pedido de vínculo foi recusado no portal.");
    }

    @Transactional
    public AvisoTelegram desvincular(String email) {
        Usuario docente = buscarDocente(email);
        expirarPendentes(LocalDateTime.now());

        TelegramVinculo v = vinculoRepo.findByUsuarioIdAndStatusIn(docente.getId(), VIGENTES)
                .orElseThrow(() -> new RegraNegocioException(
                        "Não há Telegram vinculado à sua conta.", HttpStatus.CONFLICT));

        revogar(v, "DESVINCULADO");
        return new AvisoTelegram(v.getChatId(), "Este Telegram foi desvinculado da sua conta no UniChamados.");
    }

    // =====================================================================
    // Lado do Telegram
    // =====================================================================

    /** Trata "/start CÓDIGO". Devolve o texto (HTML) a responder no chat. */
    @Transactional
    public String iniciarVinculo(Long chatId, TelegramTipos.TgUser from, String digitado) {
        LocalDateTime agora = LocalDateTime.now();
        if (limites.chatBloqueado(chatId, agora)) {
            return CHAT_BLOQUEADO;
        }
        expirarPendentes(agora);

        TelegramCodigoVinculo codigo = localizarCodigoValido(digitado, agora);
        if (codigo == null || !docenteAtivo(codigo.getUsuario())) {
            limites.registrarFalha(chatId, from);
            return CODIGO_INVALIDO;
        }
        Usuario docente = codigo.getUsuario();

        Optional<TelegramVinculo> doDocente = vinculoRepo.findByUsuarioIdAndStatusIn(docente.getId(), VIGENTES);
        if (doDocente.isPresent() && TelegramVinculo.ATIVO.equals(doDocente.get().getStatus())) {
            return "Esta conta já tem um Telegram vinculado. Desvincule no portal antes de vincular outro.";
        }
        Optional<TelegramVinculo> doChat = vinculoRepo.findByChatIdAndStatusIn(chatId, VIGENTES);
        if (doChat.isPresent() && TelegramVinculo.ATIVO.equals(doChat.get().getStatus())) {
            return "Este Telegram já está vinculado a uma conta. Envie /desvincular para soltá-lo antes.";
        }

        // Pedidos pendentes anteriores (do docente ou deste chat) são substituídos
        boolean revogou = false;
        if (doDocente.isPresent()) {
            revogar(doDocente.get(), "SUBSTITUIDO");
            revogou = true;
        }
        if (doChat.isPresent()
                && (doDocente.isEmpty() || !doChat.get().getId().equals(doDocente.get().getId()))) {
            revogar(doChat.get(), "SUBSTITUIDO");
            revogou = true;
        }
        // O UPDATE precisa ser enviado antes do INSERT, por causa dos índices únicos
        if (revogou) vinculoRepo.flush();

        TelegramVinculo novo = new TelegramVinculo();
        novo.setUsuario(docente);
        novo.setChatId(chatId);
        novo.setTelegramUserId(from == null ? null : from.id());
        novo.setUsername(from == null ? null : from.username());
        novo.setNomeExibicao(nomeExibicao(from));
        novo.setStatus(TelegramVinculo.PENDENTE);
        novo.setExpiraEm(agora.plusMinutes(props.pedidoValidadeMinutos()));
        vinculoRepo.saveAndFlush(novo);

        codigo.setUsadoEm(agora);
        return "<b>Pedido recebido.</b>\nPara concluir, abra o portal (menu Telegram) e confirme o vínculo "
                + "com este Telegram em até " + props.pedidoValidadeMinutos() + " minutos.";
    }

    /** Trata "/desvincular". */
    @Transactional
    public String desvincularPorChat(Long chatId) {
        expirarPendentes(LocalDateTime.now());
        Optional<TelegramVinculo> vigente = vinculoRepo.findByChatIdAndStatusIn(chatId, VIGENTES);
        if (vigente.isEmpty()) {
            return "Este Telegram não está vinculado a nenhuma conta.";
        }
        revogar(vigente.get(), "DESVINCULADO_TELEGRAM");
        return "Telegram desvinculado. Você não receberá mais avisos neste chat.";
    }

    // =====================================================================
    // Apoio
    // =====================================================================

    private TelegramCodigoVinculo localizarCodigoValido(String digitado, LocalDateTime agora) {
        String normalizado = digitado == null ? ""
                : digitado.trim().toUpperCase(Locale.ROOT).replaceAll("[\\s-]", "");
        if (!FORMATO_CODIGO.matcher(normalizado).matches()) return null;
        return codigoRepo.findByCodigoHash(hash(normalizado))
                .filter(c -> c.getUsadoEm() == null && c.getExpiraEm().isAfter(agora))
                .orElse(null);
    }

    private void expirarPendentes(LocalDateTime agora) {
        List<TelegramVinculo> vencidos = vinculoRepo.findByStatusAndExpiraEmBefore(TelegramVinculo.PENDENTE, agora);
        if (vencidos.isEmpty()) return;
        vencidos.forEach(v -> revogar(v, "EXPIRADO"));
        vinculoRepo.flush();
    }

    private void revogar(TelegramVinculo v, String motivo) {
        v.setStatus(TelegramVinculo.REVOGADO);
        v.setMotivoRevogacao(motivo);
        v.setExpiraEm(null);
    }

    private Usuario buscarDocente(String email) {
        Usuario u = usuarioRepo.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new RegraNegocioException("Usuário não encontrado.", HttpStatus.NOT_FOUND));
        if (!docenteAtivo(u)) {
            throw new RegraNegocioException("Acesso negado.", HttpStatus.FORBIDDEN);
        }
        return u;
    }

    private boolean docenteAtivo(Usuario u) {
        return u != null
                && "ATIVO".equals(u.getStatus())
                && u.getPerfis().stream().map(Perfil::getNome).anyMatch("DOCENTE"::equals);
    }

    private String nomeBot() {
        return props.botUsername().trim().replaceFirst("^@", "");
    }

    private String novoCodigo() {
        StringBuilder sb = new StringBuilder(TAMANHO_CODIGO);
        for (int i = 0; i < TAMANHO_CODIGO; i++) {
            sb.append(ALFABETO.charAt(random.nextInt(ALFABETO.length())));
        }
        return sb.toString();
    }

    static String hash(String codigo) {
        try {
            byte[] resumo = MessageDigest.getInstance("SHA-256").digest(codigo.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(resumo);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String nomeExibicao(TelegramTipos.TgUser u) {
        if (u == null) return null;
        String nome = ((u.firstName() == null ? "" : u.firstName()) + " "
                + (u.lastName() == null ? "" : u.lastName())).trim();
        return nome.isEmpty() ? null : TelegramTexto.truncar(nome, 200);
    }
}