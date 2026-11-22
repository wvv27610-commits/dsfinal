// ===== 設定 =====
const MAX_TITLE_LENGTH = 100;
const MAX_URL_LENGTH = 120;
const MAX_SNIPPET_LENGTH = 200;
const LANG_STORAGE_KEY = 'goodog.lang';

// ===== 頁面元素 =====
const form = document.getElementById('search-form');
const input = document.getElementById('q');
const statusEl = document.getElementById('status');
const resultsEl = document.getElementById('results');
const profilesEl = document.getElementById('profiles');
const profileDescEl = document.getElementById('profile-desc');
const scorerSelect = document.getElementById('scorer');
const scorerDescEl = document.getElementById('scorer-desc');
const langSelect = document.getElementById('lang');

// ===== 狀態 =====
const params = new URLSearchParams(location.search);
// 語言清單由後端 /api/languages 提供（內容在 languages.json）
let languages = [];
let defaultLang = 'zh-TW';
let currentLang = params.get('lang');
// 介面文字：目前語言的字典，缺的字用預設語言補（內容在 static/i18n/<語言>.json）
let messages = {};
let fallbackMessages = {};
// 身份清單由後端 /api/profiles 提供（內容在 profiles.json 和 profiles.<語言>.json），這裡不用寫死
let profiles = [];
let currentProfileId = params.get('profile');
// 計分算法清單由後端 /api/scorers 提供（所有 @Component 的 ResultScorer），預設的排第一個
let scorers = [];
let currentScorerId = params.get('scorer');

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

/** 取得介面文字，{name} 會換成 vars.name；目前語言沒有就用預設語言，再沒有就顯示 key */
function t(key, vars = {}) {
  const template = messages[key] ?? fallbackMessages[key] ?? key;
  return String(template).replace(/\{(\w+)\}/g, (_, name) => vars[name] ?? '');
}

function currentProfile() {
  return profiles.find(p => p.id === currentProfileId);
}

function currentScorer() {
  return scorers.find(s => s.id === currentScorerId);
}

/** 計分算法的名稱和說明：i18n 檔裡有翻譯就用翻譯，沒有就用後端（Java）寫的 */
function scorerText(scorer) {
  const translated = (messages.scorers || {})[scorer.id] || {};
  return {
    name: translated.name || scorer.name,
    description: translated.description || scorer.description
  };
}

/** 把目前的關鍵字、身份、算法、語言寫進網址，重新整理後還能保留 */
function updateUrl() {
  const next = new URLSearchParams();
  const q = input.value.trim();
  if (q) next.set('q', q);
  if (currentProfileId) next.set('profile', currentProfileId);
  if (currentScorerId) next.set('scorer', currentScorerId);
  if (currentLang) next.set('lang', currentLang);
  history.replaceState(null, '', '?' + next.toString());
}

// ===== 語言切換 =====
async function loadLanguages() {
  try {
    const res = await fetch('/api/languages');
    const data = await res.json();
    languages = data.languages;
    defaultLang = data.default;
  } catch {
    languages = [];
  }
  currentLang = pickLanguage();
  renderLanguages();
}

/** 網址指定的 → 上次選的 → 瀏覽器語言 → 預設語言 */
function pickLanguage() {
  let saved = null;
  try { saved = localStorage.getItem(LANG_STORAGE_KEY); } catch { /* 無痕模式等情況讀不到 */ }
  const candidates = [currentLang, saved, ...(navigator.languages || [navigator.language])];
  for (const code of candidates) {
    const match = matchLanguage(code);
    if (match) return match;
  }
  return defaultLang;
}

/** 'en-US' → 'en'；'zh-Hant-TW'、'zh' → 'zh-TW'（有的話） */
function matchLanguage(code) {
  if (!code) return null;
  const wanted = code.toLowerCase();
  const exact = languages.find(l => l.code.toLowerCase() === wanted);
  if (exact) return exact.code;
  const primary = wanted.split(/[-_]/)[0];
  const samePrimary = languages.find(l => l.code.toLowerCase().split(/[-_]/)[0] === primary);
  return samePrimary ? samePrimary.code : null;
}

function renderLanguages() {
  langSelect.innerHTML = languages.map(l => `
    <option value="${escapeHtml(l.code)}"${l.code === currentLang ? ' selected' : ''}>
      ${escapeHtml(l.label)}
    </option>`).join('');
}

async function loadMessages() {
  fallbackMessages = await fetchJson(`/i18n/${defaultLang}.json`);
  messages = currentLang === defaultLang ? fallbackMessages : await fetchJson(`/i18n/${currentLang}.json`);
  applyStaticTexts();
}

