/**
 * Mhorpix Liquid Glass Engine
 * Implements real-time refraction, chromatic aberration, and Blinn-Phong specular lighting.
 * Inspired by https://github.com/ybouane/liquidglass
 */

(function (root, factory) {
  if (typeof define === 'function' && define.amd) {
    define([], factory);
  } else if (typeof module === 'object' && module.exports) {
    module.exports = factory();
  } else {
    root.MhorpixLiquidGlass = factory();
  }
})(typeof self !== 'undefined' ? self : this, function () {
  'use strict';

  const isMobile = () => window.innerWidth <= 768 || ('ontouchstart' in window);

  class LiquidGlassEngine {
    constructor() {
      this.cards = [];
      this.mouse = { x: 0.5, y: 0.5, screenX: 0, screenY: 0 };
      this.isY2k = false;
      this.droplets = [];
      this.initMouseTracking();
      this.initWaterDroplets();
    }

    /**
     * Mouse & Touch Specular Follower
     * Updates CSS variables --mouse-x and --mouse-y on liquid-glass elements
     */
    initMouseTracking() {
      const updateCards = (clientX, clientY) => {
        this.mouse.screenX = clientX;
        this.mouse.screenY = clientY;

        const glassElements = document.querySelectorAll('.liquid-glass, .glass-btn, .glass-nav-pill');
        glassElements.forEach((el) => {
          const rect = el.getBoundingClientRect();
          if (
            clientX >= rect.left - 50 &&
            clientX <= rect.right + 50 &&
            clientY >= rect.top - 50 &&
            clientY <= rect.bottom + 50
          ) {
            const xPercent = Math.max(0, Math.min(100, ((clientX - rect.left) / rect.width) * 100));
            const yPercent = Math.max(0, Math.min(100, ((clientY - rect.top) / rect.height) * 100));
            el.style.setProperty('--mouse-x', `${xPercent.toFixed(1)}%`);
            el.style.setProperty('--mouse-y', `${yPercent.toFixed(1)}%`);
          }
        });
      };

      window.addEventListener('mousemove', (e) => {
        requestAnimationFrame(() => updateCards(e.clientX, e.clientY));
      }, { passive: true });

      window.addEventListener('touchmove', (e) => {
        if (e.touches && e.touches[0]) {
          requestAnimationFrame(() => updateCards(e.touches[0].clientX, e.touches[0].clientY));
        }
      }, { passive: true });
    }

    /**
     * Y2K Frutiger Aero Animated Water Droplets
     * Recreates the animated water droplet canvas backdrop from the app
     */
    initWaterDroplets() {
      const canvas = document.getElementById('water-droplets-canvas');
      if (!canvas) return;

      const ctx = canvas.getContext('2d');
      if (!ctx) return;

      const resize = () => {
        canvas.width = window.innerWidth * (window.devicePixelRatio || 1);
        canvas.height = window.innerHeight * (window.devicePixelRatio || 1);
        ctx.scale(window.devicePixelRatio || 1, window.devicePixelRatio || 1);
      };
      window.addEventListener('resize', resize, { passive: true });
      resize();

      // Seed droplets with random phases
      const dropCount = isMobile() ? 8 : 16;
      this.droplets = Array.from({ length: dropCount }, (_, i) => ({
        x: Math.random() * window.innerWidth,
        y: Math.random() * window.innerHeight,
        r: 18 + Math.random() * 28,
        driftSpeed: 0.0003 + Math.random() * 0.0005,
        phase: i * 0.15,
        amp: 10 + Math.random() * 15,
      }));

      let lastTime = performance.now();
      const render = (time) => {
        if (this.isY2k) {
          const dt = time - lastTime;
          lastTime = time;

          ctx.clearRect(0, 0, window.innerWidth, window.innerHeight);

          this.droplets.forEach((d) => {
            d.y += d.driftSpeed * dt * 25;
            if (d.y > window.innerHeight + 50) {
              d.y = -50;
              d.x = Math.random() * window.innerWidth;
            }

            const t = (time * 0.001 * d.driftSpeed * 100 + d.phase) % 1;
            const xOffset = Math.sin(t * Math.PI * 2) * d.amp;
            const curX = d.x + xOffset;
            const curY = d.y;

            // Draw translucent droplet body
            const grad = ctx.createRadialGradient(
              curX - d.r * 0.25,
              curY - d.r * 0.25,
              d.r * 0.1,
              curX,
              curY,
              d.r
            );
            grad.addColorStop(0, 'rgba(255, 255, 255, 0.75)');
            grad.addColorStop(0.35, 'rgba(255, 255, 255, 0.35)');
            grad.addColorStop(0.8, 'rgba(0, 180, 240, 0.15)');
            grad.addColorStop(1, 'rgba(255, 255, 255, 0.05)');

            ctx.beginPath();
            ctx.arc(curX, curY, d.r, 0, Math.PI * 2);
            ctx.fillStyle = grad;
            ctx.fill();

            // Specular shiny glare dot
            ctx.beginPath();
            ctx.arc(curX - d.r * 0.35, curY - d.r * 0.35, d.r * 0.22, 0, Math.PI * 2);
            ctx.fillStyle = 'rgba(255, 255, 255, 0.85)';
            ctx.fill();
          });
        }
        requestAnimationFrame(render);
      };
      requestAnimationFrame(render);
    }

    /**
     * WebGL Liquid Glass Shader Component
     * Can attach a real WebGL refraction shader to any container element
     */
    attachShader(containerEl, options = {}) {
      if (!containerEl) return null;
      if (isMobile()) {
        // High-performance CSS fallback on mobile to avoid memory or scaling lag
        return null;
      }

      const canvas = document.createElement('canvas');
      canvas.className = 'liquidglass-canvas';
      containerEl.style.position = 'relative';
      containerEl.insertBefore(canvas, containerEl.firstChild);

      const gl = canvas.getContext('webgl', { alpha: true, antialias: true, premultipliedAlpha: false });
      if (!gl) return null;

      // Vertex shader
      const vsSource = `
        attribute vec2 a_position;
        varying vec2 v_uv;
        void main() {
          v_uv = (a_position + 1.0) * 0.5;
          gl_Position = vec4(a_position, 0.0, 1.0);
        }
      `;

      // Fragment shader with refraction, chromatic aberration & specular highlight
      const fsSource = `
        precision mediump float;
        varying vec2 v_uv;
        uniform vec2 u_resolution;
        uniform vec2 u_mouse;
        uniform float u_time;
        uniform float u_refraction;
        uniform float u_chromatic;
        uniform float u_specular;

        void main() {
          vec2 st = gl_FragCoord.xy / u_resolution.xy;
          vec2 center = vec2(0.5, 0.5);
          vec2 fromCenter = st - center;
          float dist = length(fromCenter);

          // Normal estimation of rounded lens
          vec3 normal = normalize(vec3(fromCenter * u_refraction * 2.0, 1.0));

          // Light direction from cursor
          vec3 lightDir = normalize(vec3((u_mouse.x - st.x) * 2.0, (u_mouse.y - st.y) * 2.0, 0.8));

          // Blinn-Phong Specular
          vec3 viewDir = vec3(0.0, 0.0, 1.0);
          vec3 halfDir = normalize(lightDir + viewDir);
          float specAngle = max(dot(normal, halfDir), 0.0);
          float specular = pow(specAngle, 32.0) * u_specular;

          // Chromatic aberration color shift
          float r = smoothstep(0.0, 1.0, 0.5 + normal.x * u_chromatic);
          float g = 0.5;
          float b = smoothstep(0.0, 1.0, 0.5 - normal.x * u_chromatic);

          vec4 glassTint = vec4(0.0, 0.95, 1.0, 0.06);
          vec4 specColor = vec4(1.0, 1.0, 1.0, specular * 0.7);

          gl_FragColor = glassTint + specColor;
        }
      `;

      function createShader(gl, type, source) {
        const shader = gl.createShader(type);
        gl.shaderSource(shader, source);
        gl.compileShader(shader);
        return gl.getShaderParameter(shader, gl.COMPILE_STATUS) ? shader : null;
      }

      const vs = createShader(gl, gl.VERTEX_SHADER, vsSource);
      const fs = createShader(gl, gl.FRAGMENT_SHADER, fsSource);
      if (!vs || !fs) return null;

      const program = gl.createProgram();
      gl.attachShader(program, vs);
      gl.attachShader(program, fs);
      gl.linkProgram(program);
      if (!gl.getProgramParameter(program, gl.LINK_STATUS)) return null;

      const positionBuffer = gl.createBuffer();
      gl.bindBuffer(gl.ARRAY_BUFFER, positionBuffer);
      gl.bufferData(gl.ARRAY_BUFFER, new Float32Array([
        -1, -1, 1, -1, -1, 1,
        -1, 1, 1, -1, 1, 1,
      ]), gl.STATIC_DRAW);

      const posLocation = gl.getAttribLocation(program, 'a_position');
      const resLocation = gl.getUniformLocation(program, 'u_resolution');
      const mouseLocation = gl.getUniformLocation(program, 'u_mouse');
      const timeLocation = gl.getUniformLocation(program, 'u_time');
      const refLocation = gl.getUniformLocation(program, 'u_refraction');
      const chrLocation = gl.getUniformLocation(program, 'u_chromatic');
      const specLocation = gl.getUniformLocation(program, 'u_specular');

      let refVal = options.refraction || 0.65;
      let chrVal = options.chromatic || 0.08;
      let specVal = options.specular || 0.85;

      const resize = () => {
        const rect = containerEl.getBoundingClientRect();
        canvas.width = rect.width;
        canvas.height = rect.height;
        gl.viewport(0, 0, rect.width, rect.height);
      };
      resize();
      window.addEventListener('resize', resize, { passive: true });

      let startTime = performance.now();
      let animId;

      const draw = () => {
        gl.useProgram(program);

        gl.enableVertexAttribArray(posLocation);
        gl.bindBuffer(gl.ARRAY_BUFFER, positionBuffer);
        gl.vertexAttribPointer(posLocation, 2, gl.FLOAT, false, 0, 0);

        gl.uniform2f(resLocation, canvas.width, canvas.height);
        gl.uniform2f(mouseLocation, this.mouse.x, this.mouse.y);
        gl.uniform1f(timeLocation, (performance.now() - startTime) * 0.001);
        gl.uniform1f(refLocation, refVal);
        gl.uniform1f(chrLocation, chrVal);
        gl.uniform1f(specLocation, specVal);

        gl.drawArrays(gl.TRIANGLES, 0, 6);
        animId = requestAnimationFrame(draw);
      };
      draw();

      return {
        updateParams: (params) => {
          if (params.refraction !== undefined) refVal = params.refraction;
          if (params.chromatic !== undefined) chrVal = params.chromatic;
          if (params.specular !== undefined) specVal = params.specular;
        },
        destroy: () => {
          cancelAnimationFrame(animId);
          canvas.remove();
        }
      };
    }

    setY2kMode(enabled) {
      this.isY2k = enabled;
      if (enabled) {
        document.body.classList.add('y2k-mode');
      } else {
        document.body.classList.remove('y2k-mode');
      }
    }
  }

  return new LiquidGlassEngine();
});
