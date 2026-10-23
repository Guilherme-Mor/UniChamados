package com.example.demandas.repository;

import com.example.demandas.entity.StatusChamado;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface StatusChamadoRepository extends JpaRepository<StatusChamado, Integer> {
    Optional<StatusChamado> findByNome(String nome);
}