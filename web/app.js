'use strict';
(() => {
  // ------------------------------------------------------------------ helpers
  const $ = (s, el = document) => el.querySelector(s);
  const $$ = (s, el = document) => [...el.querySelectorAll(s)];
  const esc = s => String(s ?? '').replace(/[&<>"']/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
  const store = {
    get(k, d) { try { const v = localStorage.getItem('gideon.' + k); return v == null ? d : JSON.parse(v); } catch { return d; } },
    set(k, v) { try { localStorage.setItem('gideon.' + k, JSON.stringify(v)); } catch { /* private mode */ } },
  };
  const ic = (name, cls = '') => `<svg class="ic ${cls}"><use href="#i-${name}"/></svg>`;
  const favicon = d => `https://www.google.com/s2/favicons?domain=${encodeURIComponent(d || '')}&sz=128`;
  const CATS = {
    UNE: ['À la une', '#ff375f'], MONDE: ['Monde', '#0a84ff'], FRANCE: ['France', '#5e5ce6'], ECO: ['Économie', '#30d158'],
    TECH: ['Tech', '#bf5af2'], SCIENCES: ['Sciences', '#40cbe0'], SPORT: ['Sport', '#ff9f0a'], CULTURE: ['Culture', '#ff2d55'],
  };
  const TABS = ['feed', 'saved', 'settings'];
  const standalone = window.navigator.standalone === true || matchMedia('(display-mode: standalone)').matches;

  function ago(ts) {
    if (!ts) return 'jamais';
    const m = Math.max(0, Math.floor((Date.now() / 1000 - ts) / 60));
    if (m < 1) return "à l'instant";
    if (m < 60) return `il y a ${m} min`;
    const h = Math.floor(m / 60);
    if (h < 24) return `il y a ${h} h`;
    const d = Math.floor(h / 24);
    return d === 1 ? 'hier' : `il y a ${d} j`;
  }

  // ------------------------------------------------------------------ state
  const S = {
    data: null, pubs: {}, kinds: {}, all: [], forYou: [], list: [], pending: null,
    screen: 'feed', lastTab: 'feed', query: '', feedTab: 'foryou', filter: null,
    liked: new Set(store.get('liked', [])), saved: store.get('saved', []), disabled: new Set(store.get('disabled', [])),
    shorts: store.get('shorts', false), sound: store.get('sound', false), scale: store.get('scale', 1),
    affinity: store.get('affinity', {}), rendered: 0, active: -1, reader: null,
  };
  document.documentElement.style.setProperty('--text-scale', S.scale);

  const isSaved = id => S.saved.some(x => x.id === id);
  const pubOf = it => S.pubs[it.p] || { id: it.p, name: it.p, domain: '' };
  function bump(cat, by) {
    S.affinity[cat] = (S.affinity[cat] || 0) + by;
    store.set('affinity', S.affinity);
  }

  // ------------------------------------------------------------------ data
  async function load(manual) {
    const btn = $('#refresh');
    btn.classList.add('spin');
    try {
      const r = await fetch('data/feed.json?t=' + Date.now(), { cache: 'no-store' });
      if (!r.ok) throw new Error('HTTP ' + r.status);
      const data = await r.json();
      const before = S.data ? new Set(S.data.items.map(i => i.id)) : null;
      const fresh = before ? data.items.filter(i => !before.has(i.id)).length : 0;
      if (!before || manual || S.active < 1) {
        apply(data);
        if (manual && before) toast(fresh ? `${fresh} nouvelles infos` : 'Tu es à jour');
      } else if (fresh) {
        S.pending = data;
        toast(`↑ ${fresh} nouvelles infos`, () => { apply(S.pending); S.pending = null; }, 6000);
      }
    } catch (e) {
      if (manual || !S.data) toast('Connexion impossible');
    } finally {
      btn.classList.remove('spin');
      $('#splash').classList.add('gone');
    }
  }

  function apply(data) {
    S.data = data;
    S.pubs = Object.fromEntries(data.publishers.map(p => [p.id, p]));
    S.kinds = {};
    for (const it of data.items) (S.kinds[it.p] ||= new Set()).add(it.k);
    rebuild();
  }

  function rebuild() {
    if (!S.data) return;
    S.all = S.data.items.filter(it => !S.disabled.has(`${it.k}:${it.p}`) && (S.shorts || !it.sh));
    S.forYou = mix(S.all);
    renderFeed();
    renderSearch();
    renderSaved();
    renderSettings();
  }

  // "Pour toi": fresh news first, nudged by what you like, a video every third story.
  function mix(items) {
    const now = Date.now() / 1000;
    const maxA = Math.max(1, ...Object.values(S.affinity));
    const score = it => Math.exp(-((now - it.d) / 3600) / 14) * 10 + ((S.affinity[it.c] || 0) / maxA) * 3 + (it.c === 'UNE' ? 1.2 : 0) + (it.i ? 2 : 0);
    const arts = items.filter(i => !i.v).sort((a, b) => score(b) - score(a));
    const vids = items.filter(i => i.v).sort((a, b) => score(b) - score(a));
    const out = [];
    let since = 0;
    while (arts.length || vids.length) {
      const wantVideo = vids.length && (since >= 2 || !arts.length);
      const q = wantVideo ? vids : arts;
      const last = out.length ? out[out.length - 1].p : null;
      let idx = 0;
      for (let i = 0; i < Math.min(4, q.length); i++) if (q[i].p !== last) { idx = i; break; }
      out.push(q.splice(idx, 1)[0]);
      since = wantVideo ? 0 : since + 1;
    }
    return out;
  }

  function currentList() {
    const f = S.filter;
    if (f?.type === 'pub') return S.all.filter(i => i.p === f.id);
    if (f?.type === 'cat') return S.all.filter(i => i.c === f.id || (f.id === 'UNE' && i.v));
    if (S.feedTab === 'videos') return S.all.filter(i => i.v);
    if (S.feedTab === 'articles') return S.all.filter(i => !i.v);
    return S.forYou;
  }

  // ------------------------------------------------------------------ feed
  const feedEl = $('#feed');
  const io = new IntersectionObserver(entries => {
    for (const en of entries) if (en.isIntersecting && en.intersectionRatio >= 0.6) setActive(+en.target.dataset.i);
  }, { root: feedEl, threshold: [0.6] });

  function renderFeed() {
    destroyPlayer();
    io.disconnect();
    feedEl.innerHTML = '';
    S.rendered = 0;
    S.active = -1;
    S.list = currentList();
    updateTopBar();
    if (!S.list.length) {
      feedEl.innerHTML = `<div class="page"><div class="empty" style="color:#fff;padding-top:40vh">${S.data ? 'Rien ici pour l’instant.' : 'Chargement de l’actu…'}</div></div>`;
      return;
    }
    appendPages(10);
    feedEl.scrollTop = 0;
  }

  function appendPages(n) {
    const frag = document.createDocumentFragment();
    const end = Math.min(S.list.length, S.rendered + n);
    for (let i = S.rendered; i < end; i++) frag.appendChild(pageEl(S.list[i], i));
    feedEl.appendChild(frag);
    S.rendered = end;
    $$('.page[data-i]:not([data-obs])', feedEl).forEach(p => { p.dataset.obs = '1'; io.observe(p); });
  }

  function railButton(act, icon, label, cls = '') {
    return `<div class="act"><button class="circle glass dark-glass clear-glass ${cls}" data-act="${act}" aria-label="${label}">${icon}</button><span>${label}</span></div>`;
  }

  function pageEl(it, i) {
    const el = document.createElement('article');
    el.className = 'page' + (it.v ? ' video' : '');
    el.dataset.i = i;
    const pub = pubOf(it);
    const cat = CATS[it.c] || CATS.UNE;
    const liked = S.liked.has(it.id);
    const saved = isSaved(it.id);
    const img = esc(it.i || '');
    const media = it.v
      ? `<div class="vbg" style="background-image:url('${img}')"></div><div class="vframe${it.sh ? ' short' : ''}" style="background-image:url('${img}')"><div class="yt"></div></div>`
      : it.i
        ? `<img class="hero" src="${img}" alt="" decoding="async" referrerpolicy="no-referrer" onerror="this.remove()">`
        : `<div class="hero-fallback"><img src="${favicon(pub.domain)}" alt=""></div>`;
    const body = it.lead || it.s;
    el.innerHTML = `${media}<div class="shade"></div><div class="tap"></div>
      <div class="info">
        <div class="src"><img src="${favicon(pub.domain)}" alt=""><b>${esc(pub.name)}</b><span>· ${ago(it.d)}</span></div>
        ${it.c !== 'UNE' ? `<div class="kicker" style="color:${cat[1]}">${cat[0]}</div>` : ''}
        <h2>${esc(it.t)}</h2>
        ${body ? `<p>${esc(body)}</p>` : ''}
        ${it.v ? '' : `<button class="pill glass dark-glass clear-glass read">${ic('book')}Lire l’article${it.m ? ` · ${it.m} min` : ''}</button>`}
      </div>
      <div class="rail">
        <button class="avatar" style="background-image:url('${favicon(pub.domain)}')" data-act="pub" aria-label="${esc(pub.name)}"></button>
        ${railButton('like', ic('heart', liked ? 'filled' : ''), 'J’aime', liked ? 'liked' : '')}
        ${railButton('save', ic('bookmark', saved ? 'filled' : ''), 'Garder', saved ? 'saved' : '')}
        ${railButton('share', ic('share'), 'Partager')}
        ${it.v ? railButton('sound', ic(S.sound ? 'sound' : 'mute'), S.sound ? 'Son' : 'Muet') : ''}
      </div>
      ${it.v ? '<div class="progress"><i></i></div>' : ''}`;
    return el;
  }

  function setActive(i) {
    if (i === S.active) return;
    S.active = i;
    $$('.page.active', feedEl).forEach(p => p.classList.remove('active'));
    const page = feedEl.querySelector(`.page[data-i="${i}"]`);
    if (!page) return;
    page.classList.add('active');
    if (i >= S.rendered - 4) appendPages(10);
    destroyPlayer();
    const it = S.list[i];
    if (it?.v && S.screen === 'feed' && !S.reader) createPlayer(page, it);
    // read ahead the next articles
    for (const next of S.list.slice(i + 1, i + 3)) if (next && next.a) fetch(articleUrl(next)).catch(() => {});
  }

  let lastTap = 0;
  let tapTimer = 0;
  feedEl.addEventListener('click', e => {
    const page = e.target.closest('.page[data-i]');
    if (!page) return;
    const it = S.list[+page.dataset.i];
    if (!it) return;
    const act = e.target.closest('[data-act]');
    if (act) return onAction(act.dataset.act, it, act, page);
    if (e.target.closest('.read')) return openReader(it);
    const now = Date.now();
    if (now - lastTap < 300) {
      clearTimeout(tapTimer);
      lastTap = 0;
      const r = page.getBoundingClientRect();
      heartBurst(page, e.clientX - r.left, e.clientY - r.top);
      if (!S.liked.has(it.id)) onAction('like', it, page.querySelector('[data-act="like"]'), page);
      return;
    }
    lastTap = now;
    tapTimer = setTimeout(() => (it.v ? togglePlay(page) : openReader(it)), 300);
  });

  function heartBurst(page, x, y) {
    const b = document.createElement('div');
    b.className = 'burst';
    b.style.left = x + 'px';
    b.style.top = y + 'px';
    b.style.setProperty('--r', (Math.random() * 36 - 18).toFixed(0) + 'deg');
    b.innerHTML = ic('heart', 'filled');
    page.appendChild(b);
    setTimeout(() => b.remove(), 1000);
  }

  function onAction(act, it, btn, page) {
    if (act === 'like') {
      const on = !S.liked.has(it.id);
      on ? S.liked.add(it.id) : S.liked.delete(it.id);
      store.set('liked', [...S.liked]);
      if (on) bump(it.c, 1);
      if (btn) {
        btn.classList.toggle('liked', on);
        btn.innerHTML = ic('heart', on ? 'filled' : '');
        if (on) btn.firstElementChild.classList.add('bump');
      }
    } else if (act === 'save') {
      toggleSave(it);
      const on = isSaved(it.id);
      if (btn) {
        btn.classList.toggle('saved', on);
        btn.innerHTML = ic('bookmark', on ? 'filled' : '');
        if (on) btn.firstElementChild.classList.add('bump');
      }
    } else if (act === 'share') {
      share(it);
    } else if (act === 'sound') {
      S.sound = !S.sound;
      store.set('sound', S.sound);
      if (player?.unMute) S.sound ? player.unMute() : player.mute();
      $$('[data-act="sound"]', feedEl).forEach(b => {
        b.innerHTML = ic(S.sound ? 'sound' : 'mute');
        b.nextElementSibling.textContent = S.sound ? 'Son' : 'Muet';
      });
      renderSettings();
    } else if (act === 'pub') {
      openFilter({ type: 'pub', id: it.p, label: pubOf(it).name });
    }
  }

  function toggleSave(it) {
    if (isSaved(it.id)) S.saved = S.saved.filter(x => x.id !== it.id);
    else {
      S.saved.unshift(it);
      bump(it.c, 1.5);
    }
    store.set('saved', S.saved);
    renderSaved();
  }

  async function share(it) {
    try {
      if (navigator.share) await navigator.share({ title: it.t, url: it.l });
      else {
        await navigator.clipboard.writeText(it.l);
        toast('Lien copié');
      }
    } catch { /* cancelled */ }
  }

  // ------------------------------------------------------------------ YouTube
  let ytApi = null;
  let player = null;
  let progressTimer = 0;
  function loadYouTube() {
    if (!ytApi) {
      ytApi = new Promise(resolve => {
        window.onYouTubeIframeAPIReady = () => resolve(window.YT);
        const s = document.createElement('script');
        s.src = 'https://www.youtube.com/iframe_api';
        document.head.appendChild(s);
      });
    }
    return ytApi;
  }

  async function createPlayer(page, it) {
    const YT = await loadYouTube();
    if (S.list[S.active] !== it || S.screen !== 'feed' || S.reader) return;
    const slot = $('.yt', page);
    if (!slot) return;
    player = new YT.Player(slot, {
      videoId: it.v,
      host: 'https://www.youtube-nocookie.com',
      playerVars: { playsinline: 1, autoplay: 1, mute: S.sound ? 0 : 1, controls: 0, rel: 0, modestbranding: 1, iv_load_policy: 3, fs: 0, disablekb: 1 },
      events: {
        onReady: e => { S.sound ? e.target.unMute() : e.target.mute(); e.target.playVideo(); },
        onStateChange: e => onState(page, e.data),
        onError: () => onVideoError(page, it),
      },
    });
    const bar = $('.progress i', page);
    progressTimer = setInterval(() => {
      try {
        const d = player.getDuration();
        if (d > 0 && bar) bar.style.width = (100 * player.getCurrentTime() / d) + '%';
      } catch { /* not ready */ }
    }, 400);
  }

  function onState(page, state) {
    const badge = $('.play-badge', page);
    if (state === 1) badge?.remove();
    if ((state === 2 || state === 0) && !badge) {
      const b = document.createElement('div');
      b.className = 'play-badge glass dark-glass';
      b.innerHTML = ic(state === 0 ? 'replay' : 'play', state === 0 ? '' : 'filled');
      page.appendChild(b);
    }
  }

  function onVideoError(page, it) {
    const frame = $('.vframe', page);
    if (!frame || $('.yt-fallback', frame)) return;
    frame.insertAdjacentHTML('beforeend', `<a class="pill glass dark-glass yt-fallback" href="${esc(it.l)}" target="_blank" rel="noopener" style="position:absolute;left:50%;top:50%;transform:translate(-50%,-50%)">${ic('play', 'filled')}Regarder sur YouTube</a>`);
  }

  function togglePlay(page) {
    if (!player?.getPlayerState) return;
    const st = player.getPlayerState();
    if (st === 1) player.pauseVideo();
    else {
      if (st === 0) player.seekTo(0, true);
      player.playVideo();
    }
  }

  function destroyPlayer() {
    clearInterval(progressTimer);
    if (player) {
      try { player.destroy(); } catch { /* gone */ }
      player = null;
    }
    $$('.vframe', feedEl).forEach(f => {
      if (!$('.yt', f) && !$('iframe', f)) f.insertAdjacentHTML('afterbegin', '<div class="yt"></div>');
    });
    $$('.play-badge', feedEl).forEach(b => b.remove());
  }

  // ------------------------------------------------------------------ top bar / filters / toast
  function updateTopBar() {
    const seg = $('#segmented');
    const chip = $('#filter-chip');
    seg.classList.toggle('hidden', !!S.filter);
    chip.classList.toggle('hidden', !S.filter);
    if (S.filter) $('span', chip).textContent = S.filter.label;
    const idx = ['foryou', 'videos', 'articles'].indexOf(S.feedTab);
    seg.style.setProperty('--i', idx);
    $$('button', seg).forEach((b, i) => b.classList.toggle('on', i === idx));
  }

  $('#segmented').addEventListener('click', e => {
    const b = e.target.closest('button[data-tab]');
    if (!b || b.dataset.tab === S.feedTab) return;
    S.feedTab = b.dataset.tab;
    renderFeed();
  });
  $('#filter-chip').addEventListener('click', () => { S.filter = null; renderFeed(); });
  $('#refresh').addEventListener('click', () => {
    if (S.pending) { apply(S.pending); S.pending = null; return; }
    load(true);
  });

  function openFilter(filter) {
    S.filter = filter;
    show('feed');
    renderFeed();
  }

  let toastTimer = 0;
  function toast(text, onTap, ms = 2600) {
    const t = $('#toast');
    t.textContent = text;
    t.classList.remove('hidden');
    t.onclick = () => { t.classList.add('hidden'); onTap?.(); };
    clearTimeout(toastTimer);
    toastTimer = setTimeout(() => t.classList.add('hidden'), ms);
  }

  // ------------------------------------------------------------------ screens & tab bar
  const tabbar = $('#tabbar');
  const input = $('#q');

  function show(screen) {
    const leavingFeed = S.screen === 'feed' && screen !== 'feed';
    S.screen = screen;
    $$('.screen').forEach(s => s.classList.toggle('active', s.id === 'screen-' + screen));
    document.body.classList.toggle('on-feed', screen === 'feed');
    tabbar.classList.toggle('searching', screen === 'search');
    tabbar.classList.remove('folded');
    if (screen !== 'search') {
      S.lastTab = screen;
      const idx = TABS.indexOf(screen);
      $$('.tab').forEach((t, i) => t.classList.toggle('on', i === idx));
      $('.tab-pill').style.setProperty('--i', idx);
      $('.tab-folded').innerHTML = $$('.tab')[idx].querySelector('svg').outerHTML;
      input.blur();
    }
    if (leavingFeed) destroyPlayer();
    if (screen === 'feed' && S.active >= 0) {
      const page = feedEl.querySelector(`.page[data-i="${S.active}"]`);
      const it = S.list[S.active];
      if (page && it?.v && !player) createPlayer(page, it);
    }
  }

  $$('.tab').forEach(t => t.addEventListener('click', () => {
    const target = t.dataset.screen;
    if (target === 'feed' && S.screen === 'feed') {
      if (S.filter) { S.filter = null; renderFeed(); } else feedEl.scrollTo({ top: 0, behavior: 'smooth' });
      return;
    }
    show(target);
  }));
  $('.tab-folded').addEventListener('click', () => show(S.lastTab));
  input.addEventListener('focus', () => { if (S.screen !== 'search') show('search'); });
  input.addEventListener('input', () => {
    S.query = input.value;
    $('#q-clear').classList.toggle('hidden', !S.query);
    renderSearch();
  });
  input.addEventListener('keydown', e => { if (e.key === 'Enter') input.blur(); });
  $('#q-clear').addEventListener('click', e => {
    e.preventDefault();
    input.value = '';
    S.query = '';
    $('#q-clear').classList.add('hidden');
    renderSearch();
    input.focus();
  });

  // iOS 26: lists fold the tab bar while scrolling down; large titles collapse into the bar
  $$('.screen.list').forEach(el => {
    let last = 0;
    el.addEventListener('scroll', () => {
      const y = el.scrollTop;
      el.classList.toggle('scrolled', y > 44);
      if (S.screen !== 'search') {
        if (y > last + 8 && y > 60) tabbar.classList.add('folded');
        else if (y < last - 8) tabbar.classList.remove('folded');
      }
      last = y;
    }, { passive: true });
  });

  function storyRow(it, extra = '') {
    const pub = pubOf(it);
    return `<div class="story" data-id="${esc(it.id)}">
      <div class="txt"><div class="who"><img src="${favicon(pub.domain)}" alt="">${esc(pub.name)}</div><h3>${esc(it.t)}</h3><div class="when">${ago(it.d)}</div></div>
      <div class="thumb" style="background-image:url('${esc(it.i || favicon(pub.domain))}')"></div>${extra}</div>`;
  }

  function renderSearch() {
    const el = $('#screen-search');
    const q = S.query.trim().toLowerCase();
    let html = '<div class="inline-title">Rechercher</div><h1 class="large-title">Rechercher</h1>';
    if (q.length >= 2) {
      const res = S.all.filter(i => i.t.toLowerCase().includes(q) || (i.s || '').toLowerCase().includes(q) || pubOf(i).name.toLowerCase().includes(q)).slice(0, 80);
      html += `<div class="subtitle">${res.length ? `${res.length} résultat${res.length > 1 ? 's' : ''}` : `Aucun résultat pour « ${esc(S.query)} »`}</div>`;
      html += res.map(i => storyRow(i)).join('');
    } else {
      const counts = {};
      for (const i of S.all) counts[i.c] = (counts[i.c] || 0) + 1;
      html += '<div class="section-title">Catégories</div><div class="cats">' +
        Object.entries(CATS).map(([id, [label, color]]) =>
          `<button class="cat" data-cat="${id}" style="background:linear-gradient(135deg, ${color}, ${color}a0)">${ic('cat-' + id)}<em>${counts[id] || 0}</em><b>${label}</b></button>`).join('') +
        '</div>';
      const pubs = (S.data?.publishers || []).filter(p => S.kinds[p.id]?.has('rss'));
      html += '<div class="section-title">Journaux</div><div class="pubs">' +
        pubs.map(p => `<button class="pub" data-pub="${esc(p.id)}"><i style="background-image:url('${favicon(p.domain)}')"></i><span>${esc(p.name)}</span></button>`).join('') + '</div>';
      const latest = S.all.filter(i => !i.v).slice(0, 12);
      if (latest.length) html += '<div class="section-title">Les plus récentes</div>' + latest.map(i => storyRow(i)).join('');
    }
    el.innerHTML = html;
  }

  $('#screen-search').addEventListener('click', e => {
    const cat = e.target.closest('[data-cat]');
    if (cat) return openFilter({ type: 'cat', id: cat.dataset.cat, label: CATS[cat.dataset.cat][0] });
    const pub = e.target.closest('[data-pub]');
    if (pub) return openFilter({ type: 'pub', id: pub.dataset.pub, label: S.pubs[pub.dataset.pub]?.name || '' });
    const row = e.target.closest('.story');
    if (row) {
      const it = S.all.find(i => i.id === row.dataset.id);
      if (it) { input.blur(); openReader(it); }
    }
  });

  function renderSaved() {
    const el = $('#screen-saved');
    let html = '<div class="inline-title">Gardés</div><h1 class="large-title">Gardés</h1>';
    if (!S.saved.length) {
      html += `<div class="empty">${ic('bookmark', 'filled')}<h3>Aucun article gardé</h3>Touche « Garder » sur une info du fil pour la retrouver ici.</div>`;
    } else {
      html += `<div class="subtitle">${S.saved.length} article${S.saved.length > 1 ? 's' : ''}</div>`;
      html += S.saved.map(i => storyRow(i, `<button class="del" data-del="${esc(i.id)}" aria-label="Retirer">${ic('trash')}</button>`)).join('');
    }
    el.innerHTML = html;
  }

  $('#screen-saved').addEventListener('click', e => {
    const del = e.target.closest('[data-del]');
    if (del) {
      const it = S.saved.find(x => x.id === del.dataset.del);
      if (it) toggleSave(it);
      return;
    }
    const row = e.target.closest('.story');
    if (row) {
      const it = S.saved.find(i => i.id === row.dataset.id);
      if (it) openReader(it);
    }
  });

  function switchRow(title, key, on, { icon, color, sub, logo } = {}) {
    const lead = logo
      ? `<span class="logo-tile" style="background-image:url('${favicon(logo)}')"></span>`
      : `<span class="sq" style="background:${color}">${ic(icon)}</span>`;
    return `<div class="row">${lead}<div class="texts"><div class="title">${esc(title)}</div>${sub ? `<div class="sub">${esc(sub)}</div>` : ''}</div><button class="switch${on ? ' on' : ''}" data-set="${esc(key)}" aria-label="${esc(title)}"></button></div>`;
  }

  function renderSettings() {
    const el = $('#screen-settings');
    const scaleLabel = S.scale < 0.95 ? 'Petit' : S.scale < 1.05 ? 'Normal' : S.scale < 1.2 ? 'Grand' : 'Très grand';
    const pubs = S.data?.publishers || [];
    const group = kind => pubs.filter(p => S.kinds[p.id]?.has(kind))
      .map(p => switchRow(p.name, `pub:${kind}:${p.id}`, !S.disabled.has(`${kind}:${p.id}`), { logo: p.domain })).join('');
    el.innerHTML = `<div class="inline-title">Réglages</div><h1 class="large-title">Réglages</h1>
      ${standalone ? '' : `<div class="install"><b>Installer Gideon sur l’iPhone</b>Touche ${ic('share')} Partager dans Safari, puis « Sur l’écran d’accueil ». Gideon s’ouvrira en plein écran comme une app.</div><div style="height:22px"></div>`}
      <div class="inset">
        ${switchRow('Son des vidéos', 'sound', S.sound, { icon: 'sound', color: '#0a84ff' })}
        ${switchRow('YouTube Shorts', 'shorts', S.shorts, { icon: 'play', color: '#ff3b30', sub: 'Vidéos verticales courtes' })}
        <div class="row" data-row="scale"><span class="sq" style="background:#8e8e93">${ic('text')}</span><div class="texts"><div class="title">Taille du texte</div></div><span class="value">${scaleLabel}</span>${ic('chevron', 'chev')}</div>
        <div class="row" data-row="refresh"><span class="sq" style="background:#30d158">${ic('refresh')}</span><div class="texts"><div class="title">Actualiser</div></div><span class="value">${ago(S.data?.updatedAt)}</span></div>
      </div>
      <div class="section-head">Journaux</div><div class="inset">${group('rss')}</div>
      <div class="section-foot">Désactive un journal pour ne plus voir ses articles dans ton fil.</div>
      <div class="section-head">Chaînes vidéo</div><div class="inset">${group('youtube')}</div>
      <div class="section-head">À propos</div>
      <div class="inset"><div class="row"><span class="logo-tile" style="background:#07070a"><svg viewBox="0 0 108 108" width="30" height="30"><g fill="none" stroke-width="9" stroke-linecap="round"><path d="M67.44,40.56 A19,19 0 1,0 73,54 L56,54" stroke="#fff"/></g></svg></span><div class="texts"><div class="title">Gideon Web</div><div class="sub">${pubs.length} médias · mis à jour ${ago(S.data?.updatedAt)}</div></div></div></div>
      <div class="section-foot">Gideon rassemble automatiquement les flux publics (RSS et YouTube) des médias. Les articles et vidéos restent la propriété de leurs éditeurs.</div>`;
  }

  $('#screen-settings').addEventListener('click', e => {
    const sw = e.target.closest('[data-set]');
    if (sw) {
      const key = sw.dataset.set;
      if (key === 'sound') { S.sound = !S.sound; store.set('sound', S.sound); }
      else if (key === 'shorts') { S.shorts = !S.shorts; store.set('shorts', S.shorts); }
      else if (key.startsWith('pub:')) {
        const id = key.slice(4);
        S.disabled.has(id) ? S.disabled.delete(id) : S.disabled.add(id);
        store.set('disabled', [...S.disabled]);
      }
      sw.classList.toggle('on');
      clearTimeout(sw._t);
      sw._t = setTimeout(rebuild, 350);
      return;
    }
    const row = e.target.closest('[data-row]');
    if (row?.dataset.row === 'scale') {
      const steps = [0.9, 1, 1.12, 1.25];
      S.scale = steps[(steps.findIndex(s => s >= S.scale - 0.01) + 1) % steps.length];
      store.set('scale', S.scale);
      document.documentElement.style.setProperty('--text-scale', S.scale);
      renderSettings();
    } else if (row?.dataset.row === 'refresh') load(true);
  });

  // ------------------------------------------------------------------ reader sheet
  const sheetHost = $('#sheet');
  const sheet = $('.sheet', sheetHost);
  const scroller = $('.sheet-scroll', sheetHost);
  const articleUrl = it => `data/a/${it.id.replace(':', '_')}.json`;

  function readerHead(it, art) {
    const pub = pubOf(it);
    const cat = CATS[it.c];
    const meta = [art?.byline, new Date(it.d * 1000).toLocaleString('fr-FR', { day: 'numeric', month: 'long', hour: '2-digit', minute: '2-digit' }), art && !art.truncated ? `${art.minutes} min de lecture` : null].filter(Boolean).join(' · ');
    return `<div class="r-head">
      <div class="r-src"><img src="${favicon(pub.domain)}" alt="">${esc(pub.name)}${it.c !== 'UNE' && cat ? `<span style="color:var(--label2);font-weight:400">&nbsp;·&nbsp;</span><span style="color:${cat[1]}">${cat[0]}</span>` : ''}</div>
      <h1 class="r-title">${esc(it.t)}</h1>
      ${art?.standfirst ? `<p class="r-stand">${esc(art.standfirst)}</p>` : ''}
      <div class="r-meta">${esc(meta)}</div></div>`;
  }

  function readerFoot(it) {
    const pub = pubOf(it);
    return `<div class="r-foot"><img src="${favicon(pub.domain)}" alt=""><div class="t"><b>${esc(pub.name)}</b><small>Publié ${ago(it.d)}</small></div>
      <a class="btn" href="${esc(it.l)}" target="_blank" rel="noopener">${ic('safari')}Page d’origine</a></div>`;
  }

  async function openReader(it) {
    S.reader = it;
    bump(it.c, 0.5);
    destroyPlayer();
    scroller.scrollTop = 0;
    const hero = it.i ? `<img class="r-hero" src="${esc(it.i)}" alt="" referrerpolicy="no-referrer" onerror="this.outerHTML='<div class=r-spacer></div>'">` : '<div class="r-spacer"></div>';
    if (it.v) {
      scroller.innerHTML = `<div class="r-spacer"></div><div class="r-video"><iframe src="https://www.youtube-nocookie.com/embed/${esc(it.v)}?playsinline=1&rel=0&modestbranding=1" allow="autoplay; encrypted-media; picture-in-picture" allowfullscreen></iframe></div>${readerHead(it, null)}<div class="r-body"><p>${esc(it.s || '')}</p></div>${readerFoot(it)}`;
    } else {
      scroller.innerHTML = hero + readerHead(it, null) + `<div class="r-body skeleton">${'<i style="width:96%"></i><i></i><i style="width:88%"></i><i style="width:70%"></i><br>'.repeat(2)}</div>` + readerFoot(it);
    }
    updateSaveButton();
    document.body.classList.add('sheet-open');
    sheetHost.setAttribute('aria-hidden', 'false');
    if (!history.state?.sheet) history.pushState({ sheet: 1 }, '');
    if (it.v) return;
    let art = null;
    if (it.a) {
      try {
        const r = await fetch(articleUrl(it));
        if (r.ok) art = await r.json();
      } catch { /* offline */ }
    }
    if (S.reader !== it) return;
    const body = art
      ? art.blocks.map(b => {
          if (b.t === 'p') return `<p>${esc(b.x)}</p>`;
          if (b.t === 'h') return `<h3>${esc(b.x)}</h3>`;
          if (b.t === 'q') return `<blockquote>${esc(b.x)}</blockquote>`;
          if (b.t === 'li') return `<li>${esc(b.x)}</li>`;
          if (b.t === 'img') return `<figure><img src="${esc(b.u)}" alt="" loading="lazy" referrerpolicy="no-referrer" onerror="this.parentNode.remove()">${b.x ? `<figcaption>${esc(b.x)}</figcaption>` : ''}</figure>`;
          return '';
        }).join('')
      : `<p>${esc(it.s || '')}</p>`;
    const card = !art
      ? `<div class="r-card"><b>Le texte complet n’est pas disponible ici.</b><a class="btn blue" href="${esc(it.l)}" target="_blank" rel="noopener">${ic('safari')}Lire sur ${esc(pubOf(it).name)}</a></div>`
      : art.truncated
        ? `<div class="r-card"><b>La suite est réservée aux abonnés de ${esc(pubOf(it).name)}.</b><a class="btn blue" href="${esc(it.l)}" target="_blank" rel="noopener">${ic('safari')}Continuer sur le site</a></div>`
        : '';
    const heroUrl = it.i || art?.hero;
    const heroHtml = heroUrl ? `<img class="r-hero" src="${esc(heroUrl)}" alt="" referrerpolicy="no-referrer" onerror="this.outerHTML='<div class=r-spacer></div>'">` : '<div class="r-spacer"></div>';
    scroller.innerHTML = heroHtml + readerHead(it, art) + `<div class="r-body">${body}</div>` + card + readerFoot(it);
  }

  function closeReader(fromHistory) {
    if (!S.reader) return;
    S.reader = null;
    if (fromHistory !== true && history.state?.sheet) history.back();
    document.body.classList.remove('sheet-open');
    sheetHost.setAttribute('aria-hidden', 'true');
    sheet.style.setProperty('--drag', '0px');
    setTimeout(() => { if (!S.reader) scroller.innerHTML = ''; }, 500);
    if (S.screen === 'feed') {
      const page = feedEl.querySelector(`.page[data-i="${S.active}"]`);
      const it = S.list[S.active];
      if (page && it?.v && !player) createPlayer(page, it);
    }
  }

  function updateSaveButton() {
    const btn = $('[data-act="save"]', sheetHost);
    const on = S.reader && isSaved(S.reader.id);
    btn.innerHTML = ic('bookmark', on ? 'filled' : '');
    btn.style.color = on ? '#ff9f0a' : '';
  }

  $('.sheet-bar', sheetHost).addEventListener('click', e => {
    const act = e.target.closest('[data-act]')?.dataset.act;
    if (!act || !S.reader) return;
    if (act === 'close') closeReader();
    else if (act === 'share') share(S.reader);
    else if (act === 'save') { toggleSave(S.reader); updateSaveButton(); }
    else if (act === 'text') {
      const steps = [0.9, 1, 1.12, 1.25];
      S.scale = steps[(steps.findIndex(s => s >= S.scale - 0.01) + 1) % steps.length];
      store.set('scale', S.scale);
      document.documentElement.style.setProperty('--text-scale', S.scale);
      renderSettings();
    }
  });
  $('.sheet-dim', sheetHost).addEventListener('click', () => closeReader());

  // pull the sheet down to dismiss (from the grabber, or when the article is scrolled to the top)
  let dragStart = null;
  sheet.addEventListener('touchstart', e => {
    if (e.target.closest('.grabber') || scroller.scrollTop <= 0) dragStart = e.touches[0].clientY;
  }, { passive: true });
  sheet.addEventListener('touchmove', e => {
    if (dragStart == null) return;
    const dy = e.touches[0].clientY - dragStart;
    if (dy > 0 && scroller.scrollTop <= 0) {
      sheet.classList.add('dragging');
      sheet.style.setProperty('--drag', dy + 'px');
    } else if (dy < 0) {
      dragStart = null;
      sheet.classList.remove('dragging');
      sheet.style.setProperty('--drag', '0px');
    }
  }, { passive: true });
  sheet.addEventListener('touchend', () => {
    if (dragStart == null) return;
    const d = parseFloat(sheet.style.getPropertyValue('--drag')) || 0;
    dragStart = null;
    sheet.classList.remove('dragging');
    sheet.style.setProperty('--drag', '0px');
    if (d > 130) closeReader();
  });

  // Back gesture / history: close the reader or the search first
  window.addEventListener('popstate', () => {
    if (S.reader) closeReader(true);
    else if (S.screen === 'search') show(S.lastTab);
  });

  document.addEventListener('visibilitychange', () => {
    if (document.visibilityState === 'visible' && S.data && Date.now() / 1000 - S.data.updatedAt > 20 * 60) load(false);
    if (document.visibilityState === 'hidden' && player?.pauseVideo) player.pauseVideo();
  });

  // ------------------------------------------------------------------ start
  show('feed');
  renderFeed();
  renderSearch();
  renderSaved();
  renderSettings();
  load(false);
  if ('serviceWorker' in navigator) navigator.serviceWorker.register('sw.js').catch(() => {});
})();
