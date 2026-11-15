package com.example.demandas.repository;

import com.example.demandas.entity.TelegramTentativaInvalida;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface TelegramTentativaInvalidaRepository extends JpaRepository<TelegramTentativaInvalida, Long> {

    long countByChatIdAndCriadoEmAfter(Long chatId, LocalDateTime desde);

    Optional<TelegramTentativaInvalida> findFirstByChatIdOrderByCriadoEmDesc(Long chatId);

    /** Chats com o limite de tentativas atingido: [chatId, total, última tentativa]. */
    @Query("select t.chatId, count(t), max(t.criadoEm) from TelegramTentativaInvalida t "
            + "where t.criadoEm > :desde group by t.chatId having count(t) >= :limite")
    List<Object[]> bloqueados(@Param("desde") LocalDateTime desde, @Param("limite") long limite);

    @Transactional
    @Modifying
    @Query("delete from TelegramTentativaInvalida t where t.chatId = :chatId")
    int apagarDoChat(@Param("chatId") Long chatId);

    @Transactional
    @Modifying
    @Query("delete from TelegramTentativaInvalida t where t.criadoEm < :limite")
    int apagarAnteriores(@Param("limite") LocalDateTime limite);
}