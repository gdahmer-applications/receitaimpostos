package br.gov.serpro.rtc.api.simplesnacional.model.output;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 *
 * Repasse dos dados relevantes da operação do Simples Nacional na saída
 * (NT 2026.005).
 *
 */
@JsonInclude(NON_NULL)
@JsonPropertyOrder({ "data", "tpDoc", "CRT", "tpRegimeIBSCBS", "tpEnteGov" })
public record OperacaoSimplesDomain(

        @Schema(description = "Data do fato gerador")
        String data,

        @Schema(description = "Tipo de documento fiscal eletrônico da operação")
        String tpDoc,

        @SuppressWarnings("java:S116") // Nomenclatura oficial VERBATIM do leiaute DF-e/NT 2026.005.
        @JsonProperty("CRT")
        @Schema(name = "CRT", description = "Código de Regime Tributário do emitente")
        String CRT,

        @Schema(description = "Tipo de regime de apuração do IBS/CBS, quando informado")
        String tpRegimeIBSCBS,

        @Schema(description = "Tipo de ente governamental adquirente (compra governamental), quando informado")
        String tpEnteGov) {
}
