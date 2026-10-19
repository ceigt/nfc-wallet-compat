package nfc.wallet.compat;

import io.github.libxposed.api.XposedInterface;
import java.lang.reflect.Executable;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Arrays;
import java.util.List;

public final class HookFlowTest {
    private static void voidTarget() {}
    private static boolean booleanTarget(Object ignored) { return false; }
    private interface Invocation { Object run() throws Throwable; }

    static final class Chain implements XposedInterface.Chain {
        final Method method;
        final Object result;
        final Throwable error;
        final Object[] args;
        int calls;
        Chain(Method method, Object result, Throwable error, Object... args) {
            this.method = method; this.result = result; this.error = error; this.args = args;
        }
        public Executable getExecutable() { return method; }
        public Object getThisObject() { return null; }
        public List<Object> getArgs() { return Arrays.asList(args); }
        public Object getArg(int index) { return args[index]; }
        public Object proceed() throws Throwable {
            calls++;
            if (error != null) throw error;
            return result;
        }
        public Object proceed(Object[] values) { throw new AssertionError("Arguments must remain unchanged"); }
        public Object proceedWith(Object owner) { throw new AssertionError("Receiver must remain unchanged"); }
        public Object proceedWith(Object owner, Object[] values) { throw new AssertionError("Receiver must remain unchanged"); }
    }

    private static void expectSame(Throwable expected, Invocation call) {
        try { call.run(); throw new AssertionError("Exception swallowed"); }
        catch (Throwable actual) { assert actual == expected : actual; }
    }

    public static void main(String[] args) throws Throwable {
        XposedInterface api = (XposedInterface) Proxy.newProxyInstance(HookFlowTest.class.getClassLoader(),
                new Class<?>[]{XposedInterface.class}, (proxy, method, values) -> {
                    if ("log".equals(method.getName())) return null;
                    throw new AssertionError("Unexpected API call: " + method);
                });
        Method voidMethod = HookFlowTest.class.getDeclaredMethod("voidTarget");
        Method boolMethod = HookFlowTest.class.getDeclaredMethod("booleanTarget", Object.class);
        String missing = "Lcom/oplus/osense/OsenseResEventClient; startBackgroundRunning(Landroid/content/Context;ILcom/oplus/osense/task/BgRunningCallback;)V";
        Chain missingHint = new Chain(voidMethod, null, new NoSuchMethodError(missing));
        assert WalletHooks.powerHint(api, missingHint) == null && missingHint.calls == 1;
        Throwable unrelated = new NoSuchMethodError("other method");
        Chain wrongError = new Chain(voidMethod, null, unrelated);
        expectSame(unrelated, () -> WalletHooks.powerHint(api, wrongError));
        assert wrongError.calls == 1;
        Throwable sameTextWrongType = new IllegalStateException(missing);
        Chain wrongType = new Chain(voidMethod, null, sameTextWrongType);
        expectSame(sameTextWrongType, () -> WalletHooks.powerHint(api, wrongType));
        Throwable wrongReturnError = new NoSuchMethodError(missing);
        Chain wrongReturn = new Chain(boolMethod, null, wrongReturnError, (Object) null);
        expectSame(wrongReturnError, () -> WalletHooks.powerHint(api, wrongReturn));
        Object token = new Object();
        Chain normal = new Chain(voidMethod, token, null);
        assert WalletHooks.powerHint(api, normal) == token && normal.calls == 1;

        Chain supported = new Chain(boolMethod, true, null, (Object) null);
        assert Boolean.TRUE.equals(WalletHooks.nfcCapability(api, supported)) && supported.calls == 1;
        Chain noContext = new Chain(boolMethod, false, null, (Object) null);
        assert Boolean.FALSE.equals(WalletHooks.nfcCapability(api, noContext)) && noContext.calls == 1;
        Throwable uiError = new SecurityException("original UI failure");
        Chain failedUi = new Chain(boolMethod, null, uiError, (Object) null);
        expectSame(uiError, () -> WalletHooks.nfcCapability(api, failedUi));
        assert failedUi.calls == 1;

        Chain seNormal = new Chain(voidMethod, token, null);
        assert SeAccessCompat.access(api, null, seNormal) == token && seNormal.calls == 1;
        Throwable denied = new SecurityException("no apdu access allowed");
        Chain otherCaller = new Chain(voidMethod, null, denied, null, "other.app", null, 0, false);
        expectSame(denied, () -> SeAccessCompat.access(api, null, otherCaller));
        assert otherCaller.calls == 1;
        Chain adapterFailure = new Chain(voidMethod, null, denied, null, CompatPolicy.WALLET, null, 0, false);
        expectSame(denied, () -> SeAccessCompat.access(api, null, adapterFailure));
        assert adapterFailure.calls == 1;
        System.out.println("HookFlowTest passed: original results/errors preserved, proceed once, narrow fallback");
    }
}
