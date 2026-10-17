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
  filter: "", // the track list's filter text
  trackId: null,
  detail: null,
  found: [], // search results and pasted videos for the open track
  players: [],
  mode: "tracks", // or "playlists"
  coverage: null, // /api/playlists: families ranked by their gaps
  playlistSearch: null, // the family's playlist search: { query, results }
  preview: null, // { anchor, data }: shown under the control that asked for it
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

// A slice's start or end, to a tenth of a second when it isn't whole (0:03.5).
function stamp(ms) {
  const s = ms / 1000;
  const whole = Math.floor(s);
  const tenths = Math.round((s - whole) * 10);
  return `${Math.floor(whole / 60)}:${String(whole % 60).padStart(2, "0")}${tenths ? `.${tenths}` : ""}`;
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
  if (ui.mode === "playlists") return renderCoverage();
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
  const rows = visibleRows();
  // A matched track shows its first source's confidence (1.00 when picked by hand), others
  // their best candidate's score.
  $("tracks").innerHTML = rows.length ? rows.map((r) => { const score = r.confidence ?? r.best; return `
    <li data-id="${esc(r.id)}" class="${r.id === ui.trackId ? "selected" : ""}">
      <span class="title">${esc(r.title)} ${r.games ? r.games.map((g) => chip(games[g])).join("") : ""}</span>
      <span class="meta status-${r.status}">${r.decided ? "✎ " : ""}${score == null ? "–" : score.toFixed(2)}</span>
    </li>`; }).join("") : `<li class="empty">${ui.rows.length ? "No tracks match the filter." : "No tracks."}</li>`;
  for (const li of $("tracks").querySelectorAll("li[data-id]")) {
    li.onclick = () => openTrack(li.dataset.id);
  }
}

// Lower case without accents, so "pokemon" finds "Pokémon".
function plain(text) {
  return text.normalize("NFD").replace(/\p{Diacritic}/gu, "").toLowerCase();
}

// The rows matching the filter: every word in the title or the id.
function visibleRows() {
  const words = plain(ui.filter).split(/\s+/).filter(Boolean);
  return ui.rows.filter((r) => words.every((w) => plain(r.title).includes(w) || r.id.includes(w)));
}

function moveSelection(step) {
  const rows = visibleRows();
  const i = rows.findIndex((r) => r.id === ui.trackId);
  const next = rows[Math.min(rows.length - 1, Math.max(0, i + step))];
  if (next) openTrack(next.id);
}

// Track detail

