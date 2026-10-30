package nfc.wallet.compat;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.telephony.TelephonyManager;
import io.github.libxposed.api.XposedInterface;
import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicBoolean;

/** Uses the official page's own lookup with the real device IMEI. */
final class WarrantyCompat {
    private static final AtomicBoolean logged = new AtomicBoolean();
    private static final AtomicBoolean unavailable = new AtomicBoolean();

    static void install(XposedInterface api, ClassLoader loader) {
        try {
            Class<?> callback = Class.forName("com.finshell.jsbridge.JsCallback", false, loader);
            Method method = callback.getDeclaredMethod("doJSCallback", boolean.class, int.class,
                    org.json.JSONObject.class, String.class, boolean.class);
            api.hook(method).setId("wallet.warranty-lookup").intercept(chain -> {
                Object result = chain.proceed();
                try {
                    if (Boolean.TRUE.equals(chain.getArg(0))) schedule(api, loader, chain.getThisObject());
                } catch (Throwable error) {
                    if (unavailable.compareAndSet(false, true)) WalletHooks.log(api,
                            "Warranty lookup unavailable: " + error.getClass().getSimpleName());
                }
                return result;
            });
            WalletHooks.log(api, "Official warranty lookup adapter installed; page, signer and version guarded");
        } catch (Throwable error) {
            WalletHooks.log(api, "Warranty bridge unavailable: " + error.getClass().getSimpleName());
        }
    }

    private static void schedule(XposedInterface api, ClassLoader loader, Object callback) throws Throwable {
        String method = (String) Reflect.get(callback, "mMethodName", String.class);
        String url = (String) Reflect.call(callback, "getUrl", String.class);
        if (!WarrantyPolicy.allowed(url, method)
                || !NfcPolicy.supportedDevice(android.os.Build.MODEL, android.os.Build.VERSION.SDK_INT)) return;
        Class<?> util = Class.forName("com.finshell.common.util.d", false, loader);
        Context context = (Context) Reflect.invoke(util.getDeclaredMethod("c"), null);
        if (context == null || !WalletIdentity.matches(context, android.os.Process.myUid())
                || context.getPackageManager().getPackageInfo(CompatPolicy.WALLET, 0).versionCode != 551053) return;
        TelephonyManager tm = context.getSystemService(TelephonyManager.class);
        String imei = tm == null ? null : tm.getImei(0);
        if (!WarrantyPolicy.validImei(imei)) return;
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
                if (logged.compareAndSet(false, true)) WalletHooks.log(api,
                        "Official warranty explicit-device lookup scheduled; identifiers are not logged");
            } catch (Throwable ignored) { }
        });
    }
}
