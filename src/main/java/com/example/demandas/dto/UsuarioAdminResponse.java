package com.example.demandas.dto;

import java.util.List;

public record UsuarioAdminResponse(Integer id, String nome, String email, String matricula,
                                   List<String> perfis, String status) {}