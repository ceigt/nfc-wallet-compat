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
            if (location.href !== href) return;
            var pending = window.$nuxt ? [window.$nuxt] : [], seen = new Set(), page;
            while (pending.length && seen.size < 1000) {
              var node = pending.pop();
              if (!node || seen.has(node)) continue;
              seen.add(node);
              if (typeof node.getSecurity === 'function' && typeof node.popupImei === 'function' &&
                  typeof node.dataIMEI === 'function' && typeof node.applySecurityData === 'function' && typeof node.isShowInputImei === 'boolean' &&
                  node.dataImei && typeof node.dataImei === 'object') { page = node; break; }
              if (Array.isArray(node.$children)) pending.push.apply(pending, node.$children);
            }
            if (!page) {
              if (++tries < 50) setTimeout(install, 100);
              else window.__nfcWalletWarrantyCompat = false;
              return;
            }
            var lookup = page.getSecurity, popup = page.popupImei, apply = page.applySecurityData;
            var fallbackAvailable = true, manualOther = false, lastOwnResult = false;
            page.applySecurityData = function (data) {
              var id = data && data.mobileInfoRespVO && data.mobileInfoRespVO.deviceId;
              var unknown = !!(data && data.modelMatch && (!id || /^0+$/.test(String(id))));
              if (!manualOther && unknown) {
                if (!this.dataImei || this.dataImei.deviceId !== imei) this.popupImei();
                return Promise.resolve(false);
              }
              lastOwnResult = !!(data && data.modelMatch && id === imei);
              return apply.apply(this, arguments);
            };
            page.getSecurity = function () {
              var args = Array.prototype.slice.call(arguments);
              if (!args[0] || /^0+$/.test(String(args[0]))) args[0] = imei;
              manualOther = args[0] !== imei;
              if (manualOther) fallbackAvailable = false;
              refreshDeviceCache();
              var self = this, previousData = this.dataImei;
              return Promise.resolve(lookup.apply(this, args)).then(function (result) {
                if (args[0] === imei && lastOwnResult && self.dataImei !== previousData && self.dataImei && self.dataImei.deviceId === imei) self.isShowInputImei = false;
                return result;
              });
            };
            page.popupImei = function () {
              if (!fallbackAvailable || (this.dataImei && this.dataImei.deviceId === imei)) return popup.apply(this, arguments);
              fallbackAvailable = false;
              var self = this, previousData = this.dataImei;
              Promise.resolve().then(function () { refreshDeviceCache(); return lookup.call(self, imei); }).then(function () {
                if (lastOwnResult && self.dataImei !== previousData && self.dataImei && self.dataImei.deviceId === imei) self.isShowInputImei = false;
                else popup.call(self);
              }, function () { popup.call(self); });
            };
            if (page.isShowInputImei && (!page.dataImei.deviceId || /^0+$/.test(page.dataImei.deviceId)))
              page.popupImei();
          }
          try { install(); } catch (ignored) { window.__nfcWalletWarrantyCompat = false; }
        })();
        """;
}
