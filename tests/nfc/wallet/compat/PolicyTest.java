package nfc.wallet.compat;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class PolicyTest {
    public static void main(String[] args) {
        assert NfcPolicy.supportedDevice("PJE110", 36);
        assert NfcPolicy.supportedDevice("PJE110", 37);
        assert !NfcPolicy.supportedDevice("another-phone", 36);
        assert !NfcPolicy.supportedDevice("PJE110", 35);
        Map<String, Integer> forward = new HashMap<>();
        Map<Integer, String> reverse = new HashMap<>();
        forward.put("eSE1", 1); forward.put("SIM1", 0x81); forward.put("NDEF-NFCEE", 0xC0);
        reverse.put(1, "eSE1"); reverse.put(0x81, "SIM1"); reverse.put(0xC0, "NDEF-NFCEE");
        byte[] ese = {1};
        assert NfcPolicy.correct(ese, new byte[]{(byte) 0x81}, forward, reverse);
        assert (ese[0] & 255) == 0xC0 && forward.get("eSE1") == 0xC0;
        assert "eSE1".equals(reverse.get(0xC0)) && !reverse.containsKey(1);
        assert forward.get("SIM1") == 0x81 && "SIM1".equals(reverse.get(0x81));
        Map<String, Integer> before = new HashMap<>(forward);
        Map<Integer, String> reverseBefore = new HashMap<>(reverse);
        assert NfcPolicy.correct(ese, null, forward, reverse);
        assert before.equals(forward) && reverseBefore.equals(reverse);
        assert !NfcPolicy.correct(ese, new byte[]{(byte) 0xC0}, forward, reverse);
        assert before.equals(forward) && reverseBefore.equals(reverse);
        assert !NfcPolicy.correct(new byte[]{1, 2}, null, forward, reverse);
        assert !NfcPolicy.correct(new byte[]{(byte) 0xC2}, null, forward, reverse);
        assert !NfcPolicy.canCorrect(ese, null, Collections.unmodifiableMap(forward), reverse);
        forward.put("SIM2", 0xC0);
        before = new HashMap<>(forward);
        assert !NfcPolicy.correct(ese, null, forward, reverse);
        assert before.equals(forward) && reverseBefore.equals(reverse);
        forward.remove("SIM2"); reverse.put(0xC0, "SIM2");
        assert !NfcPolicy.correct(ese, null, forward, reverse);
        assert "SIM2".equals(reverse.get(0xC0));
        assert NfcPolicy.mergedTransitAids(Arrays.asList("A0000000031010")) == null;
        List<String> merged = NfcPolicy.mergedTransitAids(Arrays.asList("5041592E535A54", "A0000000031010"));
        assert merged.containsAll(NfcPolicy.TRANSIT_AIDS) && merged.contains("A0000000031010");
        assert merged.size() == 5;
        assert !NfcPolicy.needsAidUpdate(merged, NfcPolicy.mergedTransitAids(merged));
        assert NfcPolicy.needsAidUpdate(Collections.singletonList("5041592E535A54"), merged);
        merged = NfcPolicy.mergedTransitAids(Arrays.asList("A0000000041010"),
                Arrays.asList("5041592E535A54", "A0000000031010"));
        assert merged.size() == 6 && merged.contains("A0000000041010") && merged.contains("A0000000031010");
        java.util.ArrayList<String> oversized = new java.util.ArrayList<>();
        oversized.add("5041592E535A54");
        for (int i = 0; i < 252; i++) oversized.add(String.format("%010X", i));
        assert NfcPolicy.mergedTransitAids(oversized).size() == 256;
        oversized.add("FFFFFFFFFF");
        assert NfcPolicy.mergedTransitAids(oversized) == null;
        Throwable denied = new SecurityException("No APDU access allowed");
        assert SePolicy.allowOriginalWallet("com.finshell.wallet", "eSE1", 10634, 10634,
                SePolicy.FINSHELL_CERT, true, denied);
        assert !SePolicy.allowOriginalWallet("com.finshell.wallet", "eSE1", 12345, 10634,
                SePolicy.FINSHELL_CERT, true, denied);
        assert !SePolicy.allowOriginalWallet("com.finshell.wallet", "eSE1", 10634, 10634,
                "wrong-signer", true, denied);
        assert !SePolicy.allowOriginalWallet("com.finshell.wallet", "SIM1", 10634, 10634,
                SePolicy.FINSHELL_CERT, true, denied);
        assert !SePolicy.allowOriginalWallet("com.finshell.wallet", "eSE1", 10634, 10634,
                SePolicy.FINSHELL_CERT, true, new RuntimeException("No APDU access allowed"));
        System.out.println("PASS: device/layout guards, SIM conflicts, unchanged rejection, idempotency, AID preservation, wallet UID/cert/SE/exception boundary");
    }
}
