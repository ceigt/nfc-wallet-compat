package nfc.wallet.compat;

import android.content.ComponentName;
import android.content.Context;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;

/** Also callable by a read-only device probe, without loading Xposed or NFC services. */
final class NfcProfile {
    static Class<?>[] check(ClassLoader loader) throws Exception {
        Class<?> routing = Class.forName("com.android.nfc.cardemulation.RoutingOptionManager", false, loader);
        Class<?> manager = Class.forName("com.android.nfc.cardemulation.CardEmulationManager", false, loader);
        Class<?> service = Class.forName("android.nfc.cardemulation.ApduServiceInfo", false, loader);
        Class<?> group = Class.forName("android.nfc.cardemulation.AidGroup", false, loader);
        field(routing, "mOffHostRouteEse", byte[].class);
        field(routing, "mOffHostRouteUicc", byte[].class);
        field(routing, "mRouteForSecureElement", Map.class);
        field(routing, "mSecureElementForRoute", Map.class);
        field(manager, "mContext", Context.class);
        field(manager, "mRoutingOptionManager", routing);
        method(manager, "onServicesUpdated", void.class, int.class, List.class, boolean.class);
        method(service, "getComponent", ComponentName.class);
        method(service, "getUid", int.class);
        method(service, "isOnHost", boolean.class);
        method(service, "getOffHostSecureElement", String.class);
        method(service, "setOffHostSecureElement", void.class, String.class);
        method(service, "getAidGroups", List.class);
        field(service, "mStaticAidGroups", Map.class);
        field(service, "mOffHostName", String.class);
        method(service, "getDynamicAidGroupForCategory", group, String.class);
        method(service, "setDynamicAidGroup", void.class, group);
        method(service, "removeDynamicAidGroupForCategory", boolean.class, String.class);
        method(group, "getCategory", String.class);
        method(group, "getAids", List.class);
        group.getConstructor(List.class, String.class);
        return new Class<?>[]{routing, manager};
    }

    static Field field(Class<?> owner, String name, Class<?> type) throws Exception {
        for (Class<?> current = owner; current != null; current = current.getSuperclass()) {
            try {
                Field field = current.getDeclaredField(name);
                if (!type.isAssignableFrom(field.getType())) throw new IllegalStateException("Field type: " + name);
                return field;
            } catch (NoSuchFieldException missing) { /* Check framework base classes. */ }
        }
        throw new NoSuchFieldException(name);
    }

    static Method method(Class<?> owner, String name, Class<?> result, Class<?>... args) throws Exception {
        for (Class<?> current = owner; current != null; current = current.getSuperclass()) {
            try {
                Method method = current.getDeclaredMethod(name, args);
                if (!result.isAssignableFrom(method.getReturnType())) throw new IllegalStateException("Method type: " + name);
                return method;
            } catch (NoSuchMethodException missing) { /* Check framework base classes. */ }
        }
        throw new NoSuchMethodException(name);
    }
}
