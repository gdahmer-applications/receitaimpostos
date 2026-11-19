# ADR003 - Estratégia de Precisão Numérica

## Data

> 24/07/2026

## Status

> Aceito

## Contexto

Os cálculos do Simples Nacional envolvem alíquotas, bases e valores monetários. A classe
[ArredondamentoUtils](../../src/main/java/br/gov/serpro/rtc/shared/util/ArredondamentoUtils.java)
centraliza as regras de precisão e arredondamento.

## Decisão

- Precisão interna de cálculo: `PRECISAO_INTERNA = 8` casas decimais.
- Modo de arredondamento: `RoundingMode.HALF_EVEN` (constante `MODO`), descrito na Javadoc como arredondamento bancário.
- Operações com precisão interna (8 casas, `HALF_EVEN`): arredondamento de valor, divisão por 100,
  divisão, multiplicação e subtração.
- Formatação para apresentação, também com `HALF_EVEN`:
  - `formatarMoeda`: exatamente 2 casas decimais;
  - `formatarAliquota`: 2 a 4 casas decimais, removendo zeros à direita além do mínimo;
  - quantidades: inteiro quando possível, senão 4 casas decimais.
- Serialização de valores decimais com os serializers
  [BigDecimalTDec1302Serializer](../../src/main/java/br/gov/serpro/rtc/shared/serializer/BigDecimalTDec1302Serializer.java) e
  [BigDecimalTDec0302_04Serializer](../../src/main/java/br/gov/serpro/rtc/shared/serializer/BigDecimalTDec0302_04Serializer.java).

## Consequências

Positivas:
- Regras de arredondamento em um único ponto, facilitando auditoria
- Resultados determinísticos para os mesmos insumos

Negativas:
- Qualquer alteração na precisão ou no modo de arredondamento altera os resultados e exige nova validação

## Notas

- Introdução de `PRECISAO_INTERNA`: 24/07/2026.
