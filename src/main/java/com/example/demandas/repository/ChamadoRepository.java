package com.example.demandas.repository;

import com.example.demandas.entity.Chamado;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface ChamadoRepository extends JpaRepository<Chamado, Long> {
    List<Chamado> findByUsuarioIdOrderByCriadoEmDesc(Integer usuarioId);
    List<Chamado> findByResponsavelIdOrderByCriadoEmDesc(Integer responsavelId);
    List<Chamado> findAllByOrderByCriadoEmDesc();

    @Query("select c.status.nome, count(c) from Chamado c group by c.status.nome")
    List<Object[]> contarPorStatus();

    @Query("select c.status.nome, count(c) from Chamado c where c.usuario.id = :id group by c.status.nome")
    List<Object[]> contarPorStatusDoUsuario(Integer id);

    @Query("select c.status.nome, count(c) from Chamado c where c.responsavel.id = :id group by c.status.nome")
    List<Object[]> contarPorStatusDoResponsavel(Integer id);
}