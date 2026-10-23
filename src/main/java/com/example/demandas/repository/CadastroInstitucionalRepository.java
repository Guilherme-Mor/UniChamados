package com.example.demandas.repository;

import com.example.demandas.entity.CadastroInstitucional;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CadastroInstitucionalRepository extends JpaRepository<CadastroInstitucional, Integer> {
    Optional<CadastroInstitucional> findByMatriculaAndCpfAndEmailIgnoreCase(String matricula, String cpf, String email);
}