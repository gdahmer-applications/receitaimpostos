package br.gov.serpro.rtc.testesunitarios.config.db;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import br.gov.serpro.rtc.config.db.DatabaseDownloadService;

/**
 * Testes unitários de {@link DatabaseDownloadService}: cobre o desvio quando o
 * arquivo já existe (sem requisição HTTP) e o download efetivo gravando o
 * conteúdo recebido no destino configurado. O comportamento de retentativa
 * ({@code @Retryable}/{@code @Recover}) é da própria infraestrutura do Spring
 * Retry, já usada em {@code VersaoOfflineService}, e não é reexercitado aqui.
 */
class Teste_DatabaseDownloadService {

    private static final String URL_BASE = "http://obs.exemplo";
    private static final String ARQUIVO = "calculadora-pro.db";
    private static final String URL_COMPLETA = URL_BASE + "/" + ARQUIVO;

    private DatabaseDownloadService criarServico(Path diretorioDestino) {
        DatabaseDownloadService servico = new DatabaseDownloadService(new RestTemplateBuilder(),
                Duration.ofSeconds(5), Duration.ofSeconds(30));
        ReflectionTestUtils.setField(servico, "dbFileName", ARQUIVO);
        ReflectionTestUtils.setField(servico, "urlDownload", URL_BASE);
        ReflectionTestUtils.setField(servico, "diretorioDestino", diretorioDestino.toString());
        return servico;
    }

    private MockRestServiceServer mockServidor(DatabaseDownloadService servico) {
        RestTemplate restTemplate = (RestTemplate) ReflectionTestUtils.getField(servico, "restTemplate");
        return MockRestServiceServer.createServer(restTemplate);
    }

    @Test
    void teste_ArquivoJaExiste_NaoBaixaDaObs(@TempDir Path tempDir) throws IOException {
        Path destino = tempDir.resolve(ARQUIVO);
        Files.writeString(destino, "conteudo-local-existente");

        DatabaseDownloadService servico = criarServico(tempDir);
        MockRestServiceServer mockServer = mockServidor(servico);

        servico.baixarSeAusente();

        mockServer.verify(); // nenhuma expectativa registrada: falha se qualquer requisição ocorrer
        assertEquals("conteudo-local-existente", Files.readString(destino));
    }

    @Test
    void teste_ArquivoAusente_BaixaEGravaNoDestino(@TempDir Path tempDir) throws IOException {
        byte[] conteudo = "conteudo-baixado-da-obs".getBytes(StandardCharsets.UTF_8);
        Path destino = tempDir.resolve(ARQUIVO);

        DatabaseDownloadService servico = criarServico(tempDir);
        MockRestServiceServer mockServer = mockServidor(servico);
        mockServer.expect(requestTo(URL_COMPLETA))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(conteudo, MediaType.APPLICATION_OCTET_STREAM));

        servico.baixarSeAusente();

        mockServer.verify();
        assertArrayEquals(conteudo, Files.readAllBytes(destino));
    }
}
