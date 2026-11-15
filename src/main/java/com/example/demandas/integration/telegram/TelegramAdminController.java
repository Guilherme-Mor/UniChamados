package com.example.demandas.integration.telegram;

import com.example.demandas.dto.TelegramAdminDtos;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/telegram")
@PreAuthorize("hasRole('ADMIN')")
public class TelegramAdminController {

    private final TelegramAdminService servico;

    public TelegramAdminController(TelegramAdminService servico) {
        this.servico = servico;
    }

    @GetMapping
    public TelegramAdminDtos.Painel painel() {
        return servico.painel();
    }

    @PostMapping("/docentes/{id}/liberar")
    public ResponseEntity<Void> liberarDocente(@PathVariable Integer id, Authentication auth) {
        servico.liberarDocente(auth.getName(), id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/chats/{chatId}/liberar")
    public ResponseEntity<Void> liberarChat(@PathVariable Long chatId, Authentication auth) {
        servico.liberarChat(auth.getName(), chatId);
        return ResponseEntity.noContent().build();
    }
}