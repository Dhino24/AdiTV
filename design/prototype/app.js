/* ADITV — prototype de design. Navigation « télécommande » au clavier, écrans et états de démonstration.
   Aucune requête réseau : tout est local et fictif. */
(() => {
"use strict";
const $ = (s, r = document) => r.querySelector(s);
const $$ = (s, r = document) => [...r.querySelectorAll(s)];
const stage = $("#stage"), screensEl = $("#screens"), nav = $("#nav"), overlay = $("#overlay"), toastEl = $("#toast");
const T = window.TITLES, P = window.PEOPLE, CH = window.CHANNELS, NOW = window.NOW;

/* ---------------- icônes ---------------- */
const svg = (b, fill) => `<svg viewBox="0 0 24 24" fill="${fill ? "currentColor" : "none"}" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">${b}</svg>`;
const I = {
  play: svg('<path d="M7 4.6v14.8L19.5 12z" stroke-width="1.5"/>', true),
  pause: svg('<rect x="6" y="4.5" width="4" height="15" rx="1.2" stroke="none"/><rect x="14" y="4.5" width="4" height="15" rx="1.2" stroke="none"/>', true),
  plus: svg('<path d="M12 5v14M5 12h14"/>'),
  check: svg('<path d="M5 12.5l4.5 4.5L19 7"/>'),
  info: svg('<circle cx="12" cy="12" r="9"/><path d="M12 11v6M12 7.6v.2"/>'),
  search: svg('<circle cx="11" cy="11" r="6.5"/><path d="M20 20l-4.2-4.2"/>'),
  back10: svg('<path d="M4 12a8 8 0 1 0 2.5-5.8"/><path d="M4 4v4.5h4.5"/><text x="12.6" y="15.4" font-size="7.2" text-anchor="middle" fill="currentColor" stroke="none" font-family="Inter" font-weight="700">10</text>'),
  fwd10: svg('<path d="M20 12a8 8 0 1 1-2.5-5.8"/><path d="M20 4v4.5h-4.5"/><text x="11.4" y="15.4" font-size="7.2" text-anchor="middle" fill="currentColor" stroke="none" font-family="Inter" font-weight="700">10</text>'),
  bolt: svg('<path d="M13 2.5L4.5 13.5H11l-1 8 8.5-11H12z"/>'),
  chart: svg('<path d="M4 20V11M10 20V5M16 20v-6M21 20H3"/>'),
  subs: svg('<rect x="3" y="5" width="18" height="14" rx="2.5"/><path d="M7 12.5h4M13 12.5h4M7 15.5h7"/>'),
  layers: svg('<path d="M12 3.5l8.5 4.5L12 12.5 3.5 8z"/><path d="M3.5 12.5L12 17l8.5-4.5"/><path d="M3.5 16.5L12 21l8.5-4.5" opacity=".5"/>'),
  bell: svg('<path d="M6 16v-5a6 6 0 1 1 12 0v5l1.5 2h-15z"/><path d="M10 21h4"/>'),
  star: svg('<path d="M12 3.2l2.7 5.6 6.1.8-4.5 4.2 1.1 6.1L12 17l-5.4 2.9 1.1-6.1-4.5-4.2 6.1-.8z" stroke-width="1.2"/>', true),
  close: svg('<path d="M6 6l12 12M18 6L6 18"/>'),
  warn: svg('<path d="M12 3.5l9.5 16.5h-19z"/><path d="M12 10v4.5M12 17.2v.2"/>'),
  cloud: svg('<path d="M7 18a4.5 4.5 0 0 1-.6-9A6 6 0 0 1 18 8.5a4.5 4.5 0 0 1-.5 9.5z"/><path d="M4 4l16 16"/>'),
  retry: svg('<path d="M20 11.5a8 8 0 1 0-2.4 5.7"/><path d="M20 4.5v7h-7"/>'),
  trailer: svg('<rect x="3" y="5" width="18" height="14" rx="2.5"/><path d="M10 9v6l5-3z" fill="currentColor"/>'),
  bookmark: svg('<path d="M6.5 3.5h11v17L12 16.5l-5.5 4z"/>'),
  sort: svg('<path d="M4 7h16M7 12h10M10 17h4"/>'),
  up: svg('<path d="M6 15l6-6 6 6"/>'),
  down: svg('<path d="M6 9l6 6 6-6"/>'),
  tv: svg('<rect x="2.5" y="5" width="19" height="12.5" rx="2"/><path d="M8 21h8"/>'),
  film: svg('<rect x="3" y="3.5" width="18" height="17" rx="2"/><path d="M7.5 3.5v17M16.5 3.5v17M3 9h4.5M3 15h4.5M16.5 9H21M16.5 15H21"/>'),
  link: svg('<path d="M10 14a4 4 0 0 0 5.7 0l3-3a4 4 0 0 0-5.7-5.7l-1 1"/><path d="M14 10a4 4 0 0 0-5.7 0l-3 3a4 4 0 0 0 5.7 5.7l1-1"/>'),
  power: svg('<path d="M12 3v8"/><path d="M6.3 7a8 8 0 1 0 11.4 0"/>'),
  del: svg('<path d="M9 5h11v14H9l-6-7z"/><path d="M12.5 9.5l5 5M17.5 9.5l-5 5"/>'),
};
$(".icon-btn", nav).innerHTML = I.search;

/* ---------------- état de démonstration ---------------- */
const st = {
  myList: new Set(["dakar", "mensonge", "reve", "frontiere", "sable"]),
  favCh: new Set(CH.filter(c => c.fav).map(c => c.id)),
  reminders: new Set(),
  turbo: false, sound: true, audio: 0, subs: 0,
  catLoaded: {},
  demo: { catalogError: false, listEmpty: false, playerError: false },
};
const rmQuery = matchMedia("(prefers-reduced-motion: reduce)");
let rm = rmQuery.matches;
document.documentElement.classList.toggle("rm", rm);
function toggleRM() { rm = !rm; document.documentElement.classList.toggle("rm", rm); toast(rm ? "Animations réduites" : "Animations normales", "check"); }

/* ---------------- utilitaires ---------------- */
const esc = s => String(s).replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/"/g, "&quot;");
const norm = s => String(s).normalize("NFD").replace(/[̀-ͯ]/g, "").toLowerCase();
const toMin = (hm, ref) => { const [h, m] = hm.split(":").map(Number); let v = h * 60 + m; if (ref != null && v < ref) v += 1440; return v; };
const hm = v => { v = ((v % 1440) + 1440) % 1440; return String(Math.floor(v / 60)).padStart(2, "0") + ":" + String(v % 60).padStart(2, "0"); };
const fmt = s => { s = Math.max(0, Math.round(s)); const h = Math.floor(s / 3600), m = Math.floor(s % 3600 / 60), x = s % 60; return (h ? h + ":" + String(m).padStart(2, "0") : m) + ":" + String(x).padStart(2, "0"); };
const durSec = d => { const m = /(\d+)\s*h\s*(\d+)?/.exec(d); return m ? (+m[1] * 60 + (+m[2] || 0)) * 60 : 6000; };
const dotJoin = arr => arr.filter(Boolean).map(esc).join('<i class="dot"></i>');
function progOf(ch) { if (!ch.progs) return null; for (const p of ch.progs) { const s = toMin(p[0]), e = toMin(p[1], s); if (s <= NOW && NOW < e) return p; } return null; }
function progPct(p) { const s = toMin(p[0]), e = toMin(p[1], s); return Math.min(1, Math.max(0, (NOW - s) / (e - s))); }
const chById = id => CH.find(c => c.id === id);

/* ---------------- composants ---------------- */
const metaLine = t => `${t.type === "serie" ? "Série · " : ""}${t.g} · ${t.y}`;
function wcard(id, k, o = {}) {
  const t = T[id];
  const pr = o.prog != null ? `<div class="prog"><i style="width:${Math.round(o.prog * 100)}%"></i></div>` : "";
  return `<button class="card w f" data-k="${k}" data-act="${o.act || "open:" + id}"><div class="art"><img src="img/${t.w}.jpg" alt="" loading="lazy">${pr}</div><div class="cap"><b>${esc(t.t)}</b><span>${esc(o.sub ?? metaLine(t))}</span></div></button>`;
}
function pcard(id, k) {
  const t = T[id];
  return `<button class="card p f" data-k="${k}" data-act="open:${id}"><div class="art"><img src="img/${t.p}.jpg" alt=""></div><div class="cap"><b>${esc(t.t)}</b><span>${esc(t.g)} · ${t.d}</span></div></button>`;
}
function liveCard(ch, k) {
  const p = progOf(ch);
  return `<button class="card w f" data-k="${k}" data-act="live:${ch.id}"><div class="art"><img src="img/${p[4]}.jpg" alt=""><div class="shade"></div><span class="tag-live">EN DIRECT</span><span class="ch-mark">${esc(ch.name)}</span><div class="prog"><i style="width:${Math.round(progPct(p) * 100)}%"></i></div></div><div class="cap"><b>${esc(p[2])}</b><span>${esc(ch.name)} · ${p[0]} – ${p[1]}</span></div></button>`;
}
function chTile(ch, k) {
  const p = progOf(ch);
  return `<button class="chtile f" data-k="${k}" data-act="live:${ch.id}"><div class="art">${esc(ch.name)}</div><div class="cap"><b>${ch.n} · ${esc(ch.name)}</b>${p ? esc(p[2]) : "Programme non communiqué"}</div></button>`;
}
const row = (title, key, items, o = {}) =>
  `<div class="sec rowsec ${o.cls || ""}"><h3 class="row-title">${title}${o.small ? `<small>${o.small}</small>` : ""}</h3><div class="rowview"><div class="track" data-row="${key}">${items}</div></div></div>`;
const stateBox = (o) => `<div class="state" style="${o.style || ""}"><div class="ico ${o.err ? "err" : ""}">${o.icon}</div><h2>${o.title}</h2><p>${o.text}</p>${o.code ? `<div class="code">${o.code}</div>` : ""}<div class="btns" data-row="${o.row || "st"}">${o.btns || ""}</div></div>`;
const versionsLine = t => `Versions annoncées : ${t.versions.map(v => v[2] ? `<b>${v[0]} · ${v[1]}</b>` : `${v[0]} · ${v[1]}`).join(' <span class="sep">·</span> ')}`;
const badgesHtml = t => `<span class="badges">${t.badges.map(b => `<span class="bdg">${b}</span>`).join("")}<span class="bdg vf">VF</span></span>`;

/* ---------------- moteur de focus ---------------- */
let scale = 1, cur = null, cs = null, stack = [];
const rootState = {};
const ROOTS = ["home", "live", "films", "series", "mylist", "search"];
const screens = {};

function fit() {
  const W = innerWidth, H = innerHeight - 44;
  scale = Math.min(W / 1920, H / 1080);
  stage.style.transform = `translate(${(W - 1920 * scale) / 2}px, ${(H - 1080 * scale) / 2}px) scale(${scale})`;
}
addEventListener("resize", fit); fit();

const vis = e => e && e.isConnected && e.offsetParent !== null && !e.classList.contains("off");
function byKey(k) { if (!k) return null; const sel = `[data-k="${CSS.escape(k)}"]`; return (overlay.hidden ? null : $(sel, overlay)) || $(sel, cs.el) || $(sel, nav); }
const navEl = k => $(`[data-k="${k || "nav-home"}"]`, nav);
function focusables() {
  if (!overlay.hidden) return $$(".f", overlay).filter(vis);
  const list = $$(".f", cs.el).filter(vis);
  if (cs.def.nav) list.push(...$$(".f", nav));
  return list;
}
function rect(e) { const r = e.getBoundingClientRect(); return { l: r.left / scale, t: r.top / scale, r: r.right / scale, b: r.bottom / scale, cx: (r.left + r.right) / 2 / scale, cy: (r.top + r.bottom) / 2 / scale }; }

function bestIn(dir) {
  const c = rect(cur); let best = null, bs = Infinity;
  for (const e of focusables()) {
    if (e === cur) continue;
    const r = rect(e); let primary, ortho;
    if (dir === "right") { if (r.cx <= c.cx + 2 || r.l < c.l + 4) continue; primary = Math.max(0, r.l - c.r); ortho = Math.max(0, r.t - c.b, c.t - r.b) * 6 + Math.abs(r.cy - c.cy) * .6; }
    else if (dir === "left") { if (r.cx >= c.cx - 2 || r.r > c.r - 4) continue; primary = Math.max(0, c.l - r.r); ortho = Math.max(0, r.t - c.b, c.t - r.b) * 6 + Math.abs(r.cy - c.cy) * .6; }
    else if (dir === "down") { if (r.cy <= c.cy + 4 || r.t < c.t + 6) continue; primary = Math.max(0, r.t - c.b) * 2; ortho = Math.max(0, r.l - c.r, c.l - r.r) + Math.abs(r.cx - c.cx) * .25; }
    else { if (r.cy >= c.cy - 4 || r.b > c.b - 6) continue; primary = Math.max(0, c.t - r.b) * 2; ortho = Math.max(0, r.l - c.r, c.l - r.r) + Math.abs(r.cx - c.cx) * .25; }
    const s = primary + ortho;
    if (s < bs) { bs = s; best = e; }
  }
  return best;
}
function resolveMem(c, dir) {
  if (!overlay.hidden) return c;
  const inNav = cur.closest("#nav");
  if (c.closest("#nav") && !inNav) return navEl(cs.def.navKey);
  if (inNav && !c.closest("#nav") && dir === "down") { const l = byKey(cs.last); if (vis(l)) return l; }
  const rowEl = c.closest("[data-row]");
  if (rowEl && rowEl !== cur.closest("[data-row]")) {
    const e = byKey(cs.mem[rowEl.dataset.row]);
    if (vis(e) && rowEl.contains(e)) return e;
  }
  return c;
}
function move(dir) {
  if (!cur) { const f = focusables()[0]; if (f) focus(f); return; }
  if (cs.def.dpad && overlay.hidden && cs.def.dpad(dir, cur, cs) === true) return;
  const c = bestIn(dir);
  if (!c) { bump(dir); return; }
  focus(resolveMem(c, dir));
}
function bump(dir) {
  if (!cur || rm) return;
  const cl = "bump-" + dir[0];
  cur.classList.remove(cl); void cur.offsetWidth; cur.classList.add(cl);
  setTimeout(() => cur && cur.classList.remove(cl), 260);
}
function focus(el, o = {}) {
  if (!el) return;
  if (cur && cur !== el) cur.classList.remove("is-focus");
  cur = el; el.classList.add("is-focus");
  const inOverlay = !overlay.hidden && overlay.contains(el);
  if (!inOverlay) {
    const r = el.closest("[data-row]"); if (r) cs.mem[r.dataset.row] = el.dataset.k;
    if (!el.closest("#nav")) cs.last = el.dataset.k;
  }
  if (!o.noScroll) ensureVisible(el);
  updateNavAway();
  if (!inOverlay && cs.def.onFocus) cs.def.onFocus(el, cs);
}
function ensureTrack(el) {
  const tr = el.closest(".track"); if (!tr) return;
  let item = el; while (item.parentElement !== tr) item = item.parentElement;
  const view = tr.parentElement, cst = getComputedStyle(view);
  const vw = view.clientWidth - parseFloat(cst.paddingLeft) - parseFloat(cst.paddingRight);
  let tx = +tr.dataset.tx || 0; const L = item.offsetLeft, R = L + item.offsetWidth;
  if (L + tx < 0) tx = -L; else if (R + tx > vw) tx = vw - R;
  tr.dataset.tx = tx; tr.style.transform = `translateX(${tx}px)`;
}
function setTy(sc, ty) {
  sc.dataset.ty = ty; sc.style.transform = `translateY(${ty}px)`;
  if (sc === $(".scroller", cs.el)) cs.el.classList.toggle("scrolled", ty < -20);
}
function ensureVisible(el) {
  if (overlay.contains(el)) return;
  ensureTrack(el);
  if (el.closest("#nav")) { const sc = $(".scroller", cs.el); if (sc) setTy(sc, 0); return; }
  const sc = el.closest(".scroller"); if (!sc) return;
  const sec = el.closest(".sec"); if (!sec) return;
  let ty = +sc.dataset.ty || 0;
  if (sec.hasAttribute("data-top")) ty = 0;
  else {
    const y = sec.offsetTop, h = sec.offsetHeight;
    const top = +(sc.dataset.top ?? 130), viewH = +(sc.dataset.h || 1080), bot = +(sc.dataset.bot ?? 40);
    if (y + ty < top) ty = top - y; else if (y + h + ty > viewH - bot) ty = viewH - bot - (y + h);
  }
  setTy(sc, Math.min(0, ty));
}
function updateNavAway() {
  stage.classList.toggle("nav-away", !!cs && cs.el.classList.contains("scrolled") && !(cur && cur.closest("#nav")));
}

/* ---------------- écrans et historique ---------------- */
function snapshot() { return { id: cs.id, params: cs.params, mem: { ...cs.mem }, last: cs.last, extra: cs.extra ? { ...cs.extra } : {} }; }
function unmount() { if (cs && cs.def.leave) cs.def.leave(cs); if (cs) (cs.timers || []).forEach(clearTimeout); }
function go(id, params = {}, opts = {}) {
  if (cs) {
    const snap = snapshot();
    if (ROOTS.includes(cs.id) && !stack.length) rootState[cs.id] = snap;
    if (opts.root) stack = [];
    else if (opts.push !== false) stack.push(snap);
  }
  unmount();
  mount(id, params, opts.restore || (opts.root ? rootState[id] : null), opts);
}
function mount(id, params, restore, opts = {}) {
  const def = screens[id];
  const el = document.createElement("section");
  el.className = "screen " + id + (opts.noAnim ? "" : " enter");
  cs = { id, params, def, el, mem: restore ? { ...restore.mem } : {}, last: restore ? restore.last : null, extra: restore ? { ...restore.extra } : {}, timers: [], restoring: !!restore };
  el.innerHTML = def.render(params, cs);
  screensEl.replaceChildren(el);
  stage.classList.toggle("has-nav", !!def.nav);
  $$(".pill, .icon-btn", nav).forEach(b => b.classList.toggle("sel", b.dataset.k === def.navKey));
  cur = null;
  if (def.mount) def.mount(cs);
  for (const k of Object.values(cs.mem)) { const e = byKey(k); if (e) ensureTrack(e); }
  const target = (restore && vis(byKey(restore.last)) && byKey(restore.last)) || (def.initial && def.initial(cs)) || focusables()[0];
  if (target) focus(target);
  updateNavAway();
  later(() => el.classList.remove("enter"), 260);
  cs.restoring = false;
}
function later(fn, ms) { const c = cs; const t = setTimeout(() => { if (cs === c) fn(); }, ms); c.timers.push(t); return t; }
function refresh() { const s = snapshot(); unmount(); mount(cs.id, cs.params, s, { noAnim: true }); }

function back() {
  if (!overlay.hidden) { closeOverlay(); return; }
  if (cs.def.back && cs.def.back(cs) === true) return;
  if (stack.length) { const s = stack.pop(); unmount(); mount(s.id, s.params, s); return; }
  if (!cs.def.nav) return;
  if (cur && cur.closest("#nav")) { if (cs.id !== "home") go("home", {}, { root: true }); else exitDialog(); return; }
  if (cs.id === "home") { const h = byKey("hero-play"); if (cur !== h) { focus(h); return; } }
  focus(navEl(cs.def.navKey));
}

function activate(el) {
  const a = el.dataset.act; if (!a) return;
  const i = a.indexOf(":"), cmd = i < 0 ? a : a.slice(0, i), arg = i < 0 ? "" : a.slice(i + 1);
  if (!overlay.hidden && overlay.contains(el)) { if (ovHandler && ovHandler(cmd, arg, el) !== false) return; }
  else if (cs.def.act && cs.def.act(cmd, arg, el, cs) === true) return;
  globalAct(cmd, arg, el);
}
function globalAct(cmd, arg, el) {
  switch (cmd) {
    case "tab": return goTab(arg);
    case "open": { const t = T[arg]; return go(t.type === "serie" ? "serie" : "film", { id: arg }); }
    case "play": return go("player", { id: arg });
    case "playep": { const [id, s, n] = arg.split(":"); return go("player", { id, s: +s, n: +n }); }
    case "live": return go("player", { ch: arg });
    case "list": return toggleList(arg, el);
    case "trailer": return toast("Bande-annonce : lecture plein écran (démo, aucune vidéo)", "trailer");
    case "person": return go("search", { q: P[arg][0] });
    case "demo": return openDemo();
    case "close": return closeOverlay();
    case "versions": return versionSheet(T[arg], false);
    case "fix": return toast("Correspondance de fiche : choix manuel (démo)", "link");
  }
}
function goTab(id) {
  if (cs.id === id && !stack.length) { const l = byKey(cs.last); focus(vis(l) ? l : (cs.def.initial && cs.def.initial(cs)) || focusables()[0]); return; }
  go(id, {}, { root: true });
}
function toggleList(id, el) {
  const on = !st.myList.has(id);
  on ? st.myList.add(id) : st.myList.delete(id);
  if (on) st.demo.listEmpty = false;
  if (el && el.classList.contains("rbtn")) {
    el.classList.toggle("on", on);
    el.innerHTML = (on ? I.check : I.plus) + `<span class="lbl">${on ? "Dans Ma liste" : "Ajouter à Ma liste"}</span>`;
  }
  toast(on ? `« ${T[id].t} » ajouté à Ma liste` : `« ${T[id].t} » retiré de Ma liste`, on ? "check" : "bookmark");
}

/* ---------------- toast & overlays ---------------- */
let toastT;
function toast(msg, icon = "check") {
  toastEl.innerHTML = (I[icon] || "") + `<span>${esc(msg)}</span>`;
  toastEl.classList.add("show"); clearTimeout(toastT);
  toastT = setTimeout(() => toastEl.classList.remove("show"), 2800);
}
let ovHandler = null, ovPrev = null, ovClose = null;
function openOverlay(html, o = {}) {
  if (overlay.hidden) ovPrev = cur;
  overlay.innerHTML = `<div class="scrim"></div>` + html; overlay.hidden = false;
  ovHandler = o.onAct || null; ovClose = o.onClose || null;
  if (cur) cur.classList.remove("is-focus");
  cur = null;
  focus((o.initial && byKey(o.initial)) || $(".f", overlay));
}
function closeOverlay() {
  overlay.hidden = true; overlay.innerHTML = "";
  const p = ovPrev, cb = ovClose; ovPrev = null; ovHandler = null; ovClose = null; cur = null;
  if (cb) cb();
  if (!cur) { if (vis(p)) focus(p, { noScroll: true }); else { const f = (cs.def.initial && cs.def.initial(cs)) || focusables()[0]; if (f) focus(f); } }
}
const opt = (k, act, label, o = {}) =>
  `<button class="opt f" data-k="${k}" data-act="${act}">${o.check !== undefined ? `<span class="ck">${o.check ? I.check : ""}</span>` : ""}<span>${label}</span>${o.small ? `<small>${o.small}</small>` : ""}${o.sw !== undefined ? `<span class="sw ${o.sw ? "on" : ""}"></span>` : ""}</button>`;

function exitDialog() {
  openOverlay(`<div class="dialog"><div class="ico">${I.power}</div><h2>Quitter ADITV ?</h2><p>Vous retrouverez votre position dans chaque rubrique au prochain lancement.</p>
    <div class="btns" data-row="dlg"><button class="btn primary f" data-k="x-stay" data-act="close">Rester</button><button class="btn f" data-k="x-quit" data-act="quit">Quitter</button></div></div>`,
    { onAct: (cmd) => { if (cmd === "quit") { closeOverlay(); go("start", {}, { root: true }); return; } return false; } });
}

function demoSheet() {
  const d = st.demo;
  return `<div class="sheet"><h2>États de démonstration</h2><p class="lead">Ces commandes n'existent que dans le prototype : elles montrent les chargements, erreurs et listes vides. Rien n'est réellement connecté.</p>
    <h4>Écrans</h4>
    ${opt("dm-cat", "d:cat", "Catalogue : erreur réseau", { sw: d.catalogError })}
    ${opt("dm-list", "d:list", "Ma liste : état vide", { sw: d.listEmpty })}
    ${opt("dm-play", "d:play", "Lecteur : flux en échec", { sw: d.playerError })}
    <h4>Préférences</h4>
    ${opt("dm-rm", "d:rm", "Réduire les animations", { sw: rm })}
    ${opt("dm-snd", "d:snd", "Son de l'ouverture", { sw: st.sound })}
    ${opt("dm-intro", "d:intro", "Rejouer l'ouverture animée")}</div>`;
}
function openDemo() {
  if (!overlay.hidden && $(".sheet h2", overlay) && $(".sheet h2", overlay).textContent.startsWith("États")) { closeOverlay(); return; }
  const handler = (cmd, arg) => {
    if (cmd !== "d") return false;
    const k = cur && cur.dataset.k;
    if (arg === "cat") st.demo.catalogError = !st.demo.catalogError;
    if (arg === "list") st.demo.listEmpty = !st.demo.listEmpty;
    if (arg === "play") st.demo.playerError = !st.demo.playerError;
    if (arg === "rm") toggleRM();
    if (arg === "snd") st.sound = !st.sound;
    if (arg === "intro") { closeOverlay(); go("intro", {}, { root: true }); return; }
    openOverlay(demoSheet(), { onAct: handler, onClose: afterDemo, initial: k });
  };
  const afterDemo = () => { if (["films", "series", "mylist"].includes(cs.id)) { st.catLoaded = {}; refresh(); } };
  openOverlay(demoSheet(), { onAct: handler, onClose: afterDemo });
}

/* ================= ÉCRAN : démarrage ================= */
screens.start = {
  render: () => `<div class="start-card"><img src="img/logo_full.png" alt="ADITV">
    <h1>Prototype de design de l'interface TV</h1>
    <p>Écran 1920 × 1080 piloté au clavier comme une télécommande. Films, séries, chaînes, personnes et mesures sont <b>fictifs</b> ; aucune connexion, aucun flux, aucun service tiers.</p>
    <div class="btns" data-row="start"><button class="btn primary f" data-k="go" data-act="begin">${I.play} Démarrer</button>
    <button class="btn toggle f ${st.sound ? "on" : ""}" data-k="snd" data-act="snd">Son de l'ouverture <span class="tgl">${st.sound ? "Activé" : "Désactivé"}</span></button></div>
    <div class="fine">Flèches = croix directionnelle · Entrée = OK · Échap ou ⌫ = Retour · D = états de démo · M = animations réduites</div></div>`,
  act(cmd, arg, el) {
    if (cmd === "begin") { go("intro", {}, { root: true }); return true; }
    if (cmd === "snd") { st.sound = !st.sound; el.classList.toggle("on", st.sound); $(".tgl", el).textContent = st.sound ? "Activé" : "Désactivé"; return true; }
  },
};

/* ================= ÉCRAN : ouverture animée ================= */
screens.intro = {
  render: () => `<div class="glow"></div><div class="lockup"><div class="symbox"><img class="sym" src="img/logo_a.png" alt=""><div class="sweep"></div></div><div class="word-wrap"><img class="word" src="img/logo_word.png" alt="ADITV"></div></div>`,
  mount(cs) {
    if (st.sound) { try { const a = new Audio("img/signature.wav"); a.volume = .8; a.play().catch(() => {}); cs.audio = a; } catch (e) {} }
    later(() => go("home", {}, { root: true }), rm ? 1100 : 2500);
  },
  leave(cs) { if (cs.audio) { const a = cs.audio; setTimeout(() => a.pause(), 400); } },
  key(e) { if (["Enter", " ", "Escape", "Backspace"].includes(e.key)) { go("home", {}, { root: true }); return true; } return true; },
};

/* ================= ÉCRAN : accueil ================= */
const HERO = [
  { id: "silence", kicker: "La sélection ADITV", pos: "28% 40%", play: "Reprendre", left: "42 min restantes", prog: .69 },
  { id: "dakar", kicker: "Nouvelle saison", pos: "60% 30%", play: "Reprendre S1 É4", left: "S1 · É4 · 34 min restantes", prog: .35 },
];
function heroCopy(i) {
  const h = HERO[i], t = T[h.id], inList = st.myList.has(h.id);
  return `<div class="kicker">${esc(h.kicker)}</div><h1 class="display">${esc(t.t)}</h1>
    <div class="meta">${dotJoin([String(t.y), t.type === "serie" ? "Série · " + t.g : t.g, t.d])}<i class="dot"></i>${badgesHtml(t)}</div>
    <p class="tagline">${esc(t.tag)}</p>
    <div class="btns" data-row="hero">
      <button class="btn primary f" data-k="hero-play" data-act="${t.type === "serie" ? `playep:${h.id}:1:4` : "play:" + h.id}">${I.play}${esc(h.play)}</button>
      <button class="btn f" data-k="hero-info" data-act="open:${h.id}">${I.info}Plus d'infos</button>
      <button class="rbtn f ${inList ? "on" : ""}" data-k="hero-list" data-act="list:${h.id}">${inList ? I.check : I.plus}<span class="lbl">${inList ? "Dans Ma liste" : "Ajouter à Ma liste"}</span></button>
    </div>
    <div class="resume"><div class="bar"><i style="width:${h.prog * 100}%"></i></div>${esc(h.left)}</div>
    <div class="dots">${HERO.map((_, j) => `<i class="${i === j ? "on" : ""}"></i>`).join("")}</div>`;
}
function setHero(cs, i, keepFocus) {
  i = (i + HERO.length) % HERO.length; cs.extra.hero = i;
  $$(".hero-bg .layer", cs.el).forEach((l, j) => l.classList.toggle("on", j === i));
  const copy = $(".hero-copy", cs.el);
  copy.classList.add("swap");
  later(() => {
    const k = cur && cur.dataset.k;
    copy.innerHTML = heroCopy(i); copy.classList.remove("swap");
    if (keepFocus && k && k.startsWith("hero-")) focus(byKey(k));
  }, rm ? 0 : 170);
}
screens.home = {
  nav: true, navKey: "nav-home",
  render(p, cs) {
    const hi = cs.extra.hero || 0;
    const live = ["teranga", "adicine", "atl24", "lumiere", "rythmes", "ocean"].map(chById);
    return `<div class="hero-bg">${HERO.map((h, j) => `<div class="layer ${j === hi ? "on" : ""} ${T[h.id].shift ? "shift" : ""}"><img src="img/${T[h.id].hero}.jpg" alt="" style="object-position:${h.pos}"></div>`).join("")}</div>
    <div class="scroller" data-bot="10">
      <div class="sec hero" data-top><div class="hero-copy">${heroCopy(hi)}</div></div>
      ${row("Reprendre la lecture", "r-resume", [
        wcard("ile", "rs-ile", { prog: .55, sub: "S1 · É3 · 18 min restantes", act: "playep:ile:1:3" }),
        wcard("paris", "rs-paris", { prog: .38, sub: "1 h 09 restantes", act: "play:paris" }),
        wcard("expedition", "rs-exp", { prog: .21, sub: "1 h 35 restantes", act: "play:expedition" }),
        wcard("reve", "rs-reve", { prog: .62, sub: "É2 · 21 min restantes", act: "playep:reve:1:2" }),
        wcard("silence", "rs-sil", { prog: .69, sub: "42 min restantes", act: "play:silence" })].join(""))}
      ${row("Votre soirée commence ici", "r-soir", ["heritiers", "coeurs", "frontiere", "mumbai", "jours", "mensonge"].map(id => pcard(id, "so-" + id)).join(""))}
      ${row("En direct maintenant", "r-live", live.map(c => liveCard(c, "lv-" + c.id)).join(""), { small: "Guide des programmes · 21:12" })}
      ${row("Séries à suivre", "r-series", ["dakar", "phare", "ndar", "sable", "derive", "ile"].map(id => wcard(id, "se-" + id)).join(""))}
      ${row("Ajouts récents", "r-new", ["kolda", "fleuve", "sama", "bantu", "mensonge", "frontiere"].map(id => wcard(id, "nw-" + id, { sub: `${T[id].g} · ${T[id].d}` })).join(""))}
    </div>`;
  },
  initial: () => byKey("hero-play"),
  mount(cs) {
    const tick = () => {
      later(tick, 9000);
      if (rm || !overlay.hidden || cs.el.classList.contains("scrolled")) return;
      if (cur && cur.closest('[data-row="hero"]')) return;
      setHero(cs, (cs.extra.hero || 0) + 1, false);
    };
    later(tick, 9000);
  },
  dpad(dir, el, cs) {
    if (!el.closest('[data-row="hero"]')) return false;
    if (dir === "left" && el.dataset.k === "hero-play") { setHero(cs, (cs.extra.hero || 0) - 1, true); return true; }
    if (dir === "right" && el.dataset.k === "hero-list") { setHero(cs, (cs.extra.hero || 0) + 1, true); focus(byKey("hero-play")); return true; }
    return false;
  },
  act(cmd, arg, el, cs) {
    if (cmd === "list" && el.dataset.k === "hero-list") { toggleList(arg, el); return true; }
  },
};

/* ================= FICHES film / série ================= */
function backdrop(t) {
  if (t.hero) return `<div class="backdrop"><div class="full ${t.shift ? "shift" : ""}"><img src="img/${t.hero}.jpg" alt="" style="object-position:${t.type === "serie" ? "60% 30%" : "28% 40%"}"></div></div>`;
  return `<div class="backdrop"><div class="amb" style="background-image:url(img/${t.w}.jpg)"></div><div class="key"><img src="img/${t.w}.jpg" alt=""></div><div class="fade"></div></div>`;
}
function detailTop(t, id, primary) {
  const inList = st.myList.has(id);
  return `<div class="kicker">${t.type === "serie" ? "Série" : "Film"}</div>
    <h1 class="display">${esc(t.t)}</h1>
    <div class="meta">${dotJoin([String(t.y), t.g, t.d, t.age])}<i class="dot"></i>${badgesHtml(t)}</div>
    <p class="syn">${esc(t.syn)}</p>
    <div class="btns" data-row="d-btns">
      ${primary}
      <button class="btn f" data-k="d-trailer" data-act="trailer:${id}">${I.trailer}Bande-annonce</button>
      <button class="rbtn f ${inList ? "on" : ""}" data-k="d-list" data-act="list:${id}">${inList ? I.check : I.plus}<span class="lbl">${inList ? "Dans Ma liste" : "Ajouter à Ma liste"}</span></button>
      <button class="rbtn f" data-k="d-ver" data-act="versions:${id}">${I.layers}<span class="lbl">Versions</span></button>
    </div>`;
}
function castRow(t) {
  return `<div class="sec d-sec first"><h3 class="row-title">Distribution</h3><div class="rowview"><div class="track" data-row="cast" style="gap:44px">${
    t.cast.map(([pid, role], i) => `<button class="person f" data-k="p-${i}" data-act="person:${pid}"><div class="ph"><img src="img/${P[pid][1]}.jpg" alt=""></div><b>${esc(P[pid][0])}</b><span>${esc(role)}</span></button>`).join("")}</div></div></div>`;
}
function infoBlock(t) {
  return `<div class="sec d-sec"><h3 class="row-title">À propos</h3><dl class="info-grid">
    <div><dt>${t.type === "serie" ? "Création" : "Réalisation"}</dt><dd>${esc(t.dir.replace(/^(Création|Réalisation) : /, ""))}</dd></div>
    <div><dt>Scénario</dt><dd>${esc(t.writer)}</dd></div>
    <div><dt>Public</dt><dd>${t.age === "Tous" ? "Tous publics" : "Déconseillé aux moins de " + t.age.replace("+", " ans")}</dd></div>
    <div><dt>Audio</dt><dd>Français 5.1 · Anglais 5.1</dd></div>
    <div><dt>Sous-titres</dt><dd>Français · Français SME · Anglais</dd></div>
    <div><dt>Qualité annoncée</dt><dd>${t.versions.map(v => `${v[0]} ${v[1]}`).join(" · ")}</dd></div>
    <div class="full"><p>Fiche de démonstration · langue et qualité « annoncées » par le fournisseur, non mesurées.</p><button class="btn small f" data-k="d-fix" data-act="fix">${I.link}Corriger la correspondance</button></div>
  </dl></div>`;
}
screens.film = {
  render({ id }) {
    const t = T[id];
    const primary = `<button class="btn primary f" data-k="d-play" data-act="play:${id}">${I.play}${t.prog ? "Reprendre" : "Lecture"}</button>`;
    return `${backdrop(t)}<div class="scroller" data-top="110">
      <div class="sec d-main" data-top>${detailTop(t, id, primary)}
        ${t.prog ? `<div class="resume"><div class="bar"><i style="width:${t.prog * 100}%"></i></div>${esc(t.left)}</div>` : ""}
        <div class="versions">${versionsLine(t)}</div></div>
      ${castRow(t)}
      ${row("Vous aimerez aussi", "reco", t.reco.map(r => wcard(r, "rc-" + r)).join(""), { cls: "d-sec" })}
      ${infoBlock(t)}
    </div>`;
  },
  initial: () => byKey("d-play"),
  act(cmd, arg, el) { if (cmd === "list") { toggleList(arg, el); return true; } },
};

function episodesOf(t, id, s) {
  if (t.seasons) return t.seasons[s];
  return Array.from({ length: 6 }, (_, i) => [`Épisode ${i + 1}`, 48 + (i * 3) % 9, "Résumé de démonstration : chaque épisode affiche ici deux lignes de synopsis au maximum.", t.resume && i + 1 < t.resume[1] ? 1 : (t.resume && i + 1 === t.resume[1] ? t.prog : 0)]);
}
function epCards(t, id, s) {
  return episodesOf(t, id, s).map((e, i) => {
    const n = i + 1, w = e[3];
    const img = t.seasons ? `ep${(i + (s - 1) * 2) % 6 + 1}` : t.w;
    return `<button class="card e f" data-k="ep-${s}-${n}" data-act="playep:${id}:${s}:${n}"><div class="art"><img src="img/${img}.jpg" alt="" style="${t.seasons ? "" : `object-position:${(i * 23) % 100}% 50%`}">${w === 1 ? `<span class="seen">${I.check}</span>` : ""}${w > 0 && w < 1 ? `<div class="prog"><i style="width:${w * 100}%"></i></div>` : ""}<span class="ep-dur">${e[1]} min</span></div>
      <div class="cap"><b><span class="epn">É${n}</span> · ${esc(e[0])}</b><p>${esc(e[2])}</p></div></button>`;
  }).join("");
}
screens.serie = {
  render({ id }, cs) {
    const t = T[id], s = cs.extra.season || (t.resume ? t.resume[0] : 1);
    cs.extra.season = s;
    const nS = t.seasons ? Object.keys(t.seasons).length : 1;
    const r = t.resume;
    const primary = `<button class="btn primary f" data-k="d-play" data-act="playep:${id}:${r ? r[0] : 1}:${r ? r[1] : 1}">${I.play}${r ? `Reprendre S${r[0]} É${r[1]}` : "Lecture S1 É1"}</button>`;
    return `${backdrop(t)}<div class="scroller" data-top="110">
      <div class="sec d-main" data-top>${detailTop(t, id, primary)}
        ${r ? `<div class="resume"><div class="bar"><i style="width:${t.prog * 100}%"></i></div>S${r[0]} · É${r[1]} en cours</div>` : ""}
        <div class="versions">${versionsLine(t)}</div></div>
      <div class="sec d-sec first">
        <div class="seasons" data-row="seasons">${Array.from({ length: nS }, (_, i) => `<button class="chip f ${i + 1 === s ? "sel" : ""}" data-k="season-${i + 1}" data-act="season:${i + 1}">Saison ${i + 1}</button>`).join("")}
          <span class="count" id="ep-count">${episodesOf(t, id, s).length} épisodes</span></div>
        <div class="rowview eps-fade"><div class="track" data-row="eps">${epCards(t, id, s)}</div></div>
      </div>
      ${castRow(t).replace("d-sec first", "d-sec")}
      ${row("Plus de séries", "reco", t.reco.map(x => wcard(x, "rc-" + x)).join(""), { cls: "d-sec" })}
      ${infoBlock(t)}
    </div>`;
  },
  initial: () => byKey("d-play"),
  act(cmd, arg, el, cs) {
    if (cmd === "list") { toggleList(arg, el); return true; }
    if (cmd === "season") {
      const s = +arg, id = cs.params.id, t = T[id];
      if (s === cs.extra.season) return true;
      cs.extra.season = s;
      $$('[data-row="seasons"] .chip', cs.el).forEach(c => c.classList.toggle("sel", c.dataset.k === "season-" + s));
      const fade = $(".eps-fade", cs.el), track = $('[data-row="eps"]', cs.el);
      fade.classList.add("swap"); delete cs.mem.eps;
      later(() => { track.innerHTML = epCards(t, id, s); track.dataset.tx = 0; track.style.transform = ""; $("#ep-count", cs.el).textContent = episodesOf(t, id, s).length + " épisodes"; fade.classList.remove("swap"); }, rm ? 0 : 170);
      return true;
    }
  },
};

/* ================= CATALOGUE films / séries ================= */
const GENRES = {
  film: [["all", "Tous"], ["sf", "Science-fiction"], ["thriller", "Thriller"], ["drame", "Drame"], ["romance", "Romance"], ["action", "Action"], ["aventure", "Aventure"], ["comedie", "Comédie"], ["docu", "Documentaire"]],
  serie: [["all", "Toutes"], ["thriller", "Thriller"], ["drame", "Drame"], ["aventure", "Aventure"], ["docu", "Documentaire"], ["comedie", "Comédie"]],
};
function catalog(kind) {
  const label = kind === "film" ? "Films" : "Séries";
  const items = () => Object.keys(T).filter(id => T[id].type === kind);
  function body(cs) {
    const f = cs.extra.filter || "all", sort = cs.extra.sort || "recent";
    let ids = items().filter(id => f === "all" || T[id].gk.includes(f));
    ids.sort(sort === "az" ? (a, b) => T[a].t.localeCompare(T[b].t, "fr") : (a, b) => T[b].y - T[a].y);
    const n = ids.length, word = kind === "film" ? (n > 1 ? "films" : "film") : (n > 1 ? "séries" : "série");
    $("#cat-sub", cs.el).textContent = `${n} ${word} · catalogue de démonstration`;
    if (!n) {
      const g = GENRES[kind].find(x => x[0] === f)[1];
      return `<div class="sec" style="height:560px">${stateBox({ icon: I.film, title: `Aucune ${kind === "film" ? "film" : "série"} « ${g} » pour l'instant`.replace("Aucune film", "Aucun film"), text: "Les titres apparaîtront ici dès que votre fournisseur les proposera. Essayez un autre genre.", row: "empty", btns: `<button class="btn primary f" data-k="e-all" data-act="filter:all">Voir ${kind === "film" ? "tous les films" : "toutes les séries"}</button>`, style: "top:40px" })}</div>`;
    }
    let html = "";
    for (let i = 0; i < ids.length; i += 4) html += `<div class="sec grid-row">${ids.slice(i, i + 4).map(id => wcard(id, "g-" + id, { sub: `${T[id].g} · ${T[id].y} · ${T[id].d}` })).join("")}</div>`;
    return html;
  }
  function load(cs, instant) {
    const el = $("#cat-body", cs.el);
    const fail = st.demo.catalogError;
    if (instant && !fail) { el.innerHTML = body(cs); return; }
    el.innerHTML = `${[0, 1, 2].map(() => `<div class="sec grid-row">${[0, 1, 2, 3].map(() => `<div class="skel"><div class="a shimmer"></div><div class="l1 shimmer"></div><div class="l2 shimmer"></div></div>`).join("")}</div>`).join("")}`;
    $("#cat-sub", cs.el).textContent = "Chargement du catalogue…";
    const wantKey = cs.restoring ? cs.last : null;
    later(() => {
      if (fail) {
        $("#cat-sub", cs.el).textContent = "Catalogue indisponible";
        el.innerHTML = `<div class="sec" style="height:560px">${stateBox({ err: true, icon: I.cloud, title: "Impossible de charger le catalogue", text: "Le serveur du fournisseur ne répond pas. Vos favoris et votre progression sont conservés sur cet appareil.", code: "Simulation · délai dépassé après 30 s", row: "err", btns: `<button class="btn primary f" data-k="e-retry" data-act="retry">${I.retry}Réessayer</button><button class="btn f" data-k="e-home" data-act="tab:home">Retour à l'accueil</button>`, style: "top:30px" })}</div>`;
        if (!cur || !cur.isConnected || cur.closest('[data-row="cat-chips"]')) focus(byKey("e-retry"));
        return;
      }
      st.catLoaded[kind] = true;
      el.innerHTML = body(cs);
      const w = wantKey && byKey(wantKey);
      if (w && cur && cur.closest('[data-row="cat-chips"]')) focus(w);
      else if (!cur || !cur.isConnected) focus(byKey("f-" + (cs.extra.filter || "all")));
    }, rm ? 300 : 650);
  }
  return {
    nav: true, navKey: kind === "film" ? "nav-films" : "nav-series",
    render: (p, cs) => `<div class="scroller" data-top="150">
      <div class="sec cat-head" data-top><h1>${label}</h1><div class="sub" id="cat-sub"></div>
        <div class="cat-bar"><div class="chips" data-row="cat-chips">${GENRES[kind].map(([k, l]) => `<button class="chip f ${(cs.extra.filter || "all") === k ? "sel" : ""}" data-k="f-${k}" data-act="filter:${k}">${l}</button>`).join("")}</div>
        <button class="btn small f sort" data-k="sort" data-act="sort">${I.sort}Trier : <span class="sv">${cs.extra.sort === "az" ? "A → Z" : "Plus récents"}</span></button></div>
      </div>
      <div id="cat-body"></div></div>`,
    mount(cs) { load(cs, !!st.catLoaded[kind]); },
    initial: (cs) => byKey("f-" + (cs.extra.filter || "all")),
    act(cmd, arg, el, cs) {
      if (cmd === "filter") {
        cs.extra.filter = arg;
        $$('[data-row="cat-chips"] .chip', cs.el).forEach(c => c.classList.toggle("sel", c.dataset.k === "f-" + arg));
        if (!el.classList.contains("chip")) focus(byKey("f-" + arg));
        load(cs, false); return true;
      }
      if (cmd === "sort") { cs.extra.sort = cs.extra.sort === "az" ? "recent" : "az"; $(".sv", el).textContent = cs.extra.sort === "az" ? "A → Z" : "Plus récents"; load(cs, false); return true; }
      if (cmd === "retry") { st.demo.catalogError = false; focus(byKey("f-" + (cs.extra.filter || "all"))); load(cs, false); return true; }
    },
  };
}
screens.films = catalog("film");
screens.series = catalog("serie");

/* ================= DIRECT & GUIDE ================= */
const WS = 20 * 60 + 30, WE = WS + 180, LANE = 1444, PPM = LANE / 180;
const LIVE_FILTERS = [["all", "Toutes"], ["fav", "Favoris"], ["sport", "Sport"], ["cinema", "Cinéma"], ["info", "Info"], ["series", "Séries"], ["kids", "Jeunesse"]];
function guideRows(f) {
  const list = CH.filter(c => f === "all" || (f === "fav" ? st.favCh.has(c.id) : c.cat === f));
  return list.map(ch => {
    let lane = "";
    if (!ch.progs) lane = `<button class="gp na f" data-k="gp-${ch.id}-na" data-act="noguide:${ch.id}" style="left:0;width:${LANE - 4}px">${I.warn}<b>Guide indisponible pour cette chaîne</b><span>· OK pour associer un guide</span></button>`;
    else ch.progs.forEach((p, i) => {
      const s = toMin(p[0]), e = toMin(p[1], s);
      if (e <= WS || s >= WE) return;
      const a = Math.max(s, WS), b = Math.min(e, WE);
      if ((b - a) * PPM < 64) { lane += `<span class="gp ghost" style="left:${(a - WS) * PPM}px;width:${(b - a) * PPM - 6}px"></span>`; return; }
      const cls = e <= NOW ? "past" : s <= NOW ? "now" : "";
      const bell = st.reminders.has(ch.id + ":" + i) ? `<span class="bell">${I.bell}</span>` : "";
      lane += `<button class="gp f ${cls}" data-k="gp-${ch.id}-${i}" data-act="prog:${ch.id}:${i}" style="left:${(a - WS) * PPM}px;width:${(b - a) * PPM - 6}px">${bell}<b>${esc(p[2])}</b><span>${p[0]} – ${p[1]}</span>${cls === "now" ? `<i class="pbar" style="width:${(NOW - a) * PPM}px"></i>` : ""}</button>`;
    });
    return `<div class="sec grow"><button class="gch f" data-k="ch-${ch.id}" data-act="live:${ch.id}"><span class="n">${ch.n}</span><span class="nm">${esc(ch.name)}</span>${st.favCh.has(ch.id) ? `<span class="star">${I.star}</span>` : ""}</button><div class="glane">${lane}</div></div>`;
  }).join("") || `<div class="sec" style="height:400px">${stateBox({ icon: I.star, title: "Aucune chaîne dans ce filtre", text: "Ajoutez une chaîne aux favoris depuis le lecteur ou sa fiche.", style: "top:20px" })}</div>`;
}
function livePreview(ch, p) {
  const img = p && p[4] ? `<img src="img/${p[4]}.jpg" alt="">` : `<div class="noimg">${esc(ch.name)}</div>`;
  let info;
  if (!p) info = `<h2>Programme non communiqué</h2><p>Le fournisseur ne publie pas de guide pour cette chaîne. Vous pouvez l'associer à une autre source de guide.</p>`;
  else {
    const s = toMin(p[0]), e = toMin(p[1], s), isNow = s <= NOW && NOW < e, past = e <= NOW;
    const idx = ch.progs.indexOf(p), nx = ch.progs[idx + 1];
    const rem = st.reminders.has(ch.id + ":" + idx);
    info = `<h2>${esc(p[2])}</h2><div class="time">${isNow ? `<span class="live-dot">EN DIRECT</span>` : ""}<span>${past ? "Terminé · " : !isNow ? "À venir · " : ""}${p[0]} – ${p[1]}</span><span>${esc(p[3])}</span>${rem ? `<span style="color:var(--ac)">· Rappel programmé</span>` : ""}</div>
      <p>${esc(p[5] || "Informations de programme fournies par le guide (démonstration).")}</p>${nx ? `<div class="next">Ensuite <b>${nx[0]} · ${esc(nx[2])}</b></div>` : ""}`;
  }
  const isNowP = p && toMin(p[0]) <= NOW && NOW < toMin(p[1], toMin(p[0]));
  return `<div class="live-prev">${img}<div class="shade"></div>${isNowP ? `<span class="tag-live">EN DIRECT</span><div class="prog"><i style="width:${progPct(p) * 100}%"></i></div>` : ""}</div>
    <div class="live-info"><div class="ch"><span class="num">${ch.n}</span>${esc(ch.name)}${st.favCh.has(ch.id) ? `<span class="star" style="width:20px;height:20px;color:var(--ac)">${I.star}</span>` : ""}</div>${info}</div>`;
}
screens.live = {
  nav: true, navKey: "nav-live",
  render(p, cs) {
    const f = cs.extra.filter || "all", nowX = (NOW - WS) * PPM;
    const ticks = []; for (let m = WS; m < WE; m += 30) ticks.push(`<span style="left:${(m - WS) * PPM}px">${hm(m)}</span>`);
    return `<div class="live-top" id="live-top"></div>
      <div class="guide-chips"><div class="chips" data-row="lf">${LIVE_FILTERS.map(([k, l]) => `<button class="chip f ${f === k ? "sel" : ""}" data-k="lf-${k}" data-act="lfilter:${k}">${l}</button>`).join("")}</div><span class="guide-day">Aujourd'hui · vendredi 20 novembre</span></div>
      <div class="ruler">${ticks.join("")}<span style="left:${nowX}px;color:var(--ac);font-weight:700;transform:translateX(-50%)">21:12</span></div>
      <div class="gwrap" style="position:absolute;left:0;right:0;top:584px;bottom:0;overflow:hidden">
        <div class="scroller" data-top="0" data-h="496" data-bot="4" id="grows">${guideRows(f)}</div>
        <div class="nowline" style="left:${380 + nowX - 1}px;top:0"></div>
      </div>`;
  },
  initial: (cs) => byKey("gp-adicine-1") || $(".gp, .gch", cs.el),
  onFocus(el, cs) {
    const k = el.dataset.k || "";
    let ch, p = null;
    if (k.startsWith("gp-")) { const [, id, i] = k.split("-"); ch = chById(id); p = i === "na" ? null : ch.progs[+i]; }
    else if (k.startsWith("ch-")) { ch = chById(k.slice(3)); p = progOf(ch); }
    else return;
    const key = ch.id + "/" + (p ? p[0] : "na");
    if (cs.extra.prev === key) return;
    cs.extra.prev = key;
    $("#live-top", cs.el).innerHTML = livePreview(ch, p);
  },
  mount(cs) { cs.extra.prev = null; const f = cur || byKey("gp-adicine-1"); if (f) this.onFocus(f, cs); },
  act(cmd, arg, el, cs) {
    if (cmd === "lfilter") {
      cs.extra.filter = arg;
      $$('[data-row="lf"] .chip', cs.el).forEach(c => c.classList.toggle("sel", c.dataset.k === "lf-" + arg));
      const g = $("#grows", cs.el); g.innerHTML = guideRows(arg); setTy(g, 0); return true;
    }
    if (cmd === "prog") {
      const [id, i] = arg.split(":"), ch = chById(id), p = ch.progs[+i];
      const s = toMin(p[0]), e = toMin(p[1], s);
      if (s <= NOW && NOW < e) { go("player", { ch: id }); return true; }
      if (e <= NOW) { toast("Programme terminé · aucun replay déclaré par le fournisseur", "info"); return true; }
      const rk = id + ":" + i, has = st.reminders.has(rk);
      openOverlay(`<div class="dialog"><div class="ico">${I.bell}</div><h2>${esc(p[2])}</h2>
        <p>${esc(ch.name)} · aujourd'hui ${p[0]} – ${p[1]}<br>${has ? "Un rappel est programmé 5 minutes avant le début." : "ADITV vous prévient 5 minutes avant le début, même pendant une lecture."}</p>
        <div class="btns" data-row="dlg"><button class="btn primary f" data-k="r-ok" data-act="remind">${I.bell}${has ? "Supprimer le rappel" : "Me rappeler"}</button><button class="btn f" data-k="r-ch" data-act="live:${id}">Regarder ${esc(ch.name)}</button></div></div>`,
        { onAct: (c) => {
          if (c !== "remind") { closeOverlay(); return false; }
          has ? st.reminders.delete(rk) : st.reminders.add(rk);
          closeOverlay();
          const g = $("#grows", cs.el), ty = g.dataset.ty; g.innerHTML = guideRows(cs.extra.filter || "all"); setTy(g, +ty || 0);
          cs.extra.prev = null; focus(byKey("gp-" + id + "-" + i), { noScroll: true });
          toast(has ? "Rappel supprimé" : `Rappel programmé · ${hm(s - 5)}`, "bell");
        } });
      return true;
    }
    if (cmd === "noguide") { toast("Associer un guide : choix d'une source XMLTV (démo)", "link"); return true; }
  },
};

/* ================= LECTEUR ================= */
const ZAP = CH;
function playerInfo(p) {
  if (p.ch) {
    const ch = chById(p.ch), pr = progOf(ch);
    return { live: true, ch, title: pr ? pr[2] : ch.name, meta: pr ? `${pr[0]} – ${pr[1]} · ${pr[3]}` : "Programme non communiqué", img: pr && pr[4] ? pr[4] : null, ver: "FHD · VF" };
  }
  const t = T[p.id];
  if (p.s) {
    const eps = episodesOf(t, p.id, p.s), e = eps[p.n - 1];
    const dur = e[1] * 60, w = e[3] > 0 && e[3] < 1 ? e[3] : 0;
    return { title: t.t, meta: `Saison ${p.s} · Épisode ${p.n} · ${e[0]}`, img: t.hero || t.w, lowres: !t.hero, dur, pos: dur * w, ver: `${t.versions[0][0]} · ${t.versions[0][1]}`, t };
  }
  const dur = durSec(t.d);
  return { title: t.t, meta: `${t.y} · ${t.g} · ${t.d}`, img: t.hero || t.w, lowres: !t.hero, dur, pos: t.prog ? dur - (t.prog === .69 ? 2520 : dur * (1 - t.prog)) : 0, ver: `${t.versions[0][0]} · ${t.versions[0][1]}`, t };
}
function videoHtml(info) {
  if (info.img) return `<img src="img/${info.img}.jpg" alt="" style="${info.lowres ? "object-position:100% 50%;filter:blur(1.2px)" : info.live ? "" : (info.t && info.t.type === "serie" ? "object-position:60% 30%" : "object-position:30% 40%")}">`;
  return `<div style="position:absolute;inset:0;display:grid;place-items:center;background:radial-gradient(circle at 40% 30%,#1d2a36,#06090c);font:700 120px Outfit;letter-spacing:.12em;text-transform:uppercase;color:rgba(255,255,255,.12)">${esc(info.ch.name)}</div>`;
}
function statsHtml(cs) {
  const x = cs.extra, live = cs.info.live, j = (a, b) => (a + Math.random() * b);
  const res = live ? "1920 × 1080" : (cs.info.ver.includes("4K") ? "3840 × 2160" : "1920 × 1080");
  const target = st.turbo ? 60 : 30;
  x.buf = Math.min(target, (x.buf || 12) + (st.turbo ? 2.4 : 1.2));
  const r = (l, v, s, c) => `<div class="r"><dt>${l}</dt><dd>${v}</dd><span class="st ${c}">${s}</span></div>`;
  return `<h3>Statistiques de lecture <small>mises à jour chaque seconde</small></h3><dl>
    ${r("Moteur", "Media3 · choix Auto", "Déclaré", "d")}
    ${r("Image affichée", res + " · 50 im/s", "Mesuré", "m")}
    ${r("Codec vidéo", res.startsWith("3840") ? "HEVC Main 10" : "H.264 High", "Déclaré", "d")}
    ${r("HDR", res.startsWith("3840") ? "HDR10 annoncé" : "Non", "Déclaré", "d")}
    ${r("Débit vidéo", j(live ? 7.2 : 17.4, .9).toFixed(1).replace(".", ",") + " Mb/s", "Mesuré", "m")}
    ${r("Bande passante", "≈ " + Math.round(j(44, 6)) + " Mb/s", "Estimé", "e")}
    ${r("Réserve", Math.round(x.buf) + " s / cible " + target + " s", "Mesuré", "m")}
    ${r("Images perdues", "0", "Mesuré", "m")}
    ${r("Interruptions", x.stalls || 0, "Mesuré", "m")}
    ${r("Latence du direct", live ? "N/D" : "—", "N/D", "n")}
    ${r("Sortie HDMI", "N/D", "N/D", "n")}
  </dl><div class="foot">Démonstration : valeurs simulées, aucun flux réel. Chaque mesure indique sa nature : mesurée, estimée, déclarée ou non disponible.${st.turbo ? "<br>Turbo : réserve cible 60 s, tâches de fond suspendues. Aucun gain de débit n'est promis." : ""}</div>`;
}
screens.player = {
  render(p, cs) {
    const info = cs.info = playerInfo(p);
    const live = info.live;
    return `<div class="video">${videoHtml(info)}</div>
    <div class="chrome">
      <div class="gtop"></div><div class="gbot"></div>
      <div class="p-title"><div class="k">${live ? `<span class="live-pill">EN DIRECT</span> ${info.ch.n} · ${esc(info.ch.name)}` : "En lecture"}</div><h2>${esc(info.title)}</h2><div class="m">${esc(info.meta)}</div></div>
      <div class="p-corner"><span class="turbo-badge">${I.bolt}Turbo</span><span>21:12</span></div>
      <div class="p-bottom">
        ${live ? `<div class="scrub"><div class="rail"><div class="play" id="pl-bar"></div></div></div>` :
          `<button class="scrub f" data-k="scrub" data-act="toggle"><div class="rail"><div class="buf" id="pl-buf"></div><div class="play" id="pl-bar"></div><div class="knob" id="pl-knob"></div></div><span class="tip" id="pl-tip"></span></button>`}
        <div class="times"><span id="pl-cur"></span><span id="pl-rem"></span></div>
        <div class="p-ctrls" data-row="pc">
          <button class="rbtn big f" data-k="pp" data-act="toggle">${I.pause}<span class="lbl">Pause</span></button>
          ${live ? `<button class="rbtn f" data-k="chup" data-act="zap:-1">${I.up}<span class="lbl">Chaîne précédente</span></button><button class="rbtn f" data-k="chdn" data-act="zap:1">${I.down}<span class="lbl">Chaîne suivante</span></button>
            <button class="rbtn f ${st.favCh.has(info.ch.id) ? "on" : ""}" data-k="fav" data-act="fav">${I.star}<span class="lbl">${st.favCh.has(info.ch.id) ? "Chaîne favorite" : "Ajouter aux favoris"}</span></button>`
          : `<button class="rbtn f" data-k="rw" data-act="seek:-10">${I.back10}<span class="lbl">Reculer de 10 s</span></button><button class="rbtn f" data-k="ff" data-act="seek:10">${I.fwd10}<span class="lbl">Avancer de 10 s</span></button>`}
          <div class="spacer"></div>
          <button class="btn small f" data-k="audio" data-act="audio">${I.subs}Audio et sous-titres</button>
          <button class="btn small f" data-k="version" data-act="version">${I.layers}<span id="pl-ver">${esc(info.ver)}</span></button>
          <button class="btn small toggle f ${st.turbo ? "on" : ""}" data-k="turbo" data-act="turbo">${I.bolt}Turbo <span class="tgl">${st.turbo ? "Activé" : "Désactivé"}</span></button>
          <button class="btn small f" data-k="stats" data-act="stats">${I.chart}Statistiques</button>
        </div>
      </div>
    </div>
    <aside class="stats" id="pl-stats"></aside>
    <div class="osd" id="pl-osd"></div>
    <div class="zap" id="pl-zap"></div>
    <div class="p-loading" id="pl-load"><div class="spinner"></div><p>${live ? "Connexion à la chaîne…" : "Préparation de la lecture…"}</p></div>`;
  },
  initial: () => byKey("pp"),
  mount(cs) {
    const x = cs.extra;
    x.playing = false; x.pos = cs.info.pos || 0; x.buf = 8;
    cs.el.classList.toggle("turbo-on", st.turbo);
    this.update(cs);
    this.startLoad(cs);
    const tick = () => {
      later(tick, 1000);
      if (x.playing && !cs.info.live) { x.pos = Math.min(cs.info.dur, x.pos + 1); this.update(cs); }
      if (x.statsOpen) $("#pl-stats", cs.el).innerHTML = statsHtml(cs);
    };
    later(tick, 1000);
  },
  startLoad(cs) {
    const ld = $("#pl-load", cs.el), x = cs.extra;
    ld.classList.remove("gone"); x.playing = false; this.showChrome(cs, false);
    later(() => {
      ld.classList.add("gone");
      if (st.demo.playerError) { this.error(cs); return; }
      x.playing = true; this.setPP(cs); this.showChrome(cs, true);
    }, rm ? 400 : 900);
  },
  error(cs) {
    openOverlay(`<div class="dialog"><div class="ico" style="background:rgba(255,77,94,.12);color:#ff8e99">${I.warn}</div><h2>Lecture impossible</h2>
      <p>Le flux ne répond pas. Aucune autre version n'a été lancée automatiquement.</p><p style="font-size:16px;color:var(--tx3)">Simulation · délai de connexion dépassé (code DEMO-408)</p>
      <div class="btns" data-row="dlg"><button class="btn primary f" data-k="pe-retry" data-act="retry">${I.retry}Réessayer</button><button class="btn f" data-k="pe-ver" data-act="ver">Changer de version</button><button class="btn f" data-k="pe-back" data-act="back">Retour</button></div></div>`,
      { onAct: (c) => {
        if (c === "retry") { st.demo.playerError = false; closeOverlay(); this.startLoad(cs); return; }
        if (c === "ver") { closeOverlay(); st.demo.playerError = false; this.versionSheet(cs); return; }
        if (c === "back") { closeOverlay(); back(); return; }
        return false;
      } });
  },
  update(cs) {
    const x = cs.extra, i = cs.info;
    if (i.live) {
      const pr = progOf(i.ch);
      $("#pl-bar", cs.el).style.width = (pr ? progPct(pr) * 100 : 0) + "%";
      $("#pl-cur", cs.el).textContent = pr ? pr[0] : "";
      $("#pl-rem", cs.el).textContent = pr ? pr[1] : "";
      return;
    }
    const f = x.pos / i.dur;
    $("#pl-bar", cs.el).style.width = f * 100 + "%";
    $("#pl-buf", cs.el).style.width = Math.min(100, f * 100 + 4 + (st.turbo ? 3 : 0)) + "%";
    $("#pl-knob", cs.el).style.left = f * 100 + "%";
    const tip = $("#pl-tip", cs.el); tip.style.left = f * 100 + "%"; tip.textContent = fmt(x.pos);
    $("#pl-cur", cs.el).textContent = fmt(x.pos);
    $("#pl-rem", cs.el).textContent = "−" + fmt(i.dur - x.pos);
  },
  setPP(cs) {
    const b = byKey("pp"); if (!b) return;
    b.innerHTML = (cs.extra.playing ? I.pause : I.play) + `<span class="lbl">${cs.extra.playing ? "Pause" : "Lecture"}</span>`;
    $(".video", cs.el).classList.toggle("dim", !cs.extra.playing);
  },
  showChrome(cs, on) {
    cs.el.classList.toggle("hide-chrome", !on);
    clearTimeout(cs.extra.hideT);
    if (on && cs.extra.playing) cs.extra.hideT = later(() => { if (overlay.hidden) { cs.el.classList.add("hide-chrome"); } }, 5000);
  },
  osd(cs, html) { const o = $("#pl-osd", cs.el); o.innerHTML = html; o.classList.add("show"); clearTimeout(cs.extra.osdT); cs.extra.osdT = later(() => o.classList.remove("show"), 900); },
  seek(cs, d) {
    const x = cs.extra; x.pos = Math.max(0, Math.min(cs.info.dur - 1, x.pos + d)); this.update(cs);
    if (cs.el.classList.contains("hide-chrome")) this.osd(cs, (d < 0 ? I.back10 : I.fwd10) + fmt(x.pos));
  },
  zap(cs, d) {
    const i = ZAP.findIndex(c => c.id === cs.info.ch.id), ch = ZAP[(i + d + ZAP.length) % ZAP.length];
    cs.params = { ch: ch.id }; cs.info = playerInfo(cs.params);
    $(".video", cs.el).innerHTML = videoHtml(cs.info);
    $(".p-title", cs.el).innerHTML = `<div class="k"><span class="live-pill">EN DIRECT</span> ${ch.n} · ${esc(ch.name)}</div><h2>${esc(cs.info.title)}</h2><div class="m">${esc(cs.info.meta)}</div>`;
    const fv = byKey("fav"); if (fv) { const on = st.favCh.has(ch.id); fv.classList.toggle("on", on); $(".lbl", fv).textContent = on ? "Chaîne favorite" : "Ajouter aux favoris"; }
    this.update(cs);
    const pr = progOf(ch), idx = pr ? ch.progs.indexOf(pr) : -1, nx = pr ? ch.progs[idx + 1] : null;
    const z = $("#pl-zap", cs.el);
    z.innerHTML = `<div class="zn">${ch.n}</div><div><div class="zc">${esc(ch.name)}</div><h3>${esc(pr ? pr[2] : "Programme non communiqué")}</h3>
      <div class="zt">${pr ? `<span>${pr[0]}</span><span class="bar"><i style="width:${progPct(pr) * 100}%"></i></span><span>${pr[1]}</span>` : `<span>Guide indisponible</span>`}${nx ? `<span style="color:var(--tx3)">· Ensuite ${nx[0]} ${esc(nx[2])}</span>` : ""}</div></div>
      <div class="zhint">↑ ↓ changer de chaîne<br>OK afficher les commandes</div>`;
    z.classList.add("show"); clearTimeout(cs.extra.zapT); cs.extra.zapT = later(() => z.classList.remove("show"), 3200);
  },
  audioSheet(cs) {
    const A = [["Français", "E-AC-3 5.1"], ["Anglais", "E-AC-3 5.1"], ["Français · audiodescription", "AAC stéréo"]];
    const S = [["Désactivés", ""], ["Français", "forcés"], ["Français", "sourds et malentendants"], ["Anglais", ""]];
    const html = () => `<div class="sheet"><h2>Audio et sous-titres</h2><p class="lead">Pistes annoncées par le flux de démonstration. Le français est proposé en premier.</p>
      <h4>Audio</h4>${A.map((a, i) => opt("au-" + i, "au:" + i, a[0], { check: st.audio === i, small: a[1] })).join("")}
      <h4>Sous-titres</h4>${S.map((s, i) => opt("su-" + i, "su:" + i, s[0], { check: st.subs === i, small: s[1] })).join("")}</div>`;
    const h = (c, a) => {
      if (c === "au") st.audio = +a; else if (c === "su") st.subs = +a; else return false;
      const k = cur.dataset.k; overlay.innerHTML = `<div class="scrim"></div>` + html(); cur = null; focus(byKey(k));
    };
    openOverlay(html(), { onAct: h, initial: "au-" + st.audio });
  },
  versionSheet(cs) {
    if (cs.info.live) { versionSheet(null, true, cs); return; }
    versionSheet(cs.info.t, true, cs);
  },
  key(e, cs) {
    const k = e.key, hidden = cs.el.classList.contains("hide-chrome"), x = cs.extra;
    if (!$("#pl-load", cs.el).classList.contains("gone")) { if (k === "Escape" || k === "Backspace") return false; return true; }
    if (hidden) {
      if (k === "ArrowLeft" || k === "ArrowRight") { if (!cs.info.live) { this.seek(cs, k === "ArrowLeft" ? -10 : 10); return true; } }
      if ((k === "ArrowUp" || k === "ArrowDown") && cs.info.live) { this.zap(cs, k === "ArrowUp" ? -1 : 1); return true; }
      if (k === "Escape" || k === "Backspace") { if (x.statsOpen) { x.statsOpen = false; $("#pl-stats", cs.el).classList.remove("open"); return true; } return false; }
      if (k.startsWith("Arrow") || k === "Enter" || k === " ") { $("#pl-zap", cs.el).classList.remove("show"); this.showChrome(cs, true); if (!vis(cur)) focus(byKey("pp")); return true; }
      return false;
    }
    if (k === "Escape" || k === "Backspace") { if (overlay.hidden) { this.showChrome(cs, false); if (x.playing) return true; } return false; }
    this.showChrome(cs, true);
    return false;
  },
  dpad(dir, el, cs) {
    if (el.dataset.k === "scrub" && (dir === "left" || dir === "right")) { this.seek(cs, dir === "left" ? -10 : 10); return true; }
    return false;
  },
  back(cs) { return false; },
  act(cmd, arg, el, cs) {
    const x = cs.extra;
    if (cmd === "toggle") { x.playing = !x.playing; this.setPP(cs); this.showChrome(cs, true); if (el.dataset.k === "scrub") this.osd(cs, (x.playing ? I.play : I.pause) + (x.playing ? "Lecture" : "Pause")); return true; }
    if (cmd === "seek") { this.seek(cs, +arg); return true; }
    if (cmd === "zap") { this.zap(cs, +arg); return true; }
    if (cmd === "fav") { const id = cs.info.ch.id, on = !st.favCh.has(id); on ? st.favCh.add(id) : st.favCh.delete(id); el.classList.toggle("on", on); $(".lbl", el).textContent = on ? "Chaîne favorite" : "Ajouter aux favoris"; toast(on ? `${cs.info.ch.name} ajoutée aux favoris` : `${cs.info.ch.name} retirée des favoris`, "star"); return true; }
    if (cmd === "turbo") {
      st.turbo = !st.turbo; el.classList.toggle("on", st.turbo); $(".tgl", el).textContent = st.turbo ? "Activé" : "Désactivé";
      cs.el.classList.toggle("turbo-on", st.turbo);
      toast(st.turbo ? "Turbo activé · réserve portée à 60 s, tâches de fond en pause" : "Turbo désactivé · réserve standard 30 s", "bolt");
      if (x.statsOpen) $("#pl-stats", cs.el).innerHTML = statsHtml(cs);
      return true;
    }
    if (cmd === "stats") { x.statsOpen = !x.statsOpen; const s = $("#pl-stats", cs.el); s.innerHTML = statsHtml(cs); s.classList.toggle("open", x.statsOpen); return true; }
    if (cmd === "audio") { this.audioSheet(cs); return true; }
    if (cmd === "version") { this.versionSheet(cs); return true; }
  },
  leave(cs) { st.lastPlayer = null; },
};
function versionSheet(t, inPlayer, pcs) {
  const vers = t ? t.versions : [["VF", "FHD", true], ["VF", "HD"], ["VO", "FHD"]];
  const curV = pcs && pcs.info ? pcs.info.ver : null;
  openOverlay(`<div class="sheet"><h2>Versions</h2><p class="lead">Regroupées automatiquement : même titre${t ? ", même année" : ", même pays"}. Langue et qualité sont celles <b>annoncées par le fournisseur</b>, pas mesurées.</p>
    <h4>${inPlayer ? "Changer en gardant la position" : "Disponibles"}</h4>
    ${vers.map((v, i) => opt("v-" + i, "v:" + i, `${v[0]} · ${v[1]}`, { check: curV ? curV === `${v[0]} · ${v[1]}` : !!v[2], small: i === 0 ? "préférée" : "" })).join("")}
    <h4>Préférence</h4>${opt("v-pref", "v:pref", "Français d'abord, qualité maximale", { small: "Réglages" })}</div>`,
    { onAct: (c, a) => {
      if (c !== "v") return false;
      if (a === "pref") { toast("Préférence modifiable dans Réglages → Lecture (démo)", "info"); return; }
      const v = vers[+a]; closeOverlay();
      if (inPlayer && pcs && cs === pcs) {
        pcs.info.ver = `${v[0]} · ${v[1]}`; $("#pl-ver", pcs.el).textContent = pcs.info.ver;
        screens.player.startLoad(pcs); toast(`Version ${pcs.info.ver} · position conservée`, "layers");
      } else toast(`Version ${v[0]} · ${v[1]} choisie pour la lecture`, "layers");
    } });
}

/* ================= RECHERCHE ================= */
const KEYS = "abcdefghijklmnopqrstuvwxyz'-".split("");
function searchResults(q) {
  const nq = norm(q.trim());
  if (!nq) {
    const recent = ["dakar", "science-fiction", "football"], gen = [["Thriller", "thriller"], ["Science-fiction", "science"], ["Drame", "drame"], ["Romance", "romance"], ["Documentaire", "documentaire"], ["Sport", "sport"]];
    return `<div class="sec res-sec"><h3 class="row-title">Recherches récentes</h3><div class="chips" data-row="rq">${recent.map(r => `<button class="chip f" data-k="rq-${r}" data-act="q:${r}">${r}</button>`).join("")}</div></div>
      <div class="sec res-sec"><h3 class="row-title">Parcourir par genre</h3><div class="chips" data-row="rg">${gen.map(([l, v]) => `<button class="chip f" data-k="rg-${v}" data-act="q:${v}">${l}</button>`).join("")}</div></div>
      <div class="sec res-sec"><h3 class="row-title">Dans Ma liste</h3><div class="rowview"><div class="track" data-row="rml">${[...st.myList].slice(0, 6).map(id => wcard(id, "rml-" + id)).join("")}</div></div></div>`;
  }
  const match = id => { const t = T[id]; return norm(t.t).includes(nq) || norm(t.g).includes(nq) || t.cast.some(([pid]) => norm(P[pid][0]).includes(nq)); };
  const films = Object.keys(T).filter(id => T[id].type === "film" && match(id));
  const series = Object.keys(T).filter(id => T[id].type === "serie" && match(id));
  const chans = CH.filter(c => norm(c.name).includes(nq) || norm(c.cat).includes(nq) || (c.progs || []).some(p => norm(p[2]).includes(nq) && toMin(p[0]) <= NOW && NOW < toMin(p[1], toMin(p[0]))));
  if (!films.length && !series.length && !chans.length)
    return `<div class="sec">${stateBox({ icon: I.search, title: `Aucun résultat pour « ${esc(q.trim())} »`, text: "Vérifiez l'orthographe, ou cherchez un genre, une chaîne ou un nom d'acteur.", row: "sempty", btns: `<button class="btn f" data-k="s-clear" data-act="key:clear">Effacer la recherche</button>` })}</div>`;
  let h = "";
  if (films.length) h += `<div class="sec res-sec"><h3 class="row-title">Films <small>${films.length}</small></h3><div class="rowview"><div class="track" data-row="rf">${films.map(id => wcard(id, "rf-" + id)).join("")}</div></div></div>`;
  if (series.length) h += `<div class="sec res-sec"><h3 class="row-title">Séries <small>${series.length}</small></h3><div class="rowview"><div class="track" data-row="rs">${series.map(id => wcard(id, "rs-" + id)).join("")}</div></div></div>`;
  if (chans.length) h += `<div class="sec res-sec"><h3 class="row-title">Chaînes <small>${chans.length}</small></h3><div class="rowview"><div class="track" data-row="rc">${chans.map(c => chTile(c, "rc-" + c.id)).join("")}</div></div></div>`;
  return h;
}
screens.search = {
  nav: true, navKey: "nav-search",
  render(p, cs) {
    cs.extra.q = cs.extra.q ?? (p.q || "");
    return `<div class="kb-pane"><div class="qfield">${I.search}<span class="q" id="sq"></span><span class="caret"></span></div>
      <div class="kb" data-row="kb">${KEYS.map(c => `<button class="key f" data-k="key-${c}" data-act="key:${c}">${c}</button>`).join("")}
        <button class="key wide3 f" data-k="key-space" data-act="key:space">Espace</button><button class="key wide2 f" data-k="key-del" data-act="key:del" aria-label="Effacer un caractère">${I.del}</button><button class="key wide2 f" data-k="key-clear" data-act="key:clear">Effacer</button></div>
      <div class="kb-hint">Sur ordinateur, tapez directement au clavier.</div></div>
      <div class="res-pane"><div class="scroller" data-top="0" data-h="930" data-bot="30" id="sres"></div></div>`;
  },
  mount(cs) { this.setQ(cs, cs.extra.q, true); },
  initial: (cs) => (cs.extra.q ? $("#sres .f", cs.el) : null) || byKey("key-a"),
  setQ(cs, q, first) {
    cs.extra.q = q;
    $("#sq", cs.el).innerHTML = q ? esc(q) : `<span class="ph">Titre, genre, chaîne, acteur…</span>`;
    const res = $("#sres", cs.el); res.innerHTML = searchResults(q); setTy(res, 0);
    for (const k of Object.keys(cs.mem)) if (/^r[fscgq]|^rml|^sempty/.test(k)) delete cs.mem[k];
    if (!first && cur && !cur.isConnected) focus(byKey("key-a"));
  },
  key(e, cs) {
    if (e.ctrlKey || e.metaKey || e.altKey) return false;
    const k = e.key;
    if (k.length === 1 && /[\p{L}\p{N}' -]/u.test(k)) { this.setQ(cs, (cs.extra.q + k).replace(/^ /, "")); return true; }
    if (k === "Backspace" && cs.extra.q) { this.setQ(cs, cs.extra.q.slice(0, -1)); return true; }
    return false;
  },
  act(cmd, arg, el, cs) {
    if (cmd === "key") {
      const q = cs.extra.q;
      this.setQ(cs, arg === "space" ? q + " " : arg === "del" ? q.slice(0, -1) : arg === "clear" ? "" : q + arg);
      if (arg === "clear" && !el.closest(".kb")) focus(byKey("key-a"));
      return true;
    }
    if (cmd === "q") { this.setQ(cs, arg); const f = $("#sres .card, #sres .chtile", cs.el); if (f) focus(f); return true; }
  },
};

/* ================= MA LISTE ================= */
const ML_F = [["all", "Tout"], ["film", "Films"], ["serie", "Séries"], ["ch", "Chaînes favorites"]];
screens.mylist = {
  nav: true, navKey: "nav-mylist",
  render(p, cs) {
    const f = cs.extra.filter || "all";
    return `<div class="scroller" data-top="150"><div class="sec ml-head" data-top><h1>Ma liste</h1><div class="sub" id="ml-sub"></div>
      <div class="chips" data-row="ml-chips">${ML_F.map(([k, l]) => `<button class="chip f ${f === k ? "sel" : ""}" data-k="mf-${k}" data-act="mfilter:${k}">${l}</button>`).join("")}</div></div><div id="ml-body"></div></div>`;
  },
  body(cs) {
    const f = cs.extra.filter || "all", empty = st.demo.listEmpty;
    const ids = empty ? [] : [...st.myList].filter(id => f === "all" || T[id].type === f);
    const chans = empty ? [] : CH.filter(c => st.favCh.has(c.id));
    $("#ml-sub", cs.el).textContent = `${empty ? 0 : st.myList.size} titres · ${chans.length} chaînes favorites`;
    let h = "";
    if (f !== "ch") for (let i = 0; i < ids.length; i += 4) h += `<div class="sec grid-row">${ids.slice(i, i + 4).map(id => wcard(id, "ml-" + id, { sub: metaLine(T[id]) })).join("")}</div>`;
    if ((f === "all" || f === "ch") && chans.length) h += `<div class="sec rowsec"><h3 class="row-title">Chaînes favorites</h3><div class="rowview"><div class="track" data-row="mlc">${chans.map(c => chTile(c, "mlc-" + c.id)).join("")}</div></div></div>`;
    if (!h) h = `<div class="sec" style="height:560px">${stateBox({ icon: I.bookmark, title: f === "ch" ? "Aucune chaîne favorite" : "Votre liste est vide",
      text: f === "ch" ? "Dans le lecteur, choisissez ★ pour retrouver une chaîne ici et en tête du guide." : "Ajoutez un film ou une série depuis sa fiche avec ＋. Vos chaînes favorites apparaîtront aussi ici.",
      row: "mle", btns: `<button class="btn primary f" data-k="mle-films" data-act="tab:films">${I.film}Explorer les films</button><button class="btn f" data-k="mle-live" data-act="tab:live">${I.tv}Voir le direct</button>`, style: "top:20px" })}</div>`;
    $("#ml-body", cs.el).innerHTML = h;
  },
  mount(cs) { this.body(cs); },
  initial: (cs) => byKey("mf-" + (cs.extra.filter || "all")),
  act(cmd, arg, el, cs) {
    if (cmd === "mfilter") { cs.extra.filter = arg; $$('[data-row="ml-chips"] .chip', cs.el).forEach(c => c.classList.toggle("sel", c.dataset.k === "mf-" + arg)); this.body(cs); return true; }
  },
};

/* ---------------- entrées clavier / souris ---------------- */
const DIRS = { ArrowUp: "up", ArrowDown: "down", ArrowLeft: "left", ArrowRight: "right" };
document.addEventListener("keydown", e => {
  if (!cs || e.ctrlKey || e.metaKey || e.altKey) return;
  if (overlay.hidden && cs.def.key && cs.def.key(e, cs) === true) { e.preventDefault(); return; }
  const k = e.key;
  if (DIRS[k]) { e.preventDefault(); move(DIRS[k]); return; }
  if (k === "Enter" || k === " ") { e.preventDefault(); if (e.repeat) return; if (cur) activate(cur); return; }
  if (k === "Escape" || k === "Backspace" || k === "BrowserBack") { e.preventDefault(); back(); return; }
  if (k === "d" || k === "D") { if (cs.id !== "start" && cs.id !== "intro") openDemo(); return; }
  if (k === "m" || k === "M") { toggleRM(); return; }
});
stage.addEventListener("mousemove", e => {
  if (!cs) return;
  if (cs.id === "player") screens.player.showChrome(cs, true);
  const f = e.target.closest(".f");
  if (f && f !== cur && (overlay.hidden ? !overlay.contains(f) : overlay.contains(f))) focus(f, { noScroll: true });
});
stage.addEventListener("click", e => {
  if (!cs) return;
  if (e.target.classList.contains("scrim")) { closeOverlay(); return; }
  const f = e.target.closest(".f"); if (!f) return;
  if (!overlay.hidden && !overlay.contains(f)) return;
  if (f !== cur) focus(f, { noScroll: true });
  activate(f);
});
let wheelT = 0;
stage.addEventListener("wheel", e => {
  e.preventDefault(); const t = Date.now(); if (t - wheelT < 160) return; wheelT = t;
  move(Math.abs(e.deltaX) > Math.abs(e.deltaY) ? (e.deltaX > 0 ? "right" : "left") : (e.deltaY > 0 ? "down" : "up"));
}, { passive: false });
$("#demobar").addEventListener("click", () => { if (cs.id !== "start" && cs.id !== "intro") openDemo(); });

/* ---------------- démarrage ---------------- */
window.ADITV = { go, focus, back, move, st, get cs() { return cs; }, get cur() { return cur; }, openDemo, byKey };
const startAt = new URLSearchParams(location.search).get("screen");
document.fonts.ready.then(() => {
  if (startAt && screens[startAt]) { go(startAt, startAt === "film" || startAt === "serie" ? { id: startAt === "film" ? "silence" : "dakar" } : {}, { root: true }); }
  else go("start", {}, { root: true });
});
})();
