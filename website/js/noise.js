/**
 * noise.js — generates a subtle film-grain canvas texture
 * for the background, giving the pure-black surface texture.
 */
(function () {
  const canvas = document.getElementById('bg-noise');
  if (!canvas) return;

  function drawNoise() {
    const W = canvas.width  = window.innerWidth;
    const H = canvas.height = window.innerHeight;
    const ctx = canvas.getContext('2d');
    const img = ctx.createImageData(W, H);
    const data = img.data;
    for (let i = 0; i < data.length; i += 4) {
      const v = (Math.random() * 255) | 0;
      data[i]     = v;
      data[i + 1] = v;
      data[i + 2] = v;
      data[i + 3] = 255;
    }
    ctx.putImageData(img, 0, 0);
  }

  drawNoise();

  // Animate grain at ~12fps for a film-grain feel
  let last = 0;
  function tick(ts) {
    if (ts - last > 80) { drawNoise(); last = ts; }
    requestAnimationFrame(tick);
  }
  requestAnimationFrame(tick);

  window.addEventListener('resize', drawNoise, { passive: true });
})();
