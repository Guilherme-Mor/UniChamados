package com.example.demandas.service;

import com.example.demandas.dto.*;
import com.example.demandas.entity.*;
import com.example.demandas.exception.RegraNegocioException;
import com.example.demandas.repository.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class ChamadoService {

    private static final Set<String> FINAIS = Set.of("ENCERRADO", "CANCELADO");

    private final ChamadoRepository chamadoRepo;
    private final InteracaoRepository interacaoRepo;
    private final CategoriaRepository categoriaRepo;
    private final DisciplinaRepository disciplinaRepo;
    private final StatusChamadoRepository statusRepo;
    private final UsuarioRepository usuarioRepo;
    private final ProtocoloGenerator protocoloGenerator;

    public ChamadoService(ChamadoRepository chamadoRepo,
                          InteracaoRepository interacaoRepo,
                          CategoriaRepository categoriaRepo,
                          DisciplinaRepository disciplinaRepo,
                          StatusChamadoRepository statusRepo,
                          UsuarioRepository usuarioRepo,
                          ProtocoloGenerator protocoloGenerator) {
        this.chamadoRepo = chamadoRepo;
        this.interacaoRepo = interacaoRepo;
        this.categoriaRepo = categoriaRepo;
        this.disciplinaRepo = disciplinaRepo;
        this.statusRepo = statusRepo;
        this.usuarioRepo = usuarioRepo;
        this.protocoloGenerator = protocoloGenerator;
    }

    // ===== Abertura e listagem =====

    @Transactional
    public ChamadoResumo abrir(String email, ChamadoRequest req) {
        Usuario usuario = buscarUsuario(email);

        Categoria categoria = categoriaRepo.findById(req.categoriaId())
                .filter(c -> Boolean.TRUE.equals(c.getAtivo()))
                .orElseThrow(() -> new RegraNegocioException(
                        "Categoria inválida.", HttpStatus.UNPROCESSABLE_ENTITY));

        if (categoriaRepo.existsByCategoriaPaiIdAndAtivoTrue(categoria.getId())) {
            throw new RegraNegocioException(
                    "Escolha uma subcategoria para abrir o chamado.", HttpStatus.UNPROCESSABLE_ENTITY);
        }

        Disciplina disciplina = null;
        if (Boolean.TRUE.equals(categoria.getExigeDisciplina())) {
            if (req.disciplinaId() == null) {
                throw new RegraNegocioException(
                        "Informe a disciplina para esta categoria.", HttpStatus.UNPROCESSABLE_ENTITY);
            }
            disciplina = disciplinaRepo.findById(req.disciplinaId())
                    .filter(d -> Boolean.TRUE.equals(d.getAtivo()))
                    .orElseThrow(() -> new RegraNegocioException(
                            "Disciplina inválida.", HttpStatus.UNPROCESSABLE_ENTITY));
        }

        // 1) docente da disciplina; 2) responsável da categoria (sobe pela árvore); 3) triagem
        Usuario responsavel = null;
        String origem = null;
        if (disciplina != null && disciplina.getDocente() != null
                && "ATIVO".equals(disciplina.getDocente().getStatus())) {
            responsavel = disciplina.getDocente();
            origem = "docente da disciplina " + disciplina.getNome();
        } else {
            responsavel = resolverResponsavelPadrao(categoria);
            if (responsavel != null) origem = "responsável pela categoria " + categoria.getNome();
        }

        Chamado chamado = new Chamado();
        chamado.setProtocolo(protocoloGenerator.proximo());
        chamado.setUsuario(usuario);
        chamado.setCategoria(categoria);
        chamado.setDisciplina(disciplina);
        chamado.setResponsavel(responsavel);
        chamado.setStatus(buscarStatus(responsavel != null ? "ATRIBUIDO" : "ABERTO"));
        chamado.setTitulo(req.titulo().trim());
        chamado.setDescricao(req.descricao().trim());
        chamadoRepo.save(chamado);

        registrar(chamado, usuario, "ABERTURA", "Chamado aberto.");
        if (responsavel != null) {
            registrar(chamado, usuario, "ATRIBUICAO",
                    "Atribuído automaticamente a " + responsavel.getNome() + " (" + origem + ").");
        }
        return paraResumo(chamado);
    }

    @Transactional(readOnly = true)
    public List<ChamadoResumo> listar(String email) {
        Usuario usuario = buscarUsuario(email);
        Set<String> perfis = perfisDe(usuario);

        List<Chamado> chamados;
        if (perfis.contains("ADMIN")) {
            chamados = chamadoRepo.findAllByOrderByCriadoEmDesc();
        } else if (perfis.contains("DOCENTE")) {
            chamados = chamadoRepo.findByResponsavelIdOrderByCriadoEmDesc(usuario.getId());
        } else {
            chamados = chamadoRepo.findByUsuarioIdOrderByCriadoEmDesc(usuario.getId());
        }
        return chamados.stream().map(this::paraResumo).toList();
    }

    // ===== Detalhe, mensagem, atribuição e status =====

    @Transactional(readOnly = true)
    public ChamadoDetalhe detalhe(String email, Long chamadoId) {
        Usuario usuario = buscarUsuario(email);
        Chamado chamado = buscarChamado(chamadoId);
        exigirAcesso(usuario, chamado);

        List<InteracaoResponse> historico = interacaoRepo
                .findByChamadoIdOrderByCriadoEmAsc(chamado.getId()).stream()
                .map(i -> new InteracaoResponse(i.getId(), i.getUsuario().getNome(),
                        i.getTipo(), i.getCanal(), i.getMensagem(), i.getCriadoEm()))
                .toList();

        return new ChamadoDetalhe(
                chamado.getId(), chamado.getProtocolo(), chamado.getTitulo(), chamado.getDescricao(),
                chamado.getCategoria().getNome(),
                chamado.getDisciplina() == null ? null : chamado.getDisciplina().getNome(),
                chamado.getStatus().getNome(), chamado.getPrioridade(),
                chamado.getUsuario().getNome(),
                chamado.getResponsavel() == null ? null : chamado.getResponsavel().getNome(),
                chamado.getCriadoEm(), chamado.getAtualizadoEm(), chamado.getEncerradoEm(),
                historico);
    }

    @Transactional
    public void adicionarMensagem(String email, Long chamadoId, MensagemRequest req) {
        Usuario usuario = buscarUsuario(email);
        Chamado chamado = buscarChamado(chamadoId);
        exigirAcesso(usuario, chamado);

        if (FINAIS.contains(chamado.getStatus().getNome())) {
            throw new RegraNegocioException(
                    "Chamado " + chamado.getStatus().getNome().toLowerCase() + " não aceita novas mensagens.",
                    HttpStatus.CONFLICT);
        }
        registrar(chamado, usuario, "MENSAGEM", req.mensagem().trim());
    }

    @Transactional
    public ChamadoResumo atribuir(String email, Long chamadoId, AtribuicaoRequest req) {
        Usuario admin = buscarUsuario(email);
        Chamado chamado = buscarChamado(chamadoId);

        if (FINAIS.contains(chamado.getStatus().getNome())) {
            throw new RegraNegocioException(
                    "Não é possível atribuir um chamado " + chamado.getStatus().getNome().toLowerCase() + ".",
                    HttpStatus.CONFLICT);
        }

        Usuario docente = usuarioRepo.findById(req.responsavelId())
                .filter(u -> perfisDe(u).contains("DOCENTE"))
                .orElseThrow(() -> new RegraNegocioException(
                        "Responsável deve ser um docente válido.", HttpStatus.UNPROCESSABLE_ENTITY));

        chamado.setResponsavel(docente);
        chamado.setStatus(buscarStatus("ATRIBUIDO"));
        registrar(chamado, admin, "ATRIBUICAO", "Chamado atribuído a " + docente.getNome() + ".");
        return paraResumo(chamado);
    }

    @Transactional
    public ChamadoResumo alterarStatus(String email, Long chamadoId, StatusRequest req) {
        Usuario usuario = buscarUsuario(email);
        Chamado chamado = buscarChamado(chamadoId);
        exigirAcesso(usuario, chamado);

        Set<String> perfis = perfisDe(usuario);
        boolean admin = perfis.contains("ADMIN");
        boolean responsavel = chamado.getResponsavel() != null
                && chamado.getResponsavel().getId().equals(usuario.getId());
        boolean dono = chamado.getUsuario().getId().equals(usuario.getId());

        String novo = req.status().trim().toUpperCase();
        String atual = chamado.getStatus().getNome();
        StatusChamado novoStatus = statusRepo.findByNome(novo)
                .orElseThrow(() -> new RegraNegocioException("Status inválido.", HttpStatus.UNPROCESSABLE_ENTITY));

        if (novo.equals(atual)) {
            throw new RegraNegocioException("O chamado já está nesse status.", HttpStatus.CONFLICT);
        }
        if (FINAIS.contains(atual)) {
            throw new RegraNegocioException(
                    "Chamado " + atual.toLowerCase() + " não pode mais ser alterado.", HttpStatus.CONFLICT);
        }

        if (admin || responsavel) {
            if (novo.equals("ABERTO")) {
                throw new RegraNegocioException("Não é possível voltar o chamado para ABERTO.",
                        HttpStatus.UNPROCESSABLE_ENTITY);
            }
        } else if (dono) {
            // discente: só cancela o próprio chamado, enquanto não estiver resolvido
            if (!novo.equals("CANCELADO") || atual.equals("RESOLVIDO")) {
                throw new RegraNegocioException(
                        "Você só pode cancelar o chamado enquanto ele não estiver resolvido.", HttpStatus.FORBIDDEN);
            }
        } else {
            throw new RegraNegocioException("Acesso negado.", HttpStatus.FORBIDDEN);
        }

        chamado.setStatus(novoStatus);
        if (FINAIS.contains(novo)) {
            chamado.setEncerradoEm(LocalDateTime.now());
        }

        String texto = "Status alterado de " + atual + " para " + novo + ".";
        if (req.mensagem() != null && !req.mensagem().isBlank()) {
            texto += " " + req.mensagem().trim();
        }
        registrar(chamado, usuario, "MUDANCA_STATUS", texto);
        return paraResumo(chamado);
    }

    // ===== Dashboard e docentes =====

    @Transactional(readOnly = true)
    public List<DashboardItem> dashboard(String email) {
        Usuario usuario = buscarUsuario(email);
        Set<String> perfis = perfisDe(usuario);

        List<Object[]> linhas;
        if (perfis.contains("ADMIN")) {
            linhas = chamadoRepo.contarPorStatus();
        } else if (perfis.contains("DOCENTE")) {
            linhas = chamadoRepo.contarPorStatusDoResponsavel(usuario.getId());
        } else {
            linhas = chamadoRepo.contarPorStatusDoUsuario(usuario.getId());
        }

        return linhas.stream()
                .map(l -> new DashboardItem((String) l[0], ((Number) l[1]).longValue()))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<DocenteResumo> docentes() {
        return usuarioRepo.findByPerfil("DOCENTE").stream()
                .map(u -> new DocenteResumo(u.getId(), u.getNome(), u.getEmail()))
                .toList();
    }

    // ===== Apoio =====

    private void exigirAcesso(Usuario usuario, Chamado chamado) {
        boolean admin = perfisDe(usuario).contains("ADMIN");
        boolean dono = chamado.getUsuario().getId().equals(usuario.getId());
        boolean responsavel = chamado.getResponsavel() != null
                && chamado.getResponsavel().getId().equals(usuario.getId());
        if (!(admin || dono || responsavel)) {
            throw new RegraNegocioException("Acesso negado.", HttpStatus.FORBIDDEN);
        }
    }

    private void registrar(Chamado chamado, Usuario autor, String tipo, String mensagem) {
        Interacao i = new Interacao();
        i.setChamado(chamado);
        i.setUsuario(autor);
        i.setTipo(tipo);
        i.setMensagem(mensagem);
        interacaoRepo.save(i);
    }

    private Set<String> perfisDe(Usuario u) {
        return u.getPerfis().stream().map(Perfil::getNome).collect(Collectors.toSet());
    }

    private Usuario buscarUsuario(String email) {
        return usuarioRepo.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new RegraNegocioException("Usuário não encontrado.", HttpStatus.NOT_FOUND));
    }

    private Chamado buscarChamado(Long id) {
        return chamadoRepo.findById(id)
                .orElseThrow(() -> new RegraNegocioException("Chamado não encontrado.", HttpStatus.NOT_FOUND));
    }

    private StatusChamado buscarStatus(String nome) {
        return statusRepo.findByNome(nome)
                .orElseThrow(() -> new IllegalStateException("Status " + nome + " não existe"));
    }

    private ChamadoResumo paraResumo(Chamado c) {
        return new ChamadoResumo(
                c.getId(), c.getProtocolo(), c.getTitulo(),
                c.getCategoria().getNome(),
                c.getDisciplina() == null ? null : c.getDisciplina().getNome(),
                c.getStatus().getNome(),
                c.getUsuario().getNome(),
                c.getResponsavel() == null ? null : c.getResponsavel().getNome(),
                c.getCriadoEm());
    }

    /** Sobe pela árvore de categorias até achar um responsável padrão ativo. */
    private Usuario resolverResponsavelPadrao(Categoria categoria) {
        Categoria atual = categoria;
        while (atual != null) {
            Usuario r = atual.getResponsavelPadrao();
            if (r != null && "ATIVO".equals(r.getStatus())) return r;
            atual = atual.getCategoriaPai();
        }
        return null;
    }
}