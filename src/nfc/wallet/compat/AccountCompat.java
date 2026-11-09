package nfc.wallet.compat;

import android.content.Context;
import android.view.Window;
import io.github.libxposed.api.XposedInterface;
import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Account-center compatibility for AOSP-based ROMs.
 *
 * com.oplus.account resolves com.oplus.os.OplusBuild while laying out the login
 * screen. That class only exists in the ColorOS framework, so on AOSP the lookup
 * throws NoClassDefFoundError and the login activity dies before it can render.
 *
 * The original call is always attempted first: on stock ColorOS nothing changes,
 * and only the failing call is neutralised with a safe default.
 */
final class AccountCompat {
    private static final String OWNER = "com.platform.usercenter.utils.AcBootGuideUtils";
    private static final AtomicBoolean loggedFallback = new AtomicBoolean();

    static void install(XposedInterface api, ClassLoader loader) {
        int hooked = hook(api, loader, "needHideNavigationBar", Context.class);
        hooked += hook(api, loader, "hideNavigationBar", Window.class);
        if (hooked == 0) {
            WalletHooks.log(api, "Account compat: hook points not found; layout may have changed");
        } else {
            WalletHooks.log(api, "Account compat hooks installed: " + hooked);
        }
    }

    private static int hook(XposedInterface api, ClassLoader loader, String name, Class<?> argument) {
        final Method target;
        try {
            target = Class.forName(OWNER, false, loader).getDeclaredMethod(name, argument);
        } catch (Throwable error) {
            WalletHooks.log(api, "Account compat: " + name + " unavailable: " + error);
            return 0;
        }
        final boolean isVoid = target.getReturnType() == void.class;
        api.hook(target).setId("account." + name).intercept(chain -> {
            try {
                return chain.proceed();
            } catch (NoClassDefFoundError missingOplusBuild) {
                if (loggedFallback.compareAndSet(false, true)) {
                    WalletHooks.log(api, "Account compat: com.oplus.os.OplusBuild absent, "
                            + "neutralised " + name);
                }
                return isVoid ? null : Boolean.FALSE;
            }
        });
        return 1;
    }
}
