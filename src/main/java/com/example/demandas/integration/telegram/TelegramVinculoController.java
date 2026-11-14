package com.example.demandas.integration.telegram;

import com.example.demandas.dto.TelegramCodigoResponse;
import com.example.demandas.dto.TelegramEstadoResponse;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/telegram/vinculo")
@PreAuthorize("hasRole('DOCENTE')")
public class TelegramVinculoController {

    private final TelegramVinculoService servico;
    private final TelegramAvisos avisos;

    public TelegramVinculoController(TelegramVinculoService servico, TelegramAvisos avisos) {
        this.servico = servico;
        this.avisos = avisos;
    }

    @GetMapping
    public TelegramEstadoResponse estado(Authentication auth) {
        return servico.estado(auth.getName());
    }

    /** POST e sem cache: a resposta contém um código válido e não pode ficar em histórico nem em cache. */
    @PostMapping("/codigo")
    public ResponseEntity<TelegramCodigoResponse> gerarCodigo(Authentication auth) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(servico.gerarCodigo(auth.getName()));
    }

    @PostMapping("/confirmar")
    public ResponseEntity<Void> confirmar(Authentication auth) {
        avisos.enviar(servico.confirmar(auth.getName()));   // o aviso só sai depois do commit
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/recusar")
    public ResponseEntity<Void> recusar(Authentication auth) {
        avisos.enviar(servico.recusar(auth.getName()));
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping
    public ResponseEntity<Void> desvincular(Authentication auth) {
        avisos.enviar(servico.desvincular(auth.getName()));
        return ResponseEntity.noContent().build();
    }
}