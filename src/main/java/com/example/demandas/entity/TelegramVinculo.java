package com.example.demandas.entity;

import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "telegram_vinculo")
public class TelegramVinculo {

    public static final String PENDENTE = "PENDENTE";
    public static final String ATIVO = "ATIVO";
    public static final String REVOGADO = "REVOGADO";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "usuario_id")
    private Usuario usuario;

    @Column(name = "chat_id", nullable = false)
    private Long chatId;

    @Column(name = "telegram_user_id")
    private Long telegramUserId;

    @Column(length = 64)
    private String username;

    @Column(name = "nome_exibicao", length = 200)
    private String nomeExibicao;

    @Column(nullable = false, length = 20)
    private String status = PENDENTE;

    @Column(name = "motivo_revogacao", length = 30)
    private String motivoRevogacao;

    /** Admin que revogou o vínculo (só preenchido quando a revogação foi feita por um admin). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "revogado_por_id")
    private Usuario revogadoPor;

    @Column(name = "expira_em")
    private LocalDateTime expiraEm;

    @Column(name = "criado_em", nullable = false)
    private LocalDateTime criadoEm;

    @Column(name = "confirmado_em")
    private LocalDateTime confirmadoEm;

    @Column(name = "atualizado_em", nullable = false)
    private LocalDateTime atualizadoEm;

    @PrePersist
    void antesDeSalvar() {
        LocalDateTime agora = LocalDateTime.now();
        if (criadoEm == null) criadoEm = agora;
        atualizadoEm = agora;
    }

    @PreUpdate
    void antesDeAtualizar() {
        atualizadoEm = LocalDateTime.now();
    }

    public Integer getId() { return id; }
    public Usuario getUsuario() { return usuario; }
    public void setUsuario(Usuario usuario) { this.usuario = usuario; }
    public Long getChatId() { return chatId; }
    public void setChatId(Long chatId) { this.chatId = chatId; }
    public Long getTelegramUserId() { return telegramUserId; }
    public void setTelegramUserId(Long telegramUserId) { this.telegramUserId = telegramUserId; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getNomeExibicao() { return nomeExibicao; }
    public void setNomeExibicao(String nomeExibicao) { this.nomeExibicao = nomeExibicao; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getMotivoRevogacao() { return motivoRevogacao; }
    public void setMotivoRevogacao(String motivoRevogacao) { this.motivoRevogacao = motivoRevogacao; }
    public Usuario getRevogadoPor() { return revogadoPor; }
    public void setRevogadoPor(Usuario revogadoPor) { this.revogadoPor = revogadoPor; }
    public LocalDateTime getExpiraEm() { return expiraEm; }
    public void setExpiraEm(LocalDateTime expiraEm) { this.expiraEm = expiraEm; }
    public LocalDateTime getCriadoEm() { return criadoEm; }
    public LocalDateTime getConfirmadoEm() { return confirmadoEm; }
    public void setConfirmadoEm(LocalDateTime confirmadoEm) { this.confirmadoEm = confirmadoEm; }
    public LocalDateTime getAtualizadoEm() { return atualizadoEm; }
}