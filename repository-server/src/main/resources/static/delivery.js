/** Graph Atlas delivery workspace. No framework or CDN runtime dependency. */
export const RELEASE_STAGES = Object.freeze(['DRAFT', 'IN_REVIEW', 'APPROVED', 'RELEASED', 'REJECTED', 'REVOKED']);

/** The explicit repository/path format never infers a repository from a filename. */
export function parseAssetLines(text) {
  const seen = new Set();
  return String(text).split(/\r?\n/).map(line => line.trim()).filter(Boolean).map(line => {
    const slash = line.indexOf('/');
    if (slash < 1 || slash === line.length - 1) throw new Error('assetReference');
    const repo = line.slice(0, slash), path = line.slice(slash + 1);
    if (!/^[a-z0-9][a-z0-9._-]{0,99}$/.test(repo) || /[%\\\x00-\x1f\x7f]/.test(path) ||
        path.split('/').some(part => ['', '.', '..'].includes(part))) throw new Error('assetReference');
    if (seen.has(line)) throw new Error('duplicateAsset');
    seen.add(line); return { repo, path };
  });
}

export function createDeliveryWorkspace(context) {
  const { $, t, api, escapeHtml: esc, size, number, when, heading, button, table, bind,
    openDialog, closeDialog, onSubmit, formActions, toast, fail, render, session, repositories } = context;
  let releaseState = '', releaseOffset = 0;
  const basket = new Map();
  const stateLabel = state => t(`stage.${state}`);
  const status = state => `<span class="release-status stage-${esc(state)}">${esc(stateLabel(state))}</span>`;
  const metric = (label, value, hint, accent = false) => `<section class="card kpi${accent ? ' metric-accent' : ''}">
    <span class="metric-label">${esc(label)}</span><strong><bdi>${esc(value)}</bdi></strong><small>${esc(hint)}</small></section>`;
  function addAsset(asset) {
    basket.set(`${asset.repo}/${asset.path}`, { repo: asset.repo, path: asset.path });
    toast(t('addedToCapsule', { count: number(basket.size) }));
  }
  function releaseCard(item) {
    return `<button class="release-card" data-capsule="${esc(item.id)}">
      <span class="release-card-top">${status(item.state)}<span class="release-version" dir="ltr">${esc(item.version)}</span></span>
      <strong>${esc(item.name)}</strong><span class="release-card-info">${t('capsuleFiles', { count: number(item.assetCount) })} <span aria-hidden="true">/</span> ${size(item.logicalBytes)}</span>
      <span class="release-card-bottom"><bdi>${esc(item.createdBy)}</bdi><span>${when(item.updated)}</span></span></button>`;
  }
  function wireCards() {
    document.querySelectorAll('[data-capsule]').forEach(element => {
      element.addEventListener('click', () => openRelease(element.dataset.capsule).catch(fail));
    });
  }
  async function dashboard() {
    const [stats, insight, releases] = await Promise.all([api('stats'), api('insights/storage'), api('releases?limit=4')]);
    const stages = insight.releaseStates || {};
    return {
      html: heading(t('dashboard'), t('atlasOverview'), button('newCapsule', `+ ${t('newCapsule')}`, 'primary')) +
        `<section class="atlas-hero"><div class="hero-copy"><span class="eyebrow">${t('atlasEyebrow')}</span>
          <h2>${t('atlasHero')}</h2><p>${t('atlasHeroText')}</p><div class="hero-actions"><a href="#releases" class="button hero-button">${t('openLanes')} <span aria-hidden="true">&rarr;</span></a>
          <a href="#clients">${t('connectToolchain')}</a></div></div><div class="hero-map" aria-hidden="true"><div class="map-node node-build">01</div>
          <div class="map-node node-review">02</div><div class="map-node node-release">03</div><div class="map-caption">CAPTURE / REVIEW / DELIVER</div></div></section>
        <div class="kpis atlas-metrics">${metric(t('releaseReady'), number(stages.RELEASED || 0), t('signedSnapshots'), true)}
          ${metric(t('waitingReview'), number(stages.IN_REVIEW || 0), t('independentApproval'))}
          ${metric(t('storedArtifacts'), number(stats.assets), t('acrossRegistries', { count: number(stats.repositories) }))}
          ${metric(t('duplicateBytesAvoided'), size(insight.deduplicatedBytes), t('measuredNotEstimated'))}</div>
        <div class="atlas-columns"><section class="card"><div class="card-head"><div><span class="eyebrow">${t('releasePulse')}</span><h2>${t('latestCapsules')}</h2></div><a href="#releases">${t('viewAll')}</a></div>
          <div class="release-grid">${releases.items.map(releaseCard).join('') || `<div class="empty-state"><span class="empty-number">01</span><h3>${t('firstCapsule')}</h3><p>${t('firstCapsuleText')}</p><a href="#browse">${t('browse')}</a></div>`}</div></section>
        <section class="card next-actions"><span class="eyebrow">${t('workspaceHealth')}</span><h2>${t('nextBestAction')}</h2>
          <a class="action-row" href="#releases"><span class="step-number">01</span><span><strong>${t('reviewQueue')}</strong><small>${t('reviewQueueText', { count: number(stages.IN_REVIEW || 0) })}</small></span><b aria-hidden="true">&rarr;</b></a>
          <a class="action-row" href="#insights"><span class="step-number">02</span><span><strong>${t('storageFootprint')}</strong><small>${t('retainedFootprint', { bytes: size(insight.retainedUniqueBytes) })}</small></span><b aria-hidden="true">&rarr;</b></a>
          <a class="action-row" href="${session().admin ? '#quarantine' : '#browse'}"><span class="step-number">03</span><span><strong>${t('contentContainment')}</strong><small>${t('heldReferences', { count: number(insight.quarantinedReferences) })}</small></span><b aria-hidden="true">&rarr;</b></a>
          <p class="subtle-note">${t('noSecurityScore')}</p></section></div>
        <section class="toolchain-strip"><span>${t('nativeToolchain')}</span><b>Maven / Gradle</b><b>npm</b><b>NuGet</b><b>PyPI</b><b>OCI</b><b>Raw</b><a href="#clients">${t('configureClient')}</a></section>`,
      wire() { bind('newCapsule', capsuleDialog); wireCards(); },
    };
  }
  async function releasesView() {
    const data = await api(`releases?${new URLSearchParams({ state: releaseState, offset: releaseOffset, limit: 100 })}`);
    const stages = releaseState ? [releaseState] : RELEASE_STAGES.slice(0, 4);
    const archived = releaseState ? [] : data.items.filter(item => ['REVOKED', 'REJECTED'].includes(item.state));
    return {
      html: heading(t('releases'), t('releasesIntro'), button('newCapsule', `+ ${t('newCapsule')}`, 'primary')) +
        `<div class="lane-toolbar"><span class="pill">${t('twoPersonRequired')}</span><label><span class="visually-hidden">${t('state')}</span><select id="releaseState"><option value="">${t('allStages')}</option>${RELEASE_STAGES.map(state => `<option value="${state}"${state === releaseState ? ' selected' : ''}>${stateLabel(state)}</option>`).join('')}</select></label>
          <span>${t('capsulesTotal', { count: number(data.total) })}</span></div>
        <div class="release-board${releaseState ? ' one-lane' : ''}">${stages.map((state, index) => {
          const items = data.items.filter(item => item.state === state);
          return `<section class="release-lane"><div class="lane-head"><span class="lane-index">${String(index + 1).padStart(2, '0')}</span><h2>${stateLabel(state)}</h2><span class="lane-count">${number(items.length)}</span></div><div class="lane-body">${items.map(releaseCard).join('') || `<p class="lane-empty">${t('emptyLane')}</p>`}</div></section>`;
        }).join('')}</div>${archived.length ? `<section class="card archived-releases"><h2>${t('closedDecisions')}</h2><div class="release-grid">${archived.map(releaseCard).join('')}</div></section>` : ''}
        <div class="pager"><span>${t('lanePageNote')}</span><div class="actions"><button id="releasePrev"${releaseOffset ? '' : ' disabled'}>${t('prev')}</button><button id="releaseNext"${data.offset + data.limit < data.total ? '' : ' disabled'}>${t('next')}</button></div></div>
        <p class="subtle-note">${t('logicalLanesNote')}</p>`,
      wire() { bind('newCapsule', capsuleDialog); wireCards();
        $('#releaseState').onchange = () => { releaseState = $('#releaseState').value; releaseOffset = 0; render(); };
        bind('releasePrev', () => { releaseOffset = Math.max(0, releaseOffset - 100); return render(); });
        bind('releaseNext', () => { releaseOffset += 100; return render(); }); },
    };
  }
  function capsuleDialog() {
    const lines = [...basket.values()].map(item => `${item.repo}/${item.path}`).join('\n');
    openDialog(() => ({
      title: t('newCapsule'),
      body: `<p class="notice">${t('freezeDescription')}</p><form id="capsuleForm" class="form-grid">
        <label><span class="field-label">${t('releaseName')}</span><input name="name" dir="ltr" required maxlength="100" placeholder="checkout-service" autofocus></label>
        <label><span class="field-label">${t('releaseVersion')}</span><input name="version" dir="ltr" required maxlength="100" placeholder="2026.10.0"></label>
        <label class="span-all"><span class="field-label">${t('artifactReferences')}</span><textarea name="assets" required rows="6" dir="ltr" placeholder="raw-hosted/releases/app.zip">${esc(lines)}</textarea><small>${t('artifactReferencesHelp')}</small></label>
        <label class="span-all"><span class="field-label">${t('releaseNote')}</span><textarea name="note" rows="3" maxlength="2000"></textarea></label>${formActions(t('freezeCapsule'))}</form>`,
      wire() { onSubmit($('#capsuleForm'), async data => {
        let assets; try { assets = parseAssetLines(data.get('assets')); } catch (error) { throw new Error(t(error.message)); }
        if (!assets.length) throw new Error(t('assetReference'));
        const value = await api('releases', { method: 'POST', body: { name: data.get('name'), version: data.get('version'), note: data.get('note'), assets } });
        basket.clear(); closeDialog(); toast(t('capsuleCreated')); await render(); await openRelease(value.id);
      }); },
    }));
  }
  async function openRelease(id) {
    const value = await api(`releases/${encodeURIComponent(id)}`); let gate = null;
    const gateBlock = () => gate ? `<div class="gate-panel ${gate.allowed ? 'gate-open' : 'gate-closed'}" role="status"><strong>${t(gate.allowed ? 'gateOpen' : 'gateClosed')}</strong>
      <p>${t('gateResult', { count: number(gate.checkedDigests), drift: number(gate.sourceDrift) })}</p><small>${when(gate.checkedAt)}</small>
      <details><summary>${t('technical')}</summary><pre class="code-box">${esc(JSON.stringify(gate, null, 2))}</pre></details></div>` : '';
    openDialog(() => ({
      title: `${value.name} / ${value.version}`,
      body: `<div class="capsule-summary">${status(value.state)}<code dir="ltr">${esc(value.id)}</code><span>${t('revisionLabel', { revision: number(value.revision) })}</span></div>
        <p class="capsule-note">${esc(value.note || t('noReleaseNote'))}</p>
        <div class="actions capsule-actions">${(value.allowedActions || []).map(action => button(`transition-${action}`, t(`transition.${action}`), ['release', 'approve'].includes(action) ? 'primary' : '')).join('')}
          ${button('verifyCapsule', t('verifyCapsule'))}${value.evidence ? `<a class="button" href="/api/releases/${encodeURIComponent(id)}/evidence">${t('exportEvidence')}</a>` : ''}</div>
        <div id="gateResult">${gateBlock()}</div><div class="section-title"><h3>${t('frozenManifest')}</h3><span>${t('capsuleFiles', { count: number(value.assetCount) })}</span></div>
        <p class="manifest-root"><span>${t('manifestFingerprint')}</span><code dir="ltr">${esc(value.manifestDigest)}</code></p>
        ${table([t('path'), 'SHA-256', t('size'), t('action')], value.manifest.map((item, index) => `<tr><td class="path-cell" title="${esc(item.repo + '/' + item.path)}">${esc(item.repo + '/' + item.path)}</td>
          <td><code title="${esc(item.sha256)}">${esc(item.sha256.slice(0, 16))}...</code></td><td>${size(item.size)}</td><td>${value.state === 'RELEASED' ? `<a href="/api/releases/${encodeURIComponent(id)}/assets/${index}">${t('downloadPinned')}</a>` : t('notReleased')}</td></tr>`).join(''))}
        <p class="help">${t('pinnedDownloadHelp')}</p><div class="section-title"><h3>${t('decisionTrail')}</h3></div>
        <ol class="decision-trail">${value.events.map(event => `<li><span class="timeline-dot"></span><div><strong>${t(`transition.${event.action}`)}</strong><span><bdi>${esc(event.actor)}</bdi> / ${when(event.time)}</span><p>${esc(event.note || '')}</p></div></li>`).join('')}</ol>
        <p class="subtle-note">${t('historicalEvidenceNote')}</p>`,
      wire() {
        for (const action of value.allowedActions || []) bind(`transition-${action}`, () => transitionDialog(value, action));
        bind('verifyCapsule', async () => { const target = $('#gateResult'); const button = $('#verifyCapsule'); button.disabled = true;
          try { gate = await api(`releases/${encodeURIComponent(id)}/gate?deep=true`); if (target.isConnected) target.innerHTML = gateBlock(); }
          finally { if (button.isConnected) button.disabled = false; } });
      },
    }));
  }
  function transitionDialog(value, action) {
    openDialog(() => ({
      title: t(`transition.${action}`),
      body: `<p class="notice">${t('decisionDescription')}</p><p><bdi>${esc(value.name)} / ${esc(value.version)}</bdi> ${status(value.state)}</p>
        <form id="releaseDecision"><label><span class="field-label">${t('decisionReason')}</span><textarea name="note" required maxlength="2000" rows="4" autofocus></textarea></label>
        <small>${t('concurrencyNote')}</small>${formActions(t(`transition.${action}`))}</form>`,
      wire: () => onSubmit($('#releaseDecision'), async data => {
        await api(`releases/${encodeURIComponent(value.id)}/transitions/${action}`, { method: 'POST', body: { expectedRevision: value.revision, note: data.get('note') } });
        closeDialog(); toast(t('decisionSaved')); await render(); await openRelease(value.id);
      }),
    }));
  }
  async function insightsView() {
    const data = await api('insights/storage');
    return { html: heading(t('insights'), t('insightsIntro')) +
      `<div class="kpis">${metric(t('logicalFootprint'), size(data.logicalBytes), t('allLiveReferences'))}
        ${metric(t('physicalReferences'), size(data.liveUniqueBytes), t('uniqueLiveDigests'))}
        ${metric(t('duplicateBytesAvoided'), size(data.deduplicatedBytes), t('notDiskBenchmark'), true)}
        ${metric(t('retainedHistory'), size(data.pinnedOnlyBytes), t('pinnedOnlyHelp'))}</div>
      <div class="atlas-columns"><section class="card efficiency-card"><span class="eyebrow">${t('contentAddressed')}</span><h2>${t('deduplicationEfficiency')}</h2>
        <strong class="efficiency-value"><bdi>${number(data.deduplicationPercent)}%</bdi></strong><meter min="0" max="100" value="${Number(data.deduplicationPercent) || 0}" aria-label="${esc(t('deduplicationEfficiency'))}"></meter>
        <p>${t('deduplicationExplanation')}</p>${data.diskUsableBytes != null ? `<div class="disk-facts"><span>${t('hostFreeSpace')}</span><strong>${size(data.diskUsableBytes)}</strong></div>` : ''}
        <small>${t('storageAccountingNote')}</small></section>
      <section class="card"><span class="eyebrow">${t('actionableConfiguration')}</span><h2>${t('configurationSignals')}</h2><div class="insight-signals">${data.recommendations.map(item => `<article class="insight-signal"><span class="signal-indicator"></span><div><strong>${t(`signal.${item.code}`)}</strong><p>${t(`signalHelp.${item.code}`)}</p>${item.repo ? `<code>${esc(item.repo)}</code>` : ''}</div></article>`).join('') || `<p class="empty">${t('noConfigurationSignals')}</p>`}</div></section></div>
      <section class="card registry-breakdown"><div class="card-head"><h2>${t('registryBreakdown')}</h2><small>${when(data.measuredAt)}</small></div>
        ${table([t('repos'), t('format'), t('files'), t('logicalFootprint')], data.repositories.map(repo => `<tr><td><bdi>${esc(repo.name)}</bdi></td><td>${esc(repo.format)}</td><td>${number(repo.assets)}</td><td>${size(repo.logicalBytes)}</td></tr>`).join(''))}</section>`, };
  }
  async function quarantineView() {
    const data = await api('quarantine');
    return { html: heading(t('quarantine'), t('quarantineIntro'), button('newHold', `+ ${t('newHold')}`, 'primary')) +
      `<div class="notice containment-notice">${t('manualNotScanner')}</div><section class="card">${table(['SHA-256', t('state'), t('decisionReason'), t('updated'), t('action')], data.items.map((item, index) => `<tr>
        <td><code title="${esc(item.sha256)}">${esc(item.sha256.slice(0, 18))}...</code></td><td><span class="badge ${item.active ? 'bad' : 'good'}">${t(item.active ? 'held' : 'resolved')}</span></td><td class="reason-cell">${esc(item.reason)}</td><td>${when(item.updated)}</td>
        <td><button class="small" data-hold="${index}">${t(item.active ? 'resolveHold' : 'reapplyHold')}</button></td></tr>`).join(''))}</section>
      <p class="subtle-note">${t('quarantineScopeNote')}</p>`,
      wire() { bind('newHold', () => holdDialog()); document.querySelectorAll('[data-hold]').forEach(element => {
        element.onclick = () => holdDialog(data.items[Number(element.dataset.hold)]);
      }); }, };
  }
  function holdDialog(existing = null) {
    const active = !existing?.active;
    openDialog(() => ({
      title: t(existing?.active ? 'resolveHold' : 'newHold'),
      body: `<p class="notice">${t('digestScopeWarning')}</p><form id="holdForm">
        <label><span class="field-label">SHA-256</span><input name="sha256" required pattern="[a-f0-9]{64}" minlength="64" maxlength="64" dir="ltr" value="${esc(existing?.sha256 || '')}"${existing ? ' readonly' : ''} autofocus></label>
        <label><span class="field-label">${t('decisionReason')}</span><textarea name="reason" required maxlength="2000" rows="4"></textarea></label>${formActions(t(active ? 'applyHold' : 'resolveHold'))}</form>`,
      wire: () => onSubmit($('#holdForm'), async data => { await api('quarantine', { method: 'POST', body: { sha256: data.get('sha256'), reason: data.get('reason'), active, expectedRevision: existing?.revision || 0 } });
        closeDialog(); toast(t('decisionSaved')); await render(); }),
    }));
  }
  return { dashboard, releasesView, insightsView, quarantineView, addAsset, capsuleDialog, openRelease };
}
