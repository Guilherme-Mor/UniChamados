package com.example.demandas.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CadastroInstitucionalRequest(
        @NotBlank @Size(max = 20) String matricula,
        @NotBlank String cpf,
        @NotBlank @Size(max = 150) String nome,
        @NotBlank @Email @Size(max = 150) String email,
        @NotBlank String tipo
) {}