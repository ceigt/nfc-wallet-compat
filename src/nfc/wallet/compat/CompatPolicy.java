package nfc.wallet.compat;

/* JADX INFO: loaded from: classes.dex */
final class CompatPolicy {
    static final String WALLET = "com.finshell.wallet";

    CompatPolicy() {
    }

    static boolean isMissingOsenseHint(Throwable th, Class<?> cls) {
        String message;
        return (th instanceof NoSuchMethodError) && cls == Void.TYPE && (message = th.getMessage()) != null && message.contains("startBackgroundRunning(Landroid/content/Context;ILcom/oplus/osense/task/BgRunningCallback;)V") && message.contains("Lcom/oplus/osense/OsenseResEventClient;");
    }

    static boolean usePlatformEseCheck(boolean z, boolean z2, boolean z3, boolean z4) {
        return !z && z2 && z3 && z4;
    }
}
