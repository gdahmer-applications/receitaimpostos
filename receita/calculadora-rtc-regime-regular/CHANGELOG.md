# Changelog

Todas as mudanças notáveis neste projeto serão documentadas neste arquivo.

O formato é baseado em [Mantenha um Changelog](https://keepachangelog.com/pt-BR/1.1.0/),
e este projeto adere ao [Versionamento Semântico](https://semver.org/lang/pt-BR/).

## [1.6.0] - 2026-10-13

### Modificado

- Inclusão do atributo `exigeGrupoIbsCbs` no retorno do serviço `GET /calculadora/dados-abertos/classificacoes-tributarias/cbs-ibs/{siglaDfe}/{cClassTrib}`

### Adicionado

- Endpoint de dados abertos `GET /calculadora/dados-abertos/ncm/vigentes` para listagem de NCMs vigentes (8 dígitos), com cache público de 1 hora e validação de data

## [1.5.4] - 2026-09-30

### Modificado

- Migração Flyway `V0058`: ajuste das vigências do cIndOp/classificações NBS relacionadas (`CLASSIF_NBS_INDOP_LC` e `INDICADOR_OPERACAO_IBS_CBS`), com fim em 02/11/2026 e início em 03/11/2026

## [1.5.1] - 2026-09-10

### Corrigido

- Correção de typos no enum `LocalFornecimento`

## [1.5.0] - 2026-09-08

### Adicionado

- Campo `adRemIS` na resposta do Imposto Seletivo (`ImpostoSeletivoDomain`): passa a retornar a alíquota ad rem do IS, substituindo o campo depreciado `pISEspec`
- Campo `pDevTrib` na resposta de devolução de tributos (`DevolucaoTributosDomain`): passa a retornar o percentual do tributo devolvido
- Script de migração `V0048`: relações dos tipos de DF-e DIR e Duimp com as classificações tributárias da CBS, com correção das relações de NFAg e NFGas com os cClassTribs 410005 e 000002

### Modificado

- Passa a desabilitar também a NFe ABI (tipo 77) nos endpoints de cálculo, além do DERE (tipo 94), lançando `ErroFaltaImplementacaoException` quando informados
- Endpoint `POST calculadora/regime-geral`: input da operação passa a aceitar o bloco `aliquotasNominais` com `cbs`, `ibsEstadual`, `ibsMunicipal` e `impostoSeletivo` (`adValorem` e `adRem`) para permitir que o usuário informe as alíquotas a serem usadas na simulação do cálculo dos tributos
- Endpoint `POST calculadora/observabilidade/regime-geral`: input da operação passa a aceitar o bloco `aliquotasNominais` com `cbs`, `ibsEstadual`, `ibsMunicipal` e `impostoSeletivo` (`adValorem` e `adRem`) para permitir que o usuário informe as alíquotas a serem usadas na simulação do cálculo dos tributos
- Respostas de cálculo passam a expor o campo `calculoSimulado` em `ObjetoDomain`, e o teste de regressão de vários itens ignora esse campo na comparação de snapshots
- Documentação OpenAPI do header `x-warning-dados-simulados` foi detalhada (incluindo o código `6`) e replicada no endpoint de observabilidade (marcado como versão beta)
- Enumerações de observabilidade/dados simulados atualizadas com `CALCULO_SIMULADO` (`EstadoItemEnum`) e `CASO_CALCULOS_SIMULADOS` (`TipoWarningDadosSimulados`)
- No cálculo de CBS/IBS, as alíquotas nominais informadas em `aliquotasNominais` passam a ter precedência sobre as alíquotas de referência do banco (padrão, uniforme nacional, uniforme setorial, fixa e "sem alíquota"); quando a expressão de alíquota do tratamento tributário não utiliza a variável `aliquota`, o resultado da alíquota é o valor informado
- No cálculo do Imposto Seletivo, as alíquotas `adValorem` e `adRem` informadas em `aliquotasNominais.impostoSeletivo` têm precedência sobre as cadastradas; quando `adRem` é informada, a unidade de medida do grupo `impostoSeletivo` passa a ser obrigatória (erro de validação)
- Quando há alíquota de IBS (estadual ou municipal) informada, o grupo de tributação regular não é aplicado ao item

### Obsoleto

- Campo `pISEspec` na resposta do Imposto Seletivo (`ImpostoSeletivoDomain`): substituído pelo campo `adRemIS`; mantido temporariamente para compatibilidade com clientes existentes, a ser removido na próxima versão

## [1.4.0] - 2026-08-27

### Adicionado

- Endpoint de cálculo de base unificada NFS-e (`POST calculadora/base-calculo/base-calculo-unificada-nfse`): calcula as bases de cálculo IBS/CBS (regime geral) e Simples Nacional para operações de NFS-e, com suporte a documentos de ajuste (subcontratação, consórcio, intermediação, cessão de mão de obra, ressarcimento, profissional parceiro), locação de imóveis (subitem 99.03 com copropriedade proporcional), regras de vigência (ISSQN até 2032, PIS/COFINS até 2026), validações de negócio (EXXX-REGAP, EXXX-LOC-SN, E1534, LEIAUTE-XTP, E0436, BASE-NEGATIVA) e retorno unificado de avisos e erros na mesma resposta HTTP 200
- Endpoint de cálculo de base unificada (`POST calculadora/base-calculo/base-calculo-unificada`): calcula as três bases de cálculo (RG/IBS-CBS, IS, SN) para um único item, com avisos informativos para campos fora de contexto (vigência, natureza ou modelo de documento)
- Endpoint de dados abertos para consulta de atores agrupados por grupo, com filtro opcional por papel (`GET calculadora/dados-abertos/ator/grupos`)
- Endpoint de dados abertos para consulta de classificações tributárias CBS/IBS filtradas por atores da operação (fornecedor/adquirente) e por tipo de DF-e (`GET calculadora/dados-abertos/classificacoes-tributarias/cbs-ibs/por-atores`)
- Validação de tipo de documento fiscal (`tpDoc`) na operação: quando informado, valida existência, vigência, vínculo com a classificação tributária e exigência de NCM/NBS conforme nomenclatura do documento
- Validação de base de cálculo derivada: rejeita operação com base de cálculo não informada, inconsistente com o valor da operação, ou menor que a base de cálculo do Imposto Seletivo
- Serviço de validação de nomenclatura (`ValidacaoNomenclaturaService`): valida obrigatoriedade e permissão de NCM/NBS por item conforme tipo de DF-e
- Campo `tpDoc` no input de operação para indicar o tipo do documento fiscal eletrônico
- Campos `obrigatorio`, `ncmOpcional` e `nbsOpcional` no endpoint de nomenclatura aplicável por DF-e/cClassTrib
- Scripts de migração: `V0044` (grupos de DF-e), `V0045` (criação de tabelas de atores), `V0046` (carga de atores) e `V0047` (inclusão de `CLTR_TPRBSN` — Tipo de Receita Bruta do Simples Nacional — na tabela de classificações tributárias)
- Endpoints de NCM e NBS em dados abertos passam a retornar header `x-warning-dados-simulados` quando `tributadoPeloImpostoSeletivo` for true

### Modificado

- Desabilita documentos fiscais sem tratamento nos endpoints de cálculo — atualmente DERE (tipo 94), lançando `ErroFaltaImplementacaoException` quando informado
- Situações Tributárias (CST): unificação dos endpoints de CBS/IBS — `GET calculadora/dados-abertos/situacoes-tributarias/cbs-ibs/{siglaDfe}` passa a ser `GET calculadora/dados-abertos/situacoes-tributarias/cbs-ibs` com `siglaDfe` como query param opcional; quando omitido, retorna todas as situações (com classificações aninhadas e campo `id`) sem filtro de DF-e, substituindo o antigo retorno "flat" desse path
- Situações Tributárias (CST) do IS: `GET calculadora/dados-abertos/situacoes-tributarias/is/{siglaDfe}` passa a ser `GET calculadora/dados-abertos/situacoes-tributarias/is` com `siglaDfe` como query param opcional (a consulta permanece sem filtro de DF-e; com `siglaDfe` informado, mantém retorno vazio quando o IS não é aplicável ao tipo de DF-e)
- Ajustes no tratamento de erros na observabilidade
- Processamento de itens passa a receber o tipo de DF-e resolvido para aplicar validações de vínculo e nomenclatura
- Tipo de operação governamental opcional

### Corrigido

- Endpoints de consulta de NCM e NBS em dados abertos (`GET calculadora/dados-abertos/ncm` e `GET calculadora/dados-abertos/nbs`) passam a retornar 404 com problem details quando o código não é encontrado para a data informada, em vez de erro 500 (`NullPointerException`)

## [1.3.1] - 2026-08-31

### Modificado

- Script de migração `V0043`: ajusta as vigências nas tabelas `CLASSIF_NBS_INDOP_LC` e `INDICADOR_OPERACAO_IBS_CBS`; registros com início de vigência em 01/09/2026 passam a vigorar em 01/10/2026, e registros com fim de vigência em 31/08/2026 passam a vigorar até 30/09/2026

## [1.3.0] - 2026-08-13

### Modificado

- Inclusão de novos tipos de ente governamental
- Inclusão de novos tipos de operacao com ente governamental

### Corrigido

- Passando a tratar ente governamental `Distrito Federal` da mesma forma que `Estado`, para fins de compras governamentais
- Adicionado local de incidência às saídas dos endpoints `/calculadora/nfse/local-operacao` e `/calculadora/nfse/indicador-operacao` 
- Input e formatação do cTribNac passam a aceitar códigos de 4 e 6 digitos

### Adicionado

- Endpoints de validação de NBS e NCM aplicáveis (`GET /classificacoes-tributarias/nbs-aplicavel` e `GET /classificacoes-tributarias/ncm-aplicavel`)
- Enumeration `LocalIncidencia` para representar locais de incidência de tributos
- Scripts de migração para atualização das planilhas de dados dos Anexos VII e VIII da NFS-e e LC 116 (migrações `V0040`, `V0041` e `V0042`)
- Scripts `run-offline.bat` e `run-offline.ps1` para execução da aplicação no Windows

## [1.2.4] - 2026-07-09

### Adicionado

- Script de migração: `V0039` (inserir e extinguir NCMs).

## [1.2.3] - 2026-06-30

### Adicionado

- Scripts de migração: `V0035` (inclusão de novos cClassTrib), `V0036` (atualizações da tabela cClassTrib em 23/06/2026), `V0037` (inclusão de alíquotas fixas e ajustes na exceção de NCM aplicável) e `V0038` (cClassTribs 200006, 200009 e 200012 não devem mais ter ncm/nbs aplicável).

### Modificado

- `possuiPercentualReducao`: origem alterada de `TRATAMENTO_TRIBUTARIO.TRTR_IN_POSSUI_PERCENTUAL_REDUCAO` para `SITUACAO_TRIBUTARIA.SITR_IND_GRED` nos endpoints de dados abertos de classificações tributárias
- Percentuais de redução (`percentualReducaoCbs`, `percentualReducaoIbsUf`, `percentualReducaoIbsMun`) retornam `null` apenas quando não há redução na tabela de percentual de redução.

### Corrigido

- Validação de input para base de cálculo, quantidade e imposto informado para o endpoint de cálculo

## [1.2.2] - 2025-06-03

### Adicionado

- Comentários a nível de classe descrevendo todas as classes do projeto
- Decisões arquiteturais em /docs/adr
- Modelos ER do banco de dados em /docs/modelos-er

### Corrigido

- Corrige comparação de versão da aplicação no modo offline, removendo sufixo de hash de commit antes de comparar (ex.: `1.2.0-65bd4bcd` → `1.2.0`)
- Validação de número de trecho do pedágio — rejeita valores menores que 1 e números duplicados entre trechos
- Validação de município de origem e de cada trecho do pedágio — rejeita município não cadastrado ou com UF divergente
- Correção duplicata na CLASSIF_NBS_INDOP_LC para NBS 1.2504.11.00 — linha CNIL_ID=1731 restaurada para cClassTrib 000001

## [1.2.1] - 2026-05-25

### Corrigido

- problema em OffSetDateTime com offset zonado
- problema no registro das novas metricas de cache no Prometheus

## [1.2.0] - 2026-05-19

### Adicionado

- Compra Governamental: cálculo e alocação de tributos com tratamento diferenciado por ente federativo, modelo de transferência CBS, percentuais redutores para 2026 e suporte a CST de suspensão
- Compra Governamental: geração da tag de compras governamentais no XML de documentos fiscais (BPe, BPe-TM, CTe, CTe Simplificado, NFe, NF3e, NFSe) com atualização dos schemas XSD (v1.14a / v1.36 / v1.01.03 - NT004) e novas classes para modelo XML
- NFSe: endpoint para cálculo de base de cálculo, validação de NBS com fallback e testes de integração
- Indicador de Operação (cIndOp): consulta (`GET calculadora/nfse/indicador-operacao`) e validação (`POST calculadora/nfse/indicador-operacao/validate`) do indicador de operação aplicável à NFS-e a partir de NBS e data de referência
- Classificação Tributária: novos endpoints de listagem por tributo e CST (`GET calculadora/dados-abertos/classificacoes-tributarias/cbs-ibs/{cst}`, `.../imposto-seletivo/{cst}`) com data de atualização, serviço para listar cClassTrib aplicável a NBS (`GET calculadora/dados-abertos/classificacoes-tributarias/nbs`)
- NBS: serviço para listar NBS por data de vigência (`GET calculadora/dados-abertos/nbs/lista`) e NBS aplicável a cClassTrib (`GET calculadora/dados-abertos/nbs-aplicaveis`)
- Local da Operação: serviço para determinar o local da operação para fins de recolhimento do IBS (`GET calculadora/nfse/local-operacao`), com base no indicador de operação e data do fato gerador
- Dados Abertos: novos endpoints em `calculadora/dados-abertos/` para redutores de compra governamental (`/redutores-compra-governamental`), transferência CBS (`/transferencias-cbs`) e transferência IBS (`/transferencias-ibs`)
- Observabilidade: novo endpoint (`POST calculadora/observabilidade/regime-geral`) que processa todos os itens da requisição sem interromper no primeiro erro, retornando alertas e códigos de erro de forma consolidada; endpoint auxiliar para consulta descritiva dos códigos de erro (`GET calculadora/observabilidade/erros`) — funcionalidade experimental, sujeita a ajustes
- Crítica de alíquota contra unidade de medida
- Carga da tabela cClassTrib com vigência em 15/04/2026
- Inclusão do município de Boa Esperança do Norte/MT na tabela de municípios

### Modificado

- Arredondamento: revisão da lógica — cálculos intermediários mantêm maior precisão, arredondamento apenas no valor final (item ou nota); nova classe `ArredondamentoUtils` substitui `CalculadoraUtils`
- CSTs 4xx e 8xx: uso dos indicadores `gIBSCBS` da tabela de cClassTrib para determinar geração do grupo de IBS e CBS (ex.: imunidades CST 410 não geram `gIBSCBS`)
- Schemas XML: atualização de schemas para documentos fiscais (BPe/BPe-TM/CTe/CTe Simplificado/NF3e v1.14a, NFe/NFCe v1.36, NFSe v1.01.03 - NT004) com suporte a compras governamentais
- Calculadora: extração de `ProcessamentoItemService`, refatoração de `CalculoCbsIbsService` e `CalculoTributoService`
- Serializers BigDecimal: ajustes na formatação (`TDec0302_04`, `TDec1104OpRTC`, `TDec1302`)
- cClassTrib: consultas ordenadas pelo código em endpoints (`GET .../classificacoes-tributarias/cbs-ibs/{cst}`, `.../imposto-seletivo/{cst}`, `.../cbs-ibs`, `.../imposto-seletivo`) e fundamentações legais ordenadas
- cIndOp: alteração de PK, FK e CK na tabela associativa
- Exception handler: reorganização de enums/records, combinação de `CodigoErroCalculo` com `ProblemType`
- `LocalFornecimento`: atualização do enum com converter dedicado
- Memória de cálculo: ajustes em casas decimais e locale

### Obsoleto

- `GET calculadora/dados-abertos/classificacoes-tributarias/{idSituacaoTributaria}`: endpoint de listagem de cClassTrib por ID da situação tributária — substituído por `GET .../classificacoes-tributarias/cbs-ibs/{cst}` e `GET .../classificacoes-tributarias/imposto-seletivo/{cst}`, que permitem a listagem de cClassTrib por tributo e CST

### Removido

- Validação de item duplicado (`ItemDuplicadoException`): itens com mesmo número na mesma operação não são mais rejeitados, pois não representam erro tributário
- Classes obsoletas: `CalculadoraUtils`, `JsonResourceObjectMapper`, `CalculadoraServiceTest`

### Corrigido

- Retorno incorreto de alíquota de pedágio
- Alíquota padrão não retornada corretamente em endpoints de dados abertos
- Valores negativos retornados no service de cálculo de base de cálculo da NFSe
