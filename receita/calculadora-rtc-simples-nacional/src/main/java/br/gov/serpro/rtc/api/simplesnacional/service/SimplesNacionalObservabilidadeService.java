package br.gov.serpro.rtc.api.simplesnacional.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.springframework.stereotype.Service;

import br.gov.serpro.rtc.api.atividadesimples.entity.AtividadeSimplesNacional;
import br.gov.serpro.rtc.api.simplesnacional.collector.ItemCollectorSimples;
import br.gov.serpro.rtc.api.simplesnacional.entity.ClassificacaoTributariaSimples;
import br.gov.serpro.rtc.api.simplesnacional.entity.RegimeApuracaoSimples;
import br.gov.serpro.rtc.api.simplesnacional.entity.TipoReceitaBrutaSimples;
import br.gov.serpro.rtc.api.simplesnacional.enumeration.RegimeCalculoSimplesEnum;
import br.gov.serpro.rtc.shared.exceptionhandler.ErrosCalculoException;
import br.gov.serpro.rtc.shared.exceptionhandler.dto.ErroDetalhe;
import br.gov.serpro.rtc.api.simplesnacional.exceptionhandler.enumeration.CodigoValidacaoSimples;
import br.gov.serpro.rtc.api.simplesnacional.model.input.ItemSimplesInput;
import br.gov.serpro.rtc.api.simplesnacional.model.input.OperacaoSimplesInput;
import br.gov.serpro.rtc.api.simplesnacional.model.output.ObjetoSimplesDomain;
import br.gov.serpro.rtc.api.simplesnacional.model.output.ObservabilidadeSimplesDomain;
import br.gov.serpro.rtc.api.simplesnacional.model.output.OperacaoSimplesDomain;
import br.gov.serpro.rtc.api.simplesnacional.model.output.TotalSimplesDomain;
import br.gov.serpro.rtc.api.simplesnacional.repository.AtividadeSimplesCalculoRepository;
import br.gov.serpro.rtc.api.simplesnacional.repository.ClassificacaoTributariaSimplesRepository;
import br.gov.serpro.rtc.api.simplesnacional.service.SimplesNacionalCalculoService.InsumoCalculoSimples;
import br.gov.serpro.rtc.api.simplesnacional.service.SimplesNacionalCalculoService.ResultadoCalculoSimples;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 *
 * Serviço de observabilidade do Simples Nacional (NT 2026.005).
 * <p>
 * Duplicado deliberadamente do análogo {@code ObservabilidadeService} do antigo
 * regime geral (removido do projeto) para
 * preservar o isolamento entre os pacotes. Responsabilidades:
 * <ul>
 * <li>validação global de escopo (tpDoc, CRT, tpRegimeIBSCBS, tpEnteGov) — HTTP
 * 422; o tpEnteGov é global porque o grupo {@code gCompraGov} pertence à
 * identificação da nota (grupo B/ide do leiaute do DF-e);</li>
 * <li>verificação PRIORITÁRIA, por item, de cClassTrib ainda não implementado
 * na Calculadora — primeira consulta de banco do item; indicador de ajuste do
 * tratamento tributário vigente ({@code TRTR_IN_POSSUI_AJUSTE}) → estado
 * {@code NAO_IMPLEMENTADO} (VSN-027), sem outras validações nem cálculo;</li>
 * <li>resolução, por item, do tipo de receita bruta pelo cClassTrib e validação
 * do código de atividade (cAtivSN);</li>
 * <li>exigência condicional das alíquotas efetivas (apenas quando o cálculo as usa);</li>
 * <li>resolução dos parâmetros de cálculo em banco (via
 * {@link ParametroCalculoSimplesService}) e entrega de insumos prontos ao
 * serviço de cálculo, que é puro;</li>
 * <li>processamento paralelo com virtual threads;</li>
 * <li>acúmulo de erros RFC 7807 por item;</li>
 * <li>totalização: itens {@code CALCULADO} em todos os totais;
 * {@code NAO_CALCULADO} apenas em {@code vRBSNtot}; itens com erro ou
 * {@code NAO_IMPLEMENTADO} em nada.</li>
 * </ul>
 * Não reutiliza código do regime geral; a única leitura de tabela de parâmetro
 * do regime geral é o indicador de ajuste (ver
 * {@link ClassificacaoTributariaSimplesRepository#contarTratamentosNaoImplementados}).
 *
 */
@Slf4j
@RequiredArgsConstructor
@Service
public class SimplesNacionalObservabilidadeService {

    /** Nomes de campos do contrato usados em JSON Pointers e mensagens. */
    private static final String CAMPO_CCLASSTRIB = "cClassTrib";
    private static final String CAMPO_TPREGIMEIBSCBS = "tpRegimeIBSCBS";
    private static final String CAMPO_CRT = "CRT";
    private static final String CAMPO_TPENTEGOV = "tpEnteGov";

    private final SimplesNacionalCalculoService calculoService;
    private final ParametroCalculoSimplesService parametroService;
    private final ClassificacaoTributariaSimplesRepository classificacaoTributariaRepository;
    private final AtividadeSimplesCalculoRepository atividadeCalculoRepository;

    /**
     * Dados globais da operação já validados e convertidos, incluindo a linha
     * vigente da matriz de regime ({@code REGIME_APURACAO_SIMPLES_NACIONAL}) e
     * os parâmetros de compra governamental — {@code tpEnteGov} (nível de
     * operação, como o grupo {@code gCompraGov} do DF-e) e o fator de transição
     * já resolvido pela vigência da data ({@code null} quando não se aplica).
     */
    private record ContextoOperacaoSimples(LocalDate data, int tpDoc, RegimeApuracaoSimples regime,
            Integer tpEnteGov, BigDecimal fatorTransicaoCompraGov) {
    }

    public ObservabilidadeSimplesDomain processarOperacao(OperacaoSimplesInput operacao, String baseURL) {
        // 1. Validar campos globais e escopo — se falhar, lança exceção (HTTP 422).
        List<ErroDetalhe> errosGlobais = validarCamposGlobais(operacao, baseURL);
        if (!errosGlobais.isEmpty()) {
            throw new ErrosCalculoException(errosGlobais);
        }

        final LocalDate data = LocalDate.parse(operacao.data().trim());

        // 2. Resolver a linha vigente da matriz de regime. Combinação aprovada
        // pela validação de escopo mas sem linha vigente na tabela é falha de
        // parametrização (VSN-026), global porque o regime vale para a operação
        // inteira.
        final RegimeApuracaoSimples regime = parametroService
                .buscarRegimeApuracao(resolverRegime(operacao), data)
                .orElseThrow(() -> new ErrosCalculoException(List.of(buildErroGlobal(
                        CodigoValidacaoSimples.PARAMETRIZACAO_INDISPONIVEL, CAMPO_CRT,
                        String.format("Não há parametrização de regime de apuração do Simples Nacional vigente "
                                + "para CRT=%s e tpRegimeIBSCBS=%s na data '%s'.",
                                operacao.CRT(), operacao.tpRegimeIBSCBS(), data),
                        baseURL))));

        // 3. Resolver o fator de transição de compra governamental — global,
        // como o tpEnteGov (a validação de domínio já ocorreu no passo 1).
        // Exigido apenas para ente estadual/distrital/municipal (2, 3 ou 4); a
        // União (1) tem regra estrutural sem parâmetro temporal. Data sem
        // vigência na tabela de transição é falha de parametrização (VSN-025) —
        // nunca assumir fator zero implícito.
        final Integer tpEnteGov = parseInteiro(operacao.tpEnteGov());
        BigDecimal fatorTransicao = null;
        if (tpEnteGov != null && tpEnteGov != 1) {
            fatorTransicao = parametroService.buscarFatorTransicaoCompraGov(data)
                    .orElseThrow(() -> new ErrosCalculoException(List.of(buildErroGlobal(
                            CodigoValidacaoSimples.COMPRA_GOV_SEM_VIGENCIA, CAMPO_TPENTEGOV,
                            String.format("Não há parametrização de transição de compra governamental vigente "
                                    + "para a data '%s'.", data),
                            baseURL))));
        }

        final ContextoOperacaoSimples contexto = new ContextoOperacaoSimples(
                data, Integer.parseInt(operacao.tpDoc().trim()), regime, tpEnteGov, fatorTransicao);
        final List<ItemSimplesInput> itens = operacao.itens();

        // 4. Processar itens em paralelo com virtual threads.
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<CompletableFuture<ObjetoSimplesDomain>> futures = new ArrayList<>();

            for (int i = 0; i < itens.size(); i++) {
                final int index = i;
                final ItemSimplesInput item = itens.get(index);
                futures.add(CompletableFuture.supplyAsync(
                        () -> processarItem(item, index, contexto, baseURL), executor));
            }

            List<ObjetoSimplesDomain> resultados = futures.stream()
                    .map(CompletableFuture::join)
                    .sorted(Comparator.comparing(ObjetoSimplesDomain::nObj,
                            Comparator.nullsFirst(Comparator.naturalOrder())))
                    .toList();

            // 5. Totalizar: CALCULADO e NAO_CALCULADO possuem tribCalc (o segundo
            // contribui apenas para vRBSNtot); itens com erro ou NAO_IMPLEMENTADO
            // não contribuem.
            List<ObjetoSimplesDomain> comTribCalc = resultados.stream()
                    .filter(r -> r.tribCalc() != null)
                    .toList();

            TotalSimplesDomain total = comTribCalc.isEmpty() ? null : TotalSimplesDomain.create(comTribCalc);

            return new ObservabilidadeSimplesDomain(
                    new OperacaoSimplesDomain(operacao.data(), operacao.tpDoc(), operacao.CRT(),
                            operacao.tpRegimeIBSCBS(), operacao.tpEnteGov()),
                    resultados,
                    total);
        }
    }

    // ─── Processamento por item ────────────────────────────────────────────────

    private ObjetoSimplesDomain processarItem(ItemSimplesInput item, int index,
            ContextoOperacaoSimples contexto, String baseURL) {
        ItemCollectorSimples collector = new ItemCollectorSimples(index, baseURL);

        Integer nObj = item.numero();
        if (nObj == null) {
            collector.addErroValidacao(CodigoValidacaoSimples.NUMERO_AUSENTE, "numero",
                    "O número do item é obrigatório.");
        }

        // 0. PRIORIDADE MÁXIMA — primeira consulta de banco do item: cClassTrib
        // vinculado, na data do fato gerador, a tratamento tributário marcado
        // como "sem implementação" (indicador de ajuste, TRTR_IN_POSSUI_AJUSTE).
        // Encerra o processamento do item: nada mais é validado nem calculado;
        // o item sai NAO_IMPLEMENTADO (VSN-027), sem tribCalc e fora dos totais.
        if (cClassTribNaoImplementado(item.cClassTrib(), contexto, collector)) {
            return new ObjetoSimplesDomain(nObj != null ? nObj : index + 1, collector.getEstado(),
                    null, collector.getErros());
        }

        // 1. Validar e converter os insumos monetários do item.
        BigDecimal baseCalculo = validarDecimal(item.baseCalculo(), collector, "baseCalculo",
                CodigoValidacaoSimples.BASE_AUSENTE, CodigoValidacaoSimples.BASE_INVALIDA);
        BigDecimal valorIs = validarDecimal(item.valorImpostoSeletivo(), collector, "valorImpostoSeletivo",
                CodigoValidacaoSimples.IS_INVALIDO, CodigoValidacaoSimples.IS_INVALIDO);

        // 2. Resolver o tipo de receita bruta do item: o código tpRBSN vem do
        // cClassTrib (vigente na data, admissível para o tpDoc) e o EFEITO do
        // código vem da tabela de tipos de receita bruta.
        TipoReceitaBrutaSimples tipoReceita = resolverTipoReceitaBruta(item.cClassTrib(), contexto, collector);

        // 3. Validar o código de atividade (cAtivSN). Código inválido NÃO é erro:
        // leva ao estado NAO_CALCULADO.
        boolean cAtivSNValido = validarCAtivSN(item.cAtivSN(), tipoReceita, contexto, collector);

        // 4. Alíquotas obrigatórias condicionais: exigidas apenas quando o
        // cálculo as usa — cAtivSN válido, tipo de receita bruta que exige
        // alíquota (parametrizado na tabela de tipos) e regime que gera o
        // tributo (para regime que não gera IBS, como o CRT=2, pIBSSN nunca é
        // exigida: é apenas insumo da transformação de compra governamental,
        // quando informada).
        boolean aliquotasExigidas = cAtivSNValido && tipoReceita != null
                && Boolean.TRUE.equals(tipoReceita.getExigeAliquota());
        boolean pIbsExigida = aliquotasExigidas && Boolean.TRUE.equals(contexto.regime().getGeraIbs());

        BigDecimal pCbs = validarAliquota(item.pCBSSN(), aliquotasExigidas, collector, "pCBSSN",
                CodigoValidacaoSimples.ALIQUOTA_CBS_AUSENTE, CodigoValidacaoSimples.ALIQUOTA_CBS_INVALIDA);
        BigDecimal pIbs = validarAliquota(item.pIBSSN(), pIbsExigida, collector, "pIBSSN",
                CodigoValidacaoSimples.ALIQUOTA_IBS_AUSENTE, CodigoValidacaoSimples.ALIQUOTA_IBS_INVALIDA);

        // 5. Se houver erros de validação, não calcula.
        if (collector.hasErros()) {
            return new ObjetoSimplesDomain(nObj != null ? nObj : index + 1, collector.getEstado(),
                    null, collector.getErros());
        }

        // 6. Calcular, com todos os parâmetros já resolvidos (o serviço de
        // cálculo é puro, sem acesso a banco). A compra governamental
        // (tpEnteGov e fator de transição) vem do contexto da operação — nível
        // de nota, como no grupo gCompraGov do DF-e — e é aplicada às alíquotas
        // deste item.
        try {
            InsumoCalculoSimples insumo = new InsumoCalculoSimples(
                    contexto.regime(), tipoReceita, cAtivSNValido,
                    baseCalculo, valorIs, pIbs, pCbs,
                    contexto.tpEnteGov(), contexto.fatorTransicaoCompraGov());

            ResultadoCalculoSimples resultado = calculoService.calcularItem(insumo);

            return new ObjetoSimplesDomain(nObj, resultado.estadoItem(), resultado.tribCalc(), null);
        } catch (Exception ex) {
            log.debug("Erro ao processar item {} (índice {}): {}",
                    nObj != null ? nObj : index + 1, index, ex.getMessage());
            collector.addFalhaTecnica(ex);
            return new ObjetoSimplesDomain(nObj != null ? nObj : index + 1, collector.getEstado(),
                    null, collector.getErros());
        }
    }

    /**
     *
     * Verificação PRIORITÁRIA de implementação do cClassTrib — a primeira
     * consulta de banco executada para o item, antes de qualquer outra
     * validação (base, tpRBSN, admissibilidade no tpDoc, cAtivSN, alíquotas).
     *
     * <p>Espelha o regime geral ({@code CalculoCbsIbsService}), que lança
     * {@code ErroFaltaImplementacaoException} (CAL-007, estado
     * {@code NAO_IMPLEMENTADO}) quando o tratamento tributário vigente da
     * classificação possui o indicador de ajuste ({@code TRTR_IN_POSSUI_AJUSTE}):
     * na Calculadora, esse indicador marca a classificação cujo tratamento ainda
     * não foi implementado. Aqui a consequência é a mesma — estado
     * {@code NAO_IMPLEMENTADO}, erro VSN-027 apontando {@code cClassTrib} — sem
     * exceção, pelo coletor de erros do item.
     *
     * <p>A consulta restringe ao grupo da CBS (tributo 2) e filtra as três
     * tabelas pela data do fato gerador. cClassTrib ausente não é verificado
     * (segue para VSN-020); código desconhecido ou fora de vigência conta zero
     * (segue para VSN-021).
     *
     * @return {@code true} quando o item foi marcado como {@code NAO_IMPLEMENTADO}
     *         (o chamador deve encerrar o processamento do item)
     *
     */
    private boolean cClassTribNaoImplementado(String cClassTrib, ContextoOperacaoSimples contexto,
            ItemCollectorSimples collector) {
        if (cClassTrib == null || cClassTrib.isBlank()) {
            return false;
        }
        String codigo = cClassTrib.trim();

        if (classificacaoTributariaRepository.contarTratamentosNaoImplementados(codigo, contexto.data()) == 0) {
            return false;
        }

        collector.addNaoImplementado(CodigoValidacaoSimples.CCLASSTRIB_NAO_IMPLEMENTADO, CAMPO_CCLASSTRIB,
                String.format("A classificação tributária '%s' ainda não possui tratamento implementado no "
                        + "cálculo do Simples Nacional na data '%s'. Essa funcionalidade está prevista no "
                        + "roadmap e será disponibilizada em versão futura.", codigo, contexto.data()));
        return true;
    }

    /**
     *
     * Resolve o tipo de receita bruta do item em dois passos:
     * <ol>
     * <li>o código tpRBSN vem do cClassTrib informado, vigente na data do fato
     * gerador e admissível para o tpDoc da operação (via
     * {@code TIPO_DFE_CLASSIFICACAO});</li>
     * <li>o efeito do código (descrição, fator de receita, exigência de
     * alíquota, etc.) vem da tabela {@code TIPO_RECEITA_BRUTA_SIMPLES_NACIONAL},
     * também pela vigência da data.</li>
     * </ol>
     *
     * <p>Como {@code CLTR_CD} não é único (os grupos do Imposto Seletivo e da
     * CBS colidem em códigos), a resolução do passo 1 é restrita ao grupo da
     * CBS (tributo 2) — o filtro vive nas consultas do repositório.
     *
     * <p>Erros de item acumulados: cClassTrib ausente (VSN-020), desconhecido ou
     * fora de vigência (VSN-021), incompatível com o tpDoc (VSN-022) e código de
     * tpRBSN sem parametrização de efeito vigente (VSN-026).
     *
     * @return a linha vigente do tipo de receita bruta, ou {@code null} com o
     *         erro adequado acumulado
     *
     */
    private TipoReceitaBrutaSimples resolverTipoReceitaBruta(String cClassTrib,
            ContextoOperacaoSimples contexto, ItemCollectorSimples collector) {
        if (cClassTrib == null || cClassTrib.isBlank()) {
            collector.addErroValidacao(CodigoValidacaoSimples.CCLASSTRIB_AUSENTE, CAMPO_CCLASSTRIB,
                    "O código de classificação tributária (cClassTrib) é obrigatório e não foi informado.");
            return null;
        }
        String codigo = cClassTrib.trim();

        List<ClassificacaoTributariaSimples> vigentes =
                classificacaoTributariaRepository.buscarVigentes(codigo, contexto.data());
        if (vigentes.isEmpty()) {
            collector.addErroValidacao(CodigoValidacaoSimples.CCLASSTRIB_DESCONHECIDO, CAMPO_CCLASSTRIB,
                    String.format("O cClassTrib '%s' não existe ou não está vigente na data '%s': "
                            + "o tpRBSN não pôde ser determinado.", codigo, contexto.data()));
            return null;
        }

        if (classificacaoTributariaRepository.contarVinculosComTpDoc(codigo, contexto.tpDoc(),
                contexto.data()) == 0) {
            collector.addErroValidacao(CodigoValidacaoSimples.CCLASSTRIB_INCOMPATIVEL_TPDOC, CAMPO_CCLASSTRIB,
                    String.format("O cClassTrib '%s' não é admissível para o tipo de documento fiscal %d.",
                            codigo, contexto.tpDoc()));
            return null;
        }

        int tpRBSN = vigentes.get(0).getTpRBSN();
        TipoReceitaBrutaSimples tipoReceita =
                parametroService.buscarTipoReceitaBruta(tpRBSN, contexto.data()).orElse(null);
        if (tipoReceita == null) {
            collector.addErroValidacao(CodigoValidacaoSimples.PARAMETRIZACAO_INDISPONIVEL, CAMPO_CCLASSTRIB,
                    String.format("Não há parametrização de efeito vigente para o tipo de receita bruta "
                            + "tpRBSN=%d na data '%s'.", tpRBSN, contexto.data()));
        }
        return tipoReceita;
    }

    /**
     *
     * Valida o código de atividade do Simples Nacional (cAtivSN).
     *
     * <p>Ausente, desconhecido, fora de vigência ou inadmissível para o tpDoc
     * NÃO é erro: retorna {@code false} e o item segue para o estado
     * {@code NAO_CALCULADO}, reportando apenas a receita bruta e o tipo de
     * receita (NT 2026.005, seção 5.3.4, Observação 2).
     *
     * <p>A admissibilidade por documento é POR ATIVIDADE, via associativa
     * {@code TIPO_DFE_ATIVIDADE_SIMPLES_NACIONAL}, com semântica de WHITELIST
     * (mundo fechado): a atividade é admissível no tpDoc da operação se e
     * somente se existe vínculo vigente na data entre a atividade e o tpDoc;
     * sem vínculo vigente algum, a atividade não é admissível em documento
     * nenhum. A admissibilidade é um fato afirmativo publicado por NT —
     * ausência de publicação significa ausência de permissão. A associativa
     * não substitui a whitelist global de tpDoc
     * ({@code TIPO_DFE_CALCULO_SIMPLES_NACIONAL}) — são camadas distintas.
     *
     * <p>A única exceção que É erro (VSN-023, espelho da regra UB14-90 da NT,
     * msg 1277): atividade marcada como de operações não tributáveis (coluna
     * {@code ATSN_IN_NAO_TRIBUTAVEL}; hoje, o código '90') combinada com tipo de
     * receita bruta vedado para não tributáveis (coluna
     * {@code TRBS_IN_VEDADO_NAO_TRIBUTAVEL}; tpRBSN 1 a 5, que declaram receita)
     * — contradição entre "não tributável" e um código que declara receita.
     *
     * @return {@code true} quando o código é válido para cálculo
     *
     */
    private boolean validarCAtivSN(String cAtivSN, TipoReceitaBrutaSimples tipoReceita,
            ContextoOperacaoSimples contexto, ItemCollectorSimples collector) {
        if (cAtivSN == null || cAtivSN.isBlank()) {
            return false;
        }
        String codigo = cAtivSN.trim();

        List<AtividadeSimplesNacional> atividades =
                atividadeCalculoRepository.buscarVigentesPorCAtivSN(codigo, contexto.data());
        if (atividades.isEmpty()) {
            return false;
        }
        AtividadeSimplesNacional atividade = atividades.get(0);

        if (atividadeCalculoRepository.contarVinculosComTpDoc(atividade.getId(),
                contexto.tpDoc(), contexto.data()) == 0) {
            return false;
        }

        if (Boolean.TRUE.equals(atividade.getNaoTributavel()) && tipoReceita != null
                && Boolean.TRUE.equals(tipoReceita.getVedadoNaoTributavel())) {
            collector.addErroValidacao(CodigoValidacaoSimples.CATIVSN_INCOMPATIVEL_CCLASSTRIB, "cAtivSN",
                    String.format("O código de atividade '%s' (Operações não Tributáveis) é incompatível com "
                            + "cClassTrib de tpRBSN=%d.", codigo, tipoReceita.getCodigo()));
        }
        return true;
    }

    /**
     *
     * Valida e converte uma alíquota recebida como texto. A ausência só é
     * erro quando a alíquota é exigida pelo cálculo; valor informado inválido ou
     * negativo é sempre erro.
     *
     * @return o {@link BigDecimal} convertido ou {@code null} quando ausente ou com erro
     *
     */
    private BigDecimal validarAliquota(String valor, boolean exigida, ItemCollectorSimples collector, String campo,
            CodigoValidacaoSimples codigoAusente, CodigoValidacaoSimples codigoInvalido) {
        if (valor == null || valor.isBlank()) {
            if (exigida) {
                collector.addErroValidacao(codigoAusente, campo,
                        "A alíquota é exigida para o cálculo do item e não foi informada.");
            }
            return null;
        }
        try {
            BigDecimal convertido = new BigDecimal(valor.trim());
            if (convertido.signum() < 0) {
                collector.addErroValidacao(codigoInvalido, campo,
                        String.format("O valor não pode ser negativo, recebido: '%s'.", valor));
                return null;
            }
            return convertido;
        } catch (NumberFormatException ex) {
            collector.addErroValidacao(codigoInvalido, campo,
                    String.format("O valor não é um número decimal válido, recebido: '%s'.", valor));
            return null;
        }
    }

    /**
     *
     * Valida e converte um valor decimal obrigatório e não negativo recebido
     * como texto. Acumula o erro adequado no collector, caso seja ausente
     * ({@code codigoAusente}), inválido ou negativo ({@code codigoInvalido}).
     *
     * @return o {@link BigDecimal} convertido ou {@code null} em caso de erro
     *
     */
    private BigDecimal validarDecimal(String valor, ItemCollectorSimples collector, String campo,
            CodigoValidacaoSimples codigoAusente, CodigoValidacaoSimples codigoInvalido) {
        if (valor == null || valor.isBlank()) {
            collector.addErroValidacao(codigoAusente, campo, "O campo é obrigatório e não foi informado.");
            return null;
        }
        try {
            BigDecimal convertido = new BigDecimal(valor.trim());
            if (convertido.signum() < 0) {
                collector.addErroValidacao(codigoInvalido, campo,
                        String.format("O valor não pode ser negativo, recebido: '%s'.", valor));
                return null;
            }
            return convertido;
        } catch (NumberFormatException ex) {
            collector.addErroValidacao(codigoInvalido, campo,
                    String.format("O valor não é um número decimal válido, recebido: '%s'.", valor));
            return null;
        }
    }

    // ─── Validação global da operação ──────────────────────────────────────────

    private List<ErroDetalhe> validarCamposGlobais(OperacaoSimplesInput operacao, String baseURL) {
        List<ErroDetalhe> erros = new ArrayList<>();

        if (operacao.id() == null || operacao.id().isBlank()) {
            erros.add(buildErroGlobal(CodigoValidacaoSimples.DADO_GLOBAL_AUSENTE, "id",
                    "O identificador da operação (id) é obrigatório.", baseURL));
        }
        if (operacao.versao() == null || operacao.versao().isBlank()) {
            erros.add(buildErroGlobal(CodigoValidacaoSimples.DADO_GLOBAL_AUSENTE, "versao",
                    "A versão do payload (versao) é obrigatória.", baseURL));
        }

        // A data é validada antes do escopo porque a verificação de suporte do
        // tpDoc é um lookup de vigência que depende dela.
        LocalDate data = validarData(operacao.data(), erros, baseURL);
        validarEscopo(operacao, data, erros, baseURL);
        validarTpEnteGov(operacao.tpEnteGov(), erros, baseURL);

        if (operacao.itens() == null || operacao.itens().isEmpty()) {
            erros.add(buildErroGlobal(CodigoValidacaoSimples.ITENS_VAZIOS, "itens",
                    "A lista de itens é obrigatória e não pode estar vazia.", baseURL));
        }
        return erros;
    }

    /**
     *
     * Validação global de escopo do cálculo (NT 2026.005; regras C21a-10/20/30,
     * msg 1275). Qualquer falha rejeita a operação inteira com HTTP 422.
     *
     * <p>Combinações que CALCULAM: CRT=1 (sem tpRegimeIBSCBS), CRT=2 (sem
     * tpRegimeIBSCBS) e CRT=3 + tpRegimeIBSCBS=2. Todo o resto é rejeitado:
     * tpDoc ausente/não suportado, CRT ausente/inválido, MEI (CRT=4),
     * regime regular (tpRegimeIBSCBS=1), regime normal puro (CRT=3 sem
     * tpRegimeIBSCBS=2) e as incoerências CRT × tpRegimeIBSCBS.
     *
     * <p>O suporte do tpDoc é parametrizado na tabela
     * {@code TIPO_DFE_CALCULO_SIMPLES_NACIONAL}, pela vigência da data do fato
     * gerador; quando a data é inválida (já reportada como VSN-012), a
     * verificação de suporte é omitida.
     *
     */
    private void validarEscopo(OperacaoSimplesInput operacao, LocalDate data,
            List<ErroDetalhe> erros, String baseURL) {
        // tpDoc: obrigatório e suportado pelo cálculo na data do fato gerador.
        if (operacao.tpDoc() == null || operacao.tpDoc().isBlank()) {
            erros.add(buildErroGlobal(CodigoValidacaoSimples.TPDOC_AUSENTE, "tpDoc",
                    "O tipo de documento fiscal (tpDoc) é obrigatório e não foi informado.", baseURL));
        } else {
            Integer tpDoc = parseInteiro(operacao.tpDoc());
            if (tpDoc == null) {
                erros.add(buildErroGlobal(CodigoValidacaoSimples.TPDOC_NAO_SUPORTADO, "tpDoc",
                        String.format("Tipo de documento não suportado pelo cálculo do Simples Nacional, "
                                + "recebido: '%s'.", operacao.tpDoc()), baseURL));
            } else if (parametroService.tpDocSemTratamentoCalculadora(tpDoc)) {
                erros.add(buildErroGlobal(CodigoValidacaoSimples.TPDOC_NAO_SUPORTADO, "tpDoc",
                        String.format("Tipo de documento fiscal (tpDoc) de código %d ainda não possui "
                                + "tratamento implementado no cálculo do Simples Nacional.", tpDoc), baseURL));
            } else if (data != null && !parametroService.tpDocSuportado(tpDoc, data)) {
                erros.add(buildErroGlobal(CodigoValidacaoSimples.TPDOC_NAO_SUPORTADO, "tpDoc",
                        String.format("Tipo de documento não suportado pelo cálculo do Simples Nacional, "
                                + "recebido: '%s'.", operacao.tpDoc()), baseURL));
            }
        }

        // CRT: obrigatório, em {1, 2, 3, 4}.
        Integer crt = parseInteiro(operacao.CRT());
        if (crt == null || crt < 1 || crt > 4) {
            erros.add(buildErroGlobal(CodigoValidacaoSimples.CRT_INVALIDO, CAMPO_CRT,
                    String.format("O Código de Regime Tributário (CRT) é obrigatório e deve ser 1, 2, 3 ou 4, "
                            + "recebido: '%s'.", operacao.CRT()), baseURL));
            return;
        }

        // tpRegimeIBSCBS: opcional; quando informado, em {1, 2}.
        boolean tpRegimeInformado = operacao.tpRegimeIBSCBS() != null && !operacao.tpRegimeIBSCBS().isBlank();
        Integer tpRegime = null;
        if (tpRegimeInformado) {
            tpRegime = parseInteiro(operacao.tpRegimeIBSCBS());
            if (tpRegime == null || (tpRegime != 1 && tpRegime != 2)) {
                erros.add(buildErroGlobal(CodigoValidacaoSimples.TPREGIMEIBSCBS_INVALIDO, CAMPO_TPREGIMEIBSCBS,
                        String.format("Tipo de regime de apuração do IBS/CBS inválido, recebido: '%s' "
                                + "(esperado 1 ou 2).", operacao.tpRegimeIBSCBS()), baseURL));
                return;
            }
        }

        // Incoerências CRT × tpRegimeIBSCBS (regras C21a-10/20/30 da NT, msg 1275).
        if (crt == 4 && tpRegimeInformado) {
            erros.add(buildErroGlobal(CodigoValidacaoSimples.TPREGIMEIBSCBS_INCOMPATIVEL, CAMPO_TPREGIMEIBSCBS,
                    "CRT=4 (MEI) não admite tpRegimeIBSCBS algum (regra C21a-30 da NT 2026.005).", baseURL));
        } else if ((crt == 1 || crt == 2) && Integer.valueOf(2).equals(tpRegime)) {
            erros.add(buildErroGlobal(CodigoValidacaoSimples.TPREGIMEIBSCBS_INCOMPATIVEL, CAMPO_TPREGIMEIBSCBS,
                    String.format("CRT=%d não admite tpRegimeIBSCBS=2 (regra C21a-10 da NT 2026.005).", crt),
                    baseURL));
        } else if (crt == 3 && Integer.valueOf(1).equals(tpRegime)) {
            erros.add(buildErroGlobal(CodigoValidacaoSimples.TPREGIMEIBSCBS_INCOMPATIVEL, CAMPO_TPREGIMEIBSCBS,
                    "CRT=3 não admite tpRegimeIBSCBS=1 (regra C21a-20 da NT 2026.005).", baseURL));
        }

        // Fora do escopo do cálculo assistido do Simples Nacional.
        if (crt == 4) {
            erros.add(buildErroGlobal(CodigoValidacaoSimples.OPERACAO_FORA_ESCOPO, CAMPO_CRT,
                    "Operação fora do escopo do cálculo assistido do Simples Nacional: MEI (CRT=4) "
                            + "está fora do Evento de Tributação do Simples Nacional.", baseURL));
        }
        if (Integer.valueOf(1).equals(tpRegime)) {
            erros.add(buildErroGlobal(CodigoValidacaoSimples.OPERACAO_FORA_ESCOPO, CAMPO_TPREGIMEIBSCBS,
                    "Operação fora do escopo do cálculo assistido do Simples Nacional: optante pelo regime "
                            + "regular do IBS/CBS (tpRegimeIBSCBS=1) tem a tributação calculada pelo regime "
                            + "regular, não por este endpoint.", baseURL));
        }
        if (crt == 3 && !tpRegimeInformado) {
            erros.add(buildErroGlobal(CodigoValidacaoSimples.OPERACAO_FORA_ESCOPO, CAMPO_CRT,
                    "Operação fora do escopo do cálculo assistido do Simples Nacional: CRT=3 sem "
                            + "tpRegimeIBSCBS=2 (regime normal puro) não é Simples Nacional.", baseURL));
        }
    }

    /**
     * Validação GLOBAL do tipo de ente governamental (compra governamental),
     * quando informado: deve ser 1 (União), 2 (Estado), 3 (DF) ou 4 (Município).
     *
     * <p>O campo é da OPERAÇÃO — espelha a tag {@code gCompraGov/tpEnteGov} da
     * identificação da nota (grupo B/ide do leiaute do DF-e, NT 2025.002-RTC):
     * o ente público adquirente é o destinatário do documento e não varia por
     * item. Valor fora do domínio rejeita a operação inteira (VSN-024,
     * HTTP 422).
     */
    private void validarTpEnteGov(String tpEnteGov, List<ErroDetalhe> erros, String baseURL) {
        if (tpEnteGov == null || tpEnteGov.isBlank()) {
            return;
        }
        Integer valor = parseInteiro(tpEnteGov);
        if (valor == null || valor < 1 || valor > 4) {
            erros.add(buildErroGlobal(CodigoValidacaoSimples.TPENTEGOV_INVALIDO, CAMPO_TPENTEGOV,
                    String.format("Tipo de ente governamental inválido, recebido: '%s' (esperado 1, 2, 3 ou 4).",
                            tpEnteGov), baseURL));
        }
    }

    /**
     * Resolve o regime de cálculo a partir de uma operação JÁ APROVADA pela
     * validação de escopo (as combinações restantes são exatamente as que
     * calculam). O comportamento do regime vem da tabela
     * {@code REGIME_APURACAO_SIMPLES_NACIONAL}, resolvida na sequência.
     */
    private RegimeCalculoSimplesEnum resolverRegime(OperacaoSimplesInput operacao) {
        int crt = Integer.parseInt(operacao.CRT().trim());
        return switch (crt) {
            case 1 -> RegimeCalculoSimplesEnum.CRT_1;
            case 2 -> RegimeCalculoSimplesEnum.CRT_2;
            default -> RegimeCalculoSimplesEnum.CRT_3_TPREGIME_2;
        };
    }

    /**
     * Valida a data do fato gerador e a converte.
     *
     * @return a data convertida, ou {@code null} (com o erro VSN-012 acumulado)
     *         quando ausente ou inválida
     */
    private LocalDate validarData(String data, List<ErroDetalhe> erros, String baseURL) {
        if (data == null || data.isBlank()) {
            erros.add(buildErroGlobal(CodigoValidacaoSimples.DATA_INVALIDA, "data",
                    "A data do fato gerador é obrigatória.", baseURL));
            return null;
        }
        try {
            return LocalDate.parse(data.trim());
        } catch (DateTimeParseException ex) {
            erros.add(buildErroGlobal(CodigoValidacaoSimples.DATA_INVALIDA, "data",
                    String.format("Data inválida, recebido: '%s' (esperado YYYY-MM-DD).", data), baseURL));
            return null;
        }
    }

    /** Converte texto em inteiro de forma tolerante ({@code null} quando inválido). */
    private Integer parseInteiro(String valor) {
        if (valor == null || valor.isBlank()) {
            return null;
        }
        try {
            return Integer.valueOf(valor.trim());
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private ErroDetalhe buildErroGlobal(CodigoValidacaoSimples codigo, String campo, String detail,
            String baseURL) {
        String pointer = "#/" + campo;
        String type = String.format(
                "%s/calculadora/observabilidade/simples-nacional/erros?codigo=%s", baseURL, codigo.getCode());
        return new ErroDetalhe(type, codigo.getTitulo(), codigo.getCode(), pointer, detail);
    }
}
