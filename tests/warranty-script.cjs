const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');
const path = require('node:path');
const source = fs.readFileSync(path.join(__dirname,'../src/nfc/wallet/compat/WarrantyScript.java'),'utf8');
const imei = '490154203237518'; // Public Luhn example.
const script = source.match(/SCRIPT = """([\s\S]*?)""";/)[1].replaceAll('__DEVICE_IMEI__',imei);
const tick = () => new Promise(resolve => setImmediate(resolve));
const valid = id => ({modelMatch:true,mobileInfoRespVO:{deviceId:id}});
function setup({origin='https://rinsurance.finzfin.com',pagePath='/insurance/screenSecurity/electronicCard/',lookup}={}) {
 const calls=[],events={};
 const methods={
  async applySecurityData(data) {if(!data.modelMatch){this.popupImei();return;}this.dataImei=data.mobileInfoRespVO||{};if(!this.dataImei.deviceId)this.popupImei();},
  popupImei(){this.isShowInputImei=true;},dataIMEI(){},
  getSecurity:lookup||async function(id){calls.push(id);await this.applySecurityData(valid(id));}
 };
 const page={dataImei:{},isShowInputImei:false,$children:[],$options:{methods}};
 for(const [name,method] of Object.entries(methods))page[name]=method.bind(page); // Vue binds instance methods.
 const window={$nuxt:{$children:[page]}};
 const document={addEventListener(name,fn){events[name]=fn;},removeEventListener(name){delete events[name];}};
 const context=vm.createContext({window,document,location:{origin,pathname:pagePath,href:'page'},setTimeout,Set,Promise,Proxy,Reflect});
 return {page,window,calls,run(){vm.runInContext(script,context);},interact(){events.pointerdown?.({isTrusted:true});}};
}
(async()=>{
 for(const config of [{origin:'https://example.com'},{pagePath:'/insurance/trade/'}]) {
  const x=setup(config),original=x.page.getSecurity;x.run();await tick();assert.equal(x.page.getSecurity,original);assert.equal(x.calls.length,0);
 }
 const x=setup();x.run();await tick();assert.deepEqual(x.calls,[imei]);assert.equal(x.page.dataImei.deviceId,imei);
 const wrapped=x.page.getSecurity;x.run();await tick();assert.equal(x.page.getSecurity,wrapped);assert.equal(x.calls.length,1);
 await x.page.applySecurityData({modelMatch:false});x.page.popupImei();assert.equal(x.page.isShowInputImei,false); // Old untracked responses/errors cannot overwrite this query.
 x.interact();x.page.popupImei();assert.equal(x.page.isShowInputImei,false);
 await x.page.getSecurity('another-device');assert.equal(x.calls.at(-1),'another-device');assert.equal(x.page.dataImei.deviceId,'another-device');
 await x.page.getSecurity('000000000000000');assert.equal(x.calls.at(-1),imei);
 const pending=[];
 const race=setup({lookup:async function(id){await new Promise(resolve=>pending.push(resolve));await this.applySecurityData(valid(id));}});
 race.run();race.interact();const latest=race.page.getSecurity('explicit-device');assert.equal(pending.length,2);
 pending[1]();await latest;pending[0]();await tick();assert.equal(race.page.dataImei.deviceId,'explicit-device');
 const denied=setup({lookup:async function(id){await this.applySecurityData(valid(id));await this.applySecurityData({modelMatch:false});}});
 denied.run();await tick();assert.equal(denied.page.isShowInputImei,true); // Preserve the latest official refusal after cached valid data.
 const lateEmpty=setup({lookup:async function(id){await this.applySecurityData(valid(id));await this.applySecurityData({modelMatch:true,mobileInfoRespVO:{}});}});
 lateEmpty.run();await tick();assert.equal(lateEmpty.page.dataImei.deviceId,imei);assert.equal(lateEmpty.page.isShowInputImei,false);
 let attempts=0;
 const empty=setup({lookup:async function(){attempts++;await this.applySecurityData({modelMatch:true,mobileInfoRespVO:{deviceId:'000000000000000'}});}});
 empty.run();await tick();await tick();assert.equal(attempts,2);assert.equal(empty.page.isShowInputImei,true); // Bounded recovery, no fabricated result.
 const cached=setup(),removed=[];
 const memory={getDeviceInfo:{imei:'000000000000000'},getBaseDeviceInfo:{imei},getToken:{token:'unchanged'}};
 const storage={getDeviceInfo:{imei:'000000000000000'},getHeader:{imei:'000000000000000',id:'000000000000000'}};
 cached.window.RainbowBridge={getCache(k){return memory[k]||storage[k];},removeMemoryCache(k){removed.push('memory:'+k);delete memory[k];},removeStorageCache(k){removed.push('storage:'+k);delete storage[k];}};
 cached.run();await tick();assert.equal(memory.getDeviceInfo,undefined);assert.equal(storage.getDeviceInfo,undefined);assert.equal(storage.getHeader,undefined);
 assert.deepEqual(memory.getBaseDeviceInfo,{imei});assert.deepEqual(memory.getToken,{token:'unchanged'});assert.equal(removed.length,4);
 const broken=setup();broken.window.RainbowBridge={getCache(){throw new Error('cache unavailable');},removeMemoryCache(){},removeStorageCache(){}};
 broken.run();await tick();assert.deepEqual(broken.calls,[imei]);
 const battery=setup({pagePath:'/insurance/screenSecurity/screenProduct/'}),original=battery.page.getSecurity;let invalidated=0;
 battery.window.RainbowBridge={getCache(){return {imei:'000000000000000'};},removeMemoryCache(){invalidated++;},removeStorageCache(){invalidated++;}};
 battery.run();assert.equal(battery.page.getSecurity,original);assert.equal(invalidated,6);
 let releaseAux;
 const delayed=setup({lookup:async function(id){await this.applySecurityData(valid(id));await new Promise(resolve=>{releaseAux=resolve;});}});
 delayed.page.isShowInputImei=true;delayed.run();await tick();
 assert.equal(delayed.page.isShowInputImei,false); // Identity is valid before unrelated warranty-cache work completes.
 releaseAux();await tick();
 const auxFailure=setup({lookup:async function(id){await this.applySecurityData(valid(id));this.popupImei();}});
 auxFailure.run();await tick();assert.equal(auxFailure.page.isShowInputImei,false);
 const unidentified=setup({lookup:async function(){this.popupImei();}});
 unidentified.run();await tick();assert.equal(unidentified.page.isShowInputImei,true);
 const manual=setup({lookup:async function(id){await this.applySecurityData(id===imei?{modelMatch:false}:valid(id));}});
 manual.run();await tick();assert.equal(manual.page.isShowInputImei,true);
 await manual.page.getSecurity('manual-device');assert.equal(manual.page.dataImei.deviceId,'manual-device');assert.equal(manual.page.isShowInputImei,false);
 console.log('PASS: Vue-bound methods, request identity, late stale responses, current official refusals, manual queries, scoped cache recovery and bounded retries');
})().catch(error=>{console.error(error);process.exitCode=1;});
