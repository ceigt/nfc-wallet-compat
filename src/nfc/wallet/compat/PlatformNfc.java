package nfc.wallet.compat;

import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Context;
import android.nfc.NfcAdapter;

/* JADX INFO: loaded from: classes.dex */
final class PlatformNfc {
    PlatformNfc() {
    }

    static boolean walletIsDefault(Context context) throws ReflectiveOperationException {
        NfcAdapter defaultAdapter = NfcAdapter.getDefaultAdapter(context);
        if (defaultAdapter == null || !defaultAdapter.isEnabled()) {
            return false;
        }
        Class<?> cls = Class.forName("android.nfc.cardemulation.CardEmulation");
        return Boolean.TRUE.equals(cls.getMethod("isDefaultServiceForCategory", ComponentName.class, String.class).invoke(cls.getMethod("getInstance", NfcAdapter.class).invoke(null, defaultAdapter), new ComponentName("com.finshell.wallet", "com.nearme.wallet.nfc.CardService"), "payment"));
    }

    static boolean useEseFallback(Context context) throws ReflectiveOperationException {
        boolean z = false;
        String str = (String) Class.forName("android.provider.Settings$Global").getMethod("getString", ContentResolver.class, String.class).invoke(null, context.getContentResolver(), "nfc_multise_active");
        NfcAdapter defaultAdapter = NfcAdapter.getDefaultAdapter(context);
        boolean z2 = (str == null || str.isEmpty()) ? false : true;
        boolean zHasSystemFeature = context.getPackageManager().hasSystemFeature("android.hardware.nfc.ese");
        if (defaultAdapter != null && defaultAdapter.isEnabled()) {
            z = true;
        }
        return CompatPolicy.usePlatformEseCheck(z2, zHasSystemFeature, z, walletIsDefault(context));
    }
}
