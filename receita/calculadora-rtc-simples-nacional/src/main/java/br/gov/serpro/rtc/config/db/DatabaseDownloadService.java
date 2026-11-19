package br.gov.serpro.rtc.config.db;

import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpMethod;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.Retryable;
import org.springframework.retry.support.RetrySynchronizationManager;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import lombok.extern.slf4j.Slf4j;

/**
 * Baixa o arquivo do banco de dados SQLite de um bucket OBS para
 * {@code calculadora/db/} quando ele ainda não existe localmente.
 *
 * <p>{@code application.db.download.url} é a URL base do bucket (sem o nome do
 * arquivo); o nome final é composto em tempo de execução com
 * {@code application.db.filename}, permitindo que ambas as variáveis de
 * ambiente ({@code DB_DOWNLOAD_URL} e {@code API_SIMPLES_DB_FILENAME}) sejam
 * configuradas de forma independente.
 *
 * <p>Chamado a partir de {@link DatabaseDownloadInitializer#init()} — nunca a
 * partir de um {@code @PostConstruct} deste próprio bean: {@code @Retryable}
 * depende do proxy AOP do Spring Retry, que só entra em ação para chamadas
 * vindas de OUTRO bean (auto-invocação não passa pelo proxy).
 *
 * <p>{@link DatabaseDownloadOrderingConfig} garante, via
 * {@code BeanFactoryPostProcessor}, que o bean autoconfigurado
 * {@code dataSource} só é criado depois que o download (ou o desvio, caso o
 * arquivo já exista) tiver terminado — necessário porque a URL JDBC usa
 * {@code mode=ro}: o driver SQLite não cria um arquivo ausente em modo
 * somente leitura, e o HikariCP abre a pool de conexões de forma antecipada,
 * antes de qualquer {@code ApplicationReadyEvent}/{@code CommandLineRunner}.
 */
@Slf4j
@Service
public class DatabaseDownloadService {

    @Value("${application.db.filename}")
    private String dbFileName;

    @Value("${application.db.download.url}")
    private String urlDownload;

    @Value("${application.db.download.target-dir:calculadora/db}")
    private String diretorioDestino;

    @Value("${application.db.download.tentativas:3}")
    private int tentativas;

    private final RestTemplate restTemplate;

    public DatabaseDownloadService(RestTemplateBuilder restTemplateBuilder,
            @Value("${application.db.download.connect-timeout:5s}") Duration connectTimeout,
            @Value("${application.db.download.read-timeout:30s}") Duration readTimeout) {
        this.restTemplate = restTemplateBuilder
                .connectTimeout(connectTimeout)
                .readTimeout(readTimeout)
                .build();
    }

    /**
     * Baixa o arquivo se ele ainda não existir; não faz nada caso já exista
     * (dev local via {@code run-offline.sh}/{@code run-online.sh} já tem o
     * arquivo versionado, sem necessidade de rede).
     */
    @Retryable(maxAttemptsExpression = "${application.db.download.tentativas:3}", backoff = @Backoff(delay = 2000), retryFor = DatabaseDownloadException.class)
    public void baixarSeAusente() {
        Path destino = Path.of(diretorioDestino, dbFileName);
        if (Files.exists(destino)) {
            log.info("Banco de dados já presente em [{}]; download da OBS dispensado.", destino);
            return;
        }
        baixar(destino);
    }

    private void baixar(Path destino) {
        String urlCompleta = comporUrlDownload();
        log.info("Baixando banco de dados de [{}] para [{}] (tentativa {}/{})...", urlCompleta, destino,
                tentativaAtual(), tentativas);
        try {
            Files.createDirectories(destino.getParent());
            Path temporario = Files.createTempFile(destino.getParent(), "calculadora-pro", ".db.tmp");
            restTemplate.execute(URI.create(urlCompleta), HttpMethod.GET, null, response -> {
                try (OutputStream out = Files.newOutputStream(temporario)) {
                    response.getBody().transferTo(out);
                }
                return null;
            });
            Files.move(temporario, destino, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            log.info("Banco de dados baixado com sucesso em [{}].", destino);
        } catch (IOException | RestClientException e) {
            log.warn("Falha na tentativa {}/{} de download de [{}]: {}", tentativaAtual(), tentativas, urlCompleta,
                    e.getMessage());
            throw new DatabaseDownloadException(
                    "Falha ao baixar o banco de dados de '" + urlCompleta + "' para '" + destino + "': "
                            + e.getMessage(),
                    e);
        }
    }

    /** Completa a URL base do bucket (sem barra final) com o nome do arquivo do banco. */
    private String comporUrlDownload() {
        String base = urlDownload.endsWith("/") ? urlDownload.substring(0, urlDownload.length() - 1) : urlDownload;
        return base + "/" + dbFileName;
    }

    /** Número da tentativa atual (1-based), obtido do contexto do Spring Retry. */
    private int tentativaAtual() {
        var contexto = RetrySynchronizationManager.getContext();
        return contexto != null ? contexto.getRetryCount() + 1 : 1;
    }

    @Recover
    void falharAposTentativas(DatabaseDownloadException e) {
        throw new IllegalStateException(
                "Não foi possível baixar o banco de dados de '" + comporUrlDownload() + "' após " + tentativas
                        + " tentativas. Última falha: " + e.getCause().getMessage()
                        + ". Encerrando a inicialização.", e);
    }
}
