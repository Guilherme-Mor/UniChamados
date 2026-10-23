package com.example.demandas.repository;

import com.example.demandas.entity.Interacao;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface InteracaoRepository extends JpaRepository<Interacao, Long> {
    List<Interacao> findByChamadoIdOrderByCriadoEmAsc(Long chamadoId);
}