const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');
const path = require('node:path');
const source = fs.readFileSync(path.join(__dirname, '../src/nfc/wallet/compat/WarrantyScript.java'), 'utf8');
const script = source.match(/SCRIPT = """([\s\S]*?)""";/)[1].replaceAll('__DEVICE_IMEI__', '490154203237518');
const imei = '490154203237518'; // Public Luhn example.
const tick = () => new Promise(resolve => setImmediate(resolve));
function setup({goodOrigin = true, goodPath = true, success = true, late = false, pagePath} = {}) {
  const calls = [], page = {dataImei: {}, isShowInputImei: late, $children: [], popups: 0,
    dataIMEI() {}, popupImei() { this.popups++; this.isShowInputImei = true; },
    async getSecurity(id) { calls.push(id); if (success) this.dataImei = {deviceId: id}; else this.popupImei(); }
  };
  const location = {origin: goodOrigin ? 'https://rinsurance.finzfin.com' : 'https://example.com',
    pathname: pagePath || (goodPath ? '/insurance/screenSecurity/electronicCard/' : '/insurance/trade/'), href: 'page'};
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
  const cached = setup(), removed = [];
  const memory = {getDeviceInfo: {imei: '000000000000000'}, getBaseDeviceInfo: {imei}, getToken: {token: 'unchanged'}};
  const storage = {getDeviceInfo: {imei: '000000000000000'}, getHeader: {imei: '000000000000000', id: '000000000000000'}};
  cached.window.RainbowBridge = {
    getCache(method) { return memory[method] || storage[method]; },
    removeMemoryCache(method) { removed.push('memory:' + method); delete memory[method]; },
    removeStorageCache(method) { removed.push('storage:' + method); delete storage[method]; }
  };
  cached.run();
  assert.equal(memory.getDeviceInfo, undefined); assert.equal(storage.getDeviceInfo, undefined);
  assert.equal(storage.getHeader, undefined); assert.deepEqual(memory.getBaseDeviceInfo, {imei});
  assert.deepEqual(memory.getToken, {token: 'unchanged'});
  assert.equal(removed.length, 4); // Remove stale memory AND storage, including storage-only entries.
  const firstWrapper = cached.page.getSecurity;
  memory.getDeviceInfo = {imei: '000000000000000'}; cached.run();
  assert.equal(memory.getDeviceInfo, undefined); assert.equal(cached.page.getSecurity, firstWrapper);
  const throwingCache = setup(); throwingCache.window.RainbowBridge = {
    getCache() { throw new Error('cache unavailable'); }, removeMemoryCache() {}, removeStorageCache() {}
  };
  throwingCache.run(); await throwingCache.page.getSecurity(); assert.deepEqual(throwingCache.calls, [imei]);
  const race = setup(); let finish;
  race.page.getSecurity = async function(id) {race.calls.push(id); await new Promise(resolve => {finish=resolve;}); this.dataImei={deviceId:id};};
  race.run(); const query = race.page.getSecurity(); race.page.popupImei();
  assert.equal(race.page.isShowInputImei, true); finish(); await query;
  assert.deepEqual(race.calls, [imei]); assert.equal(race.page.isShowInputImei, false);
  const rejectedOldData = setup({success: false}); rejectedOldData.page.dataImei = {deviceId: imei};
  rejectedOldData.run(); await rejectedOldData.page.getSecurity();
  assert.equal(rejectedOldData.page.isShowInputImei, true); // Old valid data must not hide a new server rejection.
  const battery = setup({pagePath: '/insurance/screenSecurity/screenProduct/'});
  const batteryLookup = battery.page.getSecurity; let batteryInvalidated = 0;
  battery.window.RainbowBridge = {getCache(){return {imei:'000000000000000'};},removeMemoryCache(){batteryInvalidated++;},removeStorageCache(){batteryInvalidated++;}};
  battery.run(); assert.equal(battery.page.getSecurity, batteryLookup); assert.equal(batteryInvalidated,6);
  console.log('PASS: warranty lookup origin/path, real-device fallback, explicit user choice, server rejection, stale device caches and late popup race');
})().catch(error => { console.error(error); process.exitCode = 1; });
