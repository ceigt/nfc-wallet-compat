package nfc.wallet.compat;

import android.util.Log;
import io.github.libxposed.api.XposedModule;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

public final class WalletModule extends XposedModule {
    /** Single source of truth for the hooked processes. */
    private static final List<String> SCOPE = List.of(
            CompatPolicy.WALLET, "com.android.nfc", "com.android.se", "com.oplus.account");

    private final AtomicBoolean installed = new AtomicBoolean();

    @FunctionalInterface
    private interface Installer { void install() throws Throwable; }

    @Override public void onModuleLoaded(ModuleLoadedParam param) {
        log(Log.INFO, "NfcWalletCompat", "0.6.2 loaded; API=" + getApiVersion()
                + "; process=" + param.getProcessName());
    }

    @Override public void onPackageReady(PackageReadyParam param) {
        String pkg = param.getPackageName();
        if (!param.isFirstPackage() || !SCOPE.contains(pkg)) return;
        if (getApiVersion() < 102 || !installed.compareAndSet(false, true)) return;

        ClassLoader loader = param.getClassLoader();
        Installer installer = switch (pkg) {
            case CompatPolicy.WALLET -> () -> {
                WalletHooks.install(this, loader);
                WarrantyCompat.install(this, loader);
            };
            case "com.android.nfc" -> () -> NfcRouteCompat.install(this, loader);
            case "com.android.se" -> () -> SeAccessCompat.install(this, loader);
            case "com.oplus.account" -> () -> AccountCompat.install(this, loader);
            default -> null;
        };
        if (installer == null) return;
        try {
            installer.install();
        } catch (Throwable error) {
            log(Log.ERROR, "NfcWalletCompat", "Adapter inactive: " + pkg, error);
        }
    }
}
