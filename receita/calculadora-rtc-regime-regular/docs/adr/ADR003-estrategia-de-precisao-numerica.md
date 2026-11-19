# ADR003 - Estratégia de Precisão Numérica

## Data

> 26/05/2026

## Status

> Aceito

## Contexto

Cálculos de CBS, IBS e Imposto Seletivo envolvem múltiplas operações matemáticas em cascata
(base de cálculo → aplicar alíquota → aplicar reduções → resultado). Cada operação pode
introduzir erro de arredondamento se realizada prematuramente ou de forma inconsistente.

Em contextos fiscais, diferenças de centavos impactam conformidade regulatória, auditoria
fiscal e recolhimento de tributos. Aplicar `setScale` e `RoundingMode` localmente em cada
serviço de cálculo gera política divergente e dificulta auditoria.

## Decisão

Usar `BigDecimal` com política centralizada de precisão interna e formatação em
`ArredondamentoUtils`.

Precisão interna fixada em 8 casas com `RoundingMode.HALF_EVEN`. Os serviços de cálculo usam
os helpers de `ArredondamentoUtils` (`multiplicar`, `dividir`, `subtrair`, `arredondarInterno`)
e passam a precisão interna ao avaliador de expressões.

Os serializers de `BigDecimal` delegam a formatação para `ArredondamentoUtils`,
com helpers distintos para quantidade, moeda e alíquota.

## Consequências

Positivas:
- Política decimal consistente para todos os cálculos fiscais
- Cálculos reproduzíveis e auditáveis por helpers centralizados
- Reduz divergências por arredondamento aplicado em locais diferentes

Negativas:
- Exige disciplina para evitar `setScale` e `RoundingMode` locais fora da política centralizada
- `BigDecimal` e helpers tornam o código mais explícito e verboso
- Mudanças na política central afetam todos os cálculos consumidores
