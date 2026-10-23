package com.example.demandas.dto;

import java.util.List;

public record MeResponse(Integer id, String nome, String email, List<String> perfis) {}