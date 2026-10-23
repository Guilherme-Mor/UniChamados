package com.example.demandas.service;

import com.example.demandas.dto.ChamadoResumo;
import com.example.demandas.dto.PaginaChamados;
import com.example.demandas.entity.Chamado;
import com.example.demandas.entity.Perfil;
import com.example.demandas.entity.Usuario;
import com.example.demandas.exception.RegraNegocioException;
import com.example.demandas.repository.ChamadoRepository;
import com.example.demandas.repository.UsuarioRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.Normalizer;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class ChamadoConsultaService {

    private final ChamadoRepository chamadoRepo;
    private final UsuarioRepository usuarioRepo;

    public ChamadoConsultaService(ChamadoRepository chamadoRepo, UsuarioRepository usuarioRepo) {
        this.chamadoRepo = chamadoRepo;
        this.usuarioRepo = usuarioRepo;
    }

    @Transactional(readOnly = true)
    public PaginaChamados listar(String email, int pagina, int tamanho,
                                 String busca, String status, LocalDate de, LocalDate ate) {
        Usuario usuario = usuarioRepo.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new RegraNegocioException("Usuário não encontrado.", HttpStatus.NOT_FOUND));
        Set<String> perfis = usuario.getPerfis().stream().map(Perfil::getNome).collect(Collectors.toSet());

        // 1) escopo por perfil (quem pode ver o quê)
        List<Chamado> base;
        if (perfis.contains("ADMIN")) {
            base = chamadoRepo.findAllByOrderByCriadoEmDesc();
        } else if (perfis.contains("DOCENTE")) {
            base = chamadoRepo.findByResponsavelIdOrderByCriadoEmDesc(usuario.getId());
        } else {
            base = chamadoRepo.findByUsuarioIdOrderByCriadoEmDesc(usuario.getId());
        }

        // 2) filtros
        String termo = normalizar(busca);
        String statusFiltro = status == null ? "" : status.trim().toUpperCase();

        List<Chamado> filtrados = base.stream()
                .filter(c -> statusFiltro.isEmpty() || c.getStatus().getNome().equals(statusFiltro))
                .filter(c -> de == null || !c.getCriadoEm().toLocalDate().isBefore(de))
                .filter(c -> ate == null || !c.getCriadoEm().toLocalDate().isAfter(ate))
                .filter(c -> termo.isEmpty() || textoBusca(c).contains(termo))
                .toList();

        // 3) paginação
        int tam = Math.min(Math.max(tamanho, 1), 50);
        int total = filtrados.size();
        int totalPaginas = Math.max(1, (int) Math.ceil(total / (double) tam));
        int pag = Math.min(Math.max(pagina, 0), totalPaginas - 1);

        List<ChamadoResumo> conteudo = filtrados.stream()
                .skip((long) pag * tam)
                .limit(tam)
                .map(this::paraResumo)
                .toList();

        return new PaginaChamados(conteudo, pag, tam, total, totalPaginas);
    }

    private String textoBusca(Chamado c) {
        StringBuilder sb = new StringBuilder();
        sb.append(c.getProtocolo()).append(' ')
                .append(c.getTitulo()).append(' ')
                .append(c.getDescricao()).append(' ')
                .append(c.getCategoria().getNome()).append(' ')
                .append(c.getUsuario().getNome()).append(' ');
        if (c.getDisciplina() != null) sb.append(c.getDisciplina().getNome()).append(' ');
        if (c.getResponsavel() != null) sb.append(c.getResponsavel().getNome());
        return normalizar(sb.toString());
    }

    private static String normalizar(String texto) {
        if (texto == null) return "";
        return Normalizer.normalize(texto, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase()
                .trim();
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
}