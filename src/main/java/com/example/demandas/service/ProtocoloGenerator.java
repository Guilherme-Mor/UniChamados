package com.example.demandas.service;

import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

@Component
public class ProtocoloGenerator {

    private final EntityManager em;

    public ProtocoloGenerator(EntityManager em) {
        this.em = em;
    }

    /** Gera o protocolo no formato AAAAMMDD-NNNNNN usando a sequence do SQL Server. */
    public String proximo() {
        Number numero = (Number) em.createNativeQuery("SELECT NEXT VALUE FOR seq_protocolo")
                .getSingleResult();
        String data = LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE);
        return String.format("%s-%06d", data, numero.longValue());
    }
}