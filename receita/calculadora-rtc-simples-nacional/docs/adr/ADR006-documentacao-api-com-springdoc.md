# ADR006 - Documentação de API com SpringDoc OpenAPI

## Data

> 24/07/2026

## Status

> Aceito

## Contexto

A API precisa de documentação navegável para o time e para o frontend, com Swagger UI acessível
em `<API>/swagger-ui.html`.

## Decisão

- Biblioteca `springdoc-openapi` na versão `2.8.9` (propriedade `openapi.version` no [pom.xml](../../pom.xml)).
- Especificação gerada em OpenAPI 3.1 (`springdoc.api-docs.version: openapi-3-1`) no caminho `/api-docs`.
- Configurações da UI em `springdoc.swagger-ui` (layout `BaseLayout`, duração das requisições exibida, submissão pela UI limitada aos métodos `get` e `post`).
- `springdoc.show-actuator: false` e `springdoc.cache.disabled: true` ([application-offline.yml](../../src/main/resources/application-offline.yml)).
- Anotações OpenAPI ficam em interfaces separadas dos controllers, com sufixo `ControllerOpenApi`, por exemplo
  [AtividadeSimplesControllerOpenApi](../../src/main/java/br/gov/serpro/rtc/api/atividadesimples/controller/AtividadeSimplesControllerOpenApi.java),
  [SimplesNacionalControllerOpenApi](../../src/main/java/br/gov/serpro/rtc/api/simplesnacional/controller/SimplesNacionalControllerOpenApi.java),
  [VersaoControllerOpenApi](../../src/main/java/br/gov/serpro/rtc/api/versao/controller/VersaoControllerOpenApi.java) e
  [VersaoOfflineControllerOpenApi](../../src/main/java/br/gov/serpro/rtc/api/versao/controller/VersaoOfflineControllerOpenApi.java).
- A política `frameOptions` com `sameOrigin` em [WebSecurityConfig](../../src/main/java/br/gov/serpro/rtc/config/security/WebSecurityConfig.java)
  permite exibir o Swagger dentro de um frame do portal da RTC.

## Consequências

Positivas:
- Contrato da API gerado a partir do código, com OpenAPI 3.1
- Separação entre anotações de documentação e lógica do controller

Negativas:
- Anotações extensas nas interfaces `*ControllerOpenApi` precisam acompanhar mudanças nos endpoints

## Notas

- Introdução do springdoc e das interfaces `*ControllerOpenApi`: 24/07/2026.
