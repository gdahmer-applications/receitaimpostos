import { MAX_CNPJS, parseCnpjs, formatCnpj, queryCompany, classificationView, UFS, REGIMES, PRICE_MODES, ICMS_MODES, registrationDraft, registrationIssues, registrationCsv, parseCteText, addCteEvidence } from './core.js?v=5';

const $ = id => document.getElementById(id);
const element = (tag, text, cls) => { const e = document.createElement(tag); if (text != null) e.textContent = text; if (cls) e.className = cls; return e; };
let requestGeneration = 0;
const registrations = new Map();

function badge(text, cls = '') { return element('span', text, `badge ${cls}`); }
function cell(row, text) { const e = element('td', text); row.append(e); return e; }
function addRow(item, result, error) {
  const row = element('tr');
  const company = cell(row);
  company.append(element('span', result?.name || (item.valid ? 'Consulta indisponível' : 'CNPJ inválido'), 'company-name'));
  company.append(element('span', formatCnpj(item.input), 'subtext'));
  if (result?.source) company.append(element('span', `Fonte: ${result.source}`, 'subtext'));
  if (error) {
    const err = cell(row); err.colSpan = 4; err.append(badge(error, 'error'));
  } else {
    cell(row, [result.city, result.uf].filter(Boolean).join(' / ') || 'Não informada');
    const st = cell(row); st.append(badge(result.status, result.status.toUpperCase() === 'ATIVA' ? 'good' : 'warn'));
    const sn = cell(row); sn.append(badge(result.simples, result.simples === 'Não informado' ? 'warn' : ''));
    const hist = cell(row);
    if (result.history) { hist.append(element('span', result.history.description)); hist.append(element('span', `Último ano informado: ${result.history.year}`, 'subtext')); }
    else hist.append(element('span', 'Não informado', 'subtext'));
  }
  $('company-rows').append(row);
}

function rememberCompany(item, result) {
  if (!item.valid) return;
  const company = result || { cnpj: item.normalized, name: '', city: '', uf: '', status: 'Não informado', simples: 'Não informado', history: null, source: 'Indisponível' };
  const existing = registrations.get(item.normalized);
  if (!existing) registrations.set(item.normalized, registrationDraft(company));
  else if (result) existing.company = company;
  return registrations.get(item.normalized);
}

function refreshCompanyTable() {
  $('company-rows').replaceChildren();
  for (const record of registrations.values()) addRow({input:record.company.cnpj, valid:true}, record.company);
  const count = registrations.size; $('empty').hidden = count > 0; $('table-wrap').hidden = count === 0; $('results-note').hidden = count === 0;
  $('count').textContent = count ? `${count} ${count === 1 ? 'empresa em preparação' : 'empresas em preparação'}` : 'Nenhuma consulta';
}

function labelledControl(label, control, helper) {
  const wrapper = element('label', null, 'draft-field');
  wrapper.append(element('span', label), control);
  if (helper) wrapper.append(element('small', helper));
  return wrapper;
}

function selectControl(items, value, onChange) {
  const select = element('select');
  for (const [key, label] of items) { const option = element('option', label); option.value = key; select.append(option); }
  select.value = value;
  select.addEventListener('change', () => onChange(select.value));
  return select;
}

function textControl(value, placeholder, onInput, attributes = {}) {
  const input = element('input'); input.type = attributes.type || 'text'; input.value = value || ''; input.placeholder = placeholder;
  for (const [key, val] of Object.entries(attributes)) if (key !== 'type') input.setAttribute(key, val);
  input.addEventListener('input', () => onInput(input.value));
  return input;
}

