package br.gov.serpro.rtc.api.simplesnacional.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.springframework.stereotype.Service;

import br.gov.serpro.rtc.api.simplesnacional.entity.RegimeApuracaoSimples;
import br.gov.serpro.rtc.api.simplesnacional.entity.TipoReceitaBrutaSimples;
import br.gov.serpro.rtc.api.simplesnacional.entity.TransicaoAliquotaCompraGovSimples;
import br.gov.serpro.rtc.api.simplesnacional.enumeration.RegimeCalculoSimplesEnum;
import br.gov.serpro.rtc.api.simplesnacional.repository.RegimeApuracaoSimplesRepository;
import br.gov.serpro.rtc.api.simplesnacional.repository.TipoDfeCalculoSimplesRepository;
import br.gov.serpro.rtc.api.simplesnacional.repository.TipoReceitaBrutaSimplesRepository;
import br.gov.serpro.rtc.api.simplesnacional.repository.TransicaoAliquotaCompraGovSimplesRepository;
import lombok.RequiredArgsConstructor;

/**
 * Fachada de resolução dos parâmetros do cálculo do Simples Nacional que vivem
 * em banco de dados: documentos suportados, efeito dos tipos de receita bruta,
 * matriz de regime e fator de transição de compra governamental.
 *
 * <p>
 * Concentra os lookups para manter o serviço de observabilidade enxuto e para
 * ser o ponto único de um eventual cache futuro. Todos os lookups usam a DATA
 * DO FATO GERADOR da operação, nunca a data corrente.
 *
 * <p>
 * Retornos vazios significam FALHA DE PARAMETRIZAÇÃO (ou dado fora de vigência)
 * e são convertidos pelo chamador nos erros catalogados adequados (VSN-015,
 * VSN-025 ou VSN-026) — o cálculo nunca assume comportamento implícito.
 */
@Service
@RequiredArgsConstructor
public class ParametroCalculoSimplesService {

    private static final Set<Integer> TPDOCS_SEM_TRATAMENTO_CALCULADORA = Set.of(77, 94);

    private final TipoDfeCalculoSimplesRepository tipoDfeCalculoRepository;
    private final TipoReceitaBrutaSimplesRepository tipoReceitaBrutaRepository;
    private final RegimeApuracaoSimplesRepository regimeApuracaoRepository;
    private final TransicaoAliquotaCompraGovSimplesRepository transicaoCompraGovRepository;

    /**
     * Indica se o tipo de documento fiscal ({@code tpDoc}: 55, 65, 91, ...) é
     * suportado pelo cálculo na data do fato gerador, conforme a tabela
     * {@code TIPO_DFE_CALCULO_SIMPLES_NACIONAL}.
     */
    public boolean tpDocSuportado(int tpDoc, LocalDate data) {
        return tipoDfeCalculoRepository.contarVinculosVigentes(tpDoc, data) > 0;
    }

    /**
     * Indica se o documento possui parametrização conhecida, mas ainda não tem
     * tratamento implementado nos endpoints de cálculo.
     */
    public boolean tpDocSemTratamentoCalculadora(int tpDoc) {
        return TPDOCS_SEM_TRATAMENTO_CALCULADORA.contains(tpDoc);
    }

    /**
     * Resolve o efeito do Tipo de Receita Bruta do Simples Nacional (tpRBSN)
     * vigente na data do fato gerador. Vazio = falha de parametrização
     * (erro VSN-026).
     */
    public Optional<TipoReceitaBrutaSimples> buscarTipoReceitaBruta(int tpRBSN, LocalDate data) {
        return primeiro(tipoReceitaBrutaRepository.buscarVigentesPorCodigo(tpRBSN, data));
    }

    /**
     * Resolve a linha da matriz de regime vigente na data do fato gerador para
     * o regime de apuração informado. Vazio = falha de parametrização
     * (erro VSN-026).
     */
    public Optional<RegimeApuracaoSimples> buscarRegimeApuracao(RegimeCalculoSimplesEnum regime, LocalDate data) {
        return switch (regime) {
            case CRT_1 -> primeiro(regimeApuracaoRepository.buscarVigentes(1, null, data));
            case CRT_2 -> primeiro(regimeApuracaoRepository.buscarVigentes(2, null, data));
            case CRT_3_TPREGIME_2 -> primeiro(regimeApuracaoRepository.buscarVigentes(3, 2, data));
        };
    }

    /**
     * Resolve o fator de transição de alíquotas de compra governamental (fração)
     * vigente na data do fato gerador, aplicável a ente estadual, distrital ou
     * municipal ({@code tpEnteGov} = 2, 3 ou 4). Vazio = data fora das vigências
     * (erro VSN-025; nunca assumir fator zero implícito).
     */
    public Optional<BigDecimal> buscarFatorTransicaoCompraGov(LocalDate data) {
        return primeiro(transicaoCompraGovRepository.buscarVigentes(data))
                .map(TransicaoAliquotaCompraGovSimples::getFator);
    }

    /** Primeira linha da lista (vigência mais recente), ou vazio quando não há vigente. */
    private static <T> Optional<T> primeiro(List<T> vigentes) {
        return vigentes.isEmpty() ? Optional.empty() : Optional.of(vigentes.get(0));
    }
}
