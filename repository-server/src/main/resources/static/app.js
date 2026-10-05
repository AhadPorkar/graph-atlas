import { I18n, LANGUAGES, initialLanguage } from './i18n.js';
import { clientExample } from './client-examples.js';
import { createDeliveryWorkspace } from './delivery.js';

const $ = selector => document.querySelector(selector);
const escapeHtml = value => String(value ?? '').replace(/[&<>"']/g, character =>
  ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[character]));
const formats = Object.freeze({
  maven: ['M', 'Maven / Gradle'], npm: ['npm', 'npm'], nuget: ['Nu', 'NuGet'],
  pypi: ['Py', 'Python / pip'], raw: ['{ }', 'Raw'], docker: ['OCI', 'Docker / OCI'],
});
const navigation = [
  ['dashboard', '01'], ['releases', '02'], ['browse', '03'], ['clients', '04'], ['insights', '05'],
  ['repos', '06'], ['quarantine', '07', true], ['maintenance', '08', true],
  ['users', '09', true], ['tokens', '10'], ['audit', '11', true], ['settings', '12', true],
];

let storage = null;
try { storage = window.localStorage; } catch { /* Private browsing may deny storage. */ }
const i18n = new I18n({ storage });
const t = (key, values) => i18n.t(key, values);
const number = value => i18n.number(value);
const size = value => i18n.size(value);
const when = value => i18n.date(value);

let session = null;
let repositories = [];
let page = 'dashboard';
let renderVersion = 0;
let repositoryQuery = '';
let clientRepository = '';
let browseState = { repo: '', q: '', offset: 0 };
let activeDialog = null;
let loginFailure = null;

/** Use the selected locale explicitly; do not depend on browser Accept-Language. */
async function api(path, options = {}) {
  const method = options.method ?? 'GET';
  const headers = { 'Accept-Language': i18n.language, ...options.headers };
  let body = options.body;
  if (body !== undefined && !(body instanceof Blob) && !(body instanceof FormData)) {
    headers['Content-Type'] = 'application/json';
    body = JSON.stringify(body);
  }
  if (!['GET', 'HEAD'].includes(method) && session?.csrf) {
    headers['X-CSRF-Token'] = session.csrf;
  }
  let response;
  try {
    response = await fetch(`/api/${path}`, {
      ...options, method, headers, body, credentials: 'same-origin',
    });
  } catch {
    throw new Error(t('networkError'));
  }
  let data = {};
  if (response.status !== 204) {
    try { data = await response.json(); }
    catch {
      if (response.ok) throw new Error(t('invalidResponse'));
    }
  }
  if (!response.ok) {
    const key = `error.${data.error}`;
    const translated = t(key);
    const error = new Error(translated !== key ? translated :
      (data.message || t('requestFailed', { status: response.status })));
    error.status = response.status;
    error.code = data.error;
    error.detail = [data.detail, data.requestId].filter(Boolean).join(' | ');
    throw error;
  }
  return data;
}

function toast(message, isError = false, detail = '') {
  const item = document.createElement('div');
  item.className = `toast${isError ? ' error' : ''}`;
  item.setAttribute('role', isError ? 'alert' : 'status');
  item.innerHTML = escapeHtml(message) +
    (detail ? `<details><summary>${t('technical')}</summary><p dir="ltr">${escapeHtml(detail)}</p></details>` : '');
  $('#toasts').append(item);
  while ($('#toasts').children.length > 3) $('#toasts').firstElementChild.remove();
  window.setTimeout(() => item.remove(), isError ? 14000 : 4500);
}

function fail(error) {
  toast(error.message || t('unexpectedError'), true, error.detail || '');
  if (error.status === 401 && session) {
    session = null;
    closeDialog();
    showLogin();
  }
}

function showLogin() {
  renderVersion += 1;
  $('#bootStatus').hidden = true;
  $('#shell').hidden = true;
  $('#login').hidden = false;
  $('#loginForm [name=password]').value = '';
  $('#loginForm [name=username]').focus();
}

function renderShell() {
  $('#bootStatus').hidden = true;
  $('#login').hidden = true;
  $('#shell').hidden = false;
  $('#accountName').innerHTML = `<bdi>${escapeHtml(session.username)}</bdi><small>${t(session.admin ? 'admin' : 'user')}</small>`;
  $('#avatar').textContent = session.username.slice(0, 1).toUpperCase();
  $('#navigation').innerHTML = navigation
    .filter(([, , adminOnly]) => !adminOnly || session.admin)
    .map(([id, icon, adminOnly]) =>
      `${({ dashboard: t('navWorkspace'), repos: t('navOperations'), users: t('navAccess') })[id] ? `<div class="nav-section">${({ dashboard: t('navWorkspace'), repos: t('navOperations'), users: t('navAccess') })[id]}</div>` : ''}<a href="#${id}" data-page="${id}"${adminOnly ? ' class="admin-link"' : ''}>
        <span class="nav-icon" aria-hidden="true">${icon}</span><span>${t(id)}</span>
      </a>`).join('');
}

function heading(title, description = '', actions = '') {
  return `<div class="page-head"><div><h1>${escapeHtml(title)}</h1><p>${escapeHtml(description)}</p></div>
    <div class="actions">${actions}</div></div>`;
}

function button(id, text, className = '') {
  return `<button type="button" id="${id}" class="${className}">${escapeHtml(text)}</button>`;
}

