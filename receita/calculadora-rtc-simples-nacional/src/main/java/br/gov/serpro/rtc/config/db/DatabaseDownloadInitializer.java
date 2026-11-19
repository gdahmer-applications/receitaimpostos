package br.gov.serpro.rtc.config.db;

import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;

/**
 * Aciona {@link DatabaseDownloadService#baixarSeAusente()} durante a
 * inicialização do contexto, antes do bean {@code dataSource} (ver
 * {@link DatabaseDownloadOrderingConfig}).
 *
 * <p>Bean separado deliberadamente: chamar o método {@code @Retryable} a
 * partir de um {@code @PostConstruct} do PRÓPRIO {@code DatabaseDownloadService}
 * ignoraria o retry (auto-invocação não passa pelo proxy AOP do Spring Retry).
 * Aqui a chamada vem de outro bean, através da referência injetada (o proxy),
 * então o retry funciona normalmente.
 */
@Component
@RequiredArgsConstructor
class DatabaseDownloadInitializer {

    private final DatabaseDownloadService databaseDownloadService;

    @PostConstruct
    void init() {
        databaseDownloadService.baixarSeAusente();
    }
}