async function fetchJson(url) {
  try {
    const res = await fetch(url);
    return res.ok ? await res.json() : {};
  } catch {
    return {};
  }
}

/** 把 HTML 裡標了 data-i18n 的文字換成目前語言 */
function applyStaticTexts() {
  document.documentElement.lang = currentLang === 'zh-TW' ? 'zh-Hant' : currentLang;
  document.title = t('pageTitle');
  document.querySelectorAll('[data-i18n]').forEach(el => {
    el.textContent = t(el.dataset.i18n);
  });
  document.querySelectorAll('[data-i18n-placeholder]').forEach(el => {
    el.placeholder = t(el.dataset.i18nPlaceholder);
  });
  document.querySelectorAll('[data-i18n-aria-label]').forEach(el => {
    el.setAttribute('aria-label', t(el.dataset.i18nAriaLabel));
  });
}

async function onLanguageChange() {
  currentLang = langSelect.value;
  try { localStorage.setItem(LANG_STORAGE_KEY, currentLang); } catch { /* 存不了就算了 */ }
  await Promise.all([loadMessages(), loadProfiles()]);
  renderProfiles();   // 介面文字載完後再畫一次（「尚未翻譯」標記要用新語言）
  renderScorers();
  updateUrl();

  // 已經有關鍵字的話，用新的語言重新搜尋（搜尋語言和身份關鍵字都會換）
  const q = input.value.trim();
  if (q) doSearch(q);
}

// ===== 身份切換 =====
async function loadProfiles() {
  profiles = await fetchJson('/api/profiles?lang=' + encodeURIComponent(currentLang));
  if (!Array.isArray(profiles)) profiles = [];
  // 網址沒指定或指定了不存在的身份 → 用第一個
  if (!currentProfile()) {
    currentProfileId = profiles.length ? profiles[0].id : null;
  }
  renderProfiles();
}

function renderProfiles() {
  profilesEl.innerHTML = profiles.map(p => {
    const active = p.id === currentProfileId;
    const marker = p.translated ? '' : `<span class="untranslated">${escapeHtml(t('notTranslated'))}</span>`;
    return `
      <button type="button" class="profile-chip${active ? ' active' : ''}"
              role="radio" aria-checked="${active}" data-id="${escapeHtml(p.id)}">
        ${escapeHtml(p.name)}${marker}
      </button>`;
  }).join('');

  const profile = currentProfile();
  profileDescEl.textContent = profile ? profile.description : '';
}

function onProfileClick(event) {
  const button = event.target.closest('.profile-chip');
  if (!button || button.dataset.id === currentProfileId) return;

  currentProfileId = button.dataset.id;
  // 身份在 profiles.json 有指定計分算法的話，切換身份時一起換
  const profileScorer = currentProfile()?.scorer;
  if (profileScorer && scorers.some(s => s.id === profileScorer)) {
    currentScorerId = profileScorer;
  }
  renderProfiles();
  renderScorers();
  updateUrl();

  // 已經有關鍵字的話，用新的身份重新搜尋
  const q = input.value.trim();
  if (q) doSearch(q);
}

// ===== 計分算法切換 =====
async function loadScorers() {
  scorers = await fetchJson('/api/scorers');
  if (!Array.isArray(scorers)) scorers = [];
}

/** 決定一開始用哪個算法：網址指定的 → 身份指定的 → 預設（第一個） */
function pickInitialScorer() {
  if (currentScorer()) return;
  const profileScorer = currentProfile()?.scorer;
  if (profileScorer && scorers.some(s => s.id === profileScorer)) {
    currentScorerId = profileScorer;
  } else {
    currentScorerId = scorers.length ? scorers[0].id : null;
  }
}

function renderScorers() {
  const profileScorer = currentProfile()?.scorer;
  scorerSelect.innerHTML = scorers.map(s => {
    let tag = '';
    if (s.id === profileScorer) tag = t('profileScorerTag');
    else if (s.isDefault) tag = t('defaultTag');
    return `
      <option value="${escapeHtml(s.id)}"${s.id === currentScorerId ? ' selected' : ''}>
        ${escapeHtml(scorerText(s).name)}${escapeHtml(tag)}
      </option>`;
  }).join('');
  const scorer = currentScorer();
  scorerDescEl.textContent = scorer ? scorerText(scorer).description : '';
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
  statusEl.textContent = t('searching', {
    profile: profile ? profile.name : t('defaultName'),
    scorer: scorer ? scorerText(scorer).name : t('defaultName')
  });
  resultsEl.innerHTML = '';

  try {
    const data = await fetchSearch(q);
    statusEl.textContent = buildStatusText(q, data);
    resultsEl.innerHTML = data.results.map(renderResult).join('');
  } catch (err) {
    statusEl.textContent = t('searchFailed', { message: err.message });
  }
}

