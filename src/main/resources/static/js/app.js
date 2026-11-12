// ===== 設定 =====
const MAX_TITLE_LENGTH = 100;
const MAX_URL_LENGTH = 120;
const MAX_SNIPPET_LENGTH = 200;

// ===== 頁面元素 =====
const form = document.getElementById('search-form');
const input = document.getElementById('q');
const statusEl = document.getElementById('status');
const resultsEl = document.getElementById('results');
const profilesEl = document.getElementById('profiles');
const profileDescEl = document.getElementById('profile-desc');
const scorerSelect = document.getElementById('scorer');
const scorerDescEl = document.getElementById('scorer-desc');

// ===== 狀態 =====
// 身份清單由後端 /api/profiles 提供（內容在 profiles.json），這裡不用寫死
let profiles = [];
let currentProfileId = new URLSearchParams(location.search).get('profile');
// 計分算法清單由後端 /api/scorers 提供（所有 @Component 的 ResultScorer），預設的排第一個
let scorers = [];
let currentScorerId = new URLSearchParams(location.search).get('scorer');

// ===== 小工具 =====
function escapeHtml(value) {
  return String(value ?? '').replace(/[&<>"']/g, c => ({
    '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;'
  }[c]));
}

function truncateText(value, maxLength) {
  const text = String(value ?? '');
  return text.length > maxLength ? text.slice(0, maxLength) + '...' : text;
}

function currentProfile() {
  return profiles.find(p => p.id === currentProfileId);
}

function currentScorer() {
  return scorers.find(s => s.id === currentScorerId);
}

/** 把目前的關鍵字和身份寫進網址，重新整理後還能保留 */
function updateUrl() {
  const params = new URLSearchParams();
  const q = input.value.trim();
  if (q) params.set('q', q);
  if (currentProfileId) params.set('profile', currentProfileId);
  if (currentScorerId) params.set('scorer', currentScorerId);
  history.replaceState(null, '', '?' + params.toString());
}

// ===== 身份切換 =====
async function loadProfiles() {
  try {
    const res = await fetch('/api/profiles');
    profiles = await res.json();
  } catch {
    profiles = [];
  }
  // 網址沒指定或指定了不存在的身份 → 用第一個
  if (!currentProfile()) {
    currentProfileId = profiles.length ? profiles[0].id : null;
  }
  renderProfiles();
}

function renderProfiles() {
  profilesEl.innerHTML = profiles.map(p => {
    const active = p.id === currentProfileId;
    return `
      <button type="button" class="profile-chip${active ? ' active' : ''}"
              role="radio" aria-checked="${active}" data-id="${escapeHtml(p.id)}">
        ${escapeHtml(p.name)}
      </button>`;
  }).join('');

  const profile = currentProfile();
  profileDescEl.textContent = profile ? profile.description : '';
}

function onProfileClick(event) {
  const button = event.target.closest('.profile-chip');
  if (!button || button.dataset.id === currentProfileId) return;

  currentProfileId = button.dataset.id;
  renderProfiles();
  updateUrl();

  // 已經有關鍵字的話，用新的身份重新搜尋
  const q = input.value.trim();
  if (q) doSearch(q);
}

// ===== 計分算法切換 =====
async function loadScorers() {
  try {
    const res = await fetch('/api/scorers');
    scorers = await res.json();
  } catch {
    scorers = [];
  }
  // 網址沒指定或指定了不存在的算法 → 用預設（第一個）
  if (!currentScorer()) {
    currentScorerId = scorers.length ? scorers[0].id : null;
  }
  renderScorers();
}

function renderScorers() {
  scorerSelect.innerHTML = scorers.map(s => `
    <option value="${escapeHtml(s.id)}"${s.id === currentScorerId ? ' selected' : ''}>
      ${escapeHtml(s.name)}${s.isDefault ? '（預設）' : ''}
    </option>`).join('');
  const scorer = currentScorer();
  scorerDescEl.textContent = scorer ? scorer.description : '';
}

function onScorerChange() {
  currentScorerId = scorerSelect.value;
  renderScorers();
  updateUrl();

  // 已經有關鍵字的話，用新的算法重新搜尋
  const q = input.value.trim();
  if (q) doSearch(q);
}

// ===== 搜尋 =====
async function doSearch(q) {
  document.body.classList.add('has-results');
  const profile = currentProfile();
  const scorer = currentScorer();
  statusEl.textContent = `以「${profile ? profile.name : '預設'}」身份、「${scorer ? scorer.name : '預設'}」算法搜尋中…（需要讀取網頁，請稍候）`;
  resultsEl.innerHTML = '';

  try {
    const data = await fetchSearch(q);
    statusEl.textContent = buildStatusText(q, data);
    resultsEl.innerHTML = data.results.map(renderResult).join('');
  } catch (err) {
    statusEl.textContent = '搜尋失敗：' + err.message;
  }
}

async function fetchSearch(q) {
  let url = '/api/search?q=' + encodeURIComponent(q);
  if (currentProfileId) url += '&profile=' + encodeURIComponent(currentProfileId);
  if (currentScorerId) url += '&scorer=' + encodeURIComponent(currentScorerId);

  const res = await fetch(url);
  if (!res.ok) {
    const err = await res.json().catch(() => ({}));
    throw new Error(err.error || 'HTTP ' + res.status);
  }
  return res.json();
}

function buildStatusText(q, { results, warnings, searchedQuery }) {
  let text = results.length
    ? `找到 ${results.length} 筆「${q}」的結果`
    : `找不到「${q}」的相關結果`;
  if (searchedQuery && searchedQuery !== q) {
    text += `（實際搜尋：${searchedQuery}）`;
  }
  if (warnings.length) {
    text += `（部分來源失敗：${warnings.join('；')}）`;
  }
  return text;
}

function renderResult(r) {
  return `
    <div class="result">
      <a href="${escapeHtml(r.url)}" title="${escapeHtml(r.title)}" target="_blank" rel="noopener">
        ${escapeHtml(truncateText(r.title, MAX_TITLE_LENGTH))}
      </a>
      <div class="url" title="${escapeHtml(r.url)}">${escapeHtml(truncateText(r.url, MAX_URL_LENGTH))}</div>
      <p>${escapeHtml(truncateText(r.snippet, MAX_SNIPPET_LENGTH))}</p>
      <div class="score">
        score: ${Number(r.score).toFixed(2)} · 來源: ${escapeHtml(r.sources.join(', '))} · 原名次: ${r.rank}
      </div>
    </div>`;
}

function onSubmit(event) {
  event.preventDefault();
  const q = input.value.trim();
  if (!q) return;
  updateUrl();
  doSearch(q);
}

// ===== 啟動 =====
form.addEventListener('submit', onSubmit);
profilesEl.addEventListener('click', onProfileClick);
scorerSelect.addEventListener('change', onScorerChange);

// 先載入身份和計分算法，網址有 ?q=xxx 時再自動搜尋
Promise.all([loadProfiles(), loadScorers()]).then(() => {
  const initialQuery = new URLSearchParams(location.search).get('q');
  if (initialQuery) {
    input.value = initialQuery;
    doSearch(initialQuery);
  }
});
