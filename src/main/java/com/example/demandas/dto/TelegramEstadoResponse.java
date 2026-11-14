package com.example.demandas.dto;

/** estado: NENHUM, PENDENTE ou ATIVO. */
public record TelegramEstadoResponse(boolean habilitado, String estado, String username, String nome,
                                     Long restanteSegundos, String botUsername) {}