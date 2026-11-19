package br.gov.serpro.rtc.api.simplesnacional.controller;

import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;
import static org.springframework.http.MediaType.APPLICATION_PROBLEM_JSON_VALUE;

import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;

import br.gov.serpro.rtc.api.simplesnacional.exceptionhandler.dto.ErroDescricaoSimples;
import br.gov.serpro.rtc.api.simplesnacional.model.input.OperacaoSimplesInput;
import br.gov.serpro.rtc.api.simplesnacional.model.output.ObservabilidadeSimplesDomain;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * Contrato OpenAPI dos endpoints de observabilidade do Simples Nacional,
 * incluindo o cálculo com erros por item e a consulta ao catálogo de códigos
 * {@code VSN-}.
 */
@Tag(name = "Observabilidade - Calculadora do Simples Nacional",
        description = "Endpoint de observabilidade da Calculadora de Tributos do Simples Nacional")
public interface SimplesNacionalControllerOpenApi {

    @Operation(summary = "Cálculo do Simples Nacional com observabilidade (NT 2026.005)",
            description = "Implementa a NT 2026.005 v1.00 (Reforma Tributária do Consumo — Simples Nacional; "
                    + "LC 123/2006, LC 214/2025, LC 227/2026). Recebe os dados da operação (data, tpDoc, CRT, "
                    + "tpRegimeIBSCBS e tpEnteGov — este último a nível de operação, espelhando a tag "
                    + "gCompraGov/tpEnteGov da identificação da nota, grupo B/ide do leiaute do DF-e) e os itens "
                    + "(cClassTrib, cAtivSN, base de cálculo, vIS, alíquotas efetivas "
                    + "pIBSSN/pCBSSN), resolve internamente o tpRBSN do cClassTrib (restrito ao grupo "
                    + "da CBS da tabela de classificação) e espelha, por item, o leiaute do Evento de Tributação do "
                    + "Simples Nacional (tpEvento 412140) — incluindo o código tpRBSN e sua descrição "
                    + "(tpRBSNDescricao) —, acumulando erros individuais por item. O comportamento do cálculo é "
                    + "parametrizado em banco de dados, sempre pela vigência da data do fato gerador: os tipos de "
                    + "documento suportados, o efeito de cada tipo de receita bruta, a matriz de regime de apuração "
                    + "e o fator de transição de compra governamental. O cAtivSN é validado contra o catálogo de "
                    + "atividades, com restrição de documento POR ATIVIDADE: atividade sem vínculo na associativa "
                    + "de tipos de DFE é admissível em qualquer tpDoc suportado pelo cálculo; atividade com "
                    + "vínculos, somente nos tpDoc vinculados — cAtivSN inadmissível leva ao estado NAO_CALCULADO, "
                    + "sem erro. Antes de qualquer outra validação do item, verifica-se (primeira consulta de banco) "
                    + "se o cClassTrib está vinculado, na data, a tratamento tributário ainda não implementado na "
                    + "Calculadora (indicador de ajuste): nesse caso o item sai com o estado NAO_IMPLEMENTADO e o "
                    + "erro VSN-027, sem cálculo e fora dos totais. Compras governamentais (tpEnteGov informado na operação) aplicam a todos os itens "
                    + "o deslocamento de alíquotas CBS-IBS dos arts. 372 e 473 da LC 214/2025 (o redutor dos "
                    + "arts. 370/472 não integra o evento).")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Processamento realizado (itens podem ter erros individuais)", content = {
                    @Content(mediaType = APPLICATION_JSON_VALUE, schema = @Schema(implementation = ObservabilidadeSimplesDomain.class)) }),
            @ApiResponse(responseCode = "400", description = "Estrutura e/ou dados informados em formato não reconhecido", content = {
                    @Content(mediaType = APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = ProblemDetail.class)) }),
            @ApiResponse(responseCode = "422", description = "Erro de validação global (data, itens, dados obrigatórios "
                    + "da operação, tpEnteGov inválido, parametrização de compra governamental indisponível) ou "
                    + "operação fora do escopo do cálculo (tpDoc não suportado, MEI, regime regular, "
                    + "regime normal puro, incoerências CRT × tpRegimeIBSCBS)", content = {
                    @Content(mediaType = APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = ProblemDetail.class)) }),
            @ApiResponse(responseCode = "500", description = "Erro interno na API", content = {
                    @Content(mediaType = APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = ProblemDetail.class)) }) })
    ResponseEntity<ObservabilidadeSimplesDomain> calcularTributosSimplesNacional(
            @RequestBody(description = "Dados de uma operação do Simples Nacional", required = true, content = @Content(mediaType = APPLICATION_JSON_VALUE, schema = @Schema(implementation = OperacaoSimplesInput.class))) OperacaoSimplesInput operacao);

    @Operation(summary = "Consultar descrição de erro do Simples Nacional",
            description = "Retorna título e descrição em PT-BR para o código de erro VSN- informado.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Descrição do erro encontrada", content = {
                    @Content(mediaType = APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErroDescricaoSimples.class)) }),
            @ApiResponse(responseCode = "404", description = "Código de erro não encontrado") })
    ResponseEntity<ErroDescricaoSimples> buscarErro(
            @Parameter(description = "Código do erro (ex: VSN-001)", required = true) String codigo);
}
