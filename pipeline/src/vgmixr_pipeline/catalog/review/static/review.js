// The review page. All state comes from the server (/api/...); every decision is written at once.
"use strict";

const ACCEPT = 0.8;
const REVIEW = 0.5;
const MIN_RATIO = 0.4;
const MAX_RATIO = 3.5;
const TABS = [["review", "Review"], ["unmatched", "Unmatched"], ["matched", "Matched"], ["excluded", "Removed"], ["all", "All"]];

const ui = {
  state: null,
  family: null,
  status: "review",
  rows: [],
  trackId: null,
  detail: null,
  found: [], // search results and pasted videos for the open track
  players: [],
};

const $ = (id) => document.getElementById(id);

function esc(text) {
  return String(text ?? "").replace(/[&<>"']/g, (c) => `&#${c.charCodeAt(0)};`);
}

function minutes(ms) {
  if (ms == null) return "?";
  const s = Math.round(ms / 1000);
  return `${Math.floor(s / 60)}:${String(s % 60).padStart(2, "0")}`;
}

// Black or white text, whichever contrasts more with the game's color.
function chip(game) {
  const [r, g, b] = [1, 3, 5].map((i) => parseInt(game.color.slice(i, i + 2), 16) / 255);
  const lin = (c) => (c <= 0.03928 ? c / 12.92 : ((c + 0.055) / 1.055) ** 2.4);
  const light = 0.2126 * lin(r) + 0.7152 * lin(g) + 0.0722 * lin(b) > 0.179;
  return `<span class="chip" style="background:${game.color};color:${light ? "#000" : "#fff"}">${esc(game.shortName)}</span>`;
}

function gamesById() {
  return Object.fromEntries(ui.state.families.flatMap((f) => f.games.map((g) => [g.id, g])));
}

async function api(path, body) {
  const options = body === undefined ? {} : {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(body),
  };
  const response = await fetch(`/api/${path}`, options);
  const data = await response.json();
  if (!response.ok) {
    showMessage((data.errors || [response.statusText]).join("\n"));
    throw new Error(response.statusText);
  }
  showMessage(data.notice || "");
  if (data.youtube) renderYoutube(data.youtube);
  return data;
}

function showMessage(text) {
  $("message").hidden = !text;
  $("message").textContent = text;
}

// State and families

async function loadState() {
  ui.state = await api("state");
  ui.family ??= (ui.state.families.find((f) => f.counts.review) || ui.state.families[0]).id;
  renderFamilies();
}

function renderYoutube(youtube) {
  const el = $("youtube");
  el.classList.toggle("blocked", youtube.mode === "blocked");
  if (youtube.mode === "live") {
    el.textContent = "YouTube: live for searches and lookups, cache for matching";
  } else if (youtube.mode === "cached-only") {
    el.textContent = "YouTube: cached responses only (--cached-only)";
  } else {
    const until = new Date(youtube.until * 1000).toLocaleTimeString([], { hour: "2-digit", minute: "2-digit" });
    el.innerHTML = `YouTube is blocking requests, using the cache until ${until} <button id="retry">Try again</button>`;
    $("retry").onclick = async () => renderYoutube(await api("youtube/retry", {}));
  }
}

function renderFamilies() {
  renderYoutube(ui.state.youtube);
  $("families").innerHTML = ui.state.families.map((f) => `
    <button data-family="${esc(f.id)}" class="${f.id === ui.family ? "selected" : ""}" title="${esc(f.name)}">
      <span>${f.games.map(chip).join("")}${f.playlists ? "" : ' <span class="badge">no playlist</span>'}</span>
      <span class="counts">${f.counts.matched}·<span class="r">${f.counts.review}</span>·${f.counts.unmatched}${f.counts.excluded ? ` (−${f.counts.excluded})` : ""}</span>
    </button>`).join("");
  for (const button of $("families").querySelectorAll("button")) {
    button.onclick = () => { ui.family = button.dataset.family; renderFamilies(); loadTracks(); };
  }
}

// Track list

async function loadTracks() {
  const status = ui.status === "all" ? "" : ui.status;
  ui.rows = await api(`tracks?family=${encodeURIComponent(ui.family)}&status=${status}`);
  renderTracks();
}

function renderTracks() {
  $("tabs").innerHTML = TABS.map(([id, label]) =>
    `<button data-status="${id}" class="${id === ui.status ? "selected" : ""}">${label}</button>`).join("");
  for (const button of $("tabs").querySelectorAll("button")) {
    button.onclick = () => { ui.status = button.dataset.status; loadTracks(); };
  }
  const games = gamesById();
  $("tracks").innerHTML = ui.rows.length ? ui.rows.map((r) => `
    <li data-id="${esc(r.id)}" class="${r.id === ui.trackId ? "selected" : ""}">
      <span class="title">${esc(r.title)} ${r.games ? r.games.map((g) => chip(games[g])).join("") : ""}</span>
      <span class="meta status-${r.status}">${r.decided ? "✎ " : ""}${r.best == null ? "–" : r.best.toFixed(2)}</span>
    </li>`).join("") : '<li class="empty">No tracks.</li>';
  for (const li of $("tracks").querySelectorAll("li[data-id]")) {
    li.onclick = () => openTrack(li.dataset.id);
  }
}

function moveSelection(step) {
  const i = ui.rows.findIndex((r) => r.id === ui.trackId);
  const next = ui.rows[Math.min(ui.rows.length - 1, Math.max(0, i + step))];
  if (next) openTrack(next.id);
}

// Track detail

async function openTrack(id) {
  if (id !== ui.trackId) ui.found = [];
  ui.trackId = id;
  ui.detail = await api(`tracks/${encodeURIComponent(id)}`);
  renderTracks();
  renderDetail();
}

const sliceOf = (c) => ({ videoId: c.videoId, startMs: c.startMs || 0, endMs: c.endMs ?? null });
const same = (a, b) => a.videoId === b.videoId && (a.startMs || 0) === (b.startMs || 0) && (a.endMs ?? null) === (b.endMs ?? null);

async function decide(decision) {
  ui.detail = await api(`tracks/${encodeURIComponent(ui.trackId)}/streams`, decision);
  renderDetail();
  // The list and the family counts change with the status.
  await Promise.all([loadState(), loadTracks()]);
}

async function setRemoved(removed) {
  ui.detail = await api(`tracks/${encodeURIComponent(ui.trackId)}/exclude`, { removed });
  renderDetail();
  await Promise.all([loadState(), loadTracks()]);
}

function accept(candidate) {
  const { accept, reject } = ui.detail.decision;
  const slice = sliceOf(candidate);
  if (accept.some((s) => same(s, slice))) return;
  decide({ accept: [...accept, slice], reject: reject.filter((v) => v !== slice.videoId) });
}

function reject(videoId) {
  const { accept, reject } = ui.detail.decision;
  decide({ accept: accept.filter((s) => s.videoId !== videoId), reject: [...new Set([...reject, videoId])] });
}

function moveAccepted(i, step) {
  const accept = [...ui.detail.decision.accept];
  const j = i + step;
  if (j < 0 || j >= accept.length) return;
  [accept[i], accept[j]] = [accept[j], accept[i]];
  decide({ ...ui.detail.decision, accept });
}

function verdict(score) {
  if (score == null) return "";
  return score >= ACCEPT ? "accept" : score >= REVIEW ? "review" : "reject";
}

function lengthLine(candidate) {
  const expected = ui.detail.durationMs;
  if (candidate.durationMs == null || !expected) return `length ${minutes(candidate.durationMs)}`;
  const ratio = candidate.durationMs / expected;
  const off = ratio < MIN_RATIO || ratio > MAX_RATIO;
  return `<span class="${off ? "bad" : ""}">${minutes(candidate.durationMs)} vs ${minutes(expected)} (${ratio.toFixed(2)}×)</span>`;
}

function card(candidate, index) {
  const slice = sliceOf(candidate);
  const taken = ui.detail.decision.accept.some((s) => same(s, slice)) || candidate.accepted;
  const range = candidate.startMs || candidate.endMs != null
    ? ` · ${minutes(candidate.startMs)}–${candidate.endMs == null ? "end" : minutes(candidate.endMs)}` : "";
  return `
    <div class="card ${taken ? "accepted" : ""}">
      <div class="player" data-index="${index}">
        <img src="https://i.ytimg.com/vi/${esc(candidate.videoId)}/mqdefault.jpg" alt="">
        <button class="play" title="Play from the start of the slice">▶</button>
      </div>
      <div class="ctitle">${esc(candidate.title ?? candidate.videoId)}</div>
      <div class="line">
        <span class="score ${verdict(candidate.score)}">${candidate.score == null ? "unscored" : candidate.score.toFixed(2)}</span>
        · ${esc(candidate.kind)} · ${esc(candidate.channel ?? "unknown channel")} · ${lengthLine(candidate)}${range}
      </div>
      <div class="line">${esc((candidate.reasons || []).join(", "))}</div>
      <div class="actions">
        <button class="primary" data-accept="${index}" ${taken ? "disabled" : ""}>Accept</button>
        <button data-reject="${index}">Reject</button>
        <a href="${watchUrl(candidate)}" target="_blank" rel="noreferrer">YouTube ↗</a>
      </div>
    </div>`;
}

function watchUrl(candidate) {
  return `https://www.youtube.com/watch?v=${encodeURIComponent(candidate.videoId)}&t=${Math.floor((candidate.startMs || 0) / 1000)}s`;
}

function renderDetail() {
  destroyPlayers();
  const d = ui.detail;
  const games = gamesById();
  // Pasted videos and search results first, so a new one shows up next to the box that added it.
  const shown = [...ui.found, ...d.candidates.filter((c) => !ui.found.some((f) => same(c, f)))];
  const sources = d.streams.length ? d.streams.map((s, i) => {
    const hand = !s.auto;
    const index = d.decision.accept.findIndex((a) => same(a, s));
    return `<li>
      <a href="${watchUrl(s)}" target="_blank" rel="noreferrer">${esc(s.videoId)}</a>
      ${s.startMs || s.endMs != null ? `${minutes(s.startMs)}–${s.endMs == null ? "end" : minutes(s.endMs)}` : ""}
      ${hand ? "<strong>accepted</strong>" : `auto ${s.confidence.toFixed(2)}`}
      ${hand ? `<button data-up="${index}">↑</button><button data-down="${index}">↓</button>` : `<button data-keep="${i}">Keep</button>`}
      <button data-drop="${i}">${hand ? "Remove" : "Reject"}</button>
    </li>`;
  }).join("") : '<li class="empty">None: this track is left out of the manifest.</li>';
  const rejected = d.decision.reject.length
    ? `<p class="facts">Rejected: ${d.decision.reject.map(esc).join(", ")} <button id="clear">Clear decision</button></p>`
    : d.decision.accept.length ? '<p><button id="clear">Clear decision</button></p>' : "";
  $("detail").innerHTML = `
    <h2>${esc(d.title)}</h2>
    <div class="facts">
      ${d.games.map((g) => chip(games[g])).join("")}${d.exclusive ? " (exclusive)" : ""}
      · ${esc(d.category)} · ${minutes(d.durationMs)} · <span class="status-${d.status}">${d.status}</span>
      · <code>${esc(d.id)}</code>
    </div>
    <div class="facts">${d.origins.map(esc).join("<br>")}</div>
    ${d.status === "excluded"
      ? '<p class="removed">Removed from the catalog (<code>exclude</code> override); it leaves this list at the next Rebuild. <button id="restore">Restore</button></p>'
      : '<p><button id="remove" title="Leave this track out of the catalog: writes exclude: true">Remove track</button></p>'}
    <h3>Sources</h3>
    <ol class="sources">${sources}</ol>
    ${rejected}
    <h3>Candidates</h3>
    <div class="find">
      <input class="wide" id="query" value="${esc(d.query)}"><button id="search">Search YouTube</button>
    </div>
    <div class="find">
      <input class="wide" id="paste" placeholder="Video id or link">
      <input class="ms" id="start" placeholder="start ms"><input class="ms" id="end" placeholder="end ms">
      <button id="lookup">Add video</button>
    </div>
    <div class="cards">${shown.length ? shown.map(card).join("") : '<p class="empty">No candidates.</p>'}</div>`;
  bindDetail(shown);
}

function bindDetail(shown) {
  const d = ui.detail;
  const on = (selector, handler) => {
    for (const el of $("detail").querySelectorAll(selector)) el.onclick = () => handler(el);
  };
  on("[data-accept]", (el) => accept(shown[el.dataset.accept]));
  on("[data-reject]", (el) => reject(shown[el.dataset.reject].videoId));
  on("[data-keep]", (el) => accept(d.streams[el.dataset.keep]));
  on("[data-drop]", (el) => {
    const s = d.streams[el.dataset.drop];
    if (s.auto) return reject(s.videoId);
    decide({ ...d.decision, accept: d.decision.accept.filter((a) => !same(a, s)) });
  });
  on("[data-up]", (el) => moveAccepted(Number(el.dataset.up), -1));
  on("[data-down]", (el) => moveAccepted(Number(el.dataset.down), 1));
  on("#clear", () => decide({ accept: [], reject: [] }));
  on("#remove", () => { if (confirm(`Remove "${d.title}" from the catalog?`)) setRemoved(true); });
  on("#restore", () => setRemoved(false));
  on(".play", (el) => play(el.parentElement, shown[el.parentElement.dataset.index]));
  on("#search", search);
  on("#lookup", lookup);
  $("query").onkeydown = (e) => { if (e.key === "Enter") search(); };
  for (const id of ["paste", "start", "end"]) {
    $(id).onkeydown = (e) => { if (e.key === "Enter") lookup(); };
  }
}

async function search() {
  const result = await api(`tracks/${encodeURIComponent(ui.trackId)}/search`, { query: $("query").value });
  ui.found = result.results;
  await openTrack(ui.trackId); // the rematch may have picked the results up as candidates
  $("detail").querySelector(".card")?.scrollIntoView({ behavior: "smooth", block: "nearest" });
}

// A link's own start time ("t=42", "t=42s", "t=1m2s") in ms, or null.
function linkStartMs(text) {
  const t = /[?&#]t=(?:(\d+)h)?(?:(\d+)m)?(\d+)?s?(?:&|$)/.exec(text.trim());
  if (!t || !(t[1] || t[2] || t[3])) return null;
  return ((Number(t[1] || 0) * 60 + Number(t[2] || 0)) * 60 + Number(t[3] || 0)) * 1000;
}

async function lookup() {
  const ms = (id) => {
    const text = $(id).value.trim();
    if (text === "") return null;
    if (!/^\d+$/.test(text)) throw new Error(`${id} must be a whole number of milliseconds, e.g. 3500`);
    return Number(text);
  };
  let startMs;
  let endMs;
  try {
    startMs = ms("start") ?? linkStartMs($("paste").value);
    endMs = ms("end");
  } catch (e) {
    showMessage(e.message);
    return;
  }
  const result = await api(`tracks/${encodeURIComponent(ui.trackId)}/video`, {
    text: $("paste").value, startMs, endMs,
  });
  ui.found = [result.candidate, ...ui.found.filter((f) => !same(f, result.candidate))];
  renderDetail();
  $("detail").querySelector(".card")?.scrollIntoView({ behavior: "smooth", block: "nearest" });
}

// Players: created on click only, one playing at a time.

function play(container, candidate) {
  if (!window.YT || !YT.Player) {
    showMessage("The YouTube player didn't load; use the YouTube links.");
    return;
  }
  const target = document.createElement("div");
  container.replaceChildren(target);
  const vars = { start: Math.floor((candidate.startMs || 0) / 1000), autoplay: 1, rel: 0 };
  if (candidate.endMs != null) vars.end = Math.ceil(candidate.endMs / 1000);
  const player = new YT.Player(target, {
    videoId: candidate.videoId,
    width: "100%",
    height: "100%",
    playerVars: vars,
    events: {
      onStateChange: (e) => {
        if (e.data === YT.PlayerState.PLAYING) {
          for (const other of ui.players) if (other !== player) other.pauseVideo?.();
        }
      },
      onError: (e) => {
        const why = { 2: "bad video id", 100: "removed or private", 101: "embedding disabled",
          150: "embedding disabled", 153: "player configuration (referrer)" }[e.data] || "unknown";
        const overlay = document.createElement("div");
        overlay.className = "embed-error";
        overlay.innerHTML = `Can't embed (error ${e.data}: ${why})<a href="${watchUrl(candidate)}" target="_blank" rel="noreferrer">Open on YouTube ↗</a>`;
        container.append(overlay);
      },
    },
  });
  ui.players.push(player);
}

function destroyPlayers() {
  for (const player of ui.players) player.destroy?.();
  ui.players = [];
}

// Rebuild and keys

$("rebuild").onclick = async () => {
  const button = $("rebuild");
  button.disabled = true;
  button.textContent = "Rebuilding…";
  try {
    const result = await api("rebuild", {});
    $("output").querySelector("pre").textContent = (result.ok ? "" : "FAILED\n\n") + result.output;
    $("output").showModal();
    await loadState();
    await loadTracks();
    if (ui.trackId) await openTrack(ui.trackId);
  } finally {
    button.disabled = false;
    button.textContent = "Rebuild";
  }
};

document.addEventListener("keydown", (e) => {
  if (e.target.closest("input, textarea") || e.metaKey || e.ctrlKey) return;
  if (e.key === "j") moveSelection(1);
  if (e.key === "k") moveSelection(-1);
});

window.onYouTubeIframeAPIReady = () => {};

(async () => {
  await loadState();
  await loadTracks();
  if (ui.rows.length) openTrack(ui.rows[0].id);
})();
