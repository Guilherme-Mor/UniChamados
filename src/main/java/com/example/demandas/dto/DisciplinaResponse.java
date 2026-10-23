package com.example.demandas.dto;

public record DisciplinaResponse(Integer id, String codigo, String nome,
                                 Integer docenteId, String docente) {}