function badge(online) {
  return `<span class="badge ${online ? 'good' : 'bad'}"><span class="status-dot"></span>${t(online ? 'active' : 'offline')}</span>`;
}

function table(headers, rows) {
  return `<div class="table-wrap" tabindex="0"><table>
    <thead><tr>${headers.map(header => `<th scope="col">${header}</th>`).join('')}</tr></thead>
    <tbody>${rows || `<tr><td colspan="${headers.length}" class="empty">${t('empty')}</td></tr>`}</tbody>
  </table></div>`;
}

function bind(id, action) {
  document.getElementById(id)?.addEventListener('click', () =>
    Promise.resolve().then(action).catch(fail));
}

function validateForm(form) {
  for (const input of form.elements) {
    if (!input.willValidate) continue;
    input.removeAttribute('aria-invalid');
    const tooShort = input.minLength > 0 && input.value && input.value.length < input.minLength;
    if (input.validity.valid && !tooShort) continue;
    input.setAttribute('aria-invalid', 'true');
    const field = input.closest('label')?.querySelector('.field-label')?.textContent ||
      input.closest('label')?.querySelector('span')?.textContent || input.name;
    let key = 'validationInvalid';
    if (input.validity.valueMissing) key = 'validationRequired';
    else if (input.validity.tooShort || tooShort) key = 'validationMinLength';
    else if (input.validity.rangeOverflow || input.validity.rangeUnderflow) key = 'validationRange';
    input.focus();
    throw new Error(t(key, { field, min: input.minLength }));
  }
}

function onSubmit(form, action) {
  form.noValidate = true; // Keep validation text in the selected application language.
  form.addEventListener('submit', async event => {
    event.preventDefault();
    if (form.dataset.busy === 'true') return;
    const submit = form.querySelector('[type=submit]');
    try {
      validateForm(form);
      form.dataset.busy = 'true';
      if (submit) submit.disabled = true;
      document.querySelectorAll('[data-language-switch]').forEach(select => { select.disabled = true; });
      await action(new FormData(form));
    } catch (error) {
      if (form.id === 'loginForm') {
        loginFailure = error;
        $('#loginError').textContent = error.message;
      } else fail(error);
    } finally {
      delete form.dataset.busy;
      if (submit) submit.disabled = false;
      document.querySelectorAll('[data-language-switch]').forEach(select => { select.disabled = false; });
    }
  });
}

function formActions(label = t('save')) {
  return `<div class="form-actions"><button type="button" data-cancel>${t('cancel')}</button>
    <button type="submit" class="primary">${escapeHtml(label)}</button></div>`;
}

/** Preserve entered values (including a chosen File) when translating an open dialog. */
function snapshotForm() {
  const form = $('#dialogBody form');
  if (!form) return null;
  return [...form.elements].filter(element => element.name).map(element => ({
    name: element.name, type: element.type, value: element.value, checked: element.checked,
    selected: element.selectedOptions ? [...element.selectedOptions].map(option => option.value) : null,
    fileInput: element.type === 'file' ? element : null,
  }));
}

function restoreForm(snapshot) {
  if (!snapshot) return;
  const form = $('#dialogBody form');
  if (!form) return;
  for (const field of snapshot) {
    const element = form.elements.namedItem(field.name);
    if (!element) continue;
    if (field.fileInput) element.replaceWith(field.fileInput);
    else if (field.selected) {
      for (const option of element.options) option.selected = field.selected.includes(option.value);
    } else if (['checkbox', 'radio'].includes(field.type)) element.checked = field.checked;
    else element.value = field.value;
  }
  form.dispatchEvent(new Event('formrestored'));
  // Dependent multi-select options are rebuilt by the form's handler.
  for (const field of snapshot) {
    const element = form.elements.namedItem(field.name);
    if (!field.selected || !element?.multiple) continue;
    for (const option of element.options) option.selected = field.selected.includes(option.value);
  }
}

function drawDialog(snapshot = null) {
  if (!activeDialog) return;
  const { title, body, wire } = activeDialog();
  $('#dialogTitle').textContent = title;
  $('#dialogBody').innerHTML = body;
  $('#dialogBody').querySelectorAll('[data-cancel]').forEach(element =>
    element.addEventListener('click', closeDialog));
  wire?.();
  restoreForm(snapshot);
  if (!$('#dialog').open) $('#dialog').showModal();
  i18n.applyDocument(document);
}

function openDialog(factory) {
  activeDialog = factory;
  drawDialog();
  $('#dialogBody [autofocus]')?.focus();
}

function closeDialog() {
  if ($('#dialog').open) $('#dialog').close();
  activeDialog = null;
  $('#dialogBody').replaceChildren(); // Clear secrets and file references.
}

function setMenu(open) {
  $('#shell').classList.toggle('menu-open', open);
  $('#menuBtn').setAttribute('aria-expanded', String(open));
}

function repoOptions(selected = '', predicate = () => true, all = false) {
  return (all ? `<option value="">${t('all')}</option>` : '') +
    repositories.filter(predicate).map(repository =>
      `<option value="${escapeHtml(repository.name)}"${repository.name === selected ? ' selected' : ''}>
        ${escapeHtml(repository.name)}
      </option>`).join('');
}

