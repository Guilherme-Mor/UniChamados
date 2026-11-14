package com.example.demandas.repository;

import com.example.demandas.entity.TelegramMensagem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface TelegramMensagemRepository extends JpaRepository<TelegramMensagem, Long> {

    Optional<TelegramMensagem> findByChatIdAndMessageId(Long chatId, Long messageId);
}