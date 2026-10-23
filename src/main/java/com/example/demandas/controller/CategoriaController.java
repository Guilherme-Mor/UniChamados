package com.example.demandas.controller;

import com.example.demandas.dto.CategoriaNo;
import com.example.demandas.service.CategoriaService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class CategoriaController {

    private final CategoriaService categoriaService;

    public CategoriaController(CategoriaService categoriaService) {
        this.categoriaService = categoriaService;
    }

    @GetMapping("/api/categorias/arvore")
    public List<CategoriaNo> arvore() {
        return categoriaService.arvore();
    }
}