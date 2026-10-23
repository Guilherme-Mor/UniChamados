package com.example.demandas.dto;

import java.util.List;

public record PaginaChamados(List<ChamadoResumo> conteudo, int pagina, int tamanho,
                             long total, int totalPaginas) {}