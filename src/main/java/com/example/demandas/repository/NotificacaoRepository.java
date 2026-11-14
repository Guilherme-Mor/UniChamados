package com.example.demandas.repository;

import com.example.demandas.entity.Notificacao;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface NotificacaoRepository extends JpaRepository<Notificacao, Long> {

    /** Próximas notificações a enviar, da mais antiga para a mais nova. */
    List<Notificacao> findTop20ByStatusAndProximoEnvioLessThanEqualOrderByIdAsc(String status, LocalDateTime agora);
}