package com.chen.doudoutv.data.pair

/**
 * 手机扫码后打开的那一张页面。
 *
 * 两个 TAB：「视频来源」沿用电视端来源维护的口径 —— 一页搞定，顶上那排 chips 在已有来源之间切换，
 * 选中即回填，改完点「保存并推送到电视」；没有删除入口 —— 手机上抹掉电视配置的误触代价太高，
 * 电视端自己那套红色二次确认才是删除的唯一出口。「TMDB 密钥」维护刮削开关与两版凭据。
 *
 * 密码与密钥这两个敏感项同一口径：默认不回显库里的原值，留空即保持不动；
 * 电视输入法那条路已经证明「看不见就等于出错」，所以输入框明文显示。
 *
 * 这是浏览器页面不是效果图，所以带 JS；所有请求都要把二维码里那串 k 带回来。
 */
internal object PairWebPage {

    val html: String = """
<!DOCTYPE html>
<html lang="zh-CN">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1,viewport-fit=cover">
<meta name="theme-color" content="#06080C">
<title>维护电视设置</title>
<style>
:root { color-scheme: dark; }
* { box-sizing: border-box; -webkit-tap-highlight-color: transparent; }
body {
  margin: 0; padding: 20px 16px 40px;
  background: #06080C; color: #FFFFFF;
  font: 16px/1.5 -apple-system, BlinkMacSystemFont, "PingFang SC", "Microsoft YaHei", sans-serif;
}
.wrap { max-width: 520px; margin: 0 auto; }
h1 { font-size: 20px; margin: 0 0 4px; font-weight: 700; }
.sub { font-size: 13px; color: #99FFFFFF; margin-bottom: 14px; }
.tabs { display: flex; gap: 8px; margin-bottom: 14px; }
.tab {
  flex: 1; padding: 12px; font-size: 15px; border-radius: 11px;
  background: #0F141B; border: 1px solid #40FFFFFF; color: #B3FFFFFF;
}
.tab.on { background: #4F8CFF; border-color: #4F8CFF; color: #FFFFFF; font-weight: 600; }
.chips { display: flex; gap: 8px; overflow-x: auto; padding: 2px 0 12px; }
.chip {
  flex: 0 0 auto; padding: 9px 15px; border-radius: 999px; font-size: 14px;
  background: #0F141B; border: 1px solid #40FFFFFF; color: #B3FFFFFF; white-space: nowrap;
}
.chip.on { background: #4F8CFF; border-color: #4F8CFF; color: #FFFFFF; font-weight: 600; }
.chip.add { border-style: dashed; }
.card { background: #0F141B; border: 1px solid #2A3441; border-radius: 14px; padding: 18px 16px; }
label { display: block; font-size: 13px; color: #B3FFFFFF; margin: 0 0 7px; }
.field { margin-bottom: 16px; }
input, select {
  width: 100%; padding: 13px 14px; font-size: 16px; border-radius: 11px;
  background: #06080C; border: 1px solid #40FFFFFF; color: #FFFFFF;
}
input:focus, select:focus { outline: none; border-color: #4F8CFF; }
.hint { font-size: 12px; color: #73FFFFFF; margin-top: 6px; }
.seg { display: flex; gap: 8px; }
.seg button {
  flex: 1; padding: 12px; font-size: 15px; border-radius: 11px;
  background: #06080C; border: 1px solid #40FFFFFF; color: #B3FFFFFF;
}
.seg button.on { background: #18202A; border-color: #4F8CFF; color: #FFFFFF; font-weight: 600; }
.row2 { display: flex; align-items: center; justify-content: space-between; }
.link { background: none; border: 0; color: #4F8CFF; font-size: 13px; padding: 4px 0; }
.save {
  width: 100%; padding: 15px; font-size: 16px; font-weight: 700; border: 0; border-radius: 12px;
  background: #4F8CFF; color: #FFFFFF;
}
.save:disabled { opacity: .5; }
.toast { display: none; margin-top: 14px; padding: 12px 14px; border-radius: 11px; font-size: 14px; }
.toast.ok { display: block; background: rgba(46,160,67,.16); border: 1px solid rgba(46,160,67,.5); }
.toast.bad { display: block; background: rgba(229,72,77,.16); border: 1px solid rgba(229,72,77,.5); }
[hidden] { display: none !important; }
</style>
</head>
<body>
<div class="wrap">
  <h1>维护电视设置</h1>
  <div class="sub">改完点保存，配置立刻推到这台上的电视</div>

  <div class="tabs">
    <button type="button" class="tab on" id="tabSrc">视频来源</button>
    <button type="button" class="tab" id="tabTmdb">TMDB 密钥</button>
  </div>

  <!-- TAB 1：视频来源 -->
  <div id="paneSrc">
    <div class="chips" id="chips"></div>

    <div class="card">
      <div class="field">
        <label>来源类型</label>
        <div class="seg" id="seg">
          <button type="button" data-kind="EMBY">Emby 服务器</button>
          <button type="button" data-kind="TVBOX">TVBox 接口</button>
        </div>
      </div>

      <div class="field">
        <label for="name">名称</label>
        <input id="name" type="text" autocomplete="off" placeholder="例如：我的 Emby">
      </div>

      <div class="field">
        <label for="location">地址</label>
        <input id="location" type="text" autocomplete="off" placeholder="http://192.168.1.10:8096">
        <div class="hint">Emby 填服务器地址；TVBox 填配置 JSON 地址。没写 http 会自动补。</div>
      </div>

      <div id="cred">
        <div class="field">
          <label for="username">用户名</label>
          <input id="username" type="text" autocomplete="off">
        </div>
        <div class="field">
          <div class="row2">
            <label for="password" style="margin:0">密码</label>
            <button type="button" class="link" id="toggle">显示</button>
          </div>
          <input id="password" type="password" autocomplete="off" style="margin-top:7px">
          <div class="hint" id="pwHint">电视端已存过密码，这里不会回显；留空表示不改。</div>
        </div>
      </div>

      <button type="button" class="save" id="save">保存并推送到电视</button>
      <div class="toast" id="toast"></div>
    </div>
  </div>

  <!-- TAB 2：TMDB 密钥 -->
  <div id="paneTmdb" hidden>
    <div class="card">
      <div class="field">
        <label>刮削开关</label>
        <div class="seg" id="tmdbSeg">
          <button type="button" data-on="1">开启</button>
          <button type="button" data-on="0">关闭</button>
        </div>
        <div class="hint">关闭时电视端不展示任何 TMDB 功能，也不发起刮削请求。</div>
      </div>

      <div class="field">
        <label for="apiKey">API Key（v3）</label>
        <input id="apiKey" type="text" autocomplete="off">
        <div class="hint" id="akHint"></div>
      </div>

      <div class="field">
        <label for="readToken">Read Access Token（v4）</label>
        <input id="readToken" type="text" autocomplete="off">
        <div class="hint" id="rtHint"></div>
      </div>

      <div class="hint" style="margin:-6px 0 14px">两者填一个即可；都填时电视端优先用 v4 令牌。留空表示保持电视端已存的值不改。</div>

      <button type="button" class="save" id="saveTmdb">保存并推送到电视</button>
      <div class="toast" id="toastTmdb"></div>
    </div>
  </div>
</div>

<script>
var K = new URLSearchParams(location.search).get('k') || '';
var sources = [];
var currentId = 0;   // 0 = 新增
var kind = 'TVBOX';
var tmdbEnabled = false;
var tmdbLoaded = false;

var el = function (id) { return document.getElementById(id); };

function api(path, options) {
  var sep = path.indexOf('?') >= 0 ? '&' : '?';
  return fetch(path + sep + 'k=' + encodeURIComponent(K), options);
}

function show(ok, text) { toastTo('toast', ok, text); }
function showTmdb(ok, text) { toastTo('toastTmdb', ok, text); }
function toastTo(id, ok, text) {
  var t = el(id);
  t.className = 'toast ' + (ok ? 'ok' : 'bad');
  t.textContent = text;
}

/* ---------- TAB 切换 ---------- */
function pickTab(src) {
  el('tabSrc').className = 'tab' + (src ? ' on' : '');
  el('tabTmdb').className = 'tab' + (src ? '' : ' on');
  el('paneSrc').hidden = !src;
  el('paneTmdb').hidden = !!src;
  if (!src && !tmdbLoaded) loadTmdb();
}
el('tabSrc').onclick = function () { pickTab(true); };
el('tabTmdb').onclick = function () { pickTab(false); };

/* ---------- TAB 1：视频来源 ---------- */
function setKind(value) {
  kind = value;
  var kids = el('seg').children;
  for (var i = 0; i < kids.length; i++) {
    kids[i].className = kids[i].dataset.kind === value ? 'on' : '';
  }
  el('cred').hidden = value !== 'EMBY';
  if (value !== 'EMBY') { el('username').value = ''; el('password').value = ''; }
}

function select(src) {
  currentId = src ? src.id : 0;
  var chips = el('chips').children;
  for (var i = 0; i < chips.length; i++) {
    chips[i].className = 'chip ' + (Number(chips[i].dataset.id) === currentId ? 'on' : '');
  }
  el('name').value = src ? src.name : '';
  el('location').value = src ? src.location : '';
  setKind(src ? src.kind : kind);
  if (src && src.kind === 'EMBY') {
    el('username').value = src.username || '';
    el('password').value = '';
    el('pwHint').hidden = !src.hasPassword;
    el('pwHint').textContent = src.hasPassword
      ? '电视端已存过密码，这里不会回显；留空表示不改。'
      : '这条还没有密码，填了才能登录。';
  } else {
    el('pwHint').hidden = true;
  }
  el('toast').className = 'toast';
}

function render() {
  var box = el('chips');
  box.innerHTML = '';
  for (var i = 0; i < sources.length; i++) {
    var b = document.createElement('button');
    b.type = 'button';
    b.className = 'chip';
    b.dataset.id = sources[i].id;
    b.textContent = sources[i].name;
    (function (src) { b.onclick = function () { select(src); }; })(sources[i]);
    box.appendChild(b);
  }
  var add = document.createElement('button');
  add.type = 'button';
  add.className = 'chip add';
  add.dataset.id = '0';
  add.textContent = '＋ 新增来源';
  add.onclick = function () { select(null); };
  box.appendChild(add);
}

function load() {
  api('/api/sources')
    .then(function (r) { if (!r.ok) throw new Error('' + r.status); return r.json(); })
    .then(function (data) {
      sources = data.sources || [];
      render();
      select(sources.length ? sources[0] : null);
    })
    .catch(function () { show(false, '连不上电视，请关掉页面重新扫码。'); });
}

el('seg').onclick = function (e) {
  if (e.target.dataset && e.target.dataset.kind) setKind(e.target.dataset.kind);
};

el('toggle').onclick = function () {
  var p = el('password');
  var open = p.type === 'password';
  p.type = open ? 'text' : 'password';
  el('toggle').textContent = open ? '隐藏' : '显示';
};

el('save').onclick = function () {
  var btn = el('save');
  btn.disabled = true;
  btn.textContent = '推送中…';
  api('/api/source', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({
      id: currentId,
      name: el('name').value,
      kind: kind,
      location: el('location').value,
      username: el('username').value,
      password: el('password').value
    })
  })
    .then(function (r) { return r.json(); })
    .then(function (res) {
      show(!!res.ok, res.message || (res.ok ? '已推送到电视' : '推送失败'));
      if (res.ok) load();
    })
    .catch(function () { show(false, '电视没有应答，再点一次试试。'); })
    .then(function () {
      btn.disabled = false;
      btn.textContent = '保存并推送到电视';
    });
};

/* ---------- TAB 2：TMDB 密钥 ---------- */
function setTmdbEnabled(value) {
  tmdbEnabled = value;
  var kids = el('tmdbSeg').children;
  for (var i = 0; i < kids.length; i++) {
    kids[i].className = (kids[i].dataset.on === '1') === value ? 'on' : '';
  }
}

el('tmdbSeg').onclick = function (e) {
  if (e.target.dataset && e.target.dataset.on) setTmdbEnabled(e.target.dataset.on === '1');
};

function loadTmdb() {
  api('/api/tmdb')
    .then(function (r) { if (!r.ok) throw new Error('' + r.status); return r.json(); })
    .then(function (d) {
      tmdbLoaded = true;
      setTmdbEnabled(!!d.enabled);
      el('akHint').textContent = d.hasApiKey
        ? '电视端已存过 API Key，这里不会回显；留空表示不改。'
        : '还没填。TMDB 网站 → Settings → API 里申请。';
      el('rtHint').textContent = d.hasReadToken
        ? '电视端已存过令牌，这里不会回显；留空表示不改。'
        : '还没填。v4 的 Read Access Token。';
    })
    .catch(function () { showTmdb(false, '连不上电视，请关掉页面重新扫码。'); });
}

el('saveTmdb').onclick = function () {
  var btn = el('saveTmdb');
  btn.disabled = true;
  btn.textContent = '推送中…';
  api('/api/tmdb', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({
      enabled: tmdbEnabled,
      apiKey: el('apiKey').value,
      readToken: el('readToken').value
    })
  })
    .then(function (r) { return r.json(); })
    .then(function (res) {
      showTmdb(!!res.ok, res.message || (res.ok ? '已推送到电视' : '推送失败'));
      if (res.ok) { el('apiKey').value = ''; el('readToken').value = ''; loadTmdb(); }
    })
    .catch(function () { showTmdb(false, '电视没有应答，再点一次试试。'); })
    .then(function () {
      btn.disabled = false;
      btn.textContent = '保存并推送到电视';
    });
};

setKind('TVBOX');
load();
</script>
</body>
</html>
""".trimIndent()
}
