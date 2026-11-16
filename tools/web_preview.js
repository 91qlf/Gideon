// Screenshots of the built web app with the real data (CI, iPhone viewport).
const { chromium, devices } = require('playwright');
(async () => {
  const out = process.argv[2] || 'web-shots';
  require('fs').mkdirSync(out, { recursive: true });
  const b = await chromium.launch();
  const ctx = await b.newContext({ ...devices['iPhone 13'], colorScheme: 'dark' });
  const p = await ctx.newPage();
  const errors = [];
  p.on('pageerror', e => errors.push('pageerror: ' + e.message));
  await p.goto('http://127.0.0.1:8765/index.html');
  await p.waitForTimeout(5000);
  await p.screenshot({ path: `${out}/w1-feed.png` });
  for (let i = 2; i <= 3; i++) {
    await p.evaluate(() => document.querySelector('#feed').scrollBy(0, innerHeight));
    await p.waitForTimeout(2500);
    await p.screenshot({ path: `${out}/w${i}-feed.png` });
  }
  const read = await p.$('.page.active .read');
  if (read) {
    await read.click();
    await p.waitForTimeout(3000);
    await p.screenshot({ path: `${out}/w4-reader.png` });
    await p.click('.sheet-bar [data-act="close"]');
    await p.waitForTimeout(800);
  }
  await p.click('.searchbox');
  await p.waitForTimeout(1500);
  await p.screenshot({ path: `${out}/w5-search.png` });
  await p.click('.tab-folded');
  await p.waitForTimeout(600);
  await p.click('.tab[data-screen="settings"]');
  await p.waitForTimeout(1500);
  await p.screenshot({ path: `${out}/w6-settings.png` });
  require('fs').writeFileSync(`${out}/web-errors.txt`, errors.join('\n') || 'no JS errors');
  console.log(errors.length ? errors.join('\n') : 'no JS errors');
  await b.close();
})();
