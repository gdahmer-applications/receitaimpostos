package br.gov.serpro.rtc.api.simplesnacional.exceptionhandler.enumeration;

import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import lombok.Getter;

/**
 * Catálogo próprio de códigos de validação do Simples Nacional,
 * com prefixo {@code VSN-} (Validação Simples Nacional), alinhado à
 * NT 2026.005 (Reforma Tributária do Consumo — Simples Nacional).
 *
 * <p>
 * POLÍTICA DE APOSENTADORIA DE CÓDIGOS: os códigos {@code VSN-008} e
 * {@code VSN-009} (origem de alíquota ANEXO/REGIME_GERAL do contrato antigo)
 * foram APOSENTADOS junto com o conceito de "origem" e seus números NÃO são
 * reutilizados, para que consultas históricas ao catálogo nunca retornem um
 * significado diferente do que foi emitido à época. Novos erros recebem
 * números a partir de {@code VSN-014}.
 *
 * <p>
 * Duplicado deliberadamente do análogo {@code CodigoValidacao} do regime geral:
 * os catálogos de erro dos dois cálculos são independentes (códigos, mensagens e
 * ciclos de vida próprios) e a duplicação preserva o isolamento entre os pacotes.
 *
 */
@Getter
public enum CodigoValidacaoSimples {

