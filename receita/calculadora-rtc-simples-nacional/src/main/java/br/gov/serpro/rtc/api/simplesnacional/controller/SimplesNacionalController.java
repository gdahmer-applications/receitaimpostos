package br.gov.serpro.rtc.api.simplesnacional.controller;

import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import br.gov.serpro.rtc.api.simplesnacional.exceptionhandler.dto.ErroDescricaoSimples;
import br.gov.serpro.rtc.api.simplesnacional.model.input.OperacaoSimplesInput;
import br.gov.serpro.rtc.api.simplesnacional.model.output.ObservabilidadeSimplesDomain;
import br.gov.serpro.rtc.api.simplesnacional.service.SimplesNacionalErroService;
import br.gov.serpro.rtc.api.simplesnacional.service.SimplesNacionalObservabilidadeService;
import br.gov.serpro.rtc.shared.util.HttpUtils;
import br.gov.serpro.rtc.api.versao.service.VersaoAplicacaoService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Controlador REST de observabilidade do Simples Nacional.
 *
 */
@Slf4j
@RequiredArgsConstructor
@RestController
@RequestMapping("calculadora/observabilidade/simples-nacional")
public class SimplesNacionalController implements SimplesNacionalControllerOpenApi {

    private final SimplesNacionalObservabilidadeService observabilidadeService;
    private final SimplesNacionalErroService erroService;
    private final VersaoAplicacaoService versaoAplicacaoService;

    @Override
    @PostMapping(consumes = APPLICATION_JSON_VALUE, produces = APPLICATION_JSON_VALUE)
    public ResponseEntity<ObservabilidadeSimplesDomain> calcularTributosSimplesNacional(
            @RequestBody OperacaoSimplesInput operacao) {
        String baseURL = HttpUtils.getBaseURL();

        log.debug("Observabilidade Simples Nacional ID {}", operacao.id());
        ObservabilidadeSimplesDomain resultado = observabilidadeService.processarOperacao(operacao, baseURL);

        return ResponseEntity.ok()
                .headers(versaoAplicacaoService.getHeaders())
                .body(resultado);
    }

    @Override
    @GetMapping(value = "erros", produces = APPLICATION_JSON_VALUE)
    public ResponseEntity<ErroDescricaoSimples> buscarErro(@RequestParam String codigo) {
        return erroService.buscarPorCodigo(codigo)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
}
