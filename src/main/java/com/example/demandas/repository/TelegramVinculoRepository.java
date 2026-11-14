package com.example.demandas.repository;

import com.example.demandas.entity.TelegramVinculo;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface TelegramVinculoRepository extends JpaRepository<TelegramVinculo, Integer> {

    // Os índices parciais garantem no máximo um vínculo PENDENTE/ATIVO por docente e por chat
    Optional<TelegramVinculo> findByUsuarioIdAndStatusIn(Integer usuarioId, Collection<String> status);

    Optional<TelegramVinculo> findByChatIdAndStatusIn(Long chatId, Collection<String> status);

    List<TelegramVinculo> findByStatusAndExpiraEmBefore(String status, LocalDateTime limite);
}