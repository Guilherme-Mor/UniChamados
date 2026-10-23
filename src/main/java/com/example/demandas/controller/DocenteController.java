package com.example.demandas.controller;

import com.example.demandas.dto.DocenteResumo;
import com.example.demandas.service.ChamadoService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class DocenteController {

    private final ChamadoService chamadoService;

    public DocenteController(ChamadoService chamadoService) {
        this.chamadoService = chamadoService;
    }

    @GetMapping("/api/docentes")
    @PreAuthorize("hasRole('ADMIN')")
    public List<DocenteResumo> listar() {
        return chamadoService.docentes();
    }
}