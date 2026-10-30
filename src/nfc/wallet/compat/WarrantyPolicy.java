package nfc.wallet.compat;

import java.net.URI;

final class WarrantyPolicy {
    static boolean allowed(String url, String method) {
        if (!"getDeviceInfo".equals(method) && !"getBaseDeviceInfo".equals(method)) return false;
        if (url == null) return false;
        try {
            URI page = new URI(url);
            return "https".equalsIgnoreCase(page.getScheme())
                    && "rinsurance.finzfin.com".equalsIgnoreCase(page.getHost())
                    && page.getRawUserInfo() == null && (page.getPort() == -1 || page.getPort() == 443)
                    && "/insurance/screenSecurity/electronicCard/".equals(page.getRawPath())
                    && (page.getRawFragment() == null || page.getRawFragment().isEmpty());
        } catch (Exception invalid) { return false; }
    }

    static boolean validImei(String value) {
        if (value == null || value.length() != 15) return false;
        int sum = 0;
        boolean different = false;
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c < '0' || c > '9') return false;
            different |= c != value.charAt(0);
            int digit = c - '0';
            if ((i & 1) != 0) digit *= 2;
            sum += digit / 10 + digit % 10;
        }
        return different && sum % 10 == 0;
    }
}
