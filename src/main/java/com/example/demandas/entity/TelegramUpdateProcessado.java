package com.example.demandas.entity;

import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "telegram_update_processado")
public class TelegramUpdateProcessado {

    @Id
    @Column(name = "update_id")
    private Long updateId;

    @Column(name = "processado_em", nullable = false)
    private LocalDateTime processadoEm;

    public Long getUpdateId() { return updateId; }
    public LocalDateTime getProcessadoEm() { return processadoEm; }
}