async function render() {
  if (!session) return;
  const version = ++renderVersion;
  page = location.hash.slice(1) || 'dashboard';
  if (!navigation.some(([id, , adminOnly]) => id === page && (!adminOnly || session.admin))) page = 'dashboard';
  const currentPage = page;
  setMenu(false);
  $('#breadcrumb').textContent = t(currentPage);
  document.querySelectorAll('#navigation a').forEach(link => {
    const active = link.dataset.page === currentPage;
    link.classList.toggle('active', active);
    if (active) link.setAttribute('aria-current', 'page');
    else link.removeAttribute('aria-current');
  });
  $('#content').setAttribute('aria-busy', 'true');
  $('#content').innerHTML = `<div class="loading" role="status">${t('loading')}</div>`;
  try {
    const response = await api('repos');
    if (version !== renderVersion) return;
    repositories = response.items ?? [];
    const view = await views[currentPage]();
    if (version !== renderVersion) return;
    $('#content').innerHTML = view.html;
    view.wire?.();
  } catch (error) {
    if (version !== renderVersion) return;
    $('#content').innerHTML = heading(t(currentPage)) +
      `<div class="notice error">${escapeHtml(error.message)}</div>${button('retry', t('retry'))}`;
    bind('retry', render);
    fail(error);
  } finally {
    if (version === renderVersion) $('#content').setAttribute('aria-busy', 'false');
  }
}

function repositoryRows(items, operations = false) {
  return items.map(repository => {
    const format = formats[repository.format] ?? ['?', repository.format];
    return `<tr>
      <td><div class="name-cell"><span class="mini-symbol" aria-hidden="true">${escapeHtml(format[0])}</span><bdi>${escapeHtml(repository.name)}</bdi></div></td>
      <td>${operations ? `<bdi>${escapeHtml(format[1])}</bdi>` : t(repository.type)}</td>
      ${operations ? `<td>${t(repository.type)}</td>` : ''}
      <td>${number(repository.assetCount)}</td><td>${badge(repository.online)}</td>
      ${operations ? `<td><div class="actions">
        <button class="small" data-browse="${escapeHtml(repository.name)}">${t('browse')}</button>
        ${session.admin ? `<button class="small" data-edit="${escapeHtml(repository.name)}">${t('edit')}</button>
          <button class="small danger" data-delete="${escapeHtml(repository.name)}">${t('remove')}</button>` : ''}
      </div></td>` : ''}
    </tr>`;
  }).join('');
}

async function repositoriesView() {
  return {
    html: heading(t('repos'), t('securityNote'), button('refresh', t('refresh')) +
      (session.admin ? button('newRepo', `+ ${t('newRepo')}`, 'primary') : '')) +
      `<section class="card"><div class="filters"><label><span>${t('search')}</span><input id="repoFilter" type="search" value="${escapeHtml(repositoryQuery)}"></label>
      <span class="muted repo-count">${i18n.plural('repositoryCount', repositories.length)}</span></div><div id="repoTable"></div></section>`,
    wire() {
      const paint = () => {
        $('#repoTable').innerHTML = table(
          [t('name'), t('format'), t('type'), t('files'), t('state'), t('action')],
          repositoryRows(repositories.filter(repository =>
            repository.name.toLowerCase().includes(repositoryQuery.toLowerCase())), true));
        document.querySelectorAll('[data-edit]').forEach(element => {
          element.onclick = () => repositoryDialog(repositories.find(repository => repository.name === element.dataset.edit));
        });
        document.querySelectorAll('[data-delete]').forEach(element => {
          element.onclick = () => deleteRepository(element.dataset.delete);
        });
        document.querySelectorAll('[data-browse]').forEach(element => {
          element.onclick = () => {
            browseState = { repo: element.dataset.browse, q: '', offset: 0 };
            location.hash = 'browse';
          };
        });
      };
      paint();
      $('#repoFilter').oninput = event => { repositoryQuery = event.target.value; paint(); };
      bind('refresh', render);
      bind('newRepo', () => repositoryDialog());
    },
  };
}