async function openTrack(id) {
  if (id !== ui.trackId) ui.found = [];
  ui.trackId = id;
  ui.detail = await api(`tracks/${encodeURIComponent(id)}`);
  history.replaceState(null, "", `#${encodeURIComponent(id)}`); // a refresh reopens this track
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
  const hand = ui.detail.decision.accept.findIndex((s) => same(s, slice));
  const auto = hand < 0 && ui.detail.streams.some((s) => s.auto && same(s, slice));
  const badge = hand >= 0 ? `<span class="badge-accepted">Accepted #${hand + 1}</span>`
    : auto ? '<span class="badge-auto">Auto</span>' : "";
  const range = candidate.startMs || candidate.endMs != null
    ? ` · ${stamp(candidate.startMs || 0)}–${candidate.endMs == null ? "end" : stamp(candidate.endMs)}` : "";
  return `
    <div class="card ${hand >= 0 ? "hand" : auto ? "auto" : ""}">
      <div class="player" data-index="${index}">
        <img src="https://i.ytimg.com/vi/${esc(candidate.videoId)}/mqdefault.jpg" alt="">
        <button class="play" title="Play from the start of the slice">▶</button>
      </div>
      <div class="ctitle">${badge}${esc(candidate.title ?? candidate.videoId)}</div>
      <div class="line">
        <span class="score ${verdict(candidate.score)}">${candidate.score == null ? "unscored" : candidate.score.toFixed(2)}</span>
        · ${esc(candidate.kind)} · ${esc(candidate.channel ?? "unknown channel")} · ${lengthLine(candidate)}${range}
      </div>
      <div class="line">${esc((candidate.reasons || []).join(", "))}</div>
      <div class="actions">
        <button class="primary" data-accept="${index}" ${hand >= 0 ? "disabled" : ""}>Accept</button>
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
  // Hand-accepted videos first (in order), then pasted videos and search results, so a new one
  // shows up next to the box that added it, then the remaining candidates.
  const shown = [];
  for (const c of [...(d.accepted || []), ...ui.found, ...d.candidates]) {
    if (!shown.some((s) => same(s, c))) shown.push(c);
  }
  const sources = d.streams.length ? d.streams.map((s, i) => {
    const hand = !s.auto;
    const index = d.decision.accept.findIndex((a) => same(a, s));
    return `<li>
      <a href="${watchUrl(s)}" target="_blank" rel="noreferrer">${esc(s.videoId)}</a>
      ${s.startMs || s.endMs != null ? `${stamp(s.startMs)}–${s.endMs == null ? "end" : stamp(s.endMs)}` : ""}
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

// Playlists: families ranked by their gaps, then the selected family's playlists, a playlist
// search and previews. A preview shows under the control that asked for it.

async function setMode(mode) {
  ui.mode = mode;
  $("mode-tracks").classList.toggle("selected", mode === "tracks");
  $("mode-playlists").classList.toggle("selected", mode === "playlists");
  $("listhead").hidden = $("tracks").hidden = mode !== "tracks";
  $("gaps").hidden = mode !== "playlists";
  if (mode === "playlists") {
    await loadCoverage();
    renderPlaylists();
  } else {
    renderFamilies();
    await loadTracks();
    if (ui.trackId) await openTrack(ui.trackId);
    else $("detail").innerHTML = '<p class="empty">Pick a track.</p>';
  }
}

async function loadCoverage() {
  ui.coverage = await api("playlists");
  renderFamilies();
}

function coverageOf(family) {
  return ui.coverage.families.find((f) => f.id === family);
}

function renderCoverage() {
  $("families").innerHTML = ui.coverage.families.map((f) => `
    <button data-family="${esc(f.id)}" class="${f.id === ui.family ? "selected" : ""}" title="${esc(f.name)}">
      <span>${f.games.map(chip).join("")} <span class="badge ${f.playlists.length ? "ok" : ""}">${f.playlists.length ? `${f.playlists.length} playlist${f.playlists.length > 1 ? "s" : ""}` : "no playlist"}</span></span>
      <span class="counts">${f.counts.matched}·<span class="r">${f.counts.review}</span>·${f.counts.unmatched}</span>
    </button>`).join("");
  for (const button of $("families").querySelectorAll("button")) {
    button.onclick = () => {
      if (button.dataset.family !== ui.family) { ui.playlistSearch = null; ui.preview = null; }
      ui.family = button.dataset.family;
      renderCoverage();
      renderPlaylists();
    };
  }
}

function renderPlaylists() {
  history.replaceState(null, "", `#playlists/${encodeURIComponent(ui.family)}`);
  const family = coverageOf(ui.family);
  $("gaps").innerHTML = `<h3>Games</h3>${family.games.map((g) => `
    <div class="gap">
      <span>${chip(g)} ${g.playlists ? `${g.playlists} playlist${g.playlists > 1 ? "s" : ""}` : '<span class="badge">no playlist</span>'}</span>
      <span class="counts">${g.tracks} tracks<br><span class="r">${g.review} review</span> · ${g.unmatched} unmatched</span>
    </div>`).join("")}`;
  const listed = family.playlists.length ? family.playlists.map((p) => `
    <li>
      <div class="ptitle">${p.games.map((g) => chip(gamesById()[g])).join("")} ${esc(p.note ?? p.playlist)}</div>
      <div class="line"><a href="${playlistUrl(p.playlist)}" target="_blank" rel="noreferrer">${esc(p.playlist)} ↗</a>
        ${rangeText(p.first, p.last)} · ${p.entries == null ? "not cached" : `${p.entries} entries cached`}</div>
      <div class="actions">
        <button data-preview-removal="${p.index}" data-playlist="${esc(p.playlist)}">Preview removal</button>
        <button data-remove="${p.index}" data-playlist="${esc(p.playlist)}">Remove</button>
      </div>
      ${previewAt(`remove:${p.index}`)}
    </li>`).join("") : '<li class="empty">None: every track of this family is unmatched unless searched one by one.</li>';
  const search = ui.playlistSearch;
  const results = search ? (search.results.length ? search.results.map((r) => `
    <li>
      <div class="ptitle">${esc(r.title)}</div>
      <div class="line">${esc(r.channel ?? "unknown channel")} · <a href="${playlistUrl(r.id)}" target="_blank" rel="noreferrer">${esc(r.id)} ↗</a>
        ${r.entries == null ? "" : ` · ${r.entries} entries cached`}${r.listed.length ? ' · <strong>already listed</strong>' : ""}</div>
      <div class="actions">
        <button data-preview="${esc(r.id)}">Preview</button>
        <button class="primary" data-add="${esc(r.id)}">Add</button>
      </div>
      ${previewAt(`search:${r.id}`)}
    </li>`).join("") : '<li class="empty">No playlists found.</li>') : "";
  const counts = family.counts;
  $("detail").innerHTML = `
    <h2>${family.games.map(chip).join("")} ${esc(family.name)}</h2>
    <div class="facts">${counts.matched} matched · <span class="status-review">${counts.review} review</span> · <span class="status-unmatched">${counts.unmatched} unmatched</span></div>
    <h3>Playlists</h3>
    <ol class="playlists">${listed}</ol>
    <h3>Add a playlist</h3>
    ${gamesPicker(family)}
    <div class="find">
      <input class="ms" id="first" placeholder="first #"><input class="ms" id="last" placeholder="last #">
      <input class="wide" id="note" placeholder="Note (uploader, kind, coverage)">
    </div>
    <div class="find">
      <input class="wide" id="plquery" value="${esc(search?.query ?? "")}" placeholder="Search YouTube for playlists (empty: Pokémon + the family's games + soundtrack)">
      <button id="plsearch">Search playlists</button>
    </div>
    ${search ? `<ol class="playlists">${results}</ol>` : ""}
    <div class="find" style="margin-top:8px">
      <input class="wide" id="plpaste" placeholder="Playlist id or link">
      <button id="plpreview">Preview</button><button class="primary" id="pladd">Add</button>
    </div>
    ${previewAt("paste")}`;
  restoreForm();
  bindPlaylists();
}

const playlistUrl = (id) => `https://www.youtube.com/playlist?list=${encodeURIComponent(id)}`;

function rangeText(first, last) {
  return first == null && last == null ? "" : ` · entries ${first ?? 1}–${last ?? "end"}`;
}

// The family's games ticked by default; other families' games for albums that span families.
function gamesPicker(family) {
  const box = (g, checked) => `<label>${`<input type="checkbox" name="game" value="${esc(g.id)}" ${checked ? "checked" : ""}>`}${chip(g)}</label>`;
  const others = ui.state.families.filter((f) => f.id !== family.id).flatMap((f) => f.games);
  return `<div class="games-pick">Games: ${family.games.map((g) => box(g, true)).join("")}
    <details><summary>Other games</summary><div>${others.map((g) => box(g, false)).join("")}</div></details></div>`;
}

// The form keeps what was typed across re-renders (a preview re-renders the panel).
let form = null;

function saveForm() {
  if (!$("first")) return;
  form = {
    family: ui.family,
    games: [...$("detail").querySelectorAll('input[name="game"]')].map((b) => [b.value, b.checked]),
    first: $("first").value, last: $("last").value, note: $("note").value,
    paste: $("plpaste").value, query: $("plquery").value,
  };
}

function restoreForm() {
  if (!form || form.family !== ui.family) return;
  const checked = Object.fromEntries(form.games);
  for (const box of $("detail").querySelectorAll('input[name="game"]')) box.checked = !!checked[box.value];
  for (const id of ["first", "last", "note"]) $(id).value = form[id];
  $("plpaste").value = form.paste;
  $("plquery").value = form.query;
}

function formEntry(playlist) {
  const number = (id) => {
    const text = $(id).value.trim();
    if (text === "") return null;
    if (!/^\d+$/.test(text)) throw new Error(`${id} must be a playlist position, e.g. 18`);
    return Number(text);
  };
  return {
    playlist,
    games: [...$("detail").querySelectorAll('input[name="game"]:checked')].map((b) => b.value),
    first: number("first"),
    last: number("last"),
    note: $("note").value.trim(),
  };
}

function bindPlaylists() {
  const on = (selector, handler) => {
    for (const el of $("detail").querySelectorAll(selector)) el.onclick = () => handler(el);
  };
  on("[data-preview-removal]", (el) => previewRemoval(Number(el.dataset.previewRemoval), el.dataset.playlist));
  on("[data-remove]", (el) => removePlaylist(Number(el.dataset.remove), el.dataset.playlist));
  on("[data-preview]", (el) => previewPlaylist(el.dataset.preview, `search:${el.dataset.preview}`));
  on("[data-add]", (el) => addPlaylist(el.dataset.add));
  on("[data-preview-add]", (el) => addPlaylist(el.dataset.previewAdd));
  on("[data-open]", (el) => { ui.family = el.dataset.family; ui.trackId = el.dataset.open; setMode("tracks"); });
  on("#plsearch", searchPlaylists);
  on("#plpreview", () => previewPlaylist($("plpaste").value, "paste"));
  on("#pladd", () => addPlaylist($("plpaste").value));
  $("plquery").onkeydown = (e) => { if (e.key === "Enter") searchPlaylists(); };
  $("plpaste").onkeydown = (e) => { if (e.key === "Enter") previewPlaylist($("plpaste").value, "paste"); };
}

async function searchPlaylists() {
  saveForm();
  const result = await api("playlists/search", { family: ui.family, query: $("plquery").value });
  ui.playlistSearch = { query: result.query, results: result.results };
  if (form) form.query = result.query;
  ui.preview = null;
  renderPlaylists();
}

async function previewPlaylist(playlist, anchor) {
  saveForm();
  let entry;
  try {
    entry = formEntry(playlist);
  } catch (e) {
    showMessage(e.message);
    return;
  }
  ui.preview = { anchor, data: await api("playlists/preview", entry) };
  renderPlaylists();
  $("detail").querySelector(".preview")?.scrollIntoView({ behavior: "smooth", block: "nearest" });
}

async function previewRemoval(index, playlist) {
  saveForm();
  ui.preview = { anchor: `remove:${index}`, data: await api("playlists/preview-removal", { index, playlist }) };
  renderPlaylists();
}

async function addPlaylist(playlist) {
  saveForm();
  let entry;
  try {
    entry = formEntry(playlist);
  } catch (e) {
    showMessage(e.message);
    return;
  }
  ui.coverage = await api("playlists/add", entry);
  afterPlaylistEdit();
}

async function removePlaylist(index, playlist) {
  if (!confirm(`Remove playlist ${playlist} from games.json?`)) return;
  ui.coverage = await api("playlists/remove", { index, playlist });
  afterPlaylistEdit();
}

async function afterPlaylistEdit() {
  ui.preview = null;
  if (form) form.paste = "";
  ui.state = await api("state"); // the tracks view's counts and badges
  renderFamilies();
  renderPlaylists();
}

function previewAt(anchor) {
  const preview = ui.preview;
  if (!preview || preview.anchor !== anchor) return "";
  const p = preview.data;
  const games = gamesById();
  const removal = anchor.startsWith("remove:");
  const delta = (before, after, key, cls) =>
    `<span class="${cls}">${before[key]}${before[key] === after[key] ? "" : ` → <strong>${after[key]}</strong>`} ${key}</span>`;
  const counts = Object.entries(p.counts).map(([family, { before, after }]) => `
    <div class="delta"><strong>${esc(family)}</strong>: ${delta(before, after, "matched", "status-matched")}
      · ${delta(before, after, "review", "status-review")} · ${delta(before, after, "unmatched", "status-unmatched")}</div>`).join("");
  const source = (o) => o.sources.length
    ? `${esc(o.sources[0].videoId)} ${o.sources[0].auto ? `auto ${o.sources[0].confidence.toFixed(2)}` : "<strong>accepted</strong>"}${o.sources.length > 1 ? ` +${o.sources.length - 1}` : ""}`
    : "–";
  const changes = p.changes.length ? `<table>
    <tr><th>Track</th><th>Before</th><th>After</th></tr>
    ${p.changes.map((c) => `<tr>
      <td>${c.hand ? '<span title="Has a hand decision: its accepted videos stay first">✎</span> ' : ""}<a class="track" data-open="${esc(c.id)}" data-family="${esc(c.family)}">${esc(c.title)}</a></td>
      <td><span class="status-${c.before.status}">${c.before.status}</span> ${source(c.before)}</td>
      <td><span class="status-${c.after.status}">${c.after.status}</span> ${source(c.after)}</td>
    </tr>`).join("")}</table>` : '<p class="empty">No track would change.</p>';
  const entries = p.entries.length ? `<table>
    <tr><th class="num">#</th><th>Entry</th><th class="num">Length</th><th>${removal ? "Matches today" : "Would match"}</th></tr>
    ${p.entries.map((e) => `<tr class="${e.inRange ? "" : "out"}">
      <td class="num">${e.position}</td>
      <td><a href="https://www.youtube.com/watch?v=${encodeURIComponent(e.videoId)}" target="_blank" rel="noreferrer">${esc(e.title)}</a></td>
      <td class="num">${minutes(e.durationMs)}</td>
      <td>${e.match ? `<span class="score ${e.match.verdict}">${e.match.score.toFixed(2)}</span> ${esc(e.match.title)}${e.match.source ? " (source)" : ""}` : '<span class="empty">–</span>'}</td>
    </tr>`).join("")}</table>` : '<p class="empty">No cached entries.</p>';
  const add = removal ? "" : `<p><button class="primary" data-preview-add="${esc(p.playlist)}">Add with ${p.games.map((g) => chip(games[g])).join("")}${rangeText(p.first, p.last)}</button></p>`;
  return `<div class="preview">
    <div><strong>${removal ? "Removing" : "Adding"}</strong> ${esc(p.playlist)} ${p.games.map((g) => chip(games[g])).join("")}${rangeText(p.first, p.last)}</div>
    ${counts}${add}
    <h4>Changed tracks (${p.changes.length})</h4>${changes}
    <h4>Entries (${p.entries.length})</h4>${entries}
  </div>`;
}

$("mode-tracks").onclick = () => setMode("tracks");
$("mode-playlists").onclick = () => setMode("playlists");

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
    if (ui.mode === "playlists") {
      await loadCoverage();
      renderPlaylists();
    } else {
      await loadTracks();
      if (ui.trackId) await openTrack(ui.trackId);
    }
  } finally {
    button.disabled = false;
    button.textContent = "Rebuild";
  }
};

