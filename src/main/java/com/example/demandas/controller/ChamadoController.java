package com.example.demandas.controller;

import com.example.demandas.dto.*;
import com.example.demandas.service.ChamadoService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
public class ChamadoController {

    private final ChamadoService chamadoService;

    public ChamadoController(ChamadoService chamadoService) {
        this.chamadoService = chamadoService;
    }

    @PostMapping("/api/chamados")
    @PreAuthorize("hasRole('DISCENTE')")
    public ResponseEntity<ChamadoResumo> abrir(@Valid @RequestBody ChamadoRequest req,
                                               Authentication auth) {
        return ResponseEntity.status(HttpStatus.CREATED).body(chamadoService.abrir(auth.getName(), req));
    }

    @GetMapping("/api/chamados")
    public List<ChamadoResumo> listar(Authentication auth) {
        return chamadoService.listar(auth.getName());
    }

    @GetMapping("/api/chamados/{id}")
    public ChamadoDetalhe detalhe(@PathVariable Long id, Authentication auth) {
        return chamadoService.detalhe(auth.getName(), id);
    }

    @PostMapping("/api/chamados/{id}/interacoes")
    public ResponseEntity<Void> mensagem(@PathVariable Long id,
                                         @Valid @RequestBody MensagemRequest req,
                                         Authentication auth) {
        chamadoService.adicionarMensagem(auth.getName(), id, req);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @PatchMapping("/api/chamados/{id}/responsavel")
    @PreAuthorize("hasRole('ADMIN')")
    public ChamadoResumo atribuir(@PathVariable Long id,
                                  @Valid @RequestBody AtribuicaoRequest req,
                                  Authentication auth) {
        return chamadoService.atribuir(auth.getName(), id, req);
    }

    @PatchMapping("/api/chamados/{id}/status")
    public ChamadoResumo status(@PathVariable Long id,
                                @Valid @RequestBody StatusRequest req,
                                Authentication auth) {
        return chamadoService.alterarStatus(auth.getName(), id, req);
    }
}