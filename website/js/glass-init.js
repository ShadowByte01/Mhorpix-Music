/**
 * glass-init.js — Mhorpix website
 * Initialises @ybouane/liquidglass on all .lg-btn-wrap elements
 * and wires the playground sliders to live-update the glass config.
 *
 * Loaded as type="module" so we can use top-level await and
 * import from CDN.
 */

import { LiquidGlass } from 'https://cdn.jsdelivr.net/npm/@ybouane/liquidglass@1.0.3/dist/index.js';

/* ── Wait for DOM ──────────────────────────────────────── */
await new Promise(r => {
  if (document.readyState !== 'loading') r();
  else document.addEventListener('DOMContentLoaded', r, { once: true });
});

/* ────────────────────────────────────────────────────────
   Helper: collect all elements that need liquid glass
   ─────────────────────────────────────────────────────── */
const root = document.getElementById('root');
if (!root) {
  console.warn('[Mhorpix] #root element not found. Skipping LiquidGlass init.');
  // eslint-disable-next-line no-throw-literal
  throw 'no root';
}

/* ── Playground element ─────────────────────────────────── */
const pgGlass = document.getElementById('playground-glass');

/* ── All button-wrap elements ───────────────────────────── */
// Wrappers that should render as glass panels
const btnWraps = Array.from(document.querySelectorAll('.lg-btn-wrap'));

/* Combine into glass set (playground + all button wraps) */
const glassElements = [...btnWraps];
if (pgGlass) glassElements.push(pgGlass);

/* ── Defaults for button glass ────────────────────────────
   The ybouane/liquidglass library reads data-config (JSON)
   per-element to override these defaults.
   ─────────────────────────────────────────────────────── */
const buttonDefaults = {
  refraction: 0.55,
  blurAmount: 0,
  chromAberration: 0.04,
  edgeHighlight: 0.12,
  specular: 0,
  fresnel: 1.2,
  distortion: 0,
  cornerRadius: 14,
  zRadius: 30,
  opacity: 1,
  saturation: 0,
  shadowOpacity: 0.25,
  shadowSpread: 8,
  shadowOffsetY: 1,
  button: true,     // enables hover / press states
};

/* Primary CTA button gets the most liquid, shiny config */
const primaryConfig = {
  refraction: 0.75,
  blurAmount: 0,
  chromAberration: 0.07,
  edgeHighlight: 0.22,
  specular: 1.6,
  fresnel: 1.8,
  distortion: 0.015,
  cornerRadius: 18,
  zRadius: 50,
  opacity: 1,
  saturation: 0,
  shadowOpacity: 0.45,
  shadowSpread: 16,
  shadowOffsetY: 2,
  button: true,
};

/* Pill style (nav bar, release badge) */
const pillConfig = {
  ...buttonDefaults,
  refraction: 0.5,
  cornerRadius: 100,
  zRadius: 25,
  edgeHighlight: 0.08,
  button: true,
};

/* Feature card — frosted, not interactive-press */
const cardConfig = {
  ...buttonDefaults,
  refraction: 0.42,
  edgeHighlight: 0.07,
  cornerRadius: 18,
  zRadius: 22,
  button: false,
};

/* Playground glass — user-controlled, most extreme */
const playgroundConfig = {
  refraction: 0.69,
  blurAmount: 0.5,
  chromAberration: 0.05,
  edgeHighlight: 0.3,
  specular: 1.2,
  fresnel: 1.2,
  distortion: 0,
  cornerRadius: 65,
  zRadius: 50,
  opacity: 1,
  saturation: 0,
  shadowOpacity: 0.4,
  shadowSpread: 20,
  shadowOffsetY: 2,
  button: false,
  floating: true,
};

/* ── Apply configs as data-config attributes ────────────── */
function setConfig(el, cfg) {
  el.dataset.config = JSON.stringify(cfg);
}

/* Hero primary download button */
const heroWrap = document.getElementById('hero-primary-wrap');
if (heroWrap) setConfig(heroWrap, primaryConfig);

/* Nav download button */
const navWrap = document.getElementById('nav-download-wrap');
if (navWrap) setConfig(navWrap, primaryConfig);

/* Release pill */
const heroPillWrap = document.getElementById('hero-pill-wrap');
if (heroPillWrap) setConfig(heroPillWrap, pillConfig);

/* Hero secondary button */
const heroSecWrap = document.getElementById('hero-secondary-wrap');
if (heroSecWrap) setConfig(heroSecWrap, buttonDefaults);

/* Phone glass card overlay */
const phoneGlassWrap = document.getElementById('phone-glass-wrap');
if (phoneGlassWrap) setConfig(phoneGlassWrap, { ...buttonDefaults, cornerRadius: 14, button: false });

