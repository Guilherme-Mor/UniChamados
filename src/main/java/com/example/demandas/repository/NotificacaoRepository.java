package com.example.demandas.repository;

import com.example.demandas.entity.Notificacao;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;

public interface NotificacaoRepository extends JpaRepository<Notificacao, Long> {

    /**
     * Reserva a próxima notificação vencida. O SKIP LOCKED faz com que uma segunda instância
     * da aplicação pule a que já está sendo enviada, em vez de enviá-la de novo.
     * Precisa de transação ativa (o bloqueio dura até o commit).
     */
    @Query(value = "SELECT * FROM notificacao WHERE status = 'PENDENTE' AND proximo_envio <= :agora "
            + "ORDER BY id LIMIT 1 FOR UPDATE SKIP LOCKED", nativeQuery = true)
    Optional<Notificacao> reservarProxima(@Param("agora") LocalDateTime agora);
}