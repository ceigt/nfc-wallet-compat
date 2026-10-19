package nfc.wallet.compat;

import java.util.Locale;

/* JADX INFO: loaded from: classes.dex */
final class SePolicy {
    static final String FINSHELL_CERT = "7f68e808c85fc6f97f63197aad345a904242145ef4499310f90f8d0653e855be";

    SePolicy() {
    }

    static boolean allowOriginalWallet(String str, String str2, int i, int i2, String str3, boolean z, Throwable th) {
        return "com.finshell.wallet".equals(str) && "eSE1".equals(str2) && i == i2 && i >= 10000 && z && FINSHELL_CERT.equals(str3) && (th instanceof SecurityException) && th.getMessage() != null && th.getMessage().toLowerCase(Locale.ROOT).contains("no apdu access allowed");
    }
}
