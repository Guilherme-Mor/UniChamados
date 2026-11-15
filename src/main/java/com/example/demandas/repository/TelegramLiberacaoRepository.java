package com.example.demandas.repository;

import com.example.demandas.entity.TelegramLiberacao;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface TelegramLiberacaoRepository extends JpaRepository<TelegramLiberacao, Integer> {

    Optional<TelegramLiberacao> findFirstByTipoAndUsuarioIdOrderByCriadoEmDesc(String tipo, Integer usuarioId);
}