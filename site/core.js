export const MAX_CNPJS = 50;

export function normalizeCnpj(value) {
  const text = String(value ?? '').trim().toUpperCase();
  if (!/^[A-Z0-9.\/-]+$/.test(text)) return '';
  return text.replace(/[.\/-]/g, '');
}

function digit(base) {
  const weights = base.length === 12
    ? [5,4,3,2,9,8,7,6,5,4,3,2]
    : [6,5,4,3,2,9,8,7,6,5,4,3,2];
  const remainder = [...base].reduce((sum, char, i) => sum + (char.charCodeAt(0) - 48) * weights[i], 0) % 11;
  return String(remainder < 2 ? 0 : 11 - remainder);
}

export function validCnpj(value) {
  const n = normalizeCnpj(value);
  if (!/^[A-Z0-9]{12}[0-9]{2}$/.test(n) || /^(.)\1{13}$/.test(n)) return false;
  return digit(n.slice(0, 12)) === n[12] && digit(n.slice(0, 13)) === n[13];
}

export function formatCnpj(value) {
  const n = normalizeCnpj(value);
  return n.length === 14 ? `${n.slice(0,2)}.${n.slice(2,5)}.${n.slice(5,8)}/${n.slice(8,12)}-${n.slice(12)}` : String(value);
}

export function parseCnpjs(value) {
  const raw = String(value ?? '').split(/[\s,;]+/).map(v => v.trim()).filter(Boolean);
  const unique = [];
  const seen = new Set();
  for (const input of raw) {
    const normalized = normalizeCnpj(input);
    const key = normalized || input;
    if (!seen.has(key)) { seen.add(key); unique.push({ input, normalized, valid: validCnpj(input) }); }
  }
  return unique;
}

export function simpleLabel(value) {
  if (value === true) return 'Optante';
  if (value === false) return 'Não optante';
  return 'Não informado';
}

export function latestTaxHistory(value) {
  if (!Array.isArray(value)) return null;
  const usable = value.filter(x => x && Number.isInteger(Number(x.ano)) && x.ano != null && typeof x.forma_de_tributacao === 'string' && x.forma_de_tributacao.trim());
  if (!usable.length) return null;
  const year = Math.max(...usable.map(x => Number(x.ano)));
  const entries = [...new Set(usable.filter(x => Number(x.ano) === year).map(x => x.forma_de_tributacao.trim()))];
  return { year, description: entries.join(' / ') };
}

export function normalizeCompany(data, expected) {
  if (!data || typeof data !== 'object' || normalizeCnpj(data.cnpj) !== expected) throw new Error('A resposta não corresponde ao CNPJ consultado.');
  return {
    cnpj: expected,
    name: String(data.razao_social || data.nome_fantasia || 'Nome não informado'),
    city: String(data.municipio || ''), uf: String(data.uf || ''),
    status: String(data.descricao_situacao_cadastral || 'Não informada'),
    simples: simpleLabel(data.opcao_pelo_simples),
    history: latestTaxHistory(data.regime_tributario),
  };
}

export function flag(value) {
  return value === true ? 'Sim' : value === false ? 'Não' : 'Não informado';
}

export function cteApplicability(types) {
  if (!Array.isArray(types) || !types.length) return 'Não informado';
  return types.some(x => String(x?.tipo) === '57' || String(x?.sigla || '').toUpperCase() === 'CTE') ? 'Consta para CT-e' : 'CT-e não consta na lista';
}

export function classificationView(payload, code) {
  if (!payload || !Array.isArray(payload.classificacoes)) throw new Error('A base da Receita não está disponível.');
  const item = payload.classificacoes.find(x => String(x?.codigo) === code);
  if (!item) return null;
  return {
    code: String(item.codigo), description: String(item.descricao || 'Descrição não informada'),
    treatment: String(item.descricaoTratamentoTributario || 'Não informado'),
    cbs: flag(item.indicaApropriacaoCreditoAdquirenteCbs),
    ibs: flag(item.indicaApropriacaoCreditoAdquirenteIbs),
    presumido: flag(item.indicaCreditoPresumidoAdquirente),
    cte: cteApplicability(item.tiposDfeClassificacao),
    date: String(payload.data_referencia || ''), generated: String(payload.gerado_em || ''),
    sourceCommit: String(payload.commit_receita || ''),
  };
}

