package com.example.demandas.integration.telegram;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Cliente da API do Telegram. O token faz parte do endereço das chamadas,
 * por isso nenhuma exceção original é repassada: as mensagens do Spring incluem o endereço completo.
 */
@Component
public class TelegramClient {

    private static final ParameterizedTypeReference<TelegramTipos.Resposta<TelegramTipos.Message>> T_MENSAGEM =
            new ParameterizedTypeReference<>() {};
    private static final ParameterizedTypeReference<TelegramTipos.Resposta<TelegramTipos.TgUser>> T_USUARIO =
            new ParameterizedTypeReference<>() {};
    private static final ParameterizedTypeReference<TelegramTipos.Resposta<Boolean>> T_BOOLEANO =
            new ParameterizedTypeReference<>() {};
    private static final ParameterizedTypeReference<TelegramTipos.Resposta<List<TelegramTipos.Update>>> T_UPDATES =
            new ParameterizedTypeReference<>() {};

    private static final List<String> TIPOS_PERMITIDOS = List.of("message", "callback_query");

    private final TelegramProperties props;
    private final RestClient http;        // chamadas curtas
    private final RestClient httpLongo;   // getUpdates (espera até ~25 s)

    public TelegramClient(TelegramProperties props) {
        this.props = props;
        this.http = criar(Duration.ofSeconds(10));
        this.httpLongo = criar(Duration.ofSeconds(45));
    }

    private static RestClient criar(Duration leitura) {
        HttpClient cliente = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
        JdkClientHttpRequestFactory fabrica = new JdkClientHttpRequestFactory(cliente);
        fabrica.setReadTimeout(leitura);
        return RestClient.builder()
                .baseUrl("https://api.telegram.org")
                .requestFactory(fabrica)
                .build();
    }

    // ===== Operações =====

    /** Dados do próprio bot (usado para validar o token). */
    public TelegramTipos.TgUser obterBot() {
        return chamar(http, "getMe", Map.of(), T_USUARIO);
    }

    /**
     * Envia uma mensagem em HTML. O chamador deve escapar o conteúdo de usuários
     * (TelegramTexto.escapar) e manter o texto abaixo de 4096 caracteres.
     */
    public Long enviarMensagem(Long chatId, String textoHtml, Map<String, Object> replyMarkup) {
        Map<String, Object> corpo = new LinkedHashMap<>();
        corpo.put("chat_id", chatId);
        corpo.put("text", TelegramTexto.truncar(textoHtml, TelegramTexto.LIMITE));
        corpo.put("parse_mode", "HTML");
        corpo.put("link_preview_options", Map.of("is_disabled", true));
        if (replyMarkup != null) corpo.put("reply_markup", replyMarkup);
        TelegramTipos.Message enviada = chamar(http, "sendMessage", corpo, T_MENSAGEM);
        return enviada == null ? null : enviada.messageId();
    }

    public Long enviarMensagem(Long chatId, String textoHtml) {
        return enviarMensagem(chatId, textoHtml, null);
    }

    /** Fecha o "carregando" do botão tocado, com um aviso opcional. */
    public void responderCallback(String callbackId, String texto) {
        Map<String, Object> corpo = new LinkedHashMap<>();
        corpo.put("callback_query_id", callbackId);
        if (texto != null && !texto.isBlank()) corpo.put("text", TelegramTexto.truncar(texto, 200));
        chamar(http, "answerCallbackQuery", corpo, T_BOOLEANO);
    }

    public void registrarWebhook(String url, String segredo) {
        Map<String, Object> corpo = new LinkedHashMap<>();
        corpo.put("url", url);
        corpo.put("secret_token", segredo);
        corpo.put("allowed_updates", TIPOS_PERMITIDOS);
        corpo.put("max_connections", 5);
        corpo.put("drop_pending_updates", false);
        chamar(http, "setWebhook", corpo, T_BOOLEANO);
    }

    public void removerWebhook() {
        chamar(http, "deleteWebhook", Map.of("drop_pending_updates", false), T_BOOLEANO);
    }

    /** Consulta eventos novos (modo polling). Espera até timeoutSegundos se não houver nenhum. */
    public List<TelegramTipos.Update> obterAtualizacoes(long offset, int timeoutSegundos) {
        Map<String, Object> corpo = new LinkedHashMap<>();
        corpo.put("offset", offset);
        corpo.put("timeout", timeoutSegundos);
        corpo.put("allowed_updates", TIPOS_PERMITIDOS);
        List<TelegramTipos.Update> lista = chamar(httpLongo, "getUpdates", corpo, T_UPDATES);
        return lista == null ? List.of() : lista;
    }

    // ===== Chamada genérica =====

    private <T> T chamar(RestClient cliente, String metodo, Object corpo,
                         ParameterizedTypeReference<TelegramTipos.Resposta<T>> tipo) {
        TelegramTipos.Resposta<T> resposta;
        try {
            resposta = cliente.post()
                    .uri("/bot{token}/{metodo}", props.botToken(), metodo)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(corpo)
                    .exchange((req, res) -> res.bodyTo(tipo));   // lê o corpo mesmo em respostas de erro
        } catch (RuntimeException e) {
            // Sem repassar a causa: ela traria o endereço, e o endereço traz o token.
            throw new TelegramException(
                    "Falha de comunicação com o Telegram em " + metodo + " (" + e.getClass().getSimpleName() + ").",
                    null, null);
        }

        if (resposta == null || !Boolean.TRUE.equals(resposta.ok())) {
            Integer codigo = resposta == null ? null : resposta.errorCode();
            Integer espera = (resposta == null || resposta.parameters() == null)
                    ? null : resposta.parameters().retryAfter();
            String descricao = resposta == null ? "sem resposta" : resposta.description();
            throw new TelegramException("Telegram recusou " + metodo + ": " + descricao, codigo, espera);
        }
        return resposta.result();
    }
}