package br.gov.serpro.rtc.config.db;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;

/**
 * Valida o nome do arquivo do banco de dados configurado em
 * {@code application.db.filename} (variável de ambiente
 * {@code API_SIMPLES_DB_FILENAME}).
 */
@Slf4j
@Component
public class DatabaseValidator {

    private static final String NONPRODUCTION_DB_FILENAME_SUFFIX = "-nonpro.db";
    private static final String PRODUCTION_DB_FILENAME_SUFFIX = "-pro.db";

    @Value("${application.db.filename}")
    private String dbFileName;

    @PostConstruct
    public void validateDatabaseName() {
        if (dbFileName == null || dbFileName.isBlank() || dbFileName.contains(" ")) {
            throw new IllegalStateException("Nome do arquivo do banco de dados não pode ser nulo, vazio ou conter espaços.");
        }
        log.info(">>> Banco de dados em uso: [{}] <<<", dbFileName);
        if (!dbFileName.endsWith(PRODUCTION_DB_FILENAME_SUFFIX) && !dbFileName.endsWith(NONPRODUCTION_DB_FILENAME_SUFFIX)) {
            final var msg = String.format("Nome do banco de dados '%s' inválido. Deve terminar com '%s' ou '%s'.",
                    dbFileName, PRODUCTION_DB_FILENAME_SUFFIX, NONPRODUCTION_DB_FILENAME_SUFFIX);
            throw new IllegalStateException(msg);
        }
    }

}
