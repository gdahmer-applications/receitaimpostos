package br.gov.serpro.rtc.config.db;

/** Falha ao baixar o arquivo do banco de dados SQLite da OBS. */
class DatabaseDownloadException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    DatabaseDownloadException(String message, Throwable cause) {
        super(message, cause);
    }
}
