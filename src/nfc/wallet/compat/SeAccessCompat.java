package nfc.wallet.compat;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.os.Binder;
import io.github.libxposed.api.XposedInterface;
import java.lang.reflect.Method;
import java.security.MessageDigest;
import java.util.concurrent.atomic.AtomicBoolean;

final class SeAccessCompat {
    private static final AtomicBoolean loggedAccess = new AtomicBoolean();

    static void install(XposedInterface api, ClassLoader loader) throws Exception {
        Class<?> terminal = Class.forName("com.android.se.Terminal", false, loader);
        Method method = terminal.getDeclaredMethod("setUpChannelAccess", byte[].class,
                String.class, byte[].class, int.class, boolean.class);
        api.hook(method).setId("se.wallet-access").intercept(chain -> access(api, loader, chain));
        WalletHooks.log(api, "Secure Element hook installed; original Wallet only, eSE1 only");
    }

    static Object access(XposedInterface api, ClassLoader loader, XposedInterface.Chain chain) throws Throwable {
        Throwable original;
        try { return chain.proceed(); }
        catch (Throwable error) { original = error; }
        if (!CompatPolicy.WALLET.equals(chain.getArg(1))) throw original;
        try {
            Context context = (Context) Reflect.get(chain.getThisObject(), "mContext", Context.class);
            String terminal = (String) Reflect.call(chain.getThisObject(), "getName", String.class);
            PackageInfo info = context.getPackageManager().getPackageInfo(CompatPolicy.WALLET, 64);
            if (info.signatures != null && info.signatures.length == 1 && info.applicationInfo != null) {
                byte[] digest = MessageDigest.getInstance("SHA-256").digest(info.signatures[0].toByteArray());
                StringBuilder hex = new StringBuilder(64);
                for (byte b : digest) {
                    hex.append(Character.forDigit((b >>> 4) & 15, 16));
                    hex.append(Character.forDigit(b & 15, 16));
                }
                if (SePolicy.allowOriginalWallet((String) chain.getArg(1), terminal, Binder.getCallingUid(),
                        info.applicationInfo.uid, hex.toString(), chain.getArg(2) == null, original)) {
                    Class<?> access = Class.forName("com.android.se.security.ChannelAccess", false, loader);
                    Method privilege = access.getDeclaredMethod("getPrivilegeAccess", String.class, int.class);
                    Object result = Reflect.invoke(privilege, null, CompatPolicy.WALLET, chain.getArg(3));
                    if (loggedAccess.compareAndSet(false, true)) WalletHooks.log(api,
                            "eSE access restored for verified FinShell Wallet caller");
                    return result;
                }
            }
        } catch (Throwable error) {
            WalletHooks.log(api, "eSE compatibility unavailable: " + error.getClass().getSimpleName());
        }
        throw original;
    }
}
