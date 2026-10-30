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
              location.pathname !== '/insurance/screenSecurity/electronicCard/' ||
              window.__nfcWalletWarrantyCompat) return;
          window.__nfcWalletWarrantyCompat = true;
          var imei = '__DEVICE_IMEI__', tries = 0, href = location.href;
          function install() {
            if (location.href !== href) return;
            var pending = window.$nuxt ? [window.$nuxt] : [], seen = new Set(), page;
            while (pending.length && seen.size < 1000) {
              var node = pending.pop();
              if (!node || seen.has(node)) continue;
              seen.add(node);
              if (typeof node.getSecurity === 'function' && typeof node.popupImei === 'function' &&
                  typeof node.dataIMEI === 'function' && typeof node.isShowInputImei === 'boolean' &&
                  node.dataImei && typeof node.dataImei === 'object') { page = node; break; }
              if (Array.isArray(node.$children)) pending.push.apply(pending, node.$children);
            }
            if (!page) {
              if (++tries < 50) setTimeout(install, 100);
              else window.__nfcWalletWarrantyCompat = false;
              return;
            }
            var lookup = page.getSecurity, popup = page.popupImei, initialLookupPending = true;
            page.getSecurity = function () {
              initialLookupPending = false;
              var args = Array.prototype.slice.call(arguments);
              if (!args[0]) args[0] = imei;
              return lookup.apply(this, args);
            };
            page.popupImei = function () {
              if (!initialLookupPending) return popup.apply(this, arguments);
              initialLookupPending = false;
              var self = this;
              Promise.resolve().then(function () { return lookup.call(self, imei); }).then(function () {
                if (self.dataImei && self.dataImei.deviceId === imei) self.isShowInputImei = false;
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
