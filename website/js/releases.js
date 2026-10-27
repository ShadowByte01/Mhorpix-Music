/**
 * Mhorpix GitHub Releases Integration
 * Automatically polls GitHub API for the latest releases, populates release notes,
 * renders multi-architecture asset downloads, and triggers instant APK downloads.
 */

const REPO_ENDPOINTS = [
  'https://api.github.com/repos/ShadowByte01/Mhorpix-Music/releases',
  'https://api.github.com/repos/ShadowByte01/Mhorpix/releases',
  'https://api.github.com/repos/ShadowByte01/Mhorpix-Music/releases/latest',
];

const GITHUB_RELEASES_PAGE = 'https://github.com/ShadowByte01/Mhorpix-Music/releases';

// Verified fallback release data in case of offline or GitHub API rate limits
const FALLBACK_LATEST_RELEASE = {
  version: '1.7.4',
  tag_name: 'v1.7.4',
  name: 'Mhorpix 1.7.4: Startup Crash Fixes, Liquid Glass Web Showcase & Enhancements',
  published_at: '2026-10-27T12:00:00Z',
  html_url: 'https://github.com/ShadowByte01/Mhorpix-Music/releases/tag/v1.7.4',
  direct_apk_url: 'https://github.com/ShadowByte01/Mhorpix-Music/releases/download/v1.7.4/Mhorpix-1.7.4.apk',
  body: `### What's New in Mhorpix v1.7.4

* **Critical Startup Crash Fixes** — Resolved app auto-close on launch. Hardened database initialization with bounded join timeouts, added global uncaught exception handlers, and safe-guarded lifecycle observers.
* **Liquid Glass Web Showcase** — Brand new modern web presentation with real-time liquid glass refraction, specular highlight dynamics, and Y2K Frutiger Aero transformation.
* **Full Architecture Compatibility** — Built multi-ABI universal APK along with dedicated arm64-v8a, armeabi-v7a, and x86_64 builds.
* **Peer-to-Peer (P2P) Listening** — Synchronized listening with nearby friends over local Wi-Fi or Hotspot with zero cloud latency.
* **In-App Updates** — Automatic background release checking and system notification alerts.`,
  assets: [
    {
      name: 'Mhorpix-1.7.4.apk',
      label: 'Android Universal APK (All Phones)',
      size: 158068424,
      browser_download_url: 'https://github.com/ShadowByte01/Mhorpix-Music/releases/download/v1.7.4/Mhorpix-1.7.4.apk',
      is_primary: true,
      platform: 'android'
    },
    {
      name: 'Mhorpix-1.7.4-arm64-v8a-release.apk',
      label: 'Android ARM64 (Modern Devices)',
      size: 60302938,
      browser_download_url: 'https://github.com/ShadowByte01/Mhorpix-Music/releases/download/v1.7.4/Mhorpix-1.7.4-arm64-v8a-release.apk',
      is_primary: false,
      platform: 'android'
    },
    {
      name: 'Mhorpix-1.7.4-windows-x64-setup.exe',
      label: 'Windows Setup Installer (.exe)',
      size: 319427584,
      browser_download_url: 'https://github.com/ShadowByte01/Mhorpix-Music/releases/download/v1.7.4/Mhorpix-1.7.4-windows-x64-setup.exe',
      is_primary: false,
      platform: 'windows'
    },
    {
      name: 'Mhorpix-1.7.4-windows-x64-portable.zip',
      label: 'Windows Portable (.zip)',
      size: 315992806,
      browser_download_url: 'https://github.com/ShadowByte01/Mhorpix-Music/releases/download/v1.7.4/Mhorpix-1.7.4-windows-x64-portable.zip',
      is_primary: false,
      platform: 'windows'
    }
  ]
};

class ReleasesManager {
  constructor() {
    this.latestRelease = null;
    this.allReleases = [];
    this.primaryApkUrl = FALLBACK_LATEST_RELEASE.direct_apk_url;
  }

