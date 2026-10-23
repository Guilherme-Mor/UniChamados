package com.example.demandas.controller;

import com.example.demandas.dto.CategoriaNo;
import com.example.demandas.dto.ResponsavelPadraoRequest;
import com.example.demandas.service.CategoriaService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

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

    @PatchMapping("/api/categorias/{id}/responsavel-padrao")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> definirResponsavelPadrao(@PathVariable Integer id,
                                                         @RequestBody ResponsavelPadraoRequest req) {
        categoriaService.definirResponsavelPadrao(id, req.responsavelId());
        return ResponseEntity.noContent().build();
    }
}