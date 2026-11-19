package br.gov.serpro.rtc.api.simplesnacional.entity;

import java.time.LocalDate;

import org.hibernate.annotations.Immutable;

import br.gov.serpro.rtc.api.simplesnacional.enumeration.DestinoValorSimplesEnum;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * Entidade JPA somente leitura da tabela {@code REGIME_APURACAO_SIMPLES_NACIONAL}:
 * a matriz de regime do cálculo do Simples Nacional (fórmulas P28 a P33 da
 * NT 2026.005). Para cada combinação de CRT e tpRegimeIBSCBS que calcula, define
 * quais campos são gerados e o destino dos valores calculados.
 *
 * <p>
 * As combinações que NÃO calculam (MEI, regime regular, regime normal puro e as
 * incoerências entre CRT e tpRegimeIBSCBS) são rejeitadas pela validação de escopo
 * do serviço de observabilidade, antes de qualquer consulta a esta tabela.
 *
 * <p>
 * Tabela exclusiva do cálculo do Simples Nacional; mudanças de comportamento por
 * regime são feitas por script de migração, sem alteração de código Java.
 */
@Immutable
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@Data
@Entity
@Table(name = "REGIME_APURACAO_SIMPLES_NACIONAL")
public class RegimeApuracaoSimples {

    @EqualsAndHashCode.Include
    @Id
    @Column(name = "RASN_ID")
    private Long id;

    /** Código de Regime Tributário do emitente (1, 2 ou 3). */
    @NotNull
    @Column(name = "RASN_CRT")
    private Integer crt;

    /** Tipo de regime de apuração do IBS/CBS ({@code null} = regime informado sem essa tag). */
    @Column(name = "RASN_TPREGIMEIBSCBS")
    private Integer tpRegimeIBSCBS;

    @NotNull
    @Column(name = "RASN_DESCRICAO")
    private String descricao;

    /**
     * {@code false}: o bloco de IBS ({@code pIBSSN}, {@code vIBSSN},
     * {@code vIBSPendSusp}) é suprimido da saída. Caso do CRT=2 (excesso de
     * sublimite): o IBS desse contribuinte é declarado pelo próprio emitente no
     * gIBSCBS da nota, fora do evento do Simples.
     */
    @NotNull
    @Column(name = "RASN_IN_GERA_IBS")
    private Boolean geraIbs;

    /** Destino do valor que o tipo de receita bruta qualificou como devido. */
    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "RASN_DESTINO_DEVIDO")
    private DestinoValorSimplesEnum destinoDevido;

    /**
     * Destino do valor que o tipo de receita bruta qualificou como pendente/suspenso
     * (exportação indireta). O destino {@code ZERO} do CRT=3 + tpRegimeIBSCBS=2
     * reproduz literalmente as fórmulas P32/P33 da NT 2026.005 — intencional.
     */
    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "RASN_DESTINO_PENDENTE_SUSPENSO")
    private DestinoValorSimplesEnum destinoPendenteSuspenso;

    @NotNull
    @Column(name = "RASN_INICIO_VIGENCIA")
    private LocalDate inicioVigencia;

    @Column(name = "RASN_FIM_VIGENCIA")
    private LocalDate fimVigencia;

}