function registrationCard(record, expanded) {
  const company = record.company; const card = element('details', null, 'registration-card'); card.open = expanded;
  card.dataset.cnpj = company.cnpj;
  const summary = element('summary'); const title = element('div');
  title.append(element('strong', company.name || 'Empresa com consulta pendente'));
  title.append(element('small', `${formatCnpj(company.cnpj)} · ${[company.city, company.uf].filter(Boolean).join(' / ') || 'Localidade não informada'}`));
  const state = badge('', 'warn'); summary.append(title, state); card.append(summary);
  const body = element('div', null, 'draft-body');
  const observed = element('p', null, 'draft-observed');
  const history = company.history ? `${company.history.description} em ${company.history.year}` : 'não informado';
  observed.textContent = `Consulta cadastral: Simples ${String(company.simples || 'não informado').toLowerCase()} · IRPJ histórico: ${history}.`;
  body.append(observed);
  if (record.documents.length) {
    const evidence = element('div', null, 'cte-evidence');
    evidence.append(element('strong', `${record.documents.length} ${record.documents.length === 1 ? 'CT-e lido' : 'CT-es lidos'}`));
    for (const doc of record.documents) {
      const start = [doc.startCity, doc.startUf].filter(Boolean).join('/');
      const end = [doc.endCity, doc.endUf].filter(Boolean).join('/');
      const taxes = [doc.total ? `Frete R$ ${doc.total}` : '', doc.icmsCst ? `CST ICMS ${doc.icmsCst}` : '', doc.icmsRate ? `ICMS ${doc.icmsRate}%` : '', doc.icmsValue ? `ICMS R$ ${doc.icmsValue}` : '', doc.cClassTrib ? `cClassTrib ${doc.cClassTrib}` : ''].filter(Boolean).join(' · ');
      evidence.append(element('p', `CT-e ${doc.number || '—'} · ${doc.date || 'data não informada'} · ${start || 'origem pendente'} → ${end || 'destino pendente'}${taxes ? ` · ${taxes}` : ''}`));
    }
    const ufs = new Set(record.documents.map(d => d.startUf).filter(Boolean));
    if (ufs.size > 1) evidence.append(element('p', 'Há mais de uma UF de início nos CT-es. Confira as exceções por rota no FreteLAB.', 'cte-conflict'));
    evidence.append(element('small', 'A origem e o código do CT-e são sugestões para conferência. Os impostos exibidos são da data do documento, não as alíquotas da simulação futura.'));
    body.append(evidence);
  }
  let carrierFields, madesaInfo, dasFields;
  const updateState = () => {
    const issues = registrationIssues(record);
    state.textContent = record.role === 'madesa' ? 'Ver regras gerais' : issues.length ? `${issues.length} pendência${issues.length === 1 ? '' : 's'}` : 'Campos básicos preenchidos';
    state.className = `badge ${!issues.length && record.role === 'carrier' ? 'good' : 'warn'}`;
    if (carrierFields) carrierFields.hidden = record.role !== 'carrier';
    if (madesaInfo) madesaInfo.hidden = record.role !== 'madesa';
    if (dasFields) dasFields.hidden = record.regime !== 'simples_das';
    const pending = body.querySelector('.draft-pending');
    if (pending) pending.textContent = issues.length ? `Falta conferir: ${issues.join(' · ')}.` : record.role === 'madesa' ? 'Confira as regras gerais da Madesa no FreteLAB.' : 'Confira os dados com a transportadora antes de cadastrar.';
  };
  const set = key => value => {
    record[key] = value;
    if (key === 'originUf') record.originSource = 'manual';
    if (key === 'originCity') record.citySource = 'manual';
    if (key === 'cClassTrib') record.classSource = 'manual';
    updateState();
  };
  const role = selectControl([['','Selecione'],['carrier','Transportadora'],['madesa','Madesa · tomadora']], record.role, set('role'));
  body.append(labelledControl('Identificar empresa *', role, 'O cadastro fiscal da Madesa é geral; os demais campos são por transportadora.'));
  madesaInfo = element('p', 'Na versão 1.3.38, as regras de crédito da Madesa são compartilhadas no cenário e não cadastradas por CNPJ nesta tela.', 'draft-info'); body.append(madesaInfo);
  carrierFields = element('div', null, 'draft-grid');
  carrierFields.append(
    labelledControl('Nome no FreteLAB *', textControl(record.name, 'Nome da transportadora já cadastrada', set('name'), {maxlength:'120'}), 'Use o nome que já existe na aplicação.'),
    labelledControl('Tipo fiscal confirmado *', selectControl(Object.entries(REGIMES), record.regime, set('regime')), 'O histórico do CNPJ é uma referência e não confirma o regime do ano escolhido.'),
    labelledControl('UF de início do transporte *', selectControl([['','Pendente'], ...UFS.map(uf => [uf,uf])], record.originUf, set('originUf')), record.originSource === 'cte' ? 'Sugestão do CT-e; confirme a operação e o ano.' : 'Informe o início efetivo da prestação.'),
    labelledControl('Município de início', textControl(record.originCity, 'Ex.: Campinas', set('originCity'), {maxlength:'120'}), record.citySource === 'cte' ? 'Sugestão do CT-e; confirme a operação.' : 'Necessário para distinguir operações dentro da mesma UF.'),
    labelledControl('Tratamento do frete *', selectControl(Object.entries(PRICE_MODES), record.priceMode, set('priceMode')), 'Confira se o valor contratado já inclui impostos.'),
    labelledControl('Tratamento do ICMS *', selectControl(Object.entries(ICMS_MODES), record.icmsMode, set('icmsMode')), 'Alíquotas e exceções são tratadas por rota no FreteLAB.'),
  );
  dasFields = element('div', null, 'draft-grid draft-das');
  dasFields.append(
    labelledControl('CBS no DAS (%) *', textControl(record.cbsDas, 'Pendente', set('cbsDas'), {type:'number',min:'0',max:'99.9999',step:'0.0001'}), 'Informe o percentual confirmado para a simulação.'),
    labelledControl('IBS no DAS (%) *', textControl(record.ibsDas, 'Pendente', set('ibsDas'), {type:'number',min:'0',max:'99.9999',step:'0.0001'}), 'Informe o percentual confirmado para a simulação.'),
  );
  carrierFields.append(dasFields);
  carrierFields.append(
    labelledControl('cClassTrib do CT-e · referência', textControl(record.cClassTrib, 'Ex.: 000001', set('cClassTrib'), {maxlength:'6',inputmode:'numeric'}), record.classSource === 'cte' ? 'Lido do CT-e; valide para o ano do cenário.' : 'Campo de apoio; não é salvo no cadastro da versão 1.3.38.'),
    labelledControl('Observações', textControl(record.note, 'Ex.: contato, data ou exceção de rota', set('note'), {maxlength:'250'}), 'Registre a origem da confirmação ou particularidades.'),
  );
  body.append(carrierFields, element('p', '', 'draft-pending')); card.append(body); updateState(); return card;
}

