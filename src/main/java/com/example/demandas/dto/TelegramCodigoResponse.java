package com.example.demandas.dto;

/** O código aparece uma única vez, na resposta da geração (no banco fica só o hash). */
public record TelegramCodigoResponse(String codigo, String link, String qrCode, int validadeSegundos) {}