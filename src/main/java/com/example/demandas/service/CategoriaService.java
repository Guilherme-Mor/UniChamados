package com.example.demandas.service;

import com.example.demandas.dto.CategoriaNo;
import com.example.demandas.entity.Categoria;
import com.example.demandas.repository.CategoriaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class CategoriaService {

    private final CategoriaRepository categoriaRepo;

    public CategoriaService(CategoriaRepository categoriaRepo) {
        this.categoriaRepo = categoriaRepo;
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

    private CategoriaNo montar(Categoria c, Map<Integer, List<Categoria>> porPai) {
        List<CategoriaNo> filhos = porPai.getOrDefault(c.getId(), List.of()).stream()
                .map(f -> montar(f, porPai))
                .toList();
        return new CategoriaNo(c.getId(), c.getNome(), c.getDescricao(), c.getExigeDisciplina(), filhos);
    }
}