function repositoryDialog(existing = null) {
  const value = existing ?? {
    name: '', format: 'maven', type: 'hosted', online: true, anonymous: false,
    redeploy: false, cacheSeconds: 3600, members: [], allowedHosts: [], versionPolicy: 'mixed',
  };
  openDialog(() => ({
    title: existing ? `${t('edit')} · ${value.name}` : t('newRepo'),
    body: `<form id="repoForm"><div class="form-grid">
      <label><span class="field-label">${t('name')}</span><input name="name" dir="ltr" required maxlength="100"
        pattern="[a-z0-9][a-z0-9._\\-]{0,99}" value="${escapeHtml(value.name)}" ${existing ? 'readonly' : 'autofocus'}>
        <small>${t('repositoryNameHelp')}</small></label>
      <label><span class="field-label">${t('format')}</span><select name="format"${existing ? ' disabled' : ''}>
        ${Object.entries(formats).map(([key, [, title]]) =>
          `<option value="${key}"${value.format === key ? ' selected' : ''}>${title}</option>`).join('')}</select></label>
      <label><span class="field-label">${t('type')}</span><select name="type"${existing ? ' disabled' : ''}>
        ${['hosted', 'proxy', 'group'].map(key =>
          `<option value="${key}"${value.type === key ? ' selected' : ''}>${t(key)}</option>`).join('')}</select></label>
      <label data-maven><span class="field-label">${t('policy')}</span><select name="versionPolicy">
        ${[['mixed', 'mavenMixed'], ['release', 'mavenRelease'], ['snapshot', 'mavenSnapshot']].map(([key, label]) =>
          `<option value="${key}"${value.versionPolicy === key ? ' selected' : ''}>${t(label)}</option>`).join('')}</select></label>
      <p data-docker class="notice span-all">${t('dockerHostedOnly')}</p>
      <label data-proxy class="span-all"><span class="field-label">${t('upstream')}</span><input name="upstream" type="url" dir="ltr" value="${escapeHtml(value.upstream || '')}"></label>
      <label data-proxy><span class="field-label">${t('ttl')}</span><input name="cacheSeconds" type="number" min="0" value="${Number(value.cacheSeconds) || 0}" dir="ltr"></label>
      <label data-proxy><span class="field-label">${t('credentialEnv')}</span><input name="upstreamAuthEnv" dir="ltr" value="${escapeHtml(value.upstreamAuthEnv || '')}"></label>
      <label data-proxy class="span-all"><span class="field-label">${t('hosts')}</span><input name="allowedHosts" dir="ltr" value="${escapeHtml((value.allowedHosts || []).join(', '))}">
        <small dir="ltr">files.pythonhosted.org, cdn.example.org</small></label>
      <label data-group class="span-all"><span class="field-label">${t('members')}</span><select name="members" multiple size="5" dir="ltr"></select><small>${t('groupNote')}</small></label>
      <div class="span-all check-group">
        ${[['online', 'active'], ['anonymous', 'anonymous'], ['redeploy', 'redeploy'], ['offline', 'cacheOnly']].map(([key, label]) =>
          `<label class="check"${key === 'offline' ? ' data-proxy' : ''}><input type="checkbox" name="${key}"${value[key] ? ' checked' : ''}><span>${t(label)}</span></label>`).join('')}
      </div>
    </div>${formActions(existing ? t('save') : t('create'))}</form>`,
    wire() {
      const form = $('#repoForm');
      const field = name => form.elements.namedItem(name);
      const updateFields = (preserveMembers = false) => {
        const format = field('format').value;
        if (format === 'docker') field('type').value = 'hosted';
        for (const option of field('type').options) option.disabled = format === 'docker' && option.value !== 'hosted';
        const type = field('type').value;
        for (const [selector, visible] of [
          ['[data-proxy]', type === 'proxy'], ['[data-group]', type === 'group'],
          ['[data-maven]', format === 'maven'], ['[data-docker]', format === 'docker'],
        ]) form.querySelectorAll(selector).forEach(element => { element.hidden = !visible; });
        const selected = preserveMembers ? [...field('members').selectedOptions].map(option => option.value) : (value.members || []);
        field('members').innerHTML = repositories.filter(repository =>
          repository.format === format && repository.name !== value.name).map(repository =>
            `<option value="${escapeHtml(repository.name)}"${selected.includes(repository.name) ? ' selected' : ''}>${escapeHtml(repository.name)}</option>`).join('');
        field('upstream').required = type === 'proxy';
        field('upstream').disabled = type !== 'proxy';
        field('upstream').placeholder = ({
          maven: 'https://repo.maven.apache.org/maven2/', npm: 'https://registry.npmjs.org/',
          pypi: 'https://pypi.org/simple/', nuget: 'https://api.nuget.org/v3/index.json',
        })[format] || 'https://example.org/';
      };
      field('format').onchange = () => updateFields();
      field('type').onchange = () => updateFields(true);
      form.addEventListener('formrestored', () => updateFields(true));
      updateFields();
      onSubmit(form, async () => {
        const data = {
          name: field('name').value, format: field('format').value, type: field('type').value,
          versionPolicy: field('versionPolicy').value, upstream: field('upstream').value,
          cacheSeconds: Number(field('cacheSeconds').value), upstreamAuthEnv: field('upstreamAuthEnv').value,
          allowedHosts: field('allowedHosts').value.split(',').map(host => host.trim()).filter(Boolean),
          members: [...field('members').selectedOptions].map(option => option.value),
        };
        for (const key of ['online', 'anonymous', 'redeploy', 'offline']) data[key] = field(key).checked;
        await api(`repos${existing ? `/${encodeURIComponent(value.name)}` : ''}`, {
          method: existing ? 'PUT' : 'POST', body: data,
        });
        closeDialog(); toast(t('done')); await render();
      });
    },
  }));
}

function deleteRepository(name) {
  openDialog(() => ({
    title: `${t('remove')} · ${name}`,
    body: `<div class="notice error">${t('deleteWarning')}</div><form id="deleteRepo">
      <label><span class="field-label">${t('name')}</span><input name="confirm" required dir="ltr" autocomplete="off" autofocus></label>
      <label class="check"><input type="checkbox" name="purge"><span>${t('purge')}</span></label>${formActions(t('remove'))}</form>`,
    wire: () => onSubmit($('#deleteRepo'), async data => {
      if (data.get('confirm') !== name) throw new Error(t('confirmNameMismatch'));
      await api(`repos/${encodeURIComponent(name)}`, { method: 'DELETE', body: { confirm: name, purge: data.has('purge') } });
      closeDialog(); toast(t('done')); await render();
    }),
  }));
}

