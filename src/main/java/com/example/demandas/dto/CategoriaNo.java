package com.example.demandas.dto;

import java.util.List;

public record CategoriaNo(Integer id, String nome, String descricao,
                          Boolean exigeDisciplina,
                          List<CategoriaNo> filhos) {}