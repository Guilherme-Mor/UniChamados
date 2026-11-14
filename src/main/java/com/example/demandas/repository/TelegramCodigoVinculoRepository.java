package com.example.demandas.repository;

import com.example.demandas.entity.TelegramCodigoVinculo;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface TelegramCodigoVinculoRepository extends JpaRepository<TelegramCodigoVinculo, Integer> {

    Optional<TelegramCodigoVinculo> findByCodigoHash(String codigoHash);

    /** Quantos códigos o docente gerou desde o instante informado (limite por hora). */
    long countByUsuarioIdAndCriadoEmAfter(Integer usuarioId, LocalDateTime desde);

    /** Códigos ainda válidos do docente (para invalidar ao gerar um novo). */
    List<TelegramCodigoVinculo> findByUsuarioIdAndUsadoEmIsNullAndExpiraEmAfter(Integer usuarioId, LocalDateTime agora);
}