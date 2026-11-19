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

export async function queryCompany(cnpj, fetcher = fetch) {
  if (!validCnpj(cnpj)) throw new Error('CNPJ inválido. Confira os dígitos.');
  const controller = new AbortController();
  const timeout = setTimeout(() => controller.abort(), 15000);
  try {
    const response = await fetcher(`https://brasilapi.com.br/api/cnpj/v1/${cnpj}`, { signal: controller.signal, headers: { Accept: 'application/json' }, cache: 'no-store' });
    if (response.status === 404) throw new Error('CNPJ não encontrado na base consultada.');
    if (response.status === 429) throw new Error('Limite de consultas atingido. Aguarde e tente novamente.');
    if (!response.ok) throw new Error('Serviço cadastral indisponível. Tente novamente.');
    return normalizeCompany(await response.json(), cnpj);
  } catch (error) {
    if (error?.name === 'AbortError') throw new Error('Tempo de consulta esgotado. Tente novamente.');
    if (error instanceof TypeError) throw new Error('Não foi possível acessar a BrasilAPI. Confira a conexão e tente novamente.');
    throw error;
  } finally { clearTimeout(timeout); }
}
