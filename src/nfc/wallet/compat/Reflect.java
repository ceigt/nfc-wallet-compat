package nfc.wallet.compat;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/** Reflection is limited to the target's checked interfaces, never Xposed internals. */
final class Reflect {
    static Object get(Object owner, String name, Class<?> type) throws Exception {
        Field field = NfcProfile.field(owner.getClass(), name, type);
        field.setAccessible(true);
        return field.get(owner);
    }

    static void set(Object owner, String name, Class<?> type, Object value) throws Exception {
        Field field = NfcProfile.field(owner.getClass(), name, type);
        field.setAccessible(true);
        field.set(owner, value);
    }

    static Object call(Object owner, String name, Class<?> result, Class<?>[] types,
                       Object... args) throws Throwable {
        return invoke(NfcProfile.method(owner.getClass(), name, result, types), owner, args);
    }

    static Object call(Object owner, String name, Class<?> result) throws Throwable {
        return call(owner, name, result, new Class<?>[0]);
    }

    static Object invoke(Method method, Object owner, Object... args) throws Throwable {
        method.setAccessible(true);
        try { return method.invoke(owner, args); }
        catch (InvocationTargetException error) { throw error.getCause(); }
    }
}