  async init() {
    await this.fetchReleases();
    this.renderHomePageRelease();
    this.renderFullReleasesPage();
    this.bindDownloadButtons();
  }

  /**
   * Fetches releases from GitHub API with caching and multiple endpoint fallback
   */
  async fetchReleases() {
    const cached = this.getCachedReleases();
    if (cached) {
      this.processReleases(cached);
      return;
    }

    for (const url of REPO_ENDPOINTS) {
      try {
        const response = await fetch(url, {
          headers: {
            'Accept': 'application/vnd.github+json'
          }
        });

        if (!response.ok) continue;

        const data = await response.json();
        const releasesList = Array.isArray(data) ? data : [data];
        const publishedReleases = releasesList.filter(r => !r.draft);

        if (publishedReleases.length > 0) {
          this.setCachedReleases(publishedReleases);
          this.processReleases(publishedReleases);
          return;
        }
      } catch (err) {
        console.warn('Endpoint fetch failed, trying fallback:', err);
      }
    }

    // If all endpoints failed or rate-limited, use fallback
    console.info('Using fallback verified release dataset for Mhorpix.');
    this.processReleases([FALLBACK_LATEST_RELEASE]);
  }

  getCachedReleases() {
    try {
      const stored = localStorage.getItem('mhorpix_releases_cache');
      const storedTime = localStorage.getItem('mhorpix_releases_timestamp');
      if (stored && storedTime) {
        // Cache for 15 minutes to respect GitHub API rate limits
        if (Date.now() - parseInt(storedTime, 10) < 15 * 60 * 1000) {
          return JSON.parse(stored);
        }
      }
    } catch (_) {}
    return null;
  }

  setCachedReleases(data) {
    try {
      localStorage.setItem('mhorpix_releases_cache', JSON.stringify(data));
      localStorage.setItem('mhorpix_releases_timestamp', Date.now().toString());
    } catch (_) {}
  }

  processReleases(releases) {
    this.allReleases = releases;
    const latest = releases[0] || FALLBACK_LATEST_RELEASE;
    this.latestRelease = latest;

    // Find the primary APK asset for direct download
    if (latest.assets && latest.assets.length > 0) {
      const apkAsset =
        latest.assets.find(a => a.name.endsWith('.apk') && !a.name.includes('arm') && !a.name.includes('x86')) ||
        latest.assets.find(a => a.name.endsWith('-universal-release.apk')) ||
        latest.assets.find(a => a.name.endsWith('.apk'));

      if (apkAsset) {
        this.primaryApkUrl = apkAsset.browser_download_url;
      } else {
        this.primaryApkUrl = latest.html_url;
      }
    } else {
      this.primaryApkUrl = latest.direct_apk_url || FALLBACK_LATEST_RELEASE.direct_apk_url;
    }
  }

  /**
   * Triggers an immediate download of the APK in browser
   */
  startDownload(customUrl = null) {
    const url = customUrl || this.primaryApkUrl;
    
    // Create transient anchor to trigger direct browser file save
    const a = document.createElement('a');
    a.href = url;
    a.setAttribute('download', '');
    a.style.display = 'none';
    document.body.appendChild(a);
    a.click();
    document.body.removeChild(a);

    this.showDownloadToast(url);
  }

  showDownloadToast(url) {
    let toast = document.getElementById('download-toast');
    if (!toast) {
      toast = document.createElement('div');
      toast.id = 'download-toast';
      toast.className = 'liquid-glass download-toast';
      document.body.appendChild(toast);
    }

    const fileName = url.substring(url.lastIndexOf('/') + 1) || 'Mhorpix-1.7.3.apk';
    toast.innerHTML = `
      <div style="display: flex; align-items: center; gap: 12px;">
        <span style="font-size: 24px;">📥</span>
        <div>
          <div style="font-weight: 700; color: #fff; font-size: 15px;">Download Started!</div>
          <div style="font-size: 13px; color: #94a3b8;">${fileName} is downloading...</div>
        </div>
      </div>
    `;

    toast.classList.add('show');
    setTimeout(() => toast.classList.remove('show'), 5000);
  }

