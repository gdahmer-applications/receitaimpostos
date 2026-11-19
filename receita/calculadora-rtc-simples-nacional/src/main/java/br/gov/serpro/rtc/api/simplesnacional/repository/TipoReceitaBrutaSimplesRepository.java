package br.gov.serpro.rtc.api.simplesnacional.repository;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import br.gov.serpro.rtc.api.simplesnacional.entity.TipoReceitaBrutaSimples;

/**
 * Repositório Spring Data JPA, somente leitura, dos efeitos dos Tipos de
 * Receita Bruta do Simples Nacional ({@link TipoReceitaBrutaSimples}).
 *
 * <p>
 * Todos os lookups de vigência usam a DATA DO FATO GERADOR da operação,
 * nunca a data corrente.
 */
@Repository
public interface TipoReceitaBrutaSimplesRepository extends JpaRepository<TipoReceitaBrutaSimples, Long> {

    /**
     * Retorna as linhas do código de tpRBSN vigentes na data do fato gerador,
     * da vigência mais recente para a mais antiga (a primeira da lista é a
     * aplicável; lista vazia = falha de parametrização, erro VSN-026).
     */
    @Query("""
            SELECT t
            FROM TipoReceitaBrutaSimples t
            WHERE t.codigo = :codigo
            AND :data BETWEEN t.inicioVigencia AND COALESCE(t.fimVigencia, :data)
            ORDER BY t.inicioVigencia DESC
            """)
    List<TipoReceitaBrutaSimples> buscarVigentesPorCodigo(@Param("codigo") int codigo,
            @Param("data") LocalDate data);

}