async function browseView() {
  const query = new URLSearchParams({ ...browseState, limit: 25 });
  const data = await api(`assets?${query}`);
  return {
    html: heading(t('browse'), t('stored'), button('createFromBasket', t('newCapsule')) + button('upload', t('upload'), 'primary')) +
      `<section class="card"><form id="browseForm" class="filters">
        <label><span>${t('repos')}</span><select name="repo" dir="ltr">${repoOptions(browseState.repo, () => true, true)}</select></label>
        <label><span>${t('search')}</span><input name="q" value="${escapeHtml(browseState.q)}" dir="ltr"></label>
        <button type="submit">${t('search')}</button></form>
        ${table([t('repos'), t('path'), t('downloadBytes'), t('updated'), t('action')],
          data.items.map((asset, index) => `<tr>
            <td><bdi>${escapeHtml(asset.repo)}</bdi></td>
            <td class="path-cell" title="${escapeHtml(asset.path)}">${escapeHtml(asset.path)}</td>
            <td><bdi>${size(asset.size)}</bdi></td><td>${when(asset.updated)}</td>
            <td><div class="actions"><a class="button small" href="/api/download?${escapeHtml(new URLSearchParams({ repo: asset.repo, path: asset.path }))}">${t('download')}</a>
              <button class="small" data-add-release="${index}">${t('addToCapsule')}</button><button class="small" data-detail="${index}">${t('details')}</button></div></td></tr>`).join(''))}
        <div class="pager"><span>${i18n.plural('fileCount', data.total)}</span><div class="actions">
          <button id="prev" class="small"${data.offset === 0 ? ' disabled' : ''}>${t('prev')}</button>
          <button id="next" class="small"${data.offset + data.limit >= data.total ? ' disabled' : ''}>${t('next')}</button>
        </div></div>
      </section>`,
    wire() {
      onSubmit($('#browseForm'), async form => {
        browseState = { repo: String(form.get('repo')), q: String(form.get('q')), offset: 0 };
        await render();
      });
      bind('prev', () => { browseState.offset = Math.max(0, browseState.offset - 25); return render(); });
      bind('next', () => { browseState.offset += 25; return render(); });
      bind('upload', uploadDialog);
      bind('createFromBasket', delivery.capsuleDialog);
      document.querySelectorAll('[data-add-release]').forEach(button => { button.onclick = () => delivery.addAsset(data.items[Number(button.dataset.addRelease)]); });
      document.querySelectorAll('[data-detail]').forEach(element => {
        element.onclick = () => {
          const asset = data.items[Number(element.dataset.detail)];
          openDialog(() => ({
            title: t('details'),
            body: `<dl class="kv"><dt>${t('repos')}</dt><dd><bdi>${escapeHtml(asset.repo)}</bdi></dd>
              <dt>${t('path')}</dt><dd class="technical">${escapeHtml(asset.path)}</dd>
              <dt>${t('downloadBytes')}</dt><dd>${size(asset.size)}</dd>
              <dt>${t('updated')}</dt><dd>${when(asset.updated)}</dd></dl>
              <details><summary>${t('technical')}</summary><pre class="code-box">${escapeHtml(JSON.stringify(asset, null, 2))}</pre></details>`,
          }));
        };
      });
    },
  };
}

function uploadDialog() {
  const eligible = repositories.filter(repository => repository.type === 'hosted' &&
    ['raw', 'maven'].includes(repository.format));
  openDialog(() => ({
    title: t('upload'),
    body: `<p class="notice">${t('protocolUpload')}</p>` + (eligible.length
      ? `<form id="uploadForm">
          <label><span class="field-label">${t('repos')}</span><select name="repo" dir="ltr">${repoOptions(browseState.repo, repository => eligible.includes(repository))}</select></label>
          <label><span class="field-label">${t('path')}</span><input name="path" dir="ltr" required autofocus placeholder="releases/app.zip"></label>
          <label><span class="field-label">${t('selectFile')}</span><input name="file" type="file" required></label>
          ${formActions(t('upload'))}</form>`
      : `<p class="empty">${t('noUploadRepository')}</p>`),
    wire() {
      const form = $('#uploadForm');
      if (!form) return;
      form.elements.namedItem('file').onchange = event => {
        const path = event.target.form.elements.namedItem('path');
        if (!path.value && event.target.files[0]) path.value = event.target.files[0].name;
      };
      onSubmit(form, async data => {
        const file = data.get('file');
        toast(t('uploadProgress'));
        await api(`upload?${new URLSearchParams({ repo: data.get('repo'), path: data.get('path') })}`,
          { method: 'PUT', body: file, headers: { 'Content-Type': 'application/octet-stream' } });
        browseState.repo = String(data.get('repo'));
        browseState.offset = 0;
        closeDialog(); toast(t('done')); await render();
      });
    },
  }));
}

async function copyText(text) {
  if (navigator.clipboard && window.isSecureContext) {
    try {
      await navigator.clipboard.writeText(text);
      toast(t('copied'));
      return;
    } catch { /* Clipboard permission is optional; keep a manual fallback. */ }
  }
  openDialog(() => ({
    title: t('copy'),
    body: `<p class="help">${t('copyManual')}</p><textarea id="copyText" rows="10" dir="ltr" readonly aria-label="${t('copy')}">${escapeHtml(text)}</textarea>`,
    wire: () => { $('#copyText').focus(); $('#copyText').select(); },
  }));
}

