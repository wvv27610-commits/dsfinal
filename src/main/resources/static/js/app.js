const form = document.getElementById('search-form');
const input = document.getElementById('q');
const statusEl = document.getElementById('status');
const resultsEl = document.getElementById('results');

function escapeHtml(s) {
  return String(s ?? '').replace(/[&<>"']/g, c => ({
    '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;'
  }[c]));
}

async function doSearch(q) {
  document.body.classList.add('has-results');
  statusEl.textContent = '搜尋中…';
  resultsEl.innerHTML = '';
  try {
    const res = await fetch('/api/search?q=' + encodeURIComponent(q));
    if (!res.ok) {
      const err = await res.json().catch(() => ({}));
      throw new Error(err.error || 'HTTP ' + res.status);
    }
    const { results: data, warnings } = await res.json();
    statusEl.textContent = data.length? `找到 ${data.length} 筆「${q}」的結果`: `找不到「${q}」的相關結果`;
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
  history.replaceState(null, '', '?q=' + encodeURIComponent(q));
  doSearch(q);
});

// 網址有 ?q=xxx 時自動搜尋（重新整理也會保留結果）
const initial = new URLSearchParams(location.search).get('q');
if (initial) {
  input.value = initial;
  doSearch(initial);
}
