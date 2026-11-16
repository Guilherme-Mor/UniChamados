package com.example.demandas;

import com.example.demandas.entity.Categoria;
import com.example.demandas.entity.Chamado;
import com.example.demandas.entity.Perfil;
import com.example.demandas.entity.StatusChamado;
import com.example.demandas.entity.Usuario;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Montagem de objetos de teste. */
public final class Fixtures {

    private Fixtures() {}

    public static Usuario usuario(int id, String nome, String email, String status, String... perfis) {
        Usuario u = new Usuario();
        ReflectionTestUtils.setField(u, "id", id);
        u.setNome(nome);
        u.setEmail(email);
        u.setStatus(status);
        for (String nomePerfil : perfis) {
            Perfil p = new Perfil();
            p.setNome(nomePerfil);
            u.getPerfis().add(p);
        }
        return u;
    }

    public static StatusChamado status(String nome) {
        StatusChamado s = new StatusChamado();
        s.setNome(nome);
        return s;
    }

    public static Chamado chamado(long id, String protocolo, Usuario solicitante, Usuario responsavel, String status) {
        Chamado c = new Chamado();
        ReflectionTestUtils.setField(c, "id", id);
        ReflectionTestUtils.setField(c, "criadoEm", LocalDateTime.of(2026, 11, 5, 9, 30));
        ReflectionTestUtils.setField(c, "atualizadoEm", LocalDateTime.of(2026, 11, 5, 9, 30));
        c.setProtocolo(protocolo);
        c.setUsuario(solicitante);
        c.setResponsavel(responsavel);
        c.setStatus(status(status));
        c.setTitulo("Revisão da nota");
        c.setDescricao("A nota da prova não confere.");
        Categoria categoria = new Categoria();
        categoria.setNome("Nota");
        c.setCategoria(categoria);
        return c;
    }

    /** Lista os dados (callback_data) de todos os botões de um teclado do Telegram. */
    @SuppressWarnings("unchecked")
    public static List<String> callbacks(Map<String, Object> teclado) {
        List<String> dados = new ArrayList<>();
        for (List<Map<String, Object>> linha : (List<List<Map<String, Object>>>) teclado.get("inline_keyboard")) {
            for (Map<String, Object> botao : linha) {
                dados.add((String) botao.get("callback_data"));
            }
        }
        return dados;
    }
}