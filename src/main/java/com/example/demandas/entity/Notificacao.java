package com.example.demandas.entity;

import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "notificacao")
public class Notificacao {

    public static final String PENDENTE = "PENDENTE";
    public static final String ENVIADA = "ENVIADA";
    public static final String FALHA = "FALHA";
    public static final String SEM_VINCULO = "SEM_VINCULO";

    public static final String TIPO_ATRIBUICAO = "ATRIBUICAO";
    public static final String TIPO_NOVA_MENSAGEM = "NOVA_MENSAGEM";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "usuario_id")
    private Usuario usuario;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "chamado_id")
    private Chamado chamado;

    @Column(nullable = false, length = 20)
    private String canal = "TELEGRAM";

    @Column(nullable = false, length = 30)
    private String tipo;

    @Column(nullable = false, columnDefinition = "text")
    private String texto;

    @Column(nullable = false, length = 20)
    private String status = PENDENTE;

    @Column(nullable = false)
    private Integer tentativas = 0;

    @Column(name = "proximo_envio", nullable = false)
    private LocalDateTime proximoEnvio;

    @Column(length = 500)
    private String erro;

    @Column(name = "criado_em", nullable = false)
    private LocalDateTime criadoEm;

    @Column(name = "enviado_em")
    private LocalDateTime enviadoEm;

    @PrePersist
    void antesDeSalvar() {
        LocalDateTime agora = LocalDateTime.now();
        if (criadoEm == null) criadoEm = agora;
        if (proximoEnvio == null) proximoEnvio = agora;
    }

    public Long getId() { return id; }
    public Usuario getUsuario() { return usuario; }
    public void setUsuario(Usuario usuario) { this.usuario = usuario; }
    public Chamado getChamado() { return chamado; }
    public void setChamado(Chamado chamado) { this.chamado = chamado; }
    public String getCanal() { return canal; }
    public void setCanal(String canal) { this.canal = canal; }
    public String getTipo() { return tipo; }
    public void setTipo(String tipo) { this.tipo = tipo; }
    public String getTexto() { return texto; }
    public void setTexto(String texto) { this.texto = texto; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Integer getTentativas() { return tentativas; }
    public void setTentativas(Integer tentativas) { this.tentativas = tentativas; }
    public LocalDateTime getProximoEnvio() { return proximoEnvio; }
    public void setProximoEnvio(LocalDateTime proximoEnvio) { this.proximoEnvio = proximoEnvio; }
    public String getErro() { return erro; }
    public void setErro(String erro) { this.erro = erro; }
    public LocalDateTime getCriadoEm() { return criadoEm; }
    public LocalDateTime getEnviadoEm() { return enviadoEm; }
    public void setEnviadoEm(LocalDateTime enviadoEm) { this.enviadoEm = enviadoEm; }
}