package com.example.demandas.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.Nationalized;

@Entity
@Table(name = "categoria")
public class Categoria {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Nationalized
    @Column(nullable = false, length = 100)
    private String nome;

    @Nationalized
    @Column(length = 300)
    private String descricao;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "categoria_pai_id")
    private Categoria categoriaPai;

    @Column(name = "prazo_horas")
    private Integer prazoHoras;

    @Column(nullable = false)
    private Boolean ativo = true;

    @Column(nullable = false)
    private Integer ordem = 0;

    @Column(name = "exige_disciplina", nullable = false)
    private Boolean exigeDisciplina = false;

    public Integer getId() { return id; }
    public String getNome() { return nome; }
    public void setNome(String nome) { this.nome = nome; }
    public String getDescricao() { return descricao; }
    public void setDescricao(String descricao) { this.descricao = descricao; }
    public Categoria getCategoriaPai() { return categoriaPai; }
    public void setCategoriaPai(Categoria categoriaPai) { this.categoriaPai = categoriaPai; }
    public Integer getPrazoHoras() { return prazoHoras; }
    public void setPrazoHoras(Integer prazoHoras) { this.prazoHoras = prazoHoras; }
    public Boolean getAtivo() { return ativo; }
    public void setAtivo(Boolean ativo) { this.ativo = ativo; }
    public Integer getOrdem() { return ordem; }
    public void setOrdem(Integer ordem) { this.ordem = ordem; }
    public Boolean getExigeDisciplina() { return exigeDisciplina; }
    public void setExigeDisciplina(Boolean exigeDisciplina) { this.exigeDisciplina = exigeDisciplina; }
}