$("filter").oninput = () => { ui.filter = $("filter").value; renderTracks(); };
$("filter").onkeydown = (e) => {
  if (e.key === "Escape") { $("filter").value = ""; ui.filter = ""; renderTracks(); $("filter").blur(); }
  if (e.key === "Enter") { const first = visibleRows()[0]; if (first) openTrack(first.id); }
};

document.addEventListener("keydown", (e) => {
  if (e.target.closest("input, textarea") || e.metaKey || e.ctrlKey || ui.mode !== "tracks") return;
  if (e.key === "/") { e.preventDefault(); $("filter").focus(); return; }
  if (e.key === "j") moveSelection(1);
  if (e.key === "k") moveSelection(-1);
});

window.onYouTubeIframeAPIReady = () => {};

(async () => {
  const linked = decodeURIComponent(location.hash.slice(1));
  await loadState();
  if (linked.startsWith("playlists/")) {
    const family = linked.slice("playlists/".length);
    if (ui.state.families.some((f) => f.id === family)) ui.family = family;
    await setMode("playlists");
    return;
  }
  if (linked) {
    await openTrack(linked);
    ui.family = ui.detail.family;
    renderFamilies();
  }
  await loadTracks();
  if (!linked && ui.rows.length) openTrack(ui.rows[0].id);
})();