export const UFS = ['AC','AL','AP','AM','BA','CE','DF','ES','GO','MA','MT','MS','MG','PA','PB','PR','PE','PI','RJ','RN','RS','RO','RR','SC','SP','SE','TO'];
export const REGIMES = {
  pending: 'Pendente', real: 'Lucro Real · regular', presumido: 'Lucro Presumido · regular',
  simples_regular: 'Simples · IBS/CBS regular', simples_das: 'Simples · IBS/CBS no DAS',
};
export const PRICE_MODES = { pending: 'Pendente', included: 'Impostos incluídos', base: 'Antes dos impostos' };
export const ICMS_MODES = { pending: 'Pendente', standard: 'ICMS destacado · regra geral' };

export function registrationDraft(company) {
  return {
    company, role: '', name: '', regime: 'pending', originUf: '', originCity: '',
    priceMode: 'pending', icmsMode: 'pending', cbsDas: '', ibsDas: '', cClassTrib: '', note: '',
    documents: [], originSource: '', citySource: '', classSource: '',
  };
}

function firstTag(node, name) {
  return node?.getElementsByTagNameNS?.('*', name)?.[0] || null;
}
function tagText(node, name) {
  return String(firstTag(node, name)?.textContent || '').trim();
}

export function parseCteDocument(doc, filename = '') {
  if (!doc || firstTag(doc, 'parsererror')) throw new Error('XML inválido ou incompleto.');
  const info = firstTag(doc, 'infCte'); const ide = firstTag(info, 'ide'); const emit = firstTag(info, 'emit');
  if (!info || !ide || !emit || tagText(ide, 'mod') !== '57') throw new Error('O arquivo não é um CT-e modelo 57.');
  const cnpj = normalizeCnpj(tagText(emit, 'CNPJ'));
  if (!validCnpj(cnpj)) throw new Error('CNPJ do emissor ausente ou inválido no CT-e.');
  const type = tagText(ide, 'tpCTe');
  if (type !== '0') throw new Error('CT-e complementar, anulação ou substituição exige conferência manual.');
  const tax = firstTag(info, 'imp'); const icms = firstTag(tax, 'ICMS'); const rtc = firstTag(tax, 'IBSCBS'); const price = firstTag(info, 'vPrest');
  const id = String(info.getAttribute?.('Id') || '').replace(/^CTe/i, '');
  return {
    cnpj, name: tagText(emit, 'xNome'), tradeName: tagText(emit, 'xFant'), filename,
    date: tagText(ide, 'dhEmi').slice(0, 10), number: tagText(ide, 'nCT'), series: tagText(ide, 'serie'),
    startUf: tagText(ide, 'UFIni').toUpperCase(), startCity: tagText(ide, 'xMunIni'),
    endUf: tagText(ide, 'UFFim').toUpperCase(), endCity: tagText(ide, 'xMunFim'),
    total: tagText(price, 'vTPrest'), icmsCst: tagText(icms, 'CST'),
    icmsRate: tagText(icms, 'pICMS'), icmsValue: tagText(icms, 'vICMS'),
    cClassTrib: tagText(rtc, 'cClassTrib'), cstRtc: tagText(rtc, 'CST'),
    cbs: tagText(rtc, 'vCBS'), ibs: tagText(rtc, 'vIBS'),
    id: id || `${cnpj}|${tagText(ide, 'serie')}|${tagText(ide, 'nCT')}|${tagText(ide, 'dhEmi')}`,
  };
}

export function parseCteText(xml, filename = '') {
  if (typeof xml !== 'string' || !xml.trim()) throw new Error('Arquivo XML vazio.');
  if (xml.length > 5_000_000) throw new Error('O XML excede o limite de 5 MB.');
  if (/<!DOCTYPE|<!ENTITY/i.test(xml)) throw new Error('O XML contém uma declaração não permitida.');
  return parseCteDocument(new DOMParser().parseFromString(xml, 'application/xml'), filename);
}

