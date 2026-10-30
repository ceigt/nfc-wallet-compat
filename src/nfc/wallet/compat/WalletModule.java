package nfc.wallet.compat;

import android.util.Log;
import io.github.libxposed.api.XposedModule;
import java.util.concurrent.atomic.AtomicBoolean;

public final class WalletModule extends XposedModule {
    private final AtomicBoolean installed = new AtomicBoolean();

    @Override public void onModuleLoaded(ModuleLoadedParam param) {
        log(Log.INFO, "NfcWalletCompat", "0.6.1 loaded; API=" + getApiVersion()
                + "; process=" + param.getProcessName());
    }

    @Override public void onPackageReady(PackageReadyParam param) {
        String pkg = param.getPackageName();
        if (!param.isFirstPackage() || (!CompatPolicy.WALLET.equals(pkg)
                && !"com.android.nfc".equals(pkg) && !"com.android.se".equals(pkg))) return;
        if (getApiVersion() < 102 || !installed.compareAndSet(false, true)) return;
        try {
            if ("com.android.nfc".equals(pkg)) NfcRouteCompat.install(this, param.getClassLoader());
            else if ("com.android.se".equals(pkg)) SeAccessCompat.install(this, param.getClassLoader());
            else {
                WalletHooks.install(this, param.getClassLoader());
                WarrantyCompat.install(this, param.getClassLoader());
            }
        } catch (Throwable error) {
            log(Log.ERROR, "NfcWalletCompat", "Adapter inactive: " + pkg, error);
        }
    }
}