async function fetchSearch(q) {
  let url = '/api/search?q=' + encodeURIComponent(q);
  if (currentProfileId) url += '&profile=' + encodeURIComponent(currentProfileId);
  if (currentScorerId) url += '&scorer=' + encodeURIComponent(currentScorerId);
  if (currentLang) url += '&lang=' + encodeURIComponent(currentLang);

  const res = await fetch(url);
  if (!res.ok) {
    const err = await res.json().catch(() => ({}));
    throw new Error(err.error || 'HTTP ' + res.status);
  }
  return res.json();
}

function buildStatusText(q, { results, warnings, searchedQuery }) {
  let text = results.length
    ? t('found', { count: results.length, q })
    : t('notFound', { q });
  if (searchedQuery && searchedQuery !== q) {
    text += t('actualQuery', { query: searchedQuery });
  }
  if (warnings.length) {
    text += t('partialFailure', { warnings: warnings.join('；') });
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
        score: ${Number(r.score).toFixed(2)} · ${escapeHtml(t('resultSources'))}: ${escapeHtml(r.sources.join(', '))} · ${escapeHtml(t('resultRank'))}: ${r.rank}
      </div>
      ${renderLinkTree(r.linkTree)}
    </div>`;
}

// ===== 連結樹 =====
const MAX_LINK_TEXT_LENGTH = 60;
const MAX_KEYWORDS_SHOWN = 5;

/** 每筆結果下方可展開的連結樹（計分算法沒有建樹時不顯示） */
function renderLinkTree(tree) {
  if (!tree) return '';
  if (!tree.linkCount) {
    return `<div class="link-tree-empty">${escapeHtml(t('treeEmpty'))}</div>`;
  }
  const summary = t('treeSummary', {
    links: tree.linkCount,
    related: tree.relatedCount,
    ratio: (tree.relatedRatio * 100).toFixed(1)
  });
  const rootKeywords = formatKeywordCounts(tree.root.keywordCounts);
  return `
    <details class="link-tree">
      <summary>${escapeHtml(summary)}</summary>
      ${rootKeywords ? `<div class="tree-root-keywords">${escapeHtml(t('treeRootKeywords', { list: rootKeywords }))}</div>` : ''}
      <div class="tree-hint">${escapeHtml(t('treeHint'))}</div>
      ${renderTreeChildren(tree.root.children)}
    </details>`;
}

/** 遞迴畫出子節點（每一層是一個 <ul>） */
function renderTreeChildren(children) {
  if (!children || !children.length) return '';
  return `<ul>${children.map(renderTreeNode).join('')}</ul>`;
}

function renderTreeNode(node) {
  const label = node.text && node.text.trim() ? node.text.trim() : node.url;
  const tags = [];
  if (node.related) {
    tags.push(`<span class="tree-tag related">${escapeHtml(t('treeRelated'))}: ${escapeHtml(node.matchedKeywords.join(', '))}</span>`);
  }
  const counts = formatKeywordCounts(node.keywordCounts);
  if (node.fetched && counts) {
    tags.push(`<span class="tree-tag counts">${escapeHtml(counts)}</span>`);
  }
  return `
    <li class="depth-${node.depth}${node.related ? ' is-related' : ''}">
      <a href="${escapeHtml(node.url)}" title="${escapeHtml(node.url)}" target="_blank" rel="noopener">${escapeHtml(truncateText(label, MAX_LINK_TEXT_LENGTH))}</a>
      ${tags.join(' ')}
      ${renderTreeChildren(node.children)}
    </li>`;
}

/** {"柴犬": 3, "狗": 12} → "狗 ×12, 柴犬 ×3"（次數多的在前，最多顯示 5 個） */
function formatKeywordCounts(counts) {
  return Object.entries(counts || {})
    .sort((a, b) => b[1] - a[1])
    .slice(0, MAX_KEYWORDS_SHOWN)
    .map(([word, n]) => `${word} ×${n}`)
    .join(', ');
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
langSelect.addEventListener('change', onLanguageChange);

// 先決定語言，再載入介面文字、身份和計分算法；網址有 ?q=xxx 時再自動搜尋
async function start() {
  await loadLanguages();
  await Promise.all([loadMessages(), loadProfiles(), loadScorers()]);
  renderProfiles();
  pickInitialScorer();
  renderScorers();

  const initialQuery = params.get('q');
  if (initialQuery) {
    input.value = initialQuery;
    doSearch(initialQuery);
  }
}
start();
