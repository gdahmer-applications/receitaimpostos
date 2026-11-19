import test from 'node:test';
import assert from 'node:assert/strict';
import { validCnpj, normalizeCnpj, parseCnpjs, simpleLabel, latestTaxHistory, normalizeCompany, classificationView, queryCompany, registrationDraft, registrationIssues, registrationCsv, parseCteDocument, parseCteText, addCteEvidence } from '../site/core.js';

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

test('CNPJ e regime histórico não preenchem UF operacional ou tipo fiscal automaticamente', () => {
  const company = normalizeCompany({cnpj:'19131243000197',razao_social:'EXEMPLO',uf:'SP',regime_tributario:[{ano:2024,forma_de_tributacao:'LUCRO REAL'}]},'19131243000197');
  const draft = registrationDraft(company);
  assert.equal(draft.role,''); assert.equal(draft.originUf,''); assert.equal(draft.regime,'pending');
  assert.deepEqual(registrationIssues(draft),['Identificar a empresa']);
  draft.role='carrier';
  assert.ok(registrationIssues(draft).includes('UF de início'));
  Object.assign(draft,{name:'TRANSPORTADORA',regime:'real',originUf:'BA',priceMode:'included',icmsMode:'standard'});
  assert.deepEqual(registrationIssues(draft),[]);
});

test('Simples no DAS exige percentuais e frete com tributos incluídos', () => {
  const draft=registrationDraft({cnpj:'19131243000197'});
  Object.assign(draft,{role:'carrier',name:'EXEMPLO',regime:'simples_das',originUf:'SP',priceMode:'base',icmsMode:'standard'});
  assert.deepEqual(registrationIssues(draft),['CBS no DAS','IBS no DAS','Simples no DAS requer frete com impostos incluídos']);
  Object.assign(draft,{cbsDas:'1.25',ibsDas:'0',priceMode:'included'});
  assert.deepEqual(registrationIssues(draft),[]);
  draft.cbsDas='100'; assert.ok(registrationIssues(draft).includes('CBS no DAS'));
});

test('CSV é uma conferência e neutraliza fórmulas nos textos', () => {
  const draft=registrationDraft({cnpj:'19131243000197',name:'=EXEMPLO',city:'SAO PAULO',uf:'SP',status:'ATIVA',simples:'Não informado',history:null,source:'Minha Receita'});
  Object.assign(draft,{role:'carrier',name:'@TRANSPORTADORA',regime:'real',originUf:'SP',priceMode:'included',icmsMode:'standard'});
  const csv=registrationCsv([draft],'2027');
  assert.ok(csv.startsWith('\uFEFF"Ano do cadastro";'));
  assert.ok(csv.includes('"\'=EXEMPLO"')); assert.ok(csv.includes('"\'@TRANSPORTADORA"'));
  assert.ok(csv.includes('"19.131.243/0001-97"')); assert.ok(csv.includes('Nenhuma pendência dos campos básicos'));
  assert.ok(csv.includes('"11,2";"Provisória";"0,05";"0,05"'));
  const nextYear=registrationCsv([draft],'2028');
  assert.ok(nextYear.includes('"";"Pendente";"0,05";"0,05"'));
});

function xmlNode(name, value = '', children = [], attrs = {}) {
  return {
    name, textContent: value || children.map(child => child.textContent).join(''), children,
    getAttribute: key => attrs[key] || '',
    getElementsByTagNameNS: (_ns, target) => {
      const matches=[];
      const visit = node => { for (const child of node.children || []) { if (child.name === target) matches.push(child); visit(child); } };
      visit({children}); return matches;
    },
  };
}

test('extrai dados do CT-e e não utiliza documentos complementares', () => {
  const node=(name,value)=>xmlNode(name,value);
  const ide=type=>xmlNode('ide','',[node('mod','57'),node('tpCTe',type),node('nCT','123'),node('serie','1'),node('dhEmi','2026-11-01T12:00:00-03:00'),node('xMunIni','Campinas'),node('UFIni','SP'),node('xMunFim','Salvador'),node('UFFim','BA')]);
  const emit=xmlNode('emit','',[node('CNPJ','12ABC34501DE35'),node('xNome','EMPRESA DE TESTE')]);
  const imp=xmlNode('imp','',[xmlNode('ICMS','',[node('CST','00'),node('pICMS','7.00'),node('vICMS','7.00')]),xmlNode('IBSCBS','',[node('CST','000'),node('cClassTrib','000001')])]);
  const makeDoc=type=>xmlNode('document','',[xmlNode('infCte','',[ide(type),emit,xmlNode('vPrest','',[node('vTPrest','100.00')]),imp],{Id:'CTeTESTE'})]);
  const doc=parseCteDocument(makeDoc('0'),'exemplo.xml');
  assert.equal(doc.cnpj,'12ABC34501DE35'); assert.equal(doc.startUf,'SP'); assert.equal(doc.endUf,'BA');
  assert.equal(doc.icmsRate,'7.00'); assert.equal(doc.cClassTrib,'000001'); assert.equal(doc.date,'2026-11-01');
  assert.throws(()=>parseCteDocument(makeDoc('1')),/conferência manual/);
  assert.throws(()=>parseCteText('<!DOCTYPE foo><CTe/>'),/não permitida/);
});

test('origem por CT-e é sugerida, conflitos preservam a escolha manual e duplicatas são ignoradas', () => {
  const draft=registrationDraft({cnpj:'12ABC34501DE35'});
  const doc={cnpj:'12ABC34501DE35',id:'primeiro',startUf:'SP',startCity:'Campinas',cClassTrib:'000001'};
  assert.equal(addCteEvidence(draft,doc),true); assert.equal(draft.role,'carrier'); assert.equal(draft.originUf,'SP');
  assert.equal(draft.cClassTrib,'000001'); assert.equal(addCteEvidence(draft,doc),false);
  addCteEvidence(draft,{...doc,id:'segundo',startUf:'PE',startCity:'Olinda'});
  assert.equal(draft.originUf,''); assert.equal(draft.originCity,'');
  draft.originUf='BA'; draft.originSource='manual';
  addCteEvidence(draft,{...doc,id:'terceiro',startUf:'BA',startCity:'Salvador'});
  assert.equal(draft.originUf,'BA');
  assert.throws(()=>addCteEvidence(draft,{...doc,id:'outro',cnpj:'19131243000197'}),/outro CNPJ/);
});