function distinct(values) {
  return [...new Set(values.map(v => String(v || '').trim()).filter(Boolean))];
}

export function addCteEvidence(record, doc) {
  if (record.company.cnpj !== doc.cnpj) throw new Error('O CT-e pertence a outro CNPJ emissor.');
  if (record.documents.some(item => item.id === doc.id)) return false;
  record.documents.push(doc);
  if (!record.role) record.role = 'carrier';
  const ufs = distinct(record.documents.map(item => item.startUf).filter(uf => UFS.includes(uf)));
  const cities = distinct(record.documents.map(item => item.startCity));
  const allUfsKnown = record.documents.every(item => UFS.includes(item.startUf));
  const allCitiesKnown = record.documents.every(item => Boolean(String(item.startCity || '').trim()));
  if (record.originSource !== 'manual') { record.originUf = allUfsKnown && ufs.length === 1 ? ufs[0] : ''; record.originSource = 'cte'; }
  if (record.citySource !== 'manual') { record.originCity = allUfsKnown && allCitiesKnown && ufs.length === 1 && cities.length === 1 ? cities[0] : ''; record.citySource = 'cte'; }
  const codes = distinct(record.documents.map(item => item.cClassTrib).filter(code => /^\d{6}$/.test(code)));
  if (record.classSource !== 'manual') { record.cClassTrib = codes.length === 1 ? codes[0] : ''; record.classSource = 'cte'; }
  return true;
}

function validPercent(value) {
  const text = String(value ?? '').trim();
  if (!/^\d+(?:[.,]\d+)?$/.test(text)) return false;
  const n = Number(text.replace(',', '.'));
  return Number.isFinite(n) && n >= 0 && n < 100;
}

export function registrationIssues(record) {
  const issues = [];
  if (!['carrier', 'madesa'].includes(record.role)) return ['Identificar a empresa'];
  if (record.role === 'madesa') return [];
  if (!String(record.name || '').trim()) issues.push('Nome no FreteLAB');
  if (!Object.hasOwn(REGIMES, record.regime) || record.regime === 'pending') issues.push('Tipo fiscal');
  if (!UFS.includes(record.originUf)) issues.push('UF de início');
  if (!['included', 'base'].includes(record.priceMode)) issues.push('Tratamento do frete');
  if (record.icmsMode !== 'standard') issues.push('Tratamento do ICMS');
  if (record.regime === 'simples_das') {
    if (!validPercent(record.cbsDas)) issues.push('CBS no DAS');
    if (!validPercent(record.ibsDas)) issues.push('IBS no DAS');
    if (record.priceMode === 'base') issues.push('Simples no DAS requer frete com impostos incluídos');
  }
  if (record.cClassTrib && !/^\d{6}$/.test(record.cClassTrib)) issues.push('cClassTrib deve ter 6 dígitos');
  return issues;
}

function csvCell(value) {
  let text = String(value ?? '').replace(/[\r\n]+/g, ' ');
  if (/^\s*[=+@\-]/.test(text)) text = `'${text}`;
  return `"${text.replace(/"/g, '""')}"`;
}

