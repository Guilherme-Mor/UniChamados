package com.example.demandas.service;

import com.example.demandas.dto.CategoriaNo;
import com.example.demandas.entity.Categoria;
import com.example.demandas.entity.Usuario;
import com.example.demandas.exception.RegraNegocioException;
import com.example.demandas.repository.CategoriaRepository;
import com.example.demandas.repository.UsuarioRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class CategoriaService {

    private final CategoriaRepository categoriaRepo;
    private final UsuarioRepository usuarioRepo;

    public CategoriaService(CategoriaRepository categoriaRepo, UsuarioRepository usuarioRepo) {
        this.categoriaRepo = categoriaRepo;
        this.usuarioRepo = usuarioRepo;
    }

    @Transactional(readOnly = true)
    public List<CategoriaNo> arvore() {
        List<Categoria> todas = categoriaRepo.findByAtivoTrueOrderByOrdemAsc();

        Map<Integer, List<Categoria>> porPai = new HashMap<>();
        for (Categoria c : todas) {
            Integer paiId = c.getCategoriaPai() == null ? null : c.getCategoriaPai().getId();
            porPai.computeIfAbsent(paiId, k -> new ArrayList<>()).add(c);
        }

        return porPai.getOrDefault(null, List.of()).stream()
                .map(c -> montar(c, porPai))
                .toList();
    }

    @Transactional
    public void definirResponsavelPadrao(Integer categoriaId, Integer responsavelId) {
        Categoria categoria = categoriaRepo.findById(categoriaId)
                .orElseThrow(() -> new RegraNegocioException("Categoria não encontrada.", HttpStatus.NOT_FOUND));

        if (responsavelId == null) {
            categoria.setResponsavelPadrao(null);
            return;
        }

        Usuario docente = usuarioRepo.findById(responsavelId)
                .filter(u -> "ATIVO".equals(u.getStatus()))
                .filter(u -> u.getPerfis().stream().anyMatch(p -> "DOCENTE".equals(p.getNome())))
                .orElseThrow(() -> new RegraNegocioException(
                        "Responsável deve ser um docente válido.", HttpStatus.UNPROCESSABLE_ENTITY));
        categoria.setResponsavelPadrao(docente);
    }

    private CategoriaNo montar(Categoria c, Map<Integer, List<Categoria>> porPai) {
        List<CategoriaNo> filhos = porPai.getOrDefault(c.getId(), List.of()).stream()
                .map(f -> montar(f, porPai))
                .toList();
        Usuario resp = c.getResponsavelPadrao();
        return new CategoriaNo(c.getId(), c.getNome(), c.getDescricao(),
                c.getExigeDisciplina(),
                resp == null ? null : resp.getId(),
                resp == null ? null : resp.getNome(),
                filhos);
    }
}