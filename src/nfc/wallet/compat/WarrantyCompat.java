package nfc.wallet.compat;

import android.content.Context;
import android.telephony.TelephonyManager;
import io.github.libxposed.api.XposedInterface;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import org.json.JSONObject;

/** Supplies real device identifiers only to the verified official warranty page. */
final class WarrantyCompat {
    private static final ThreadLocal<Repair> active = new ThreadLocal<>();
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
            if (!api.deoptimize(method)) throw new IllegalStateException("Warranty caller deoptimization failed");
            hooks.add(api.hook(method).setId("wallet.warranty-bridge").intercept(chain -> {
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
                    return chain.proceed(args);
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
        if (!original.has("imei")) return null;
        TelephonyManager tm = context.getSystemService(TelephonyManager.class);
        if (tm == null) return null;
        String imei = tm.getImei(0);
        if (!WarrantyPolicy.validImei(imei)) return null;
        JSONObject patched = new JSONObject(original.toString());
        patched.put("imei", imei);
        return new Repair(original, patched);
    }
}
