package nfc.wallet.compat;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.telephony.TelephonyManager;
import io.github.libxposed.api.XposedInterface;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import org.json.JSONObject;

/** Supplies real device identifiers only to the verified official warranty page. */
final class WarrantyCompat {
    private static final java.util.Set<String> diagnosed = java.util.concurrent.ConcurrentHashMap.newKeySet();
    private static final ThreadLocal<Repair> active = new ThreadLocal<>();
    private static final AtomicBoolean loggedFallback = new AtomicBoolean();
    private static final AtomicBoolean loggedRepair = new AtomicBoolean();
    private static final AtomicBoolean loggedStatistics = new AtomicBoolean();
    private static final AtomicBoolean loggedUnavailable = new AtomicBoolean();

    private static final class Repair {
        final JSONObject original;
        final JSONObject patched;
        Repair(JSONObject original, JSONObject patched) {
            this.original = original;
            this.patched = patched;
        }
    }

    static void install(XposedInterface api, ClassLoader loader) {
        try {
            Method debug = android.webkit.WebView.class.getDeclaredMethod("setWebContentsDebuggingEnabled", boolean.class);
            api.hook(debug).setId("wallet.temporary-web-debug").intercept(chain -> chain.proceed(new Object[]{true}));
            Method finished = Class.forName("com.finshell.webview.web.WalletWebViewClient", false, loader)
                    .getDeclaredMethod("onPageFinished", android.webkit.WebView.class, String.class);
            api.hook(finished).setId("wallet.temporary-page-log").intercept(chain -> {
                Object result = chain.proceed();
                String url = (String) chain.getArg(1);
                if (url != null && url.startsWith("https://rinsurance.finzfin.com/insurance/screenSecurity/")) {
                    boolean parsed;
                    try { new java.net.URI(url); parsed = true; } catch (Exception invalid) { parsed = false; }
                    WalletHooks.log(api, "Warranty lifecycle diagnostic: allowed=" + WarrantyPolicy.allowed(url, "getDeviceInfo")
                            + "; strictUrl=" + parsed + "; fragment=" + url.contains("#"));
                }
                return result;
            });
        } catch (Throwable error) { WalletHooks.log(api, "Warranty temporary diagnostics setup unavailable: " + error.getClass().getSimpleName()); }
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            try {
                android.webkit.WebView.setWebContentsDebuggingEnabled(true);
                WalletHooks.log(api, "Warranty temporary web diagnostics enabled");
            } catch (Throwable error) { WalletHooks.log(api, "Warranty web diagnostics unavailable: " + error.getClass().getSimpleName()); }
        }, 1200);

        List<XposedInterface.HookHandle> hooks = new ArrayList<>();
        try {
            Class<?> callback = Class.forName("com.finshell.jsbridge.JsCallback", false, loader);
            Method statistics = callback.getDeclaredMethod("postResult", String.class, String.class,
                    String.class, JSONObject.class, long.class);
            hooks.add(api.hook(statistics).setId("wallet.warranty-statistics").intercept(chain -> {
                Repair repair = active.get();
                if (repair == null || chain.getArg(3) != repair.patched) return chain.proceed();
                Object[] args = chain.getArgs().toArray();
                // Preserve the original privacy-redacted response in vendor statistics.
                args[3] = repair.original;
                if (loggedStatistics.compareAndSet(false, true)) WalletHooks.log(api,
                        "Warranty statistics retained the original redacted identifiers");
                return chain.proceed(args);
            }));
            Method method = callback.getDeclaredMethod("doJSCallback", boolean.class, int.class,
                    JSONObject.class, String.class, boolean.class);
            // Keep the statistics call visible even if the app was previously AOT compiled.
            if (!api.deoptimize(method)) throw new IllegalStateException("Warranty statistics caller deoptimization failed");
            for (Method caller : callback.getDeclaredMethods()) {
                String name = caller.getName();
                if ("call".equals(name) || "callWithErrorCode".equals(name) || "invokeJsCallback".equals(name))
                    if (!api.deoptimize(caller)) throw new IllegalStateException("Warranty bridge caller deoptimization failed");
            }
            // Reflective bridge entries and the cached-result path can inline the callback chain.
            for (String entry : new String[]{"com.finshell.webview.jsmethod.JSCommondMethod#getDeviceInfo",
                    "com.finshell.webview.jsmethod.JSInterfaceMethod#getBaseDeviceInfo",
                    "com.finshell.jspai.JSCommondMethod#getHeader"}) {
                String[] parts = entry.split("#");
                Method caller = Class.forName(parts[0], false, loader).getDeclaredMethod(parts[1], JSONObject.class, callback);
                if (!api.deoptimize(caller)) throw new IllegalStateException("Warranty bridge entry deoptimization failed");
            }
            Class<?> dispatcher = Class.forName("com.finshell.jsbridge.JsCallJava", false, loader);
            for (Method caller : dispatcher.getDeclaredMethods()) {
                if ("callInner".equals(caller.getName()) || "invokeNativeMethod".equals(caller.getName()))
                    if (!api.deoptimize(caller)) throw new IllegalStateException("Warranty cached caller deoptimization failed");
            }
            hooks.add(api.hook(method).setId("wallet.warranty-bridge").intercept(chain -> {
                diagnose(api, chain);
                Repair repair = null;
                try { repair = prepare(loader, chain); }
                catch (Throwable failure) {
                    if (loggedUnavailable.compareAndSet(false, true)) WalletHooks.log(api,
                            "Warranty IMEI unavailable; check device-identifier access: " + failure.getClass().getSimpleName());
                }
                if (repair == null) return chain.proceed();
                Repair previous = active.get();
                active.set(repair);
                Object[] args = chain.getArgs().toArray();
                args[2] = repair.patched;
                try {
                    if (loggedRepair.compareAndSet(false, true)) WalletHooks.log(api,
                            "Verified warranty page received real device IMEI; identifiers are not logged");
                    Object result = chain.proceed(args);
                    try { schedule(api, loader, chain.getThisObject(), repair.patched.optString("imei")); }
                    catch (Throwable ignored) { }
                    return result;
                } finally {
                    if (previous == null) active.remove();
                    else active.set(previous);
                }
            }));
            WalletHooks.log(api, "Official warranty IMEI adapter installed; URL, Wallet signer and version guarded");
        } catch (Throwable failure) {
            for (XposedInterface.HookHandle hook : hooks) hook.unhook();
            WalletHooks.log(api, "Warranty bridge interface unavailable: " + failure.getClass().getSimpleName());
        }
    }

    private static void diagnose(XposedInterface api, XposedInterface.Chain chain) {
        try {
            Object cb = chain.getThisObject();
            String name = (String) Reflect.get(cb, "mMethodName", String.class);
            if (!"getDeviceInfo".equals(name) && !"getBaseDeviceInfo".equals(name) && !"getHeader".equals(name)) return;
            String url = (String) Reflect.call(cb, "getUrl", String.class);
            if (url != null && url.startsWith("https://rinsurance.finzfin.com/insurance/screenSecurity/")) {
                boolean parsed;
                try { new java.net.URI(url); parsed = true; } catch (Exception invalid) { parsed = false; }
                if (!parsed) WalletHooks.log(api, "Warranty callback diagnostic: official page URL rejected by strict parser");
            }
            java.net.URI uri = new java.net.URI(url);
            String path = uri.getRawPath();
            if (path == null || !path.matches("/insurance/screenSecurity/[A-Za-z/]{1,60}")) path = "other";
            String key = name + path;
            if (!diagnosed.add(key)) return;
            long started = (long) Reflect.get(cb, "apiStartTime", long.class);
            WalletHooks.log(api, "Warranty callback diagnostic: method=" + name + "; page=" + path
                    + "; allowed=" + WarrantyPolicy.allowed(url, name) + "; success=" + chain.getArg(0)
                    + "; fresh=" + (System.currentTimeMillis() - started < 59000)
                    + "; hasImei=" + (chain.getArg(2) instanceof JSONObject && ((JSONObject)chain.getArg(2)).has("imei")));
        } catch (Throwable ignored) { }
    }

    private static Repair prepare(ClassLoader loader, XposedInterface.Chain chain) throws Throwable {
        if (!Boolean.TRUE.equals(chain.getArg(0)) || !(chain.getArg(2) instanceof JSONObject)) return null;
        Object callback = chain.getThisObject();
        String method = (String) Reflect.get(callback, "mMethodName", String.class);
        String url = (String) Reflect.call(callback, "getUrl", String.class);
        if (!WarrantyPolicy.allowed(url, method)
                || !NfcPolicy.supportedDevice(android.os.Build.MODEL, android.os.Build.VERSION.SDK_INT)) return null;
        Class<?> util = Class.forName("com.finshell.common.util.d", false, loader);
        Context context = (Context) Reflect.invoke(util.getDeclaredMethod("c"), null);
        if (context == null || !WalletIdentity.matches(context, android.os.Process.myUid())
                || context.getPackageManager().getPackageInfo(CompatPolicy.WALLET, 0).versionCode != 551053) return null;
        JSONObject original = (JSONObject) chain.getArg(2);
        if (!original.has("imei") && !"getHeader".equals(method)) return null;
        long started = (long) Reflect.get(callback, "apiStartTime", long.class);
        if (System.currentTimeMillis() - started > 59000) return null;
        TelephonyManager tm = context.getSystemService(TelephonyManager.class);
        if (tm == null) return null;
        String imei = tm.getImei(0);
        if (!WarrantyPolicy.validImei(imei)) return null;
        JSONObject patched = new JSONObject(original.toString());
        patched.put("imei", imei);
        if ("getHeader".equals(method)) patched.put("id", imei);
        return new Repair(original, patched);
    }

    private static void schedule(XposedInterface api, ClassLoader loader, Object callback, String imei) throws Throwable {
        Object manager = Reflect.get(callback, "fragmentWebManager",
                Class.forName("com.finshell.jsbridge.FragmentWebManager", false, loader));
        Method getter = manager.getClass().getMethod("getCallJsCallback");
        Object sink = Reflect.invoke(getter, manager);
        if (sink == null) return;
        Method send = sink.getClass().getMethod("onCallJsCallback", String.class);
        String script = "javascript:" + WarrantyScript.render(imei);
        new Handler(Looper.getMainLooper()).post(() -> {
            try {
                Reflect.invoke(send, sink, script);
                if (loggedFallback.compareAndSet(false, true)) WalletHooks.log(api,
                        "Official warranty explicit-device lookup scheduled; identifiers are not logged");
            } catch (Throwable ignored) { }
        });
    }
}