/* Feature cards */
for (const id of ['feat-1','feat-2','feat-3','feat-4','feat-5','feat-6']) {
  const el = document.getElementById(id);
  if (el) setConfig(el, cardConfig);
}

/* All-releases button */
const allRelWrap = document.getElementById('all-releases-wrap');
if (allRelWrap) setConfig(allRelWrap, buttonDefaults);

/* Playground */
if (pgGlass) setConfig(pgGlass, playgroundConfig);

/* ── Init LiquidGlass ───────────────────────────────────── */
let lg = null;
try {
  lg = await LiquidGlass.init({
    root,
    glassElements: document.querySelectorAll('.lg-btn-wrap, #playground-glass'),
    defaults: buttonDefaults,
  });
  console.log('[Mhorpix] LiquidGlass initialized ✓', lg);
} catch (err) {
  console.warn('[Mhorpix] LiquidGlass WebGL unavailable — falling back to CSS glass.', err);
  applyFallbackCSS();
}

/* ── Expose lg instance globally for playground ─────────── */
window._lg = lg;

/* ── PLAYGROUND SLIDERS ─────────────────────────────────── */
if (pgGlass) {
  const sliderMap = {
    'r-refraction': { key: 'refraction', labelId: 'v-refraction' },
    'r-blur':       { key: 'blurAmount', labelId: 'v-blur' },
    'r-chroma':     { key: 'chromAberration', labelId: 'v-chroma' },
    'r-specular':   { key: 'specular', labelId: 'v-specular' },
    'r-edge':       { key: 'edgeHighlight', labelId: 'v-edge' },
    'r-radius':     { key: 'cornerRadius', labelId: 'v-radius' },
  };

  /* Current playground config (mutable) */
  let pgCfg = { ...playgroundConfig };

  for (const [sliderId, { key, labelId }] of Object.entries(sliderMap)) {
    const slider = document.getElementById(sliderId);
    const label  = document.getElementById(labelId);
    if (!slider) continue;

    slider.addEventListener('input', () => {
      const v = parseFloat(slider.value);
      if (label) label.textContent = v;

      /* Update config */
      pgCfg = { ...pgCfg, [key]: v };
      setConfig(pgGlass, pgCfg);

      /* If LiquidGlass is running, mark the element changed
         so it re-reads data-config on the next frame.       */
      if (lg) {
        try {
          // The library observes mutations internally via MutationObserver
          // Writing dataset.config triggers it automatically.
          // We also call markChanged if the method exists.
          if (typeof lg.markChanged === 'function') {
            lg.markChanged(pgGlass);
          }
        } catch (_) { /* silent */ }
      } else {
        /* CSS fallback: update border-radius at minimum */
        if (key === 'cornerRadius') {
          pgGlass.style.borderRadius = v + 'px';
        }
        if (key === 'blurAmount') {
          pgGlass.style.backdropFilter = `blur(${v * 40}px)`;
          pgGlass.style.webkitBackdropFilter = `blur(${v * 40}px)`;
        }
      }
    });
  }
}

/* ── CSS Fallback ───────────────────────────────────────── */
function applyFallbackCSS() {
  /* Give all glass wraps a CSS-based frosted glass look */
  const style = document.createElement('style');
  style.textContent = `
    .lg-btn-wrap {
      background: rgba(255,255,255,0.06);
      backdrop-filter: blur(18px) saturate(160%);
      -webkit-backdrop-filter: blur(18px) saturate(160%);
      border: 1px solid rgba(255,255,255,0.14);
      border-radius: 14px;
      box-shadow: inset 0 1px 0 rgba(255,255,255,0.2), 0 12px 40px rgba(0,0,0,0.5);
    }
    #hero-primary-wrap {
      background: rgba(255,255,255,0.1);
      border-radius: 18px;
      box-shadow: inset 0 1px 0 rgba(255,255,255,0.35), 0 0 0 1px rgba(255,255,255,0.2), 0 20px 60px rgba(0,0,0,0.6);
    }
    #hero-pill-wrap { border-radius: 100px; }
    #phone-glass-wrap { border-radius: 14px; }
    .feat-card .lg-btn-wrap { border-radius: 20px; background: rgba(255,255,255,0.04); }
    .playground-glass {
      background: rgba(255,255,255,0.08);
      backdrop-filter: blur(24px) saturate(180%);
      -webkit-backdrop-filter: blur(24px) saturate(180%);
      border: 1px solid rgba(255,255,255,0.2);
      box-shadow: inset 0 1px 0 rgba(255,255,255,0.3), 0 20px 60px rgba(0,0,0,0.4);
    }
  `;
  document.head.appendChild(style);

  /* Playground slider CSS updates */
  if (pgGlass) {
    pgGlass.classList.add('css-fallback');
  }
}
