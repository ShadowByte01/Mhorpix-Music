/**
 * ui.js — Mhorpix website
 * Non-glass UI interactions:
 *  - Navbar scroll behaviour
 *  - Mobile burger menu
 *  - APK download trigger (auto-download latest APK from GitHub releases)
 *  - Download toast notification
 */

(function () {
  'use strict';

  /* ── Navbar scroll ─────────────────────────────────────── */
  const navbar = document.getElementById('navbar');
  if (navbar) {
    const onScroll = () => {
      if (window.scrollY > 20) navbar.classList.add('scrolled');
      else navbar.classList.remove('scrolled');
    };
    window.addEventListener('scroll', onScroll, { passive: true });
    onScroll();
  }

  /* ── Mobile burger ──────────────────────────────────────── */
  const burger  = document.getElementById('burger-btn');
  const drawer  = document.getElementById('mobile-drawer');
  if (burger && drawer) {
    burger.addEventListener('click', () => {
      burger.classList.toggle('open');
      drawer.classList.toggle('open');
    });
    /* Close on drawer link tap */
    drawer.addEventListener('click', (e) => {
      if (e.target.tagName === 'A') {
        burger.classList.remove('open');
        drawer.classList.remove('open');
      }
    });
  }

  /* ── Download Toast ─────────────────────────────────────── */
  function showDownloadToast(version) {
    let toast = document.getElementById('dl-toast');
    if (!toast) {
      toast = document.createElement('div');
      toast.id = 'dl-toast';
      toast.className = 'dl-toast';
      toast.innerHTML = `
        <div class="dl-toast-icon">📥</div>
        <div>
          <div class="dl-toast-title">Downloading Mhorpix ${version}</div>
          <div class="dl-toast-sub">Android APK · Check your downloads</div>
        </div>`;
      document.body.appendChild(toast);
    }
    // Force reflow
    void toast.offsetWidth;
    toast.classList.add('show');
    setTimeout(() => toast.classList.remove('show'), 4000);
  }

  /* ── APK Download ───────────────────────────────────────── */
  // Tries to fetch the latest release from GitHub API and
  // initiate a direct download of the universal APK asset.
  async function triggerAPKDownload() {
    const OWNER = 'ShadowByte01';
    const REPO  = 'Mhorpix-Music';
    const API   = `https://api.github.com/repos/${OWNER}/${REPO}/releases/latest`;

    let downloadUrl = null;
    let version = 'latest';

    try {
      const res  = await fetch(API, { headers: { Accept: 'application/vnd.github+json' } });
      const data = await res.json();
      version = data.tag_name || 'latest';

      /* Find universal APK first, else any .apk */
      const assets = data.assets || [];
      const universal = assets.find(a => a.name.toLowerCase().includes('universal') && a.name.endsWith('.apk'));
      const anyApk    = assets.find(a => a.name.endsWith('.apk'));
      const asset     = universal || anyApk;

      if (asset) {
        downloadUrl = asset.browser_download_url;
      } else {
        /* Fallback: releases page */
        downloadUrl = `https://github.com/${OWNER}/${REPO}/releases/latest`;
      }
    } catch (_) {
      downloadUrl = `https://github.com/${OWNER}/${REPO}/releases/latest`;
    }

    showDownloadToast(version);

    /* Trigger download */
    const a = document.createElement('a');
    a.href = downloadUrl;
    a.download = '';
    a.rel = 'noopener noreferrer';
    document.body.appendChild(a);
    a.click();
    document.body.removeChild(a);
  }

  /* Update version labels after releases.js runs */
  function updateVersionLabels(version) {
    document.querySelectorAll('.current-version-label').forEach(el => {
      el.textContent = version;
    });
  }
  window.updateVersionLabels = updateVersionLabels;

  /* ── Bind all APK download buttons ─────────────────────── */
  document.addEventListener('click', (e) => {
    const btn = e.target.closest('.btn-download-apk');
    if (btn) {
      e.preventDefault();
      triggerAPKDownload();
    }
  });

  /* ── Smooth-scroll for anchor nav links ─────────────────── */
  document.addEventListener('click', (e) => {
    const a = e.target.closest('a[href^="#"]');
    if (!a) return;
    const id = a.getAttribute('href').slice(1);
    const target = document.getElementById(id);
    if (target) {
      e.preventDefault();
      target.scrollIntoView({ behavior: 'smooth', block: 'start' });
      // Close mobile drawer if open
      if (drawer) {
        drawer.classList.remove('open');
        if (burger) burger.classList.remove('open');
      }
    }
  });

})();
