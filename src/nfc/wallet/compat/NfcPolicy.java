package nfc.wallet.compat;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/** Pure compatibility decisions; unknown hardware layouts are never guessed. */
final class NfcPolicy {
    static final int ESE_ROUTE = 0xC0;
    static final List<String> TRANSIT_AIDS = Collections.unmodifiableList(Arrays.asList(
            "535A542E57414C4C45542E454E56", "A000000632010105100091560000",
            "5041592E535A54", "A000000632010105"));

    static boolean supportedDevice(String model, int sdk) {
        return "PJE110".equals(model) && sdk >= 36;
    }

    static boolean canCorrect(byte[] ese, byte[] uicc, Map<?, ?> forward, Map<?, ?> reverse) {
        if (ese == null || ese.length != 1 || forward == null || reverse == null
                || forward.getClass() != HashMap.class || reverse.getClass() != HashMap.class) return false;
        int old = ese[0] & 255;
        if (old != 1 && old != ESE_ROUTE) return false;
        if (!Integer.valueOf(old).equals(forward.get("eSE1"))) return false;
        Object oldOwner = reverse.get(old);
        if (!"eSE1".equals(oldOwner) && !"NDEF-NFCEE".equals(oldOwner)) return false;
        Object targetOwner = reverse.get(ESE_ROUTE);
        if (targetOwner != null && !"eSE1".equals(targetOwner)
                && !"NDEF-NFCEE".equals(targetOwner)) return false;
        if (uicc != null) {
            for (byte route : uicc) if ((route & 255) == ESE_ROUTE) return false;
        }
        for (Map.Entry<?, ?> entry : forward.entrySet()) {
            if (Integer.valueOf(ESE_ROUTE).equals(entry.getValue())
                    && !"eSE1".equals(entry.getKey()) && !"NDEF-NFCEE".equals(entry.getKey())) return false;
        }
        return true;
    }

    static boolean correct(byte[] ese, byte[] uicc, Map<String, Integer> forward,
                           Map<Integer, String> reverse) {
        if (!canCorrect(ese, uicc, forward, reverse)) return false;
        int old = ese[0] & 255;
        // All reflection and layout checks finish before any live state is changed.
        if (old == ESE_ROUTE && "eSE1".equals(reverse.get(ESE_ROUTE))) return true;
        forward.put("eSE1", ESE_ROUTE);
        reverse.put(ESE_ROUTE, "eSE1");
        if (old != ESE_ROUTE && "eSE1".equals(reverse.get(old))) reverse.remove(old);
        ese[0] = (byte) ESE_ROUTE;
        return true;
    }

    static List<String> mergedTransitAids(Collection<String> existing) {
        return mergedTransitAids(existing, Collections.<String>emptyList());
    }

    static List<String> mergedTransitAids(Collection<String> existing, Collection<String> declared) {
        if (existing == null || declared == null) return null;
        LinkedHashSet<String> original = new LinkedHashSet<>(declared);
        original.addAll(existing);
        boolean recognized = false;
        for (String aid : TRANSIT_AIDS) recognized |= original.contains(aid);
        if (!recognized) return null;
        LinkedHashSet<String> merged = new LinkedHashSet<>(TRANSIT_AIDS);
        merged.addAll(original);
        if (merged.size() > 256) return null;
        return new ArrayList<>(merged);
    }

    static boolean needsAidUpdate(Collection<String> existing, Collection<String> desired) {
        return existing == null || !new LinkedHashSet<>(existing).equals(new LinkedHashSet<>(desired));
    }
}
