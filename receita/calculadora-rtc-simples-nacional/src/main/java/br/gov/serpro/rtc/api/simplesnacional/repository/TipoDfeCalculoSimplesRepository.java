package br.gov.serpro.rtc.api.simplesnacional.repository;

import java.time.LocalDate;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.NativeQuery;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import br.gov.serpro.rtc.api.simplesnacional.entity.TipoDfeCalculoSimples;

/**
 * Repositório Spring Data JPA, somente leitura, dos tipos de documento fiscal
 * suportados pelo cálculo do Simples Nacional ({@link TipoDfeCalculoSimples}).
 *
 * <p>
 * ISOLADO. A consulta lê a tabela de catálogo compartilhada {@code TIPO_DFE}
 * por consulta nativa, sem importar entidades do domínio do regime geral.
 *
 * <p>
 * Todos os lookups de vigência usam a DATA DO FATO GERADOR da operação,
 * nunca a data corrente.
 */
@Repository
public interface TipoDfeCalculoSimplesRepository extends JpaRepository<TipoDfeCalculoSimples, Long> {

    /**
     * Conta os vínculos vigentes do tipo de documento ({@code tpDoc}: 55, 65,
     * 91, ...) com o cálculo do Simples Nacional, via {@code TIPO_DFE} (coluna
     * {@code TPDF_TIPO}). Zero = tpDoc não suportado (a operação inteira é
     * rejeitada com HTTP 422, erro VSN-015).
     */
    @NativeQuery(value = """
            SELECT COUNT(1)
            FROM TIPO_DFE_CALCULO_SIMPLES_NACIONAL t
            JOIN TIPO_DFE td ON td.TPDF_ID = t.TDCS_TPDF_ID
            WHERE td.TPDF_TIPO = :tpDoc
            AND :data BETWEEN t.TDCS_INICIO_VIGENCIA AND COALESCE(t.TDCS_FIM_VIGENCIA, :data)
            AND :data BETWEEN td.TPDF_INICIO_VIGENCIA AND COALESCE(td.TPDF_FIM_VIGENCIA, :data)
            """)
    long contarVinculosVigentes(@Param("tpDoc") int tpDoc, @Param("data") LocalDate data);

}
