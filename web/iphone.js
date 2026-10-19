/* Optional iPhone home-screen installation guidance. Does not modify streaming logic. */
(() => {
  'use strict';
  const ua = navigator.userAgent || '';
  const isIOS = /iPhone|iPad|iPod/.test(ua) || (navigator.platform === 'MacIntel' && navigator.maxTouchPoints > 1);
  const standalone = Boolean(navigator.standalone) || window.matchMedia('(display-mode: standalone)').matches;
  if (!isIOS) return;
  document.body.classList.add('ios-safari');
  if (standalone) { document.body.classList.add('ios-standalone'); return; }
  const bar = document.querySelector('.topline');
  if (!bar) return;
  const trigger = document.createElement('button');
  trigger.type = 'button';
  trigger.className = 'iphone-install-btn';
  trigger.textContent = '＋ Install';
  trigger.setAttribute('aria-label', 'Add Greek One to your iPhone Home Screen');
  bar.appendChild(trigger);
  const dialog = document.createElement('div');
  dialog.className = 'iphone-install-dialog';
  dialog.hidden = true;
  dialog.setAttribute('role', 'dialog');
  dialog.setAttribute('aria-modal', 'true');
  dialog.setAttribute('aria-label', 'Install Greek One on iPhone');
  dialog.innerHTML = '<div class="iphone-install-sheet"><h2>Greek One on your iPhone 🇬🇷</h2><p>Keep Greek One on your Home Screen like an app. No App Store download needed.</p><ol><li>Open this page in <strong>Safari</strong>.</li><li>Tap the <strong>Share</strong> button (square with an upward arrow).</li><li>Choose <strong>Add to Home Screen</strong>, then <strong>Add</strong>.</li></ol><p>Playback availability depends on each broadcaster and your location.</p><button type="button">Got it</button></div>';
  document.body.appendChild(dialog);
  const close = () => { dialog.hidden = true; trigger.focus({ preventScroll: true }); };
  trigger.addEventListener('click', () => { dialog.hidden = false; dialog.querySelector('button').focus({ preventScroll: true }); });
  dialog.querySelector('button').addEventListener('click', close);
  dialog.addEventListener('click', e => { if (e.target === dialog) close(); });
  document.addEventListener('keydown', e => { if (e.key === 'Escape' && !dialog.hidden) close(); });
})();
