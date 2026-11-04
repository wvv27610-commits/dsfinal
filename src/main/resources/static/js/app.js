const form = document.getElementById('search-form');
const input = document.getElementById('q');
const statusEl = document.getElementById('status');
const resultsEl = document.getElementById('results');
const profilesEl = document.getElementById('profiles');
const profileDescEl = document.getElementById('profile-desc');

// 身份清單由後端 /api/profiles 提供（內容在 profiles.json），這裡不用寫死
let profiles = [];
let currentProfile = new URLSearchParams(location.search).get('profile');

function escapeHtml(s) {
  return String(s ?? '').replace(/[&<>"']/g, c => ({
    '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;'
  }[c]));
}

function updateUrl() {
  const params = new URLSearchParams();
  const q = input.value.trim();
  if (q) params.set('q', q);
  if (currentProfile) params.set('profile', currentProfile);
  history.replaceState(null, '', '?' + params.toString());
}

function renderProfiles() {
  profilesEl.innerHTML = profiles.map(p => `
    <button type="button" class="profile-chip${p.id === currentProfile ? ' active' : ''}"
            role="radio" aria-checked="${p.id === currentProfile}" data-id="${escapeHtml(p.id)}">
      ${escapeHtml(p.name)}
    </button>`).join('');
  const p = profiles.find(x => x.id === currentProfile);
  profileDescEl.textContent = p ? p.description : '';
  // if (p && (p.queryPrefix || p.querySuffix)) {
  //   profileDescEl.textContent += `　·　搜尋時自動加上：${[p.queryPrefix, '〔你的關鍵字〕', p.querySuffix].filter(Boolean).join(' ')}`;
  // }
}

async function loadProfiles() {
  try {
    const res = await fetch('/api/profiles');
    profiles = await res.json();
  } catch {
    profiles = [];
  }
  if (!profiles.some(p => p.id === currentProfile)) {
    currentProfile = profiles.length ? profiles[0].id : null;
  }
  renderProfiles();
}

profilesEl.addEventListener('click', e => {
  const btn = e.target.closest('.profile-chip');
  if (!btn || btn.dataset.id === currentProfile) return;
  currentProfile = btn.dataset.id;
  renderProfiles();
  updateUrl();
  const q = input.value.trim();
  if (q) doSearch(q);   // 切換身份時，用新的權重重新搜尋
});

async function doSearch(q) {
  document.body.classList.add('has-results');
  const p = profiles.find(x => x.id === currentProfile);
  statusEl.textContent = `以「${p ? p.name : '預設'}」身份搜尋中…（需要讀取網頁，請稍候）`;
  resultsEl.innerHTML = '';
  try {
    let url = '/api/search?q=' + encodeURIComponent(q);
    if (currentProfile) url += '&profile=' + encodeURIComponent(currentProfile);
    const res = await fetch(url);
    if (!res.ok) {
      const err = await res.json().catch(() => ({}));
      throw new Error(err.error || 'HTTP ' + res.status);
    }
    const { results: data, warnings, searchedQuery } = await res.json();
    statusEl.textContent = data.length ? `找到 ${data.length} 筆「${q}」的結果` : `找不到「${q}」的相關結果`;
    if (searchedQuery && searchedQuery !== q) statusEl.textContent += `（實際搜尋：${searchedQuery}）`;
    if (warnings.length) statusEl.textContent += `（部分來源失敗：${warnings.join('；')}）`;
    resultsEl.innerHTML = data.map(r => `
      <div class="result">
        <a href="${escapeHtml(r.url)}" target="_blank" rel="noopener">${escapeHtml(r.title)}</a>
        <div class="url">${escapeHtml(r.url)}</div>
        <p>${escapeHtml(r.snippet)}</p>
        <div class="score">score: ${Number(r.score).toFixed(2)} · 來源: ${escapeHtml(r.sources.join(', '))} · 原名次: ${r.rank}</div>
      </div>`).join('');
  } catch (err) {
    statusEl.textContent = '搜尋失敗：' + err.message;
  }
}

form.addEventListener('submit', e => {
  e.preventDefault();
  const q = input.value.trim();
  if (!q) return;
  updateUrl();
  doSearch(q);
});

// 先載入身份，網址有 ?q=xxx 時再自動搜尋
loadProfiles().then(() => {
  const initial = new URLSearchParams(location.search).get('q');
  if (initial) {
    input.value = initial;
    doSearch(initial);
  }
});
