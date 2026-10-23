package com.example.demandas.controller;

import com.example.demandas.dto.DashboardItem;
import com.example.demandas.service.ChamadoService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class DashboardController {

    private final ChamadoService chamadoService;

    public DashboardController(ChamadoService chamadoService) {
        this.chamadoService = chamadoService;
    }

    @GetMapping("/api/dashboard")
    public List<DashboardItem> dashboard(Authentication auth) {
        return chamadoService.dashboard(auth.getName());
    }
}