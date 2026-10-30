const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');
const path = require('node:path');
const source = fs.readFileSync(path.join(__dirname, '../src/nfc/wallet/compat/WarrantyScript.java'), 'utf8');
const script = source.match(/SCRIPT = """([\s\S]*?)""";/)[1].replaceAll('__DEVICE_IMEI__', '490154203237518');
const imei = '490154203237518'; // Public Luhn example.
const tick = () => new Promise(resolve => setImmediate(resolve));
function setup({goodOrigin = true, goodPath = true, success = true, late = false} = {}) {
  const calls = [], page = {dataImei: {}, isShowInputImei: late, $children: [], popups: 0,
    dataIMEI() {}, popupImei() { this.popups++; this.isShowInputImei = true; },
    async getSecurity(id) { calls.push(id); if (success) this.dataImei = {deviceId: id}; else this.popupImei(); }
  };
  const location = {origin: goodOrigin ? 'https://rinsurance.finzfin.com' : 'https://example.com',
    pathname: goodPath ? '/insurance/screenSecurity/electronicCard/' : '/insurance/trade/', href: 'page'};
  const window = {$nuxt: {$children: [page]}};
  const context = vm.createContext({window, location, setTimeout, Set, Promise});
  return {calls, page, window, run() { vm.runInContext(script, context); }};
}
(async () => {
  for (const options of [{goodOrigin: false}, {goodPath: false}]) {
    const x = setup(options), original = x.page.getSecurity; x.run();
    assert.equal(x.page.getSecurity, original); assert.equal(x.calls.length, 0);
  }
  const x = setup(); x.run(); x.page.popupImei(); await tick();
  assert.deepEqual(x.calls, [imei]); assert.equal(x.page.isShowInputImei, false);
  const wrapped = x.page.getSecurity; x.run(); assert.equal(x.page.getSecurity, wrapped);
  await x.page.getSecurity(); assert.equal(x.calls.at(-1), imei);
  await x.page.getSecurity('explicit-user-choice'); assert.equal(x.calls.at(-1), 'explicit-user-choice');
  x.page.popupImei(); await tick(); assert.equal(x.calls.length, 3); assert.equal(x.page.popups, 1);
  const late = setup({late: true}); late.run(); await tick();
  assert.deepEqual(late.calls, [imei]); assert.equal(late.page.isShowInputImei, false);
  const failed = setup({success: false}); failed.run(); failed.page.popupImei(); await tick();
  assert.deepEqual(failed.calls, [imei]); assert.equal(failed.page.isShowInputImei, true);
  assert.ok(failed.page.popups <= 2); // Server rejection stays visible; no retry loop.
  console.log('PASS: warranty lookup origin/path, real-device fallback, explicit user choice and server rejection');
})().catch(error => { console.error(error); process.exitCode = 1; });