async function clientsView() {
  return {
    html: heading(t('clients'), t('clientNote')) + `<section class="card">
      <div class="client-head"><h2>${t('setupGuide')}</h2><label><span>${t('repos')}</span>
      <select id="clientRepo" dir="ltr">${repoOptions(clientRepository)}</select></label></div><div id="clientContent"></div></section>`,
    wire() {
      const paint = () => {
        clientRepository = $('#clientRepo').value;
        const repository = repositories.find(item => item.name === clientRepository);
        if (!repository) { $('#clientContent').innerHTML = `<p class="empty">${t('noRepositories')}</p>`; return; }
        const format = formats[repository.format] ?? ['?', repository.format];
        const example = clientExample(repository, session.username, t, i18n.language);
        $('#clientContent').innerHTML = `<div class="client-info"><span class="symbol">${escapeHtml(format[0])}</span>
          <strong dir="ltr">${escapeHtml(format[1])}</strong><span class="badge subtle">${t(repository.type)}</span></div>
          ${example.hosted ? '' : `<p class="notice">${t('proxyReadOnly')}</p>`}
          <div class="code-head"><h3>${t(example.hosted ? 'hostedCommands' : 'readCommands')}</h3>${button('copyClient', t('copy'))}</div>
          <pre class="code-box">${escapeHtml(example.code)}</pre>
          <p class="help">${t('clientDocs')} <code dir="ltr">${example.docs}</code></p>`;
        bind('copyClient', () => copyText(example.code));
      };
      $('#clientRepo').onchange = paint;
      paint();
    },
  };
}

async function usersView() {
  const { items } = await api('users');
  return {
    html: heading(t('users'), t('securityNote'), button('newUser', `+ ${t('newUser')}`, 'primary')) +
      `<section class="card">${table([t('username'), t('role'), t('state'), t('grants'), t('action')],
        items.map((user, index) => `<tr><td><bdi>${escapeHtml(user.username)}</bdi></td>
          <td>${t(user.admin ? 'admin' : 'user')}</td><td>${badge(!user.disabled)}</td>
          <td dir="ltr">${escapeHtml(Object.keys(user.grants || {}).join(', '))}</td>
          <td><div class="actions"><button class="small" data-user="${index}">${t('edit')}</button>
            <button class="small danger" data-delete-user="${index}">${t('remove')}</button></div></td></tr>`).join(''))}</section>`,
    wire() {
      bind('newUser', () => userDialog());
      document.querySelectorAll('[data-user]').forEach(element => {
        element.onclick = () => userDialog(items[Number(element.dataset.user)]);
      });
      document.querySelectorAll('[data-delete-user]').forEach(element => {
        element.onclick = () => {
          const user = items[Number(element.dataset.deleteUser)];
          openDialog(() => ({
            title: `${t('remove')} · ${user.username}`,
            body: `<p class="notice error">${t('deleteWarning')}</p><form id="deleteUser">
              <label><span class="field-label">${t('username')}</span><input name="confirm" dir="ltr" required autofocus></label>${formActions(t('remove'))}</form>`,
            wire: () => onSubmit($('#deleteUser'), async data => {
              if (data.get('confirm') !== user.username) throw new Error(t('confirmNameMismatch'));
              await api(`users/${encodeURIComponent(user.username)}`, { method: 'DELETE', body: { confirm: data.get('confirm') } });
              closeDialog(); toast(t('done')); await render();
            }),
          }));
        };
      });
    },
  };
}

function userDialog(existing = null) {
  const user = existing ?? { username: '', admin: false, disabled: false, grants: {} };
  openDialog(() => ({
    title: existing ? `${t('edit')} · ${user.username}` : t('newUser'),
    body: `<form id="userForm"><div class="form-grid">
      <label><span class="field-label">${t('username')}</span><input name="username" dir="ltr" required maxlength="100"
        pattern="[A-Za-z0-9][A-Za-z0-9._\\-]{0,99}" value="${escapeHtml(user.username)}" ${existing ? 'readonly' : 'autofocus'}></label>
      <label><span class="field-label">${t('password')}</span><input name="password" type="password" dir="ltr" autocomplete="new-password" minlength="12"${existing ? '' : ' required'}></label>
      <small class="span-all">${t('passwordHelp')}</small>
      <label class="check"><input type="checkbox" name="admin"${user.admin ? ' checked' : ''}><span>${t('admin')}</span></label>
      <label class="check"><input type="checkbox" name="disabled"${user.disabled ? ' checked' : ''}><span>${t('disabled')}</span></label>
      <label class="span-all"><span class="field-label">${t('grants')}</span>
        <textarea name="grants" rows="7" dir="ltr" required>${escapeHtml(JSON.stringify(user.grants, null, 2))}</textarea><small>${t('permissionsHelp')}</small></label>
    </div>${formActions(existing ? t('save') : t('create'))}</form>`,
    wire: () => onSubmit($('#userForm'), async data => {
      let grants;
      try {
        grants = JSON.parse(data.get('grants'));
        if (!grants || typeof grants !== 'object' || Array.isArray(grants)) throw new Error();
      } catch { throw new Error(t('invalidPermissions')); }
      await api(`users${existing ? `/${encodeURIComponent(user.username)}` : ''}`, {
        method: existing ? 'PUT' : 'POST',
        body: { username: data.get('username'), password: data.get('password'), admin: data.has('admin'), disabled: data.has('disabled'), grants },
      });
      closeDialog(); toast(t('done')); await render();
    }),
  }));
}

