package com.example.demandas.repository;

import com.example.demandas.entity.Usuario;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface UsuarioRepository extends JpaRepository<Usuario, Integer> {
    Optional<Usuario> findByEmailIgnoreCase(String email);
    boolean existsByEmailIgnoreCase(String email);
    boolean existsByCpf(String cpf);
    boolean existsByMatricula(String matricula);

    @Query("select u from Usuario u join u.perfis p where p.nome = :perfil and u.status = 'ATIVO'")
    List<Usuario> findByPerfil(String perfil);
}