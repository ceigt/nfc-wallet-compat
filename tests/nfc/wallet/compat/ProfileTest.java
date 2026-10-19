package nfc.wallet.compat;

import java.util.HashMap;
import java.util.Map;

public final class ProfileTest {
    static class Base {
        private final HashMap<String, Integer> routes = new HashMap<>();
        private boolean update(int user) { return true; }
    }
    static class CompatibleUpgrade extends Base {}
    static class IncompatibleUpgrade { private String routes; private int update(int user) { return 1; } }
    public static void main(String[] args) throws Exception {
        assert NfcProfile.field(CompatibleUpgrade.class, "routes", Map.class).getDeclaringClass() == Base.class;
        assert NfcProfile.method(CompatibleUpgrade.class, "update", boolean.class, int.class).getDeclaringClass() == Base.class;
        try {
            NfcProfile.field(IncompatibleUpgrade.class, "routes", Map.class);
            throw new AssertionError("Changed field type accepted");
        } catch (IllegalStateException expected) {}
        try {
            NfcProfile.method(IncompatibleUpgrade.class, "update", boolean.class, int.class);
            throw new AssertionError("Changed return type accepted");
        } catch (IllegalStateException expected) {}
        try {
            NfcProfile.method(CompatibleUpgrade.class, "update", boolean.class, int.class, boolean.class);
            throw new AssertionError("Changed argument list accepted");
        } catch (NoSuchMethodException expected) {}
        try {
            NfcProfile.field(CompatibleUpgrade.class, "renamedRoutes", Map.class);
            throw new AssertionError("Renamed field accepted");
        } catch (NoSuchFieldException expected) {}
        System.out.println("PASS: compatible inherited interfaces accepted; changed types, signatures and names rejected");
    }
}