function renderRegistrations() {
  const open = new Set([...$('registration-list').querySelectorAll('details[open]')].map(x => x.dataset.cnpj));
  const firstRender = $('registration-list').children.length === 0;
  const cards = [...registrations.values()].map((record, index) => registrationCard(record, open.has(record.company.cnpj) || (firstRender && index === 0)));
  $('registration-list').replaceChildren(...cards);
  $('registration-section').hidden = cards.length === 0;
}

$('cnpj-form').addEventListener('submit', async event => {
  event.preventDefault();
  const items = parseCnpjs($('cnpjs').value);
  if (!items.length) { $('cnpjs').setCustomValidity('Informe pelo menos um CNPJ.'); $('cnpjs').reportValidity(); return; }
  if (items.length > MAX_CNPJS) { $('cnpjs').setCustomValidity(`Informe no máximo ${MAX_CNPJS} CNPJs por consulta.`); $('cnpjs').reportValidity(); return; }
  $('cnpjs').setCustomValidity('');
  const generation = ++requestGeneration;
  $('company-rows').replaceChildren(); $('empty').hidden = true; $('table-wrap').hidden = false; $('results-note').hidden = false;
  $('consultar').disabled = true; $('consultar').firstElementChild.textContent = 'Consultando...';
  for (let i = 0; i < items.length; i++) {
    if (generation !== requestGeneration) break;
    const item = items[i];
    $('count').textContent = `Consultando ${i + 1} de ${items.length}`;
    if (!item.valid) addRow(item, null, 'CNPJ inválido. Confira os dígitos.');
    else {
      try { const result = await queryCompany(item.normalized); if (generation === requestGeneration) { addRow(item, result); rememberCompany(item, result); } }
      catch (error) { if (generation === requestGeneration) { addRow(item, null, error.message); rememberCompany(item, null); } }
    }
  }
  if (generation === requestGeneration) {
    renderRegistrations();
    $('count').textContent = `${items.length} ${items.length === 1 ? 'CNPJ consultado' : 'CNPJs consultados'}`;
    $('consultar').disabled = false; $('consultar').firstElementChild.textContent = 'Consultar empresas';
  }
});

$('cnpjs').addEventListener('input', () => $('cnpjs').setCustomValidity(''));
$('limpar').addEventListener('click', () => {
  requestGeneration++; $('cnpjs').value = ''; $('cnpjs').setCustomValidity('');
  $('company-rows').replaceChildren(); $('table-wrap').hidden = true; $('results-note').hidden = true; $('empty').hidden = false;
  registrations.clear(); $('registration-list').replaceChildren(); $('registration-section').hidden = true;
  $('cte-files').value = ''; $('cte-feedback').textContent = '';
  $('count').textContent = 'Nenhuma consulta'; $('consultar').disabled = false; $('consultar').firstElementChild.textContent = 'Consultar empresas';
});

