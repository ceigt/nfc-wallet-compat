package nfc.wallet.compat;

import android.content.Context;
import android.content.pm.PackageInfo;
import java.security.MessageDigest;

final class WalletIdentity {
    static boolean matches(Context context, int callerUid) throws Exception {
        PackageInfo wallet = context.getPackageManager().getPackageInfo(CompatPolicy.WALLET, 64);
        if (wallet.applicationInfo == null || wallet.applicationInfo.uid < 10000
                || (callerUid >= 0 && callerUid != wallet.applicationInfo.uid)
                || wallet.signatures == null || wallet.signatures.length != 1) return false;
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(wallet.signatures[0].toByteArray());
        StringBuilder hex = new StringBuilder(64);
        for (byte value : digest) {
            hex.append(Character.forDigit((value >>> 4) & 15, 16));
            hex.append(Character.forDigit(value & 15, 16));
        }
        return SePolicy.FINSHELL_CERT.equals(hex.toString());
    }
}