    BASE_AUSENTE("VSN-001",
            "Base de cálculo do Simples ausente",
            "A base de cálculo do Simples Nacional é obrigatória e não foi informada."),
    BASE_INVALIDA("VSN-002",
            "Base de cálculo do Simples inválida (negativa/não numérica)",
            "A base de cálculo informada não é um número decimal válido ou é negativa."),
    IS_INVALIDO("VSN-003",
            "Valor do Imposto Seletivo inválido (negativo/não numérico)",
            "O valor do Imposto Seletivo (vIS) informado não é um número decimal válido ou é negativo."),
    ALIQUOTA_CBS_AUSENTE("VSN-004",
            "Alíquota pCBSSN ausente",
            "A alíquota efetiva de CBS do Simples Nacional (pCBSSN) é exigida para o cálculo do item e não foi informada."),
    ALIQUOTA_CBS_INVALIDA("VSN-005",
            "Alíquota pCBSSN inválida (negativa/não numérica)",
            "A alíquota efetiva de CBS do Simples Nacional (pCBSSN) informada não é um número decimal válido ou é negativa."),
    ALIQUOTA_IBS_AUSENTE("VSN-006",
            "Alíquota pIBSSN ausente",
            "A alíquota efetiva de IBS do Simples Nacional (pIBSSN) é exigida para o cálculo do item e não foi informada."),
    ALIQUOTA_IBS_INVALIDA("VSN-007",
            "Alíquota pIBSSN inválida (negativa/não numérica)",
            "A alíquota efetiva de IBS do Simples Nacional (pIBSSN) informada não é um número decimal válido ou é negativa."),
    // VSN-008 e VSN-009 (origem de alíquota): APOSENTADOS — números não reutilizados (ver Javadoc da classe).
    NUMERO_AUSENTE("VSN-010",
            "Número do item ausente",
            "O número (identificador) do item é obrigatório e não foi informado."),
    ITENS_VAZIOS("VSN-011",
            "Lista de itens vazia",
            "A lista de itens da operação é obrigatória e não pode estar vazia."),
    DATA_INVALIDA("VSN-012",
            "Data do fato gerador ausente/inválida",
            "A data do fato gerador é obrigatória e deve estar no formato de data (YYYY-MM-DD)."),
    DADO_GLOBAL_AUSENTE("VSN-013",
            "Dado global obrigatório ausente",
            "Um campo global obrigatório da operação (id ou versao) não foi informado."),
    TPDOC_AUSENTE("VSN-014",
            "Tipo de documento fiscal (tpDoc) ausente",
            "O tipo de documento fiscal (tpDoc) é obrigatório e não foi informado."),
    TPDOC_NAO_SUPORTADO("VSN-015",
            "Tipo de documento fiscal (tpDoc) não suportado",
            "O tipo de documento fiscal (tpDoc) informado não está habilitado para o cálculo do Simples "
                    + "Nacional na data do fato gerador. A habilitação de documentos é parametrizada em banco "
                    + "de dados (tabela de tipos de DFe do cálculo do Simples), conforme as NTs publicadas."),
    CRT_INVALIDO("VSN-016",
            "Código de Regime Tributário (CRT) ausente/inválido",
            "O Código de Regime Tributário (CRT) é obrigatório e deve ser 1, 2, 3 ou 4."),
    TPREGIMEIBSCBS_INVALIDO("VSN-017",
            "Tipo de regime do IBS/CBS (tpRegimeIBSCBS) inválido",
            "O tipo de regime de apuração do IBS/CBS (tpRegimeIBSCBS), quando informado, deve ser 1 ou 2."),
    TPREGIMEIBSCBS_INCOMPATIVEL("VSN-018",
            "tpRegimeIBSCBS incompatível com o CRT",
            "A combinação de CRT e tpRegimeIBSCBS informada é incoerente (regras C21a-10/20/30 da NT 2026.005): "
                    + "CRT 1, 2 ou 4 não admite tpRegimeIBSCBS=2; CRT 3 não admite tpRegimeIBSCBS=1; "
                    + "CRT 4 não admite tpRegimeIBSCBS algum."),
    OPERACAO_FORA_ESCOPO("VSN-019",
            "Operação fora do escopo do cálculo assistido do Simples Nacional",
            "A operação não é calculada por este endpoint: optante pelo regime regular do IBS/CBS "
                    + "(tpRegimeIBSCBS=1), MEI (CRT=4) ou regime normal puro (CRT=3 sem tpRegimeIBSCBS=2)."),
    CCLASSTRIB_AUSENTE("VSN-020",
            "Código de Classificação Tributária (cClassTrib) ausente",
            "O código de classificação tributária do IBS/CBS (cClassTrib) é obrigatório e não foi informado."),
    CCLASSTRIB_DESCONHECIDO("VSN-021",
            "cClassTrib desconhecido ou fora de vigência",
            "O cClassTrib informado não existe ou não está vigente na data do fato gerador: "
                    + "o Tipo de Receita Bruta do Simples Nacional (tpRBSN) não pôde ser determinado."),
    CCLASSTRIB_INCOMPATIVEL_TPDOC("VSN-022",
            "cClassTrib incompatível com o tpDoc",
            "O cClassTrib informado não é admissível para o tipo de documento fiscal (tpDoc) da operação."),
    CATIVSN_INCOMPATIVEL_CCLASSTRIB("VSN-023",
            "Código de Atividade do Simples Nacional incompatível com cClassTrib",
            "O código de atividade cAtivSN='90' (Operações não Tributáveis) é incompatível com cClassTrib "
                    + "cujo tpRBSN esteja entre 1 e 5 (regra UB14-90 da NT 2026.005)."),
    TPENTEGOV_INVALIDO("VSN-024",
            "Tipo de ente governamental (tpEnteGov) inválido",
            "O tipo de ente governamental (tpEnteGov), quando informado, deve ser 1 (União), 2 (Estado), "
                    + "3 (Distrito Federal) ou 4 (Município). O campo é da operação — espelha a tag "
                    + "gCompraGov/tpEnteGov da identificação da nota (grupo B/ide do leiaute do DF-e) — e "
                    + "sua invalidade rejeita a operação inteira."),
    COMPRA_GOV_SEM_VIGENCIA("VSN-025",
            "Parametrização de compra governamental indisponível para a data",
            "A data do fato gerador está fora de todas as vigências do parâmetro de transição de alíquotas "
                    + "de compra governamental do Simples Nacional (NT 2026.005, seção 5.3.3; arts. 372 e 473 "
                    + "da LC 214/2025). Como o tpEnteGov é dado da operação, a falha rejeita a operação "
                    + "inteira."),
    PARAMETRIZACAO_INDISPONIVEL("VSN-026",
            "Parametrização do cálculo do Simples Nacional indisponível para a data",
            "Não há linha vigente, na data do fato gerador, em uma tabela de parametrização do cálculo do "
                    + "Simples Nacional (efeito do tipo de receita bruta ou matriz de regime de apuração). "
                    + "O cálculo nunca assume comportamento implícito: a parametrização deve ser corrigida "
                    + "por script de migração."),
    CCLASSTRIB_NAO_IMPLEMENTADO("VSN-027",
            "cClassTrib sem tratamento implementado no cálculo do Simples Nacional",
            "A classificação tributária (cClassTrib) informada está vinculada, na data do fato gerador, a "
                    + "tratamento tributário marcado como ainda não implementado na Calculadora (indicador de "
                    + "ajuste do tratamento tributário). O item é reportado com o estado NAO_IMPLEMENTADO, "
                    + "sem cálculo e fora dos totais; a funcionalidade está prevista no roadmap e será "
                    + "disponibilizada em versão futura."),
    DESCONHECIDO("VSN-000",
            "Erro de validação não categorizado",
            "O tipo de validação aplicado não possui uma categoria específica."),
    FALHA_TECNICA("VSN-999",
            "Falha técnica no cálculo do item",
            "Ocorreu uma falha técnica inesperada durante o cálculo do item.");

    private final String code;
    private final String titulo;
    private final String descricao;

    private static final Map<String, CodigoValidacaoSimples> CODE_INDEX =
            Stream.of(values())
                    .collect(Collectors.toUnmodifiableMap(CodigoValidacaoSimples::getCode, Function.identity()));

    CodigoValidacaoSimples(String code, String titulo, String descricao) {
        this.code = code;
        this.titulo = titulo;
        this.descricao = descricao;
    }

    public static Optional<CodigoValidacaoSimples> fromCodigo(String codigo) {
        if (codigo == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(CODE_INDEX.get(codigo));
    }
}
