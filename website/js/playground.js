/**
 * playground.js — Mhorpix website
 * Canvas-based liquid glass live demo.
 * Uses Canvas 2D to render an animated colorful scene behind the glass panel,
 * and updates the panel's CSS backdrop-filter + visual properties via sliders.
 * The glass panel is draggable around the canvas.
 */
(function () {
  'use strict';

  const canvas = document.getElementById('pg-canvas');
  const panel  = document.getElementById('pg-panel');
  if (!canvas || !panel) return;

  const ctx = canvas.getContext('2d');

  /* ── Slider state ─────────────────────────────────────── */
  const state = {
    blur:    24,
    opacity: 0.10,
    radius:  65,
    shine:   0.40,
    distort: 12,
    speed:   1.0,
  };

  /* ── Link sliders ─────────────────────────────────────── */
  function bindSlider(id, key, labelId, decimals = 0) {
    const slider = document.getElementById(id);
    const label  = document.getElementById(labelId);
    if (!slider) return;
    slider.addEventListener('input', () => {
      state[key] = parseFloat(slider.value);
      if (label) label.textContent = state[key].toFixed(decimals);
      applyGlass();
    });
  }

  bindSlider('r-blur',    'blur',    'v-blur',    0);
  bindSlider('r-opacity', 'opacity', 'v-opacity', 2);
  bindSlider('r-radius',  'radius',  'v-radius',  0);
  bindSlider('r-shine',   'shine',   'v-shine',   2);
  bindSlider('r-distort', 'distort', 'v-distort', 0);
  bindSlider('r-speed',   'speed',   'v-speed',   1);

  /* ── Apply glass CSS from state ───────────────────────── */
  function applyGlass() {
    const blur    = state.blur;
    const opacity = state.opacity;
    const radius  = state.radius;
    const shine   = state.shine;

    panel.style.borderRadius         = radius + 'px';
    panel.style.backdropFilter       = `blur(${blur}px) saturate(180%) brightness(1.08)`;
    panel.style.webkitBackdropFilter = `blur(${blur}px) saturate(180%) brightness(1.08)`;
    panel.style.background = `rgba(255,255,255,${opacity})`;

    /* Specular shine via box-shadow inset top edge */
    const shineAlpha = shine;
    panel.style.boxShadow = `
      inset 0 ${2 + shine * 2}px 0 rgba(255,255,255,${shineAlpha}),
      inset 0 -1px 0 rgba(255,255,255,${shine * 0.12}),
      0 20px 60px rgba(0,0,0,0.6),
      0 0 0 1px rgba(255,255,255,${shine * 0.4})
    `;
  }

  applyGlass();

  /* ── Canvas scene animation ───────────────────────────── */
  let W, H;
  let t = 0;

  /* Orbs that drift across the canvas — give glass something vivid to refract */
  const orbs = [
    { x: 0.2, y: 0.3, r: 0.25, dx: 0.0003, dy: 0.0002, col: [255, 255, 255] },
    { x: 0.75, y: 0.6, r: 0.20, dx: -0.0002, dy: 0.0003, col: [220, 220, 220] },
    { x: 0.5,  y: 0.8, r: 0.18, dx: 0.0004, dy: -0.0003, col: [255, 255, 255] },
    { x: 0.1,  y: 0.7, r: 0.15, dx: 0.0002, dy: 0.0004, col: [200, 200, 200] },
    { x: 0.9,  y: 0.2, r: 0.22, dx: -0.0003, dy: -0.0002, col: [240, 240, 240] },
  ];

  /* Lines / grid pattern underneath */
  function drawScene() {
    W = canvas.width  = canvas.offsetWidth;
    H = canvas.height = canvas.offsetHeight;

    /* Deep black base */
    ctx.fillStyle = '#000';
    ctx.fillRect(0, 0, W, H);

    /* Subtle grid lines */
    ctx.strokeStyle = 'rgba(255,255,255,0.04)';
    ctx.lineWidth = 1;
    const step = 48;
    for (let x = 0; x < W; x += step) {
      ctx.beginPath(); ctx.moveTo(x, 0); ctx.lineTo(x, H); ctx.stroke();
    }
    for (let y = 0; y < H; y += step) {
      ctx.beginPath(); ctx.moveTo(0, y); ctx.lineTo(W, y); ctx.stroke();
    }

    /* Floating radial orbs */
    for (const o of orbs) {
      const spd = state.speed;
      o.x += o.dx * spd;
      o.y += o.dy * spd;
      if (o.x < -0.1) o.x = 1.1;
      if (o.x > 1.1)  o.x = -0.1;
      if (o.y < -0.1) o.y = 1.1;
      if (o.y > 1.1)  o.y = -0.1;

      const px = o.x * W;
      const py = o.y * H;
      const pr = o.r * Math.min(W, H);
      const g = ctx.createRadialGradient(px, py, 0, px, py, pr);
      const [r, gr, b] = o.col;
      g.addColorStop(0, `rgba(${r},${gr},${b},0.12)`);
      g.addColorStop(0.5, `rgba(${r},${gr},${b},0.06)`);
      g.addColorStop(1,   `rgba(${r},${gr},${b},0)`);
      ctx.fillStyle = g;
      ctx.beginPath();
      ctx.arc(px, py, pr, 0, Math.PI * 2);
      ctx.fill();
    }

    /* Flowing diagonal light bands */
    const bandCount = 5;
    for (let i = 0; i < bandCount; i++) {
      const spd   = state.speed;
      const phase = (t * 0.0004 * spd) + (i / bandCount) * Math.PI * 2;
      const cx    = W * 0.5 + Math.cos(phase * 0.7) * W * 0.4;
      const cy    = H * 0.5 + Math.sin(phase * 0.5) * H * 0.35;
      const r     = (W * 0.18) + Math.sin(phase) * W * 0.06;
      const g     = ctx.createRadialGradient(cx, cy, 0, cx, cy, r);
      const alpha = 0.04 + Math.sin(phase * 1.3) * 0.02;
      g.addColorStop(0,   `rgba(255,255,255,${alpha})`);
      g.addColorStop(0.6, `rgba(255,255,255,${alpha * 0.3})`);
      g.addColorStop(1,   `rgba(255,255,255,0)`);
      ctx.fillStyle = g;
      ctx.beginPath();
      ctx.arc(cx, cy, r, 0, Math.PI * 2);
      ctx.fill();
    }

    /* Distortion ripple rings around panel */
    const distort = state.distort;
    if (distort > 0 && panelCenterX > 0) {
      const spd = state.speed;
      for (let ring = 0; ring < 3; ring++) {
        const phase  = (t * 0.001 * spd) - ring * 0.8;
        const ringR  = (60 + ring * 40) + Math.sin(phase * 2) * 10;
        const alpha  = Math.max(0, 0.07 - ring * 0.02) * (distort / 40);
        ctx.strokeStyle = `rgba(255,255,255,${alpha})`;
        ctx.lineWidth = 1.5;
        ctx.beginPath();
        ctx.arc(panelCenterX, panelCenterY, ringR, 0, Math.PI * 2);
        ctx.stroke();
      }
    }

    /* Watermark text */
    ctx.font = `900 ${Math.floor(W * 0.07)}px Inter, sans-serif`;
    ctx.fillStyle = 'rgba(255,255,255,0.025)';
    ctx.letterSpacing = '0.14em';
    ctx.textAlign = 'center';
    const phrases = ['MHORPIX', 'LIQUID GLASS', 'HI-RES'];
    const lineH   = H / (phrases.length + 1);
    for (let i = 0; i < phrases.length; i++) {
      ctx.fillText(phrases[i], W / 2, lineH * (i + 1) + 10);
    }
  }

  /* ── Track panel center for ripple effect ─────────────── */
  let panelCenterX = 0, panelCenterY = 0;

  function updatePanelCenter() {
    const area = document.querySelector('.playground-canvas-area');
    if (!area) return;
    const ar   = area.getBoundingClientRect();
    const pr   = panel.getBoundingClientRect();
    panelCenterX = pr.left + pr.width  / 2 - ar.left;
    panelCenterY = pr.top  + pr.height / 2 - ar.top;
  }

  /* ── Drag to move the panel ───────────────────────────── */
  let dragging = false;
  let dragStartX, dragStartY, panelStartLeft, panelStartTop;

  panel.addEventListener('pointerdown', (e) => {
    e.preventDefault();
    dragging = true;
    dragStartX = e.clientX;
    dragStartY = e.clientY;
    panelStartLeft = panel.offsetLeft;
    panelStartTop  = panel.offsetTop;
    panel.setPointerCapture(e.pointerId);
    panel.style.transition = 'none';
    panel.style.cursor = 'grabbing';
  });

  window.addEventListener('pointermove', (e) => {
    if (!dragging) return;
    const dx = e.clientX - dragStartX;
    const dy = e.clientY - dragStartY;
    const area = panel.parentElement;
    const aW   = area.offsetWidth;
    const aH   = area.offsetHeight;
    const pW   = panel.offsetWidth;
    const pH   = panel.offsetHeight;
    const newLeft = Math.max(0, Math.min(aW - pW, panelStartLeft + dx));
    const newTop  = Math.max(0, Math.min(aH - pH, panelStartTop  + dy));
    panel.style.left = newLeft + 'px';
    panel.style.top  = newTop  + 'px';
    panel.style.transform = 'none'; /* override the CSS centering after first drag */
    updatePanelCenter();
  });

  window.addEventListener('pointerup', () => {
    if (!dragging) return;
    dragging = false;
    panel.style.cursor = 'grab';
    panel.style.transition = '';
  });

  /* ── Render loop ──────────────────────────────────────── */
  function tick(ts) {
    t = ts;
    drawScene();
    updatePanelCenter();
    requestAnimationFrame(tick);
  }

  /* Resize handler */
  const ro = new ResizeObserver(() => { drawScene(); });
  const area = document.querySelector('.playground-canvas-area');
  if (area) ro.observe(area);

  requestAnimationFrame(tick);

})();
