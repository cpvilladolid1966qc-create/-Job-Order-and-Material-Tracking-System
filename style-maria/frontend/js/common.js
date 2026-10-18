// ===== Style Maria — shared JS =====
const API = (window.SM_API_BASE || 'http://localhost:8080') + '/api';
const UPLOAD_BASE = (window.SM_API_BASE || 'http://localhost:8080');

function getUser() {
  try { return JSON.parse(localStorage.getItem('sm_user') || 'null'); }
  catch { return null; }
}
function setUser(u) { localStorage.setItem('sm_user', JSON.stringify(u)); }
function clearUser() { localStorage.removeItem('sm_user'); }

async function api(path, options = {}) {
  const headers = Object.assign(
    { 'Content-Type': 'application/json' },
    options.headers || {}
  );
  const u = getUser();
  if (u && u.token) headers['Authorization'] = 'Bearer ' + u.token;

  const res = await fetch(API + path, {
    method: options.method || 'GET',
    headers,
    body: options.body ? JSON.stringify(options.body) : undefined
  });
  const text = await res.text();
  const data = text ? JSON.parse(text) : null;
  if (!res.ok) throw new Error((data && data.error) || ('Request failed: ' + res.status));
  return data;
}

async function apiUpload(file) {
  const u = getUser();
  const fd = new FormData();
  fd.append('file', file);
  const res = await fetch(API + '/files/upload', {
    method: 'POST',
    headers: u && u.token ? { 'Authorization': 'Bearer ' + u.token } : {},
    body: fd
  });
  if (!res.ok) throw new Error('Upload failed');
  return res.json();
}

function requireAuth(redirect = 'index.html') {
  const u = getUser();
  if (!u || !u.token) {
    window.location.href = redirect;
    return null;
  }
  return u;
}

function logout() {
  api('/auth/logout', { method: 'POST' }).catch(() => {});
  clearUser();
  window.location.href = 'index.html';
}

function peso(n) {
  if (n == null) return '₱0.00';
  const num = typeof n === 'number' ? n : parseFloat(n);
  return '₱' + num.toLocaleString('en-PH', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
}

// Build header consistently across pages
function renderHeader(activePage) {
  const user = getUser();
  const nav = user && user.role === 'ADMIN'
    ? [{ h: 'admin.html', t: 'Admin', k: 'admin' }]
    : [
        { h: 'order.html', t: 'Order', k: 'order' },
        { h: 'track.html', t: 'Track Order', k: 'track' },
        { h: 'cart.html', t: 'Cart', k: 'cart' }
      ];
  const navHtml = nav.map(n =>
    `<a href="${n.h}" class="${activePage === n.k ? 'active' : ''}" data-testid="nav-${n.k}">${n.t}</a>`
  ).join('');
  const initials = user ? user.initials : '';
  const avatarHtml = user
    ? `<div class="sm-avatar" onclick="logout()" title="Logout" data-testid="user-avatar">${initials}</div>`
    : `<a href="index.html" class="link" style="color:#f3eeda;" data-testid="nav-signin">Sign in</a>`;

  const header = document.createElement('div');
  header.className = 'sm-header';
  header.innerHTML = `
    <div class="sm-logo">
      <div class="sm-logo-badge">SM</div>
      <div class="sm-logo-text">STYLE MARIA</div>
    </div>
    <div class="sm-nav">${navHtml}</div>
    ${avatarHtml}
  `;
  document.body.prepend(header);
}

function showAlert(parent, msg, type = 'error') {
  let a = parent.querySelector('.alert');
  if (!a) {
    a = document.createElement('div');
    parent.prepend(a);
  }
  a.className = 'alert alert-' + type;
  a.textContent = msg;
  a.setAttribute('data-testid', 'alert-' + type);
}
