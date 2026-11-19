package br.gov.serpro.rtc.api.versao.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import br.gov.serpro.rtc.api.versao.dto.VersaoDomain;
import lombok.RequiredArgsConstructor;

/**
 * Serviço responsável por montar as informações de versão desta instalação:
 * versão da aplicação, versão/descrição/data da base de dados e ambiente.
 *
 * <p>Disponível em todos os perfis: alimenta o endpoint {@code /versao}
 * (visível em qualquer modo) e serve de versão LOCAL para o comparador do modo
 * offline ({@link VersaoOfflineService}).
 */
@RequiredArgsConstructor
@Service
public class VersaoService {

    @Value("${info.app.version:unknown}")
    private String versaoAplicacao;

    @Value("${versao.ambiente:unknown}")
    private String ambiente;

    private final VersaoBaseDadosService versaoBaseDadosService;

    public VersaoDomain getVersao() {
        final var ultimaVersao = versaoBaseDadosService.getUltimaVersao();
        return VersaoDomain.builder()
                .versaoApp(versaoAplicacao)
                .versaoDb(ultimaVersao.getNumeroVersao())
                .descricaoVersaoDb(ultimaVersao.getDescricao())
                .dataVersaoDb(ultimaVersao.getData().toString())
                .ambiente(ambiente)
                .build();
    }

}
