package nfc.wallet.compat;

import android.content.Context;
import android.util.Log;
import io.github.libxposed.api.XposedInterface;
import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicBoolean;

final class WalletHooks {
    private static final AtomicBoolean loggedPower = new AtomicBoolean();
    private static final AtomicBoolean loggedFallback = new AtomicBoolean();
    private static final AtomicBoolean loggedUi = new AtomicBoolean();

    static void install(XposedInterface api, ClassLoader loader) {
        try {
            Class<?> owner = Class.forName("com.finshell.freeze.sysquota.LongTimeUnfreezeManager", false, loader);
            int count = 0;
            for (Method method : owner.getDeclaredMethods()) {
                if (!"doCheckLongUnfreeze".equals(method.getName())) continue;
                api.hook(method).setId("wallet.osense").intercept(chain -> powerHint(api, chain));
                count++;
            }
            log(api, "Wallet power hooks installed: " + count);
        } catch (Exception | LinkageError error) {
            log(api, "Wallet power interface unavailable: " + error);
        }
        try {
            Method method = Class.forName("iv.i", false, loader).getDeclaredMethod("N", Context.class);
            if (method.getReturnType() != boolean.class) throw new NoSuchMethodException("N return type");
            api.hook(method).setId("wallet.nfc-ui").intercept(chain -> nfcCapability(api, chain));
            log(api, "Wallet NFC capability hook installed");
        } catch (Exception | LinkageError error) {
            log(api, "Wallet UI interface unavailable: " + error);
        }
    }

    static Object powerHint(XposedInterface api, XposedInterface.Chain chain) throws Throwable {
        try { return chain.proceed(); }
        catch (Throwable original) {
            if (!CompatPolicy.isMissingOsenseHint(original, ((Method) chain.getExecutable()).getReturnType())) {
                throw original;
            }
            if (loggedPower.compareAndSet(false, true)) log(api, "Ignored missing Osense background hint");
            return null;
        }
    }

    static Object nfcCapability(XposedInterface api, XposedInterface.Chain chain) throws Throwable {
        Object original = chain.proceed();
        if (!Boolean.FALSE.equals(original)) return original;
        Context context = (Context) chain.getArg(0);
        if (context == null || !CompatPolicy.WALLET.equals(context.getPackageName())) return original;
        try {
            if (context.getPackageManager().getPackageInfo(CompatPolicy.WALLET, 0).versionCode != 551053) {
                if (loggedUi.compareAndSet(false, true)) log(api, "Wallet UI profile needs review");
                return original;
            }
            if (WalletIdentity.matches(context, android.os.Process.myUid()) && PlatformNfc.useEseFallback(context)) {
                if (loggedFallback.compareAndSet(false, true)) log(api, "Using Android NFC default and eSE capability; vendor selection absent");
                return true;
            }
        } catch (Exception | LinkageError error) {
            log(api, "NFC fallback unavailable: " + error.getClass().getSimpleName());
        }
        return original;
    }

    static void log(XposedInterface api, String message) {
        api.log(Log.INFO, "NfcWalletCompat", message);
    }
}