  bindDownloadButtons() {
    const downloadButtons = document.querySelectorAll('.btn-download-apk, [data-action="download-apk"]');
    downloadButtons.forEach(btn => {
      btn.addEventListener('click', (e) => {
        e.preventDefault();
        this.startDownload();
      });
    });

    const versionLabels = document.querySelectorAll('.current-version-label');
    const ver = this.latestRelease?.tag_name || `v${FALLBACK_LATEST_RELEASE.version}`;
    versionLabels.forEach(el => {
      el.textContent = ver;
    });
  }

  formatBytes(bytes) {
    if (!bytes || isNaN(bytes)) return '';
    const mb = bytes / (1024 * 1024);
    return `${mb.toFixed(1)} MB`;
  }

  formatDate(dateStr) {
    if (!dateStr) return '';
    try {
      const date = new Date(dateStr);
      return date.toLocaleDateString(undefined, { year: 'numeric', month: 'short', day: 'numeric' });
    } catch (_) {
      return dateStr;
    }
  }

  formatMarkdown(md) {
    if (!md) return '';
    return md
      .replace(/### (.*)/g, '<h4 style="color:#fff; margin-top:14px; margin-bottom:6px; font-size:16px;">$1</h4>')
      .replace(/## (.*)/g, '<h3 style="color:#00f2fe; margin-top:16px; margin-bottom:8px; font-size:18px;">$1</h3>')
      .replace(/\*\*(.*?)\*\*/g, '<strong style="color:#fff;">$1</strong>')
      .replace(/\* (.*)/g, '<li style="margin-left:18px; margin-bottom:4px;">$1</li>')
      .replace(/- (.*)/g, '<li style="margin-left:18px; margin-bottom:4px;">$1</li>')
      .replace(/\n\n/g, '<br>');
  }

  /**
   * Renders the latest release preview card on the homepage
   */
  renderHomePageRelease() {
    const container = document.getElementById('home-release-container');
    if (!container || !this.latestRelease) return;

    const r = this.latestRelease;
    const version = r.tag_name || `v${r.version}`;
    const dateFormatted = this.formatDate(r.published_at);

    const assets = (r.assets && r.assets.length > 0) ? r.assets : FALLBACK_LATEST_RELEASE.assets;

    let assetsHtml = '';
    assets.slice(0, 4).forEach(asset => {
      const sizeStr = this.formatBytes(asset.size);
      assetsHtml += `
        <a href="${asset.browser_download_url}" class="asset-download-btn" data-url="${asset.browser_download_url}">
          <div class="asset-info">
            <span class="asset-name">${asset.name}</span>
            <span class="asset-size">${sizeStr} · Click to Download</span>
          </div>
          <span class="asset-icon">↓</span>
        </a>
      `;
    });

    container.innerHTML = `
      <div class="releases-card liquid-glass">
        <div class="release-header-row">
          <div class="release-meta">
            <span class="glass-badge">Latest Stable Release</span>
            <span class="release-version">${version}</span>
            <span class="release-date">Released on ${dateFormatted}</span>
          </div>
          <button class="glass-btn glass-btn-primary btn-download-apk" style="padding: 10px 22px;">
            <span>Download Latest APK</span>
            <span>↓</span>
          </button>
        </div>

        <div class="release-notes-box">
          ${this.formatMarkdown(r.body || FALLBACK_LATEST_RELEASE.body)}
        </div>

        <h4 style="font-size: 15px; margin-bottom: 12px; color: #fff; font-weight: 600;">Direct Release Packages</h4>
        <div class="assets-grid">
          ${assetsHtml}
        </div>
      </div>
    `;

    // Re-bind click handlers for the newly rendered buttons
    container.querySelectorAll('.btn-download-apk').forEach(btn => {
      btn.addEventListener('click', (e) => {
        e.preventDefault();
        this.startDownload();
      });
    });

    container.querySelectorAll('.asset-download-btn').forEach(btn => {
      btn.addEventListener('click', (e) => {
        e.preventDefault();
        const url = btn.getAttribute('data-url');
        this.startDownload(url);
      });
    });
  }

  /**
   * Renders all releases on the dedicated releases.html page
   */
  renderFullReleasesPage() {
    const listContainer = document.getElementById('all-releases-list');
    if (!listContainer) return;

    if (this.allReleases.length === 0) {
      this.allReleases = [FALLBACK_LATEST_RELEASE];
    }

    let html = '';
    this.allReleases.forEach((r, idx) => {
      const version = r.tag_name || `v${r.version}`;
      const dateFormatted = this.formatDate(r.published_at);
      const isLatest = idx === 0;

      const assets = (r.assets && r.assets.length > 0) ? r.assets : (isLatest ? FALLBACK_LATEST_RELEASE.assets : []);

      let assetsHtml = '';
      assets.forEach(asset => {
        const sizeStr = this.formatBytes(asset.size);
        assetsHtml += `
          <a href="${asset.browser_download_url}" class="asset-download-btn" data-url="${asset.browser_download_url}">
            <div class="asset-info">
              <span class="asset-name">${asset.name}</span>
              <span class="asset-size">${sizeStr} · Direct Download</span>
            </div>
            <span class="asset-icon">↓</span>
          </a>
        `;
      });

      html += `
        <div class="releases-card liquid-glass" style="margin-bottom: 32px;">
          <div class="release-header-row">
            <div class="release-meta">
              ${isLatest ? '<span class="glass-badge">Latest Release</span>' : '<span class="glass-badge" style="border-color: rgba(255,255,255,0.2); color:#94a3b8; background:rgba(255,255,255,0.05);">Previous Version</span>'}
              <span class="release-version">${version}</span>
              <span class="release-date">Published on ${dateFormatted}</span>
            </div>
            <a href="${r.html_url || GITHUB_RELEASES_PAGE}" target="_blank" rel="noopener noreferrer" class="glass-btn" style="padding: 8px 18px; font-size: 13px;">
              View on GitHub ↗
            </a>
          </div>

          <div class="release-notes-box">
            ${this.formatMarkdown(r.body || '')}
          </div>

          ${assets.length > 0 ? `
            <h4 style="font-size: 15px; margin-bottom: 12px; color: #fff; font-weight: 600;">Download Packages &amp; Builds</h4>
            <div class="assets-grid">
              ${assetsHtml}
            </div>
          ` : ''}
        </div>
      `;
    });

    listContainer.innerHTML = html;

    listContainer.querySelectorAll('.asset-download-btn').forEach(btn => {
      btn.addEventListener('click', (e) => {
        e.preventDefault();
        const url = btn.getAttribute('data-url');
        this.startDownload(url);
      });
    });
  }
}

// Global Toast CSS injection
const toastStyle = document.createElement('style');
toastStyle.textContent = `
  .download-toast {
    position: fixed;
    bottom: 30px;
    right: 30px;
    padding: 18px 24px;
    z-index: 99999;
    border-radius: 16px;
    transform: translateY(100px);
    opacity: 0;
    pointer-events: none;
    transition: all 0.4s cubic-bezier(0.16, 1, 0.3, 1);
  }
  .download-toast.show {
    transform: translateY(0);
    opacity: 1;
    pointer-events: auto;
  }
  @media (max-width: 600px) {
    .download-toast {
      left: 20px;
      right: 20px;
      bottom: 20px;
    }
  }
`;
document.head.appendChild(toastStyle);

const manager = new ReleasesManager();
document.addEventListener('DOMContentLoaded', () => manager.init());
window.MhorpixReleases = manager;
