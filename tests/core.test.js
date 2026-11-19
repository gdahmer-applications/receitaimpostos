import test from 'node:test';
import assert from 'node:assert/strict';
import { validCnpj, normalizeCnpj, parseCnpjs, simpleLabel, latestTaxHistory, normalizeCompany, classificationView, queryCompany } from '../site/core.js';

test('valida CNPJs numérico e alfanumérico da Receita', () => {
  assert.equal(validCnpj('19.131.243/0001-97'), true);
  assert.equal(validCnpj('12.abc.345/01de-35'), true);
  assert.equal(validCnpj('12.abc.345/01de-36'), false);
  assert.equal(validCnpj('00.000.000/0000-00'), false);
  assert.equal(validCnpj('19.131.243/0001-98'), false);
  assert.equal(normalizeCnpj('12.abc.345/01de-35'), '12ABC34501DE35');
});

test('aceita lista e remove duplicatas sem perder zeros', () => {
  const list = parseCnpjs('19.131.243/0001-97; 19131243000197\n12.abc.345/01de-35');
  assert.equal(list.length, 2);
  assert.equal(list[0].normalized, '19131243000197');
  assert.equal(list.every(x => x.valid), true);
});

test('ausência de Simples permanece desconhecida e IRPJ mantém ano', () => {
  assert.equal(simpleLabel(null), 'Não informado');
  assert.equal(simpleLabel(false), 'Não optante');
  assert.deepEqual(latestTaxHistory([{ano:2023,forma_de_tributacao:'LUCRO REAL'},{ano:2024,forma_de_tributacao:'LUCRO PRESUMIDO'}]), {year:2024,description:'LUCRO PRESUMIDO'});
  const company = normalizeCompany({cnpj:'19131243000197',razao_social:'EXEMPLO',opcao_pelo_simples:null},'19131243000197');
  assert.equal(company.simples,'Não informado');
  assert.equal(company.history,null);
  assert.throws(()=>normalizeCompany({cnpj:'00000000000191'},'19131243000197'),/não corresponde/);
});

test('classificação apresenta indicadores sem inventar elegibilidade', () => {
  const payload={data_referencia:'2027-01-01',commit_receita:'abc',classificacoes:[{codigo:'000001',descricao:'Exemplo',indicaApropriacaoCreditoAdquirenteCbs:true,indicaApropriacaoCreditoAdquirenteIbs:false,tiposDfeClassificacao:[{tipo:57,sigla:'CTe'}]}]};
  const result=classificationView(payload,'000001');
  assert.equal(result.cbs,'Sim'); assert.equal(result.ibs,'Não');
  assert.equal(result.presumido,'Não informado'); assert.equal(result.cte,'Consta para CT-e');
  assert.equal(classificationView(payload,'999999'),null);
});

test('consulta pública só usa dados da empresa solicitada e trata erro', async () => {
  let url='';
  const fetcher=async input=>{url=input;return {ok:true,status:200,json:async()=>({cnpj:'19131243000197',razao_social:'EMPRESA',qsa:[{nome_socio:'PRIVADO'}]})}};
  const result=await queryCompany('19131243000197',fetcher);
  assert.equal(url,'https://minhareceita.org/19131243000197');
  assert.equal(result.name,'EMPRESA'); assert.equal('qsa' in result,false);
  let attempts=0;
  const fallback=await queryCompany('19131243000197',async input=>{attempts++;return attempts===1?{ok:false,status:429}:{ok:true,status:200,json:async()=>({cnpj:'19131243000197',razao_social:'EMPRESA'})}});
  assert.equal(attempts,2); assert.equal(fallback.source,'BrasilAPI');
  await assert.rejects(queryCompany('19131243000197',async()=>({ok:false,status:429})),/indisponíveis/);
  await assert.rejects(queryCompany('19131243000198',fetcher),/inválido/);
});
