package br.gov.serpro.rtc.config.db;

import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Força o bean autoconfigurado {@code dataSource} (Spring Boot / HikariCP) a
 * depender de {@link DatabaseDownloadInitializer}, garantindo que o download
 * do banco (ou o desvio, caso o arquivo já exista) termine ANTES da pool de
 * conexões tentar abrir o arquivo SQLite em {@code mode=ro} — acionar o
 * download em {@code ApplicationReadyEvent}/{@code CommandLineRunner} seria
 * tarde demais, pois o HikariCP abre a pool durante o refresh do contexto.
 *
 * <p>Depende do nome interno {@code "dataSource"} usado pela autoconfiguração
 * padrão do Spring Boot para o {@code HikariDataSource}; validar manualmente
 * (build da imagem + subida do container) caso essa suposição deixe de valer
 * em uma futura versão do Spring Boot.
 */
@Configuration
public class DatabaseDownloadOrderingConfig {

    @Bean
    static BeanFactoryPostProcessor dataSourceDependsOnDatabaseDownload() {
        return beanFactory -> beanFactory.getBeanDefinition("dataSource")
                .setDependsOn("databaseDownloadInitializer");
    }
}
