package nfc.wallet.compat;

final class WarrantyScript {
    static String render(String imei) {
        if (!WarrantyPolicy.validImei(imei)) throw new IllegalArgumentException("Invalid IMEI");
        return SCRIPT.replace("__DEVICE_IMEI__", imei);
    }

    // shortcut: this fallback uses the verified official Vue page methods; recheck when that page changes.
    private static final String SCRIPT = """
        (function () {
          if (location.origin !== 'https://rinsurance.finzfin.com' ||
              (location.pathname !== '/insurance/screenSecurity/electronicCard/' &&
               location.pathname !== '/insurance/screenSecurity/screenProduct/')) return;
          var imei = '__DEVICE_IMEI__', tries = 0, href = location.href;
          var state=window.__nfcWarrantyOutcome||(window.__nfcWarrantyOutcome={phase:'search',request:0,own:false,applied:[],popups:0,stale:0,errorCode:''});
          var oldError=console.error;
          if(!window.__nfcWarrantyConsole){window.__nfcWarrantyConsole=true;console.error=function(){for(var i=0;i<arguments.length;i++){var c=arguments[i]&&arguments[i].code;if(/^\\d{1,10}$/.test(String(c)))state.errorCode=String(c);}return oldError.apply(this,arguments);};}
          function refreshDeviceCache() {
            try {
              var bridge = window.RainbowBridge;
              if (!bridge || typeof bridge.getCache !== 'function' ||
                  typeof bridge.removeMemoryCache !== 'function' || typeof bridge.removeStorageCache !== 'function') return;
              ['getDeviceInfo', 'getBaseDeviceInfo', 'getHeader'].forEach(function (method) {
                var data = bridge.getCache(method);
                if (data && (data.imei !== imei || (method === 'getHeader' && data.id !== imei))) {
                  bridge.removeMemoryCache(method);
                  bridge.removeStorageCache(method);
                }
              });
            } catch (ignored) { }
          }
          refreshDeviceCache();
          if (location.pathname !== '/insurance/screenSecurity/electronicCard/' || window.__nfcWalletWarrantyCompat) return;
          window.__nfcWalletWarrantyCompat = true;
          function install() {
            if (location.href !== href) {state.phase='url-changed';return;}
            var pending = window.$nuxt ? [window.$nuxt] : [], seen = new Set(), page;
            while (pending.length && seen.size < 1000) {
              var node = pending.pop();
              if (!node || seen.has(node)) continue;
              seen.add(node);
              if (typeof node.getSecurity === 'function' && typeof node.popupImei === 'function' &&
                  typeof node.dataIMEI === 'function' && typeof node.applySecurityData === 'function' &&
                  node.$options && node.$options.methods && typeof node.$options.methods.getSecurity === 'function' &&
                  typeof node.$options.methods.applySecurityData === 'function' &&
                  typeof node.isShowInputImei === 'boolean' &&
                  node.dataImei && typeof node.dataImei === 'object') { page = node; break; }
              if (Array.isArray(node.$children)) pending.push.apply(pending, node.$children);
            }
            if (!page) {
              if (++tries < 50) setTimeout(install, 100);
              else {state.phase='timeout';window.__nfcWalletWarrantyCompat = false;}
              return;
            }
            state.phase='attached';
            var lookup = page.$options.methods.getSecurity, popup = page.popupImei, apply = page.$options.methods.applySecurityData;
            var sequence = 0, recoveryLeft = 1, userInteracted = false;
            function userIntent(event) {
              if (!event.isTrusted) return;
              userInteracted = true;state.userInteracted=true;
              ['pointerdown','keydown','click'].forEach(function (name) { document.removeEventListener(name, userIntent, true); });
            }
            ['pointerdown','keydown','click'].forEach(function (name) { document.addEventListener(name, userIntent, true); });
            // Existing untracked requests must not update the page after an explicit-device query starts.
            page.applySecurityData = function () { return Promise.resolve(false); };
            page.popupImei = function () {
              if (userInteracted) {state.legacyPopups=(state.legacyPopups||0)+1;return popup.apply(page, arguments);}
            };
            page.getSecurity = function () {
              var args = Array.prototype.slice.call(arguments);
              if (!args[0] || /^0+$/.test(String(args[0]))) args[0] = imei;
              var request = ++sequence, accepted = false;
              state.request=request;state.own=args[0]===imei;
              refreshDeviceCache();
              var context = new Proxy(page, {
                get: function (target, name) {
                  if (name === 'applySecurityData') return function (data) {
                    if (request !== sequence) {state.stale++;return Promise.resolve(false);}
                    var id = data && data.mobileInfoRespVO && data.mobileInfoRespVO.deviceId;
                    var unknown = !!(data && data.modelMatch && (!id || /^0+$/.test(String(id))));
                    state.applied.push({request:request,match:!!(data&&data.modelMatch),own:id===imei,unknown:unknown});if(state.applied.length>5)state.applied.shift();
                    if (args[0] === imei && unknown) {
                      if (page.dataImei && page.dataImei.deviceId === imei) return Promise.resolve(false);
                      if (recoveryLeft > 0) {
                        recoveryLeft--;
                        Promise.resolve().then(function () { if (request === sequence) page.getSecurity(imei).catch(function () {}); });
                      } else popup.call(page);
                      return Promise.resolve(false);
                    }
                    accepted = !!(data && data.modelMatch && id === args[0]);
                    return apply.call(context, data);
                  };
                  if (name === 'popupImei') return function () { if (request === sequence) {state.popups++;popup.apply(page, arguments);} };
                  return Reflect.get(target, name, target);
                },
                set: function (target, name, value) {
                  return request === sequence ? Reflect.set(target, name, value, target) : true;
                }
              });
              // Use the unbound Vue method so response callbacks retain this request's identity.
              return Promise.resolve(lookup.apply(context, args)).then(function (result) {
                if (request === sequence && accepted && page.dataImei && page.dataImei.deviceId === args[0]) page.isShowInputImei = false;
                return result;
              }, function (error) {
                if (request === sequence) popup.call(page);
                throw error;
              });
            };
            page.getSecurity(imei).catch(function () {});
          }
          try { install(); } catch (ignored) { window.__nfcWalletWarrantyCompat = false; }
        })();
        """;
}
