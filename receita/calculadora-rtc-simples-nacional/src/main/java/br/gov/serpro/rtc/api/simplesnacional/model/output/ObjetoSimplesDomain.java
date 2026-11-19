package br.gov.serpro.rtc.api.simplesnacional.model.output;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import br.gov.serpro.rtc.api.simplesnacional.enumeration.EstadoItemSimplesEnum;
import br.gov.serpro.rtc.shared.exceptionhandler.dto.ErroDetalhe;

/**
 *
 * <p>Item do resultado do Simples Nacional com:
 * <ul>
 * <li>número do objeto ({@code nObj})</li>
 * <li>estado do processamento ({@code estadoItem})</li>
 * <li>tributos calculados ({@code tribCalc})</li>
 * <li>erros detalhados ({@code erros})</li>
 * </ul>
 *
 */
@JsonInclude(NON_NULL)
@JsonPropertyOrder({ "nObj", "estadoItem", "tribCalc", "erros" })
public record ObjetoSimplesDomain(
        Integer nObj,
        EstadoItemSimplesEnum estadoItem,
        TributosSimplesDomain tribCalc,
        List<ErroDetalhe> erros) {
}
