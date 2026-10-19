package nfc.wallet.compat;

import android.content.ComponentName;
import android.content.Context;
import android.os.Build;
import io.github.libxposed.api.XposedInterface;
import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

/** Interface-checked adaptation for the Ace 3's known eSE routing layout. */
final class NfcRouteCompat {
    private static final String SERVICE = "com.nearme.wallet.nfc.CardService";
    private static final AtomicBoolean loggedBinding = new AtomicBoolean();
    private static final Set<String> loggedFailures = Collections.synchronizedSet(new HashSet<String>());

    private static XposedInterface api;

    static void install(XposedInterface module, ClassLoader loader) {
        api = module;
        List<XposedInterface.HookHandle> registered = new ArrayList<>();
        try {
            if (!NfcPolicy.supportedDevice(Build.MODEL, Build.VERSION.SDK_INT)) {
                log("NFC device profile unsupported; no route hooks installed");
                return;
            }
            Class<?>[] profile = NfcProfile.check(loader);
            Class<?> routing = profile[0];
            Class<?> manager = profile[1];

            for (Constructor<?> constructor : routing.getDeclaredConstructors()) {
                registered.add(api.hook(constructor).setId("nfc.ese-map").intercept(chain -> {
                    Object result = chain.proceed();
                    try {
                        if (ensureMapping(chain.getThisObject())) log("NFC eSE1 mapping checked: C0");
                        else failure("NFC eSE layout or route ownership unsupported; left unchanged");
                    } catch (Throwable error) { failure("NFC mapping unavailable: " + error); }
                    return result;
                }));
            }
            registered.add(api.hook(NfcProfile.method(manager, "onServicesUpdated", void.class,
                    int.class, List.class, boolean.class)).setId("nfc.wallet-service").intercept(chain -> {
                try {
                    Object option = Reflect.get(chain.getThisObject(), "mRoutingOptionManager", routing);
                    if (ensureMapping(option)) {
                        Context context = (Context) Reflect.get(chain.getThisObject(), "mContext", Context.class);
                        if (chain.getArg(1) instanceof List) {
                            for (Object service : (List<?>) chain.getArg(1)) {
                                try { patchWalletService(context, service, loader); }
                                catch (Throwable error) { failure("NFC service skipped: " + error
                                        + "; rollbackErrors=" + error.getSuppressed().length); }
                            }
                        }
                    } else failure("NFC route check failed; wallet AIDs left unchanged");
                } catch (Throwable error) { failure("NFC service adapter unavailable: " + error); }
                // Keep the original call outside adapter recovery so it runs exactly once.
                return chain.proceed();
            }));
            log("NFC interface checks passed; API 102 adapter installed");
        } catch (Throwable error) {
            for (XposedInterface.HookHandle hook : registered) hook.unhook();
            failure("Unsupported NFC interface; adapter inactive: " + error);
        }
    }

    @SuppressWarnings("unchecked")
    private static boolean ensureMapping(Object routing) throws Exception {
        byte[] ese = (byte[]) Reflect.get(routing, "mOffHostRouteEse", byte[].class);
        byte[] uicc = (byte[]) Reflect.get(routing, "mOffHostRouteUicc", byte[].class);
        Map<String, Integer> forward = (Map<String, Integer>) Reflect.get(routing, "mRouteForSecureElement", Map.class);
        Map<Integer, String> reverse = (Map<Integer, String>) Reflect.get(routing, "mSecureElementForRoute", Map.class);
        return NfcPolicy.correct(ese, uicc, forward, reverse);
    }

    @SuppressWarnings("unchecked")
    private static void patchWalletService(Context context, Object service, ClassLoader loader) throws Throwable {
        if (service == null) return;
        ComponentName component = (ComponentName) Reflect.call(service, "getComponent", ComponentName.class);
        if (component == null || !CompatPolicy.WALLET.equals(component.getPackageName())
                || !SERVICE.equals(component.getClassName())) return;
        if (!WalletIdentity.matches(context, (Integer) Reflect.call(service, "getUid", int.class))) {
            failure("Wallet signer/UID mismatch; transit adapter inactive");
            return;
        }
        if (Boolean.TRUE.equals(Reflect.call(service, "isOnHost", boolean.class))) return;
        String selected = (String) Reflect.call(service, "getOffHostSecureElement", String.class);
        if (selected != null && !"eSE1".equals(selected)) return;

        List<String> existing = new ArrayList<>();
        for (Object aidGroup : (List<?>) Reflect.call(service, "getAidGroups", List.class)) {
            if ("payment".equals(Reflect.call(aidGroup, "getCategory", String.class))) {
                existing.addAll((List<String>) Reflect.call(aidGroup, "getAids", List.class));
            }
        }
        Map<?, ?> staticGroups = (Map<?, ?>) Reflect.get(service, "mStaticAidGroups", Map.class);
        List<String> declared = new ArrayList<>();
        for (Object aidGroup : staticGroups.values()) {
            if ("payment".equals(Reflect.call(aidGroup, "getCategory", String.class))) {
                declared.addAll((List<String>) Reflect.call(aidGroup, "getAids", List.class));
            }
        }
        List<String> desired = NfcPolicy.mergedTransitAids(existing, declared);
        if (desired == null) {
            failure("Wallet transit profile unknown or AID limit exceeded; left unchanged");
            return;
        }
        Class<?> groupType = Class.forName("android.nfc.cardemulation.AidGroup", false, loader);
        Object dynamic = Reflect.call(service, "getDynamicAidGroupForCategory", groupType,
                new Class<?>[]{String.class}, "payment");
        List<String> dynamicAids = dynamic == null ? null
                : (List<String>) Reflect.call(dynamic, "getAids", List.class);
        boolean updateGroup = NfcPolicy.needsAidUpdate(dynamicAids, desired);
        Object replacement = null;
        if (updateGroup) {
            replacement = groupType.getConstructor(List.class, String.class).newInstance(desired, "payment");
        }
        BindingTransaction.apply(new BindingTransaction.Editor() {
            public void writeGroup(Object group) throws Throwable { Reflect.call(service, "setDynamicAidGroup", void.class, new Class<?>[]{groupType}, group); }
            public void removeGroup() throws Throwable { Reflect.call(service, "removeDynamicAidGroupForCategory", boolean.class, new Class<?>[]{String.class}, "payment"); }
            public void bindEse() throws Throwable { Reflect.call(service, "setOffHostSecureElement", void.class, new Class<?>[]{String.class}, "eSE1"); }
            public void restoreElement(String element) throws Throwable { Reflect.set(service, "mOffHostName", String.class, element); }
        }, dynamic, selected, replacement, updateGroup, selected == null);
        if (loggedBinding.compareAndSet(false, true)) log("Verified Wallet transit profile bound to eSE1");
    }

    private static void log(String message) { WalletHooks.log(api, message); }
    private static void failure(String message) {
        int separator = message.indexOf(':');
        String category = separator < 0 ? message : message.substring(0, separator);
        if (loggedFailures.add(category)) log(message);
    }
}
