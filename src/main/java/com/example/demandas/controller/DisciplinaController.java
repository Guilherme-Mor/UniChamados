package com.example.demandas.controller;

import com.example.demandas.dto.DisciplinaEdicaoRequest;
import com.example.demandas.dto.DisciplinaRequest;
import com.example.demandas.dto.DisciplinaResponse;
import com.example.demandas.dto.DocenteDisciplinaRequest;
import com.example.demandas.service.DisciplinaService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
public class DisciplinaController {

    private final DisciplinaService disciplinaService;

    public DisciplinaController(DisciplinaService disciplinaService) {
        this.disciplinaService = disciplinaService;
    }

    @GetMapping("/api/disciplinas")
    public List<DisciplinaResponse> listar() {
        return disciplinaService.listar();
    }

    @PostMapping("/api/disciplinas")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<DisciplinaResponse> criar(@Valid @RequestBody DisciplinaRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(disciplinaService.criar(req));
    }

    @PutMapping("/api/disciplinas/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public DisciplinaResponse atualizar(@PathVariable Integer id,
                                        @Valid @RequestBody DisciplinaEdicaoRequest req) {
        return disciplinaService.atualizar(id, req);
    }

    @PatchMapping("/api/disciplinas/{id}/docente")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> definirDocente(@PathVariable Integer id,
                                               @RequestBody DocenteDisciplinaRequest req) {
        disciplinaService.definirDocente(id, req.docenteId());
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/api/disciplinas/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public Map<String, String> remover(@PathVariable Integer id) {
        return Map.of("mensagem", disciplinaService.remover(id));
    }
}