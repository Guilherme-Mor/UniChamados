package com.example.demandas.dto;

public record CadastroInstitucionalResponse(Integer id, String matricula, String cpf, String nome,
                                            String email, String tipo, String status,
                                            boolean acessoCriado) {}
