/**
 * Mhorpix Website Main Controller
 * Handles UI interactions, Y2K Frutiger Aero toggle, screenshot tabs, and playground
 */

document.addEventListener('DOMContentLoaded', () => {
  // --- Mobile Navigation Drawer ---
  const mobileToggle = document.getElementById('mobile-menu-btn');
  const mobileDrawer = document.getElementById('mobile-drawer');

  if (mobileToggle && mobileDrawer) {
    mobileToggle.addEventListener('click', () => {
      mobileDrawer.classList.toggle('open');
      const isOpen = mobileDrawer.classList.contains('open');
      mobileToggle.textContent = isOpen ? '✕' : '☰';
    });

    document.querySelectorAll('.mobile-nav-link').forEach(link => {
      link.addEventListener('click', () => {
        mobileDrawer.classList.remove('open');
        mobileToggle.textContent = '☰';
      });
    });
  }

  // --- Y2K Frutiger Aero Mode Toggle ---
  const y2kBtn = document.getElementById('y2k-toggle-btn');
  const y2kMobileBtn = document.getElementById('y2k-mobile-btn');
  let y2kActive = false;

  const toggleY2k = () => {
    y2kActive = !y2kActive;
    if (window.MhorpixLiquidGlass) {
      window.MhorpixLiquidGlass.setY2kMode(y2kActive);
    }
    if (y2kBtn) y2kBtn.classList.toggle('active', y2kActive);
    if (y2kMobileBtn) y2kMobileBtn.classList.toggle('active', y2kActive);
  };

  if (y2kBtn) y2kBtn.addEventListener('click', toggleY2k);
  if (y2kMobileBtn) y2kMobileBtn.addEventListener('click', toggleY2k);

  // --- Screenshot Showcase Tabs ---
  const tabButtons = document.querySelectorAll('.gallery-tab-btn');
  const screenshotItems = document.querySelectorAll('.screenshot-item');

  tabButtons.forEach(btn => {
    btn.addEventListener('click', () => {
      tabButtons.forEach(b => b.classList.remove('active'));
      btn.classList.add('active');

      const filter = btn.getAttribute('data-filter');
      screenshotItems.forEach(item => {
        const category = item.getAttribute('data-category');
        if (filter === 'all' || category === filter) {
          item.style.display = 'flex';
        } else {
          item.style.display = 'none';
        }
      });
    });
  });

  // --- Liquid Glass Interactive Playground ---
  const playgroundTile = document.getElementById('playground-glass-tile');
  const sliderRefraction = document.getElementById('slider-refraction');
  const sliderBlur = document.getElementById('slider-blur');
  const sliderChroma = document.getElementById('slider-chroma');
  const sliderSpecular = document.getElementById('slider-specular');

  const valRefraction = document.getElementById('val-refraction');
  const valBlur = document.getElementById('val-blur');
  const valChroma = document.getElementById('val-chroma');
  const valSpecular = document.getElementById('val-specular');

  let shaderInstance = null;
  if (playgroundTile && window.MhorpixLiquidGlass) {
    shaderInstance = window.MhorpixLiquidGlass.attachShader(playgroundTile, {
      refraction: 0.65,
      chromatic: 0.08,
      specular: 0.85
    });
  }

  const updatePlayground = () => {
    if (!playgroundTile) return;

    const ref = parseFloat(sliderRefraction?.value || '0.65');
    const blur = parseInt(sliderBlur?.value || '24', 10);
    const chr = parseFloat(sliderChroma?.value || '0.08');
    const spec = parseFloat(sliderSpecular?.value || '0.85');

    if (valRefraction) valRefraction.textContent = ref.toFixed(2);
    if (valBlur) valBlur.textContent = `${blur}px`;
    if (valChroma) valChroma.textContent = chr.toFixed(2);
    if (valSpecular) valSpecular.textContent = spec.toFixed(2);

    playgroundTile.style.backdropFilter = `blur(${blur}px) saturate(190%)`;
    playgroundTile.style.webkitBackdropFilter = `blur(${blur}px) saturate(190%)`;

    if (shaderInstance) {
      shaderInstance.updateParams({
        refraction: ref,
        chromatic: chr,
        specular: spec
      });
    }
  };

  [sliderRefraction, sliderBlur, sliderChroma, sliderSpecular].forEach(slider => {
    if (slider) {
      slider.addEventListener('input', updatePlayground);
    }
  });

  // --- Hero Phone Tilt Follow (Desktop Only) ---
  const heroMockup = document.querySelector('.phone-mockup');
  if (heroMockup && window.innerWidth > 768) {
    window.addEventListener('mousemove', (e) => {
      const x = (e.clientX / window.innerWidth - 0.5) * 16;
      const y = (e.clientY / window.innerHeight - 0.5) * -16;
      heroMockup.style.transform = `rotateY(${x.toFixed(1)}deg) rotateX(${y.toFixed(1)}deg)`;
    }, { passive: true });
  }
});
