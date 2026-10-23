package com.example.demandas.dto;

import java.util.List;

public record CategoriaNo(Integer id, String nome, String descricao,
                          Boolean exigeDisciplina,
                          Integer responsavelPadraoId, String responsavelPadrao,
                          List<CategoriaNo> filhos) {}