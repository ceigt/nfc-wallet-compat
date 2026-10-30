package nfc.wallet.compat;

import android.content.Context;
import android.telephony.TelephonyManager;
import io.github.libxposed.api.XposedInterface;
import java.lang.reflect.Method;
import java.net.URI;
import java.util.HashSet;
import java.util.Set;
import org.json.JSONObject;

/** Narrow diagnostics for the warranty bridge, without logging device identifiers. */
final class WarrantyCompat {
    private static final Set<String> logged = new HashSet<>();

    static void install(XposedInterface api, ClassLoader loader) {
        try {
            Class<?> callback = Class.forName("com.finshell.jsbridge.JsCallback", false, loader);
            Method method = callback.getDeclaredMethod("doJSCallback", boolean.class, int.class,
                    JSONObject.class, String.class, boolean.class);
            api.hook(method).setId("wallet.warranty-bridge").intercept(chain -> {
                try { inspect(api, loader, chain); }
                catch (Throwable failure) {
                    WalletHooks.log(api, "Warranty bridge inspection unavailable: " + failure.getClass().getSimpleName());
                }
                return chain.proceed();
            });
            WalletHooks.log(api, "Warranty bridge diagnostics installed; identifiers are not logged");
        } catch (Exception | LinkageError failure) {
            WalletHooks.log(api, "Warranty bridge interface unavailable: " + failure.getClass().getSimpleName());
        }
    }

    private static void inspect(XposedInterface api, ClassLoader loader, XposedInterface.Chain chain) throws Throwable {
        Object callback = chain.getThisObject();
        String method = (String) Reflect.get(callback, "mMethodName", String.class);
        if (!"getDeviceInfo".equals(method) && !"getBaseDeviceInfo".equals(method) && !"getImei".equals(method)) return;
        Class<?> util = Class.forName("com.finshell.common.util.d", false, loader);
        Context context = (Context) Reflect.invoke(util.getDeclaredMethod("c"), null);
        if (context == null || !WalletIdentity.matches(context, android.os.Process.myUid())) return;
        URI url = new URI((String) Reflect.call(callback, "getUrl", String.class));
        String route = url.getFragment();
        if (route != null) route = route.split("\\?", 2)[0];
        String page = (url.getHost() + url.getPath() + (route == null ? "" : "#" + route))
                .replaceAll("[0-9]{10,}", "[redacted]");
        String key = method + " " + page;
        synchronized (logged) { if (logged.size() >= 16 || !logged.add(key)) return; }
        Object data = chain.getArg(2);
        JSONObject result = data instanceof JSONObject ? (JSONObject) data : null;
        boolean realImeiReadable = false;
        try {
            String imei = context.getSystemService(TelephonyManager.class).getImei(0);
            realImeiReadable = imei != null && imei.matches("[0-9]{15}") && !imei.matches("0+");
        } catch (RuntimeException ignored) { }
        WalletHooks.log(api, "Warranty bridge: " + key + "; success=" + chain.getArg(0)
                + "; hasImei=" + (result != null && result.has("imei"))
                + "; realImeiReadable=" + realImeiReadable);
    }
}
