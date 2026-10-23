package com.example.demandas.repository;

import com.example.demandas.entity.Categoria;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CategoriaRepository extends JpaRepository<Categoria, Integer> {
    List<Categoria> findByAtivoTrueOrderByOrdemAsc();

    boolean existsByCategoriaPaiIdAndAtivoTrue(Integer paiId);
}