export function registrationCsv(records, year) {
  const headers = ['Ano do cadastro','Papel','Nome no FreteLAB','Razão social','CNPJ','Município cadastral','UF cadastral',
    'Situação cadastral','Simples informado','Histórico IRPJ','Fonte cadastro','Tipo fiscal confirmado','UF início transporte',
    'Município início transporte','Tratamento do frete','Tratamento do ICMS','CBS no DAS (%)','IBS no DAS (%)',
    'cClassTrib - referência','Observações','Pendências','CT-es lidos','Origens observadas nos CT-es','Destinos observados nos CT-es',
    'Datas dos CT-es','Valores de frete nos CT-es (R$)','CST ICMS nos CT-es','Alíquotas de ICMS observadas nos CT-es','Valores ICMS nos CT-es (R$)',
    'Fonte da UF de início','Padrão Madesa ICMS v1.3.38','Padrão Madesa CBS v1.3.38',
    'Padrão Madesa IBS v1.3.38','CBS padrão do ano (%)','Status CBS do ano','IBS UF padrão do ano (%)','IBS município padrão do ano (%)'];
  const annual = String(year) === '2027' ? ['11,2','Provisória','0,05','0,05'] : ['','Pendente','0,05','0,05'];
  const rows = records.map(record => {
    const c = record.company;
    const isCarrier = record.role === 'carrier';
    const pending = registrationIssues(record);
    const documents = Array.isArray(record.documents) ? record.documents : [];
    const history = c.history ? `${c.history.description} (${c.history.year})` : 'Não informado';
    return [year, record.role === 'carrier' ? 'Transportadora' : record.role === 'madesa' ? 'Madesa' : 'Pendente',
      isCarrier ? record.name : '', c.name, formatCnpj(c.cnpj), c.city, c.uf, c.status, c.simples, history, c.source || 'Indisponível',
      isCarrier ? (REGIMES[record.regime] || 'Pendente') : '', isCarrier ? record.originUf : '', isCarrier ? record.originCity : '',
      isCarrier ? (PRICE_MODES[record.priceMode] || 'Pendente') : '', isCarrier ? (ICMS_MODES[record.icmsMode] || 'Pendente') : '',
      isCarrier && record.regime === 'simples_das' ? record.cbsDas : '', isCarrier && record.regime === 'simples_das' ? record.ibsDas : '',
      isCarrier ? record.cClassTrib : '', record.note,
      record.role === 'madesa' ? 'Conferir regras gerais da Madesa no FreteLAB' : pending.length ? pending.join(' | ') : 'Nenhuma pendência dos campos básicos',
      documents.length, distinct(documents.map(d => [d.startCity, d.startUf].filter(Boolean).join('/'))).join(' | '),
      distinct(documents.map(d => [d.endCity, d.endUf].filter(Boolean).join('/'))).join(' | '),
      distinct(documents.map(d => d.date)).join(' | '), distinct(documents.map(d => d.total)).join(' | '), distinct(documents.map(d => d.icmsCst)).join(' | '),
      distinct(documents.map(d => d.icmsRate ? `${d.icmsRate}%` : '')).join(' | '), distinct(documents.map(d => d.icmsValue)).join(' | '),
      record.originSource === 'cte' ? 'Sugestão por CT-e; confirmar operação e ano' : record.originSource === 'manual' ? 'Informada manualmente' : 'Pendente',
      'Elegível 100% por premissa','Pendente','Pendente',...annual];
  });
  return '\uFEFF' + [headers, ...rows].map(row => row.map(csvCell).join(';')).join('\r\n') + '\r\n';
}

export async function queryCompany(cnpj, fetcher = fetch) {
  if (!validCnpj(cnpj)) throw new Error('CNPJ inválido. Confira os dígitos.');
  const sources = [
    { name: 'Minha Receita', url: `https://minhareceita.org/${cnpj}` },
    { name: 'BrasilAPI', url: `https://brasilapi.com.br/api/cnpj/v1/${cnpj}` },
  ];
  let notFound = false;
  for (const source of sources) {
    const controller = new AbortController();
    const timeout = setTimeout(() => controller.abort(), 12000);
    try {
      const response = await fetcher(source.url, { signal: controller.signal, headers: { Accept: 'application/json' } });
      if (response.status === 404) { notFound = true; continue; }
      if (!response.ok) continue;
      return { ...normalizeCompany(await response.json(), cnpj), source: source.name };
    } catch (error) {
      // Uma fonte pode estar fora do ar, bloquear o navegador ou devolver um registro incompatível.
      // A outra fonte é tentada sem transformar ausência de dados em informação fiscal.
    } finally { clearTimeout(timeout); }
  }
  if (notFound) throw new Error('CNPJ não encontrado em uma das bases; confirme o cadastro.');
  throw new Error('Fontes cadastrais indisponíveis. Confira a conexão e tente novamente.');
}
