// UI 冒烟脚本：用 Chrome CDP 点一遍侧边栏菜单 + 发起一次导出。
// 前置：1) 后端 8080、前端 5173 在跑 2) 先用 cdp-start.ps1 起 Chrome(9222)
// 用法：node scripts/ui-smoke.mjs   （在 Windows 端用 D:\nodejs\node.exe 跑）

// Click every sidebar item, then run a real export from the UI.
const CDP = 'http://127.0.0.1:9222';
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));
const tabs = await (await fetch(`${CDP}/json`)).json();
const page = tabs.find((t) => t.type === 'page');
const ws = new WebSocket(page.webSocketDebuggerUrl);
let seq = 0;
const pending = new Map();
ws.addEventListener('message', (e) => {
  const m = JSON.parse(e.data);
  if (m.id && pending.has(m.id)) { pending.get(m.id)(m.result); pending.delete(m.id); }
});
await new Promise((r) => ws.addEventListener('open', r, { once: true }));
const send = (method, params = {}) => new Promise((res) => { const id = ++seq; pending.set(id, res); ws.send(JSON.stringify({ id, method, params })); });
const evalJs = async (expression) => {
  const r = await send('Runtime.evaluate', { expression, returnByValue: true, awaitPromise: true });
  if (r.exceptionDetails) return { error: r.exceptionDetails.text };
  return r.result?.value;
};
await send('Page.enable'); await send('Runtime.enable');

await send('Page.navigate', { url: 'http://127.0.0.1:5173/admin/tickets' });
await sleep(3000);

const items = await evalJs(`[...document.querySelectorAll('.el-menu-item')].map(e=>e.textContent.trim())`);
const results = [];
for (const name of items) {
  await evalJs(`(function(){const it=[...document.querySelectorAll('.el-menu-item')].find(e=>e.textContent.includes(${JSON.stringify(name)})); if(it) it.click();})()`);
  await sleep(1400);
  const path = await evalJs(`location.pathname`);
  const crumb = await evalJs(`(document.querySelector('.crumb')||{}).textContent`);
  results.push({ menu: name, path, crumb });
}

// real export from the UI
await evalJs(`(function(){const it=[...document.querySelectorAll('.el-menu-item')].find(e=>e.textContent.includes('导出中心')); if(it) it.click();})()`);
await sleep(1200);
await evalJs(`(function(){const b=[...document.querySelectorAll('button')].find(x=>x.textContent.includes('发起导出')); if(b) b.click();})()`);

let uiStatus = 'no row';
for (let i = 0; i < 25; i++) {
  await sleep(1000);
  const s = await evalJs(`(function(){
    const rows=[...document.querySelectorAll('.el-table__row')];
    if(!rows.length) return 'no row';
    const cells=[...rows[0].querySelectorAll('td')].map(c=>c.innerText.trim());
    const hasBtn = !!rows[0].querySelector('button');
    return cells.slice(0,4).join(' | ') + (hasBtn ? ' | 有下载按钮' : ' | 无按钮');
  })()`);
  uiStatus = s;
  if (String(s).includes('有下载按钮') || String(s).includes('失败')) break;
}

const shot = await send('Page.captureScreenshot', { format: 'png' });
const fs = await import('node:fs');
fs.writeFileSync('D:\\_setup\\shot-flow.png', Buffer.from(shot.data, 'base64'));
console.log(JSON.stringify({ menuResults: results, uiStatus }, null, 2));
ws.close();
