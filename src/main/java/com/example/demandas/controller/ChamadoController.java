package com.example.demandas.controller;

import com.example.demandas.dto.*;
import com.example.demandas.service.ChamadoConsultaService;
import com.example.demandas.service.ChamadoService;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@RestController
public class ChamadoController {

    private final ChamadoService chamadoService;
    private final ChamadoConsultaService consultaService;

    public ChamadoController(ChamadoService chamadoService, ChamadoConsultaService consultaService) {
        this.chamadoService = chamadoService;
        this.consultaService = consultaService;
    }

    @PostMapping("/api/chamados")
    @PreAuthorize("hasRole('DISCENTE')")
    public ResponseEntity<ChamadoResumo> abrir(@Valid @RequestBody ChamadoRequest req,
                                               Authentication auth) {
        return ResponseEntity.status(HttpStatus.CREATED).body(chamadoService.abrir(auth.getName(), req));
    }

    @GetMapping("/api/chamados")
    public PaginaChamados listar(Authentication auth,
                                 @RequestParam(defaultValue = "0") int pagina,
                                 @RequestParam(defaultValue = "10") int tamanho,
                                 @RequestParam(required = false) String busca,
                                 @RequestParam(required = false) String status,
                                 @RequestParam(required = false)
                                 @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate de,
                                 @RequestParam(required = false)
                                 @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate ate) {
        return consultaService.listar(auth.getName(), pagina, tamanho, busca, status, de, ate);
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