async function tokensView() {
  const { items } = await api('tokens');
  return {
    html: heading(t('tokens'), t('tokenHelp'), button('newToken', `+ ${t('newToken')}`, 'primary')) +
      `<section class="card">${table([t('label'), t('created'), t('expires'), t('action')],
        items.map((token, index) => `<tr><td>${escapeHtml(token.label || token.id.slice(0, 12))}</td>
          <td>${when(token.created)}</td><td>${when(token.expires)}</td>
          <td><button class="small danger" data-token="${index}">${t('revoke')}</button></td></tr>`).join(''))}</section>`,
    wire() {
      bind('newToken', tokenDialog);
      document.querySelectorAll('[data-token]').forEach(element => {
        element.onclick = () => {
          const token = items[Number(element.dataset.token)];
          openDialog(() => ({
            title: t('revoke'),
            body: `<p class="notice">${escapeHtml(token.label || token.id.slice(0, 12))}</p><form id="revokeToken">${formActions(t('confirm'))}</form>`,
            wire: () => onSubmit($('#revokeToken'), async () => {
              await api(`tokens/${encodeURIComponent(token.id)}`, { method: 'DELETE' });
              closeDialog(); toast(t('done')); await render();
            }),
          }));
        };
      });
    },
  };
}

function tokenDialog() {
  openDialog(() => ({
    title: t('newToken'),
    body: `<form id="tokenForm"><div class="form-grid">
      <label><span class="field-label">${t('label')}</span><input name="label" required autofocus maxlength="200"></label>
      <label><span class="field-label">${t('expires')} (${t('optional')})</span><input name="expires" type="datetime-local" dir="ltr"></label>
    </div>${formActions(t('create'))}</form>`,
    wire: () => onSubmit($('#tokenForm'), async data => {
      const value = data.get('expires');
      const expires = value ? new Date(value) : null;
      if (expires && (!Number.isFinite(expires.getTime()) || expires.getTime() <= Date.now())) {
        throw new Error(t('invalidExpiry'));
      }
      const result = await api('tokens', { method: 'POST', body: {
        label: data.get('label'), expires: expires?.toISOString() ?? '',
      } });
      // Keep the one-time secret in the dialog closure, never in localStorage or URLs.
      closeDialog();
      await render();
      openDialog(() => ({
        title: t('newToken'),
        body: `<p class="notice">${t('tokenNote')}</p><pre class="code-box token-value">${escapeHtml(result.token)}</pre>${button('copyToken', t('copy'), 'primary')}`,
        wire: () => bind('copyToken', () => copyText(result.token)),
      }));
    }),
  }));
}

async function auditView() {
  const { items } = await api('audit?limit=100');
  return {
    html: heading(t('audit'), '', button('refresh', t('refresh'))) +
      `<section class="card">${table([t('time'), t('user'), t('action'), t('path'), t('result')],
        items.map(entry => `<tr><td>${when(entry.time)}</td><td><bdi>${escapeHtml(entry.user || '—')}</bdi></td>
          <td dir="ltr">${escapeHtml(entry.method)}</td><td class="path-cell" title="${escapeHtml(entry.requestId)}">${escapeHtml(entry.path)}</td>
          <td><span class="badge ${entry.status < 400 ? 'good' : 'bad'}">${escapeHtml(entry.status)}</span></td></tr>`).join(''))}</section>`,
    wire: () => bind('refresh', render),
  };
}

async function maintenanceView() {
  return {
    html: heading(t('maintenance'), t('maintainNote')) +
      `<div class="maintenance-grid">${[['compact', '▤'], ['verify', '✓'], ['gc', '♲']].map(([key, icon]) =>
        `<section class="card"><span class="symbol" aria-hidden="true">${icon}</span><h2>${t(key)}</h2><p>${t(`${key}Help`)}</p><div class="actions">
        ${key === 'gc' ? button('gcPreview', t('preview')) : ''}${button(`do-${key}`, t('execute'), key === 'gc' ? 'danger' : '')}</div></section>`).join('')}</div>
      <div id="maintenanceResult"></div>`,
    wire() {
      const perform = (action, dryRun) => openDialog(() => ({
        title: t(action),
        body: `<p class="notice">${t('maintainNote')}</p><form id="maintenanceForm"><label>
          <span class="field-label">${t('confirm')}: <bdi>MAINTENANCE</bdi></span>
          <input name="confirm" required pattern="MAINTENANCE" dir="ltr" autocomplete="off" autofocus></label>
          ${formActions(dryRun && action === 'gc' ? t('preview') : t('execute'))}</form>`,
        wire: () => onSubmit($('#maintenanceForm'), async () => {
          const result = await api('maintenance', { method: 'POST', body: { action, dryRun, confirm: 'MAINTENANCE' } });
          closeDialog();
          if ($('#maintenanceResult')) $('#maintenanceResult').innerHTML =
            `<section class="card result-card"><h2>${t('result')}</h2><pre class="code-box">${escapeHtml(JSON.stringify(result, null, 2))}</pre></section>`;
          toast(t('done'));
        }),
      }));
      for (const action of ['compact', 'verify', 'gc']) bind(`do-${action}`, () => perform(action, false));
      bind('gcPreview', () => perform('gc', true));
    },
  };
}

