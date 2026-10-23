package com.example.demandas.controller;

import com.example.demandas.dto.CadastroRequest;
import com.example.demandas.dto.MeResponse;
import com.example.demandas.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/api/auth/cadastro")
    public ResponseEntity<MeResponse> cadastrar(@Valid @RequestBody CadastroRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.cadastrar(req));
    }

    @GetMapping("/api/me")
    public MeResponse me(Authentication authentication) {
        return authService.usuarioAtual(authentication.getName());
    }
}