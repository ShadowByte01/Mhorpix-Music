/**
 * releases.js — Mhorpix website
 * Fetches GitHub releases from ShadowByte01/Mhorpix-Music
 * and renders them into the page (home card + releases page full list).
 */
(function () {
  'use strict';

  const OWNER = 'ShadowByte01';
  const REPO  = 'Mhorpix-Music';
  const API   = `https://api.github.com/repos/${OWNER}/${REPO}/releases`;

  /* ── Format file size ────────────────────────────────── */
  function fmtSize(bytes) {
    if (!bytes) return '';
    if (bytes >= 1024 * 1024) return (bytes / 1024 / 1024).toFixed(1) + ' MB';
    if (bytes >= 1024) return (bytes / 1024).toFixed(0) + ' KB';
    return bytes + ' B';
  }

  /* ── Format date ─────────────────────────────────────── */
  function fmtDate(iso) {
    if (!iso) return '';
    try {
      return new Date(iso).toLocaleDateString('en-US', {
        year: 'numeric', month: 'long', day: 'numeric'
      });
    } catch (_) { return iso; }
  }

  /* ── Asset icon by filename ──────────────────────────── */
  function assetIcon(name) {
    const n = name.toLowerCase();
    if (n.endsWith('.apk')) return '📱';
    if (n.endsWith('.exe') || n.endsWith('.msi')) return '🖥️';
    if (n.endsWith('.zip') || n.endsWith('.tar.gz')) return '📦';
    if (n.endsWith('.deb')) return '🐧';
    if (n.endsWith('.dmg') || n.endsWith('.pkg')) return '🍎';
    return '📄';
  }

  /* ── Parse markdown to minimal HTML ─────────────────── */
  function mdToHtml(md) {
    if (!md) return '';
    return md
      .replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
      .replace(/\*\*(.+?)\*\*/g, '<strong>$1</strong>')
      .replace(/### (.+)/g, '<h4>$1</h4>')
      .replace(/## (.+)/g,  '<h4>$1</h4>')
      .replace(/# (.+)/g,   '<h4>$1</h4>')
      .replace(/^[-*] (.+)/gm, '<li>$1</li>')
      .replace(/(<li>.*<\/li>)+/g, m => `<ul>${m}</ul>`)
      .replace(/\n\n+/g, '<br><br>')
      .replace(/\n/g, '<br>');
  }

  /* ── Build a single release card HTML ───────────────── */
  function buildReleaseCard(rel, compact = false) {
    const assets = (rel.assets || []).filter(a => !a.name.includes('source'));

    const assetsHTML = assets.length ? `
      <div class="assets-list">
        ${assets.map(a => `
          <a class="asset-item" href="${a.browser_download_url}" download rel="noopener noreferrer">
            <div style="display:flex;align-items:center;gap:10px">
              <span style="font-size:18px">${assetIcon(a.name)}</span>
              <span class="asset-name">${a.name}</span>
            </div>
            <div style="display:flex;align-items:center;gap:10px">
              <span class="asset-size">${fmtSize(a.size)}</span>
              <span class="asset-dl">↓</span>
            </div>
          </a>
        `).join('')}
      </div>` : '<p style="color:rgba(255,255,255,0.35);font-size:13px">No downloadable assets in this release.</p>';

    const body = compact
      ? ''
      : `<div class="release-notes">${mdToHtml(rel.body)}</div>`;

    return `
      <div class="release-card">
        <div class="release-header">
          <div>
            <span class="release-badge">${rel.prerelease ? 'Pre-release' : 'Stable'}</span>
            <div class="release-version">${rel.tag_name || 'Unknown'}</div>
            <div class="release-date">${fmtDate(rel.published_at)}</div>
          </div>
          <a href="${rel.html_url}" target="_blank" rel="noopener noreferrer"
             style="font-size:13px;color:rgba(255,255,255,0.4);text-decoration:none;white-space:nowrap">
            View on GitHub ↗
          </a>
        </div>
        ${body}
        ${assetsHTML}
      </div>`;
  }

  /* ── Home page: single latest release ───────────────── */
  async function renderHomeRelease() {
    const container = document.getElementById('home-release-container');
    if (!container) return;

    try {
      const res  = await fetch(`${API}/latest`, { headers: { Accept: 'application/vnd.github+json' } });
      if (!res.ok) throw new Error(res.status);
      const rel  = await res.json();

      /* Update version labels in nav / hero buttons */
      if (typeof window.updateVersionLabels === 'function') {
        window.updateVersionLabels(rel.tag_name || 'latest');
      }

      container.innerHTML = buildReleaseCard(rel, false);
    } catch (err) {
      container.innerHTML = `
        <div class="release-loading">
          <div class="loading-icon">⚠️</div>
          <p>Could not fetch release info. <a href="https://github.com/${OWNER}/${REPO}/releases" target="_blank" rel="noopener" style="color:rgba(255,255,255,0.55)">View on GitHub ↗</a></p>
        </div>`;
    }
  }

  /* ── Releases page: all releases list ───────────────── */
  async function renderAllReleases() {
    const container = document.getElementById('releases-list-container');
    if (!container) return;

    try {
      const res  = await fetch(`${API}?per_page=20`, { headers: { Accept: 'application/vnd.github+json' } });
      if (!res.ok) throw new Error(res.status);
      const list = await res.json();

      if (!list.length) {
        container.innerHTML = '<p style="color:rgba(255,255,255,0.4)">No releases found.</p>';
        return;
      }

      container.innerHTML = list.map((rel, i) => buildReleaseCard(rel, i > 0)).join('');
    } catch (err) {
      container.innerHTML = `
        <div class="release-loading">
          <div class="loading-icon">⚠️</div>
          <p>Could not fetch releases. <a href="https://github.com/${OWNER}/${REPO}/releases" target="_blank" rel="noopener" style="color:rgba(255,255,255,0.55)">View on GitHub ↗</a></p>
        </div>`;
    }
  }

  /* ── Init on DOM ready ───────────────────────────────── */
  function init() {
    renderHomeRelease();
    renderAllReleases();
  }

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', init);
  } else {
    init();
  }
})();