async function settingsView() {
  const data = await api('system');
  return {
    html: heading(t('settings'), t('systemNote')) + `<section class="card">
      <div class="card-head"><h2>${t('configuration')}</h2><span class="badge subtle">${t('readOnly')}</span></div>
      <dl class="kv">${Object.entries(data).map(([key, value]) => {
        let formatted;
        if (typeof value === 'boolean') formatted = t(value ? 'enabled' : 'booleanDisabled');
        else if (key === 'defaultLanguage') formatted = LANGUAGES[value]?.name || value;
        else if (key === 'supportedLanguages') formatted = value.map(language => LANGUAGES[language]?.name || language).join(' · ');
        else if (Array.isArray(value)) formatted = value.join(', ');
        else if (typeof value === 'number') formatted = number(value);
        else formatted = value;
        return `<dt>${escapeHtml(t(key))}</dt><dd><bdi>${escapeHtml(formatted)}</bdi></dd>`;
      }).join('')}</dl><p class="notice">${t('unlimited')}</p></section>`,
  };
}

function passwordDialog() {
  openDialog(() => ({
    title: t('changePassword'),
    body: `<p class="notice">${t('passwordRotationHelp')}</p><form id="passwordForm">
      <label><span class="field-label">${t('currentPassword')}</span><input name="oldPassword" type="password" dir="ltr" autocomplete="current-password" required autofocus></label>
      <label><span class="field-label">${t('newPassword')}</span><input name="password" type="password" dir="ltr" autocomplete="new-password" required minlength="12"></label>
      <label><span class="field-label">${t('repeatPassword')}</span><input name="repeat" type="password" dir="ltr" autocomplete="new-password" required minlength="12"></label>
      ${formActions()}</form>`,
    wire: () => onSubmit($('#passwordForm'), async data => {
      if (data.get('password') !== data.get('repeat')) throw new Error(t('passwordMismatch'));
      await api('password', { method: 'POST', body: { oldPassword: data.get('oldPassword'), password: data.get('password') } });
      closeDialog(); session = null; showLogin(); toast(t('passwordChanged'));
    }),
  }));
}

const delivery = createDeliveryWorkspace({ $, t, api, escapeHtml, size, number, when, heading, button, table, bind,
  openDialog, closeDialog, onSubmit, formActions, toast, fail, render, session: () => session, repositories: () => repositories });
const views = {
  dashboard: delivery.dashboard, releases: delivery.releasesView, insights: delivery.insightsView, quarantine: delivery.quarantineView, repos: repositoriesView, browse: browseView, clients: clientsView,
  users: usersView, tokens: tokensView, audit: auditView, maintenance: maintenanceView, settings: settingsView,
};

async function changeLanguage(language) {
  const snapshot = snapshotForm();
  try {
    if (!await i18n.setLanguage(language)) return;
    const url = new URL(location.href);
    url.searchParams.set('lang', i18n.language);
    try { history.replaceState(null, '', url); } catch { /* Embedded/locked-down browsers may deny history writes. */ }
    $('#toasts').replaceChildren();
    i18n.applyDocument(document);
    if (loginFailure) {
      const key = `error.${loginFailure.code}`;
      $('#loginError').textContent = t(key) === key ? t('unexpectedError') : t(key);
    }
    if (session) renderShell();
    if (activeDialog) drawDialog(snapshot);
    if (session) await render();
  } catch {
    i18n.applyDocument(document);
    toast(t('offlineTranslations'), true);
  }
}

async function initialize() {
  await i18n.initialize(initialLanguage(location.search, storage));
  i18n.applyDocument(document);
  document.querySelectorAll('[data-language-switch]').forEach(select => {
    select.addEventListener('change', event => changeLanguage(event.target.value));
  });
  onSubmit($('#loginForm'), async data => {
    loginFailure = null; $('#loginError').textContent = '';
    await api('login', { method: 'POST', body: { username: data.get('username'), password: data.get('password') } });
    session = await api('session');
    $('#loginForm [name=password]').value = '';
    renderShell(); await render();
  });
  $('#closeDialog').onclick = closeDialog;
  $('#dialog').addEventListener('close', () => {
    // close() queues an event. A replacement dialog may already have opened when it arrives.
    // Never clear a newer dialog or its one-time token because of a stale close event.
    if (!$('#dialog').open) {
      activeDialog = null;
      $('#dialogBody').replaceChildren();
    }
  });
  $('#menuBtn').onclick = () => setMenu(!$('#shell').classList.contains('menu-open'));
  $('#menuBackdrop').onclick = () => setMenu(false);
  document.addEventListener('keydown', event => {
    if (event.key === 'Escape' && !$('#dialog').open) { setMenu(false); $('#menuBtn').focus(); }
  });
  $('#changePassword').onclick = passwordDialog;
  $('#logout').onclick = async () => {
    try {
      await api('logout', { method: 'POST' });
      session = null; closeDialog(); showLogin();
    } catch (error) {
      // Do not pretend that a server session was invalidated when logout failed.
      toast(t('safeLogoutFailed'), true, error.message);
    }
  };
  window.addEventListener('hashchange', render);
  try {
    session = await api('session');
    renderShell(); await render();
  } catch (error) {
    showLogin();
    if (error.status !== 401) fail(error);
  }
}

initialize().catch(error => {
  $('#bootStatus').textContent = 'Unable to load the console. Reload the page or check the server connection.';
  $('#bootStatus').classList.add('error');
  console.error('Console initialization failed:', error.message);
});
