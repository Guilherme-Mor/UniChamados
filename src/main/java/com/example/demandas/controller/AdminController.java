package com.example.demandas.controller;

import com.example.demandas.dto.*;
import com.example.demandas.service.AdminService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@PreAuthorize("hasRole('ADMIN')")
public class AdminController {

    private final AdminService adminService;

    public AdminController(AdminService adminService) {
        this.adminService = adminService;
    }

    @GetMapping("/api/usuarios")
    public List<UsuarioAdminResponse> usuarios() {
        return adminService.listarUsuarios();
    }

    @PatchMapping("/api/usuarios/{id}/status")
    public UsuarioAdminResponse alterarStatus(@PathVariable Integer id,
                                              @Valid @RequestBody UsuarioStatusRequest req,
                                              Authentication auth) {
        return adminService.alterarStatus(auth.getName(), id, req.ativo());
    }

    @GetMapping("/api/cadastros-institucionais")
    public List<CadastroInstitucionalResponse> cadastros() {
        return adminService.listarCadastros();
    }

    @PostMapping("/api/cadastros-institucionais")
    public ResponseEntity<CadastroInstitucionalResponse> criarCadastro(
            @Valid @RequestBody CadastroInstitucionalRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(adminService.criarCadastro(req));
    }
}