import { MAX_CNPJS, parseCnpjs, formatCnpj, queryCompany, classificationView } from './core.js?v=2';

const $ = id => document.getElementById(id);
const element = (tag, text, cls) => { const e = document.createElement(tag); if (text != null) e.textContent = text; if (cls) e.className = cls; return e; };
let requestGeneration = 0;

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
      try { const result = await queryCompany(item.normalized); if (generation === requestGeneration) addRow(item, result); }
      catch (error) { if (generation === requestGeneration) addRow(item, null, error.message); }
    }
  }
  if (generation === requestGeneration) {
    $('count').textContent = `${items.length} ${items.length === 1 ? 'CNPJ consultado' : 'CNPJs consultados'}`;
    $('consultar').disabled = false; $('consultar').firstElementChild.textContent = 'Consultar empresas';
  }
});

$('cnpjs').addEventListener('input', () => $('cnpjs').setCustomValidity(''));
$('limpar').addEventListener('click', () => {
  requestGeneration++; $('cnpjs').value = ''; $('cnpjs').setCustomValidity('');
  $('company-rows').replaceChildren(); $('table-wrap').hidden = true; $('results-note').hidden = true; $('empty').hidden = false;
  $('count').textContent = 'Nenhuma consulta'; $('consultar').disabled = false; $('consultar').firstElementChild.textContent = 'Consultar empresas';
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
