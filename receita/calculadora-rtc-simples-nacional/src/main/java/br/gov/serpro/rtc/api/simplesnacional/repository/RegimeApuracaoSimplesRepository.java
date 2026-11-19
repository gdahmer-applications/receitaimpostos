package br.gov.serpro.rtc.api.simplesnacional.repository;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import br.gov.serpro.rtc.api.simplesnacional.entity.RegimeApuracaoSimples;

/**
 * Repositório Spring Data JPA, somente leitura, da matriz de regime do cálculo
 * do Simples Nacional ({@link RegimeApuracaoSimples}).
 *
 * <p>
 * Todos os lookups de vigência usam a DATA DO FATO GERADOR da operação,
 * nunca a data corrente.
 */
@Repository
public interface RegimeApuracaoSimplesRepository extends JpaRepository<RegimeApuracaoSimples, Long> {

    /**
     * Retorna as linhas da combinação CRT × tpRegimeIBSCBS vigentes na data do
     * fato gerador, da vigência mais recente para a mais antiga (a primeira da
     * lista é a aplicável; lista vazia = falha de parametrização, erro VSN-026).
     * {@code tpRegimeIBSCBS} nulo casa apenas com linhas de coluna nula.
     */
    @Query("""
            SELECT r
            FROM RegimeApuracaoSimples r
            WHERE r.crt = :crt
            AND ((:tpRegimeIBSCBS IS NULL AND r.tpRegimeIBSCBS IS NULL) OR r.tpRegimeIBSCBS = :tpRegimeIBSCBS)
            AND :data BETWEEN r.inicioVigencia AND COALESCE(r.fimVigencia, :data)
            ORDER BY r.inicioVigencia DESC
            """)
    List<RegimeApuracaoSimples> buscarVigentes(@Param("crt") int crt,
            @Param("tpRegimeIBSCBS") Integer tpRegimeIBSCBS, @Param("data") LocalDate data);

}