async function processCtes(files, isExample = false) {
  if (!files.length) return;
  if (files.length > 50) { $('cte-feedback').textContent = 'Selecione até 50 XMLs por vez.'; return; }
  const generation = ++requestGeneration;
  $('consultar').disabled = true; $('consultar').firstElementChild.textContent = 'Lendo CT-es...';
  let accepted = 0, duplicate = 0; const errors = []; const issuers = new Set();
  $('cte-feedback').textContent = `Lendo ${files.length} arquivo(s)...`;
  for (const file of files) {
    if (generation !== requestGeneration) return;
    try {
      if (file.size > 5_000_000) throw new Error('Arquivo excede 5 MB.');
      const doc = parseCteText(await file.text(), file.name);
      const item = {input:doc.cnpj, normalized:doc.cnpj, valid:true};
      let record = registrations.get(doc.cnpj);
      if (!record) {
        record = rememberCompany(item, null);
        record.company.name = doc.name || 'Emissor informado no CT-e'; record.company.source = 'CT-e';
      }
      if (addCteEvidence(record, doc)) { accepted++; issuers.add(doc.cnpj); } else duplicate++;
    } catch (error) { errors.push(`${file.name}: ${error.message}`); }
  }
  renderRegistrations(); refreshCompanyTable();
  for (const cnpj of isExample ? [] : issuers) {
    if (generation !== requestGeneration) return;
    $('cte-feedback').textContent = `CT-es lidos: ${accepted}. Consultando cadastro dos emissores...`;
    try { const company = await queryCompany(cnpj); if (generation === requestGeneration) registrations.get(cnpj).company = company; }
    catch { /* dados do CT-e permanecem disponíveis se a fonte cadastral falhar */ }
  }
  if (generation !== requestGeneration) return;
  renderRegistrations(); refreshCompanyTable();
  const codes = [...new Set([...registrations.values()].flatMap(r => r.documents.map(d => d.cClassTrib)).filter(code => /^\d{6}$/.test(code)))];
  if (codes.length === 1 && !$('class-code').value) $('class-code').value = codes[0];
  $('cte-feedback').textContent = (isExample ? 'Exemplo fictício. ' : '') + [`${accepted} CT-e(s) lido(s)`, duplicate ? `${duplicate} duplicado(s)` : '', errors.length ? `${errors.length} não aproveitado(s): ${errors.join(' | ')}` : ''].filter(Boolean).join(' · ') + '. Confirme se todos são de entrega ao cliente final.';
  $('consultar').disabled = false; $('consultar').firstElementChild.textContent = 'Consultar empresas';
}
$('cte-files').addEventListener('change', async event => {
  await processCtes([...event.target.files]); event.target.value = '';
});
$('cte-example').addEventListener('click', async () => {
  try {
    const response = await fetch('./exemplo-cte.xml');
    if (!response.ok) throw new Error('Exemplo indisponível.');
    const example = new File([await response.text()], 'exemplo-cte-ficticio.xml', {type:'application/xml'});
    await processCtes([example], true);
  } catch { $('cte-feedback').textContent = 'Não foi possível carregar o exemplo fictício.'; }
});

$('download-csv').addEventListener('click', () => {
  if (!registrations.size) return;
  const csv = registrationCsv([...registrations.values()], $('registration-year').value);
  const url = URL.createObjectURL(new Blob([csv], { type:'text/csv;charset=utf-8' }));
  const link = element('a'); link.href = url; link.download = `conferencia-cadastro-fretelab-${$('registration-year').value}.csv`;
  document.body.append(link); link.click(); link.remove(); setTimeout(() => URL.revokeObjectURL(url), 1000);
});

function detail(container, label, value) {
  const p = element('p'); p.append(element('strong', `${label}: `), document.createTextNode(value)); container.append(p);
}
$('class-form').addEventListener('submit', async event => {
  event.preventDefault();
  const code = $('class-code').value.trim(); const year = $('class-year').value;
  const result = $('class-result'); result.hidden = false; result.className = 'class-result'; result.replaceChildren(element('p', 'Consultando a base da Receita...'));
  try {
    if (!/^\d{6}$/.test(code) || !['2027', '2028'].includes(year)) throw new Error('Informe um código válido e uma data disponível.');
    const response = await fetch(`./dados/${year}.json`, { cache: 'no-cache' });
    if (!response.ok) throw new Error('Base da Receita indisponível. Verifique a atualização da página no GitHub Actions.');
    const view = classificationView(await response.json(), code);
    if (!view) throw new Error('Código não encontrado na base desta data. Confirme o cClassTrib do CT-e.');
    result.replaceChildren(element('h3', `${view.code} · ${view.description}`));
    detail(result, 'Tratamento', view.treatment); detail(result, 'Vínculo com o CT-e', view.cte);
    detail(result, 'Indicador de crédito do adquirente · CBS', view.cbs); detail(result, 'Indicador de crédito do adquirente · IBS', view.ibs);
    detail(result, 'Indicador de crédito presumido do adquirente', view.presumido);
    detail(result, 'Base consultada', `${view.date} · versão ${view.sourceCommit.slice(0, 12) || 'não informada'}`);
  } catch (error) { result.className = 'class-result error'; result.replaceChildren(element('p', error.message || 'Consulta indisponível.')); }
});
