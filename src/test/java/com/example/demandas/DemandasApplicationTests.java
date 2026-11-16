package com.example.demandas;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/** Sobe a aplicação inteira e precisa de um PostgreSQL acessível, por isso fica desativado. */
@Disabled("Requer banco de dados e variáveis de ambiente. Rode manualmente quando quiser.")
@SpringBootTest
class DemandasApplicationTests {

    @Test
    void contextLoads() {
    }
}