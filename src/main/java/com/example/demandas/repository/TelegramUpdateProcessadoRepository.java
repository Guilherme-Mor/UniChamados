package com.example.demandas.repository;

import com.example.demandas.entity.TelegramUpdateProcessado;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

public interface TelegramUpdateProcessadoRepository extends JpaRepository<TelegramUpdateProcessado, Long> {

    /** Devolve 1 se o evento é novo e 0 se já tinha sido processado (trava contra repetição). */
    @Transactional
    @Modifying
    @Query(value = "INSERT INTO telegram_update_processado (update_id) VALUES (:id) ON CONFLICT DO NOTHING",
            nativeQuery = true)
    int registrar(@Param("id") Long id);

    @Transactional
    @Modifying
    @Query("delete from TelegramUpdateProcessado u where u.processadoEm < :limite")
    int apagarAnteriores(@Param("limite") LocalDateTime limite);
}