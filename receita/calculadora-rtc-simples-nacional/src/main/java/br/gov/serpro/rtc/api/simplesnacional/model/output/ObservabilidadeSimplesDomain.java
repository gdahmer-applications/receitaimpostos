package br.gov.serpro.rtc.api.simplesnacional.model.output;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

/**
 *
 * Resposta do endpoint de observabilidade do Simples Nacional.
 *
 * <p>Contém:
 * <ul>
 * <li>o repasse da operação ({@code oper})</li>
 * <li>os itens com estado de processamento ({@code objetos})</li>
 * <li>o total consolidado dos itens calculados ({@code total})</li>
 * </ul>
 *
 */
@JsonInclude(NON_NULL)
@JsonPropertyOrder({ "oper", "objetos", "total" })
public record ObservabilidadeSimplesDomain(
        OperacaoSimplesDomain oper,
        List<ObjetoSimplesDomain> objetos,
        TotalSimplesDomain total) {
}
