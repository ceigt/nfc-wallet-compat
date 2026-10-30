package nfc.wallet.compat;

public class WarrantyPolicyTest {
    public static void main(String[] args) {
        String page = "https://rinsurance.finzfin.com/insurance/screenSecurity/electronicCard/";
        assert WarrantyPolicy.allowed(page, "getDeviceInfo");
        assert WarrantyPolicy.allowed(page + "?channel=wallet", "getBaseDeviceInfo");
        assert !WarrantyPolicy.allowed(page, "getHeader");
        assert !WarrantyPolicy.allowed(page.replace("electronicCard", "screenProduct"), "getDeviceInfo");
        assert !WarrantyPolicy.allowed(page.replace("electronicCard", "purchase"), "getDeviceInfo");
        assert !WarrantyPolicy.allowed(page, "getToken");
        assert !WarrantyPolicy.allowed(null, "getDeviceInfo");
        assert !WarrantyPolicy.allowed(page.replace("https:", "http:"), "getDeviceInfo");
        assert !WarrantyPolicy.allowed(page.replace(".com/", ".com.evil.example/"), "getDeviceInfo");
        assert !WarrantyPolicy.allowed(page.replace("https://", "https://user@"), "getDeviceInfo");
        assert !WarrantyPolicy.allowed(page.replace(".com/", ".com:8443/"), "getDeviceInfo");
        assert !WarrantyPolicy.allowed(page + "extra/", "getDeviceInfo");
        assert !WarrantyPolicy.allowed(page + "#other-page", "getDeviceInfo");
        assert !WarrantyPolicy.allowed(page.replace("screenSecurity", "%73creenSecurity"), "getDeviceInfo");
        assert !WarrantyPolicy.allowed("https://loanshop.finzfin.com/budo/home/", "getDeviceInfo");
        assert WarrantyPolicy.validImei("490154203237518"); // Public Luhn example, not a user's identifier.
        assert !WarrantyPolicy.validImei("490154203237519");
        assert !WarrantyPolicy.validImei("000000000000000");
        assert !WarrantyPolicy.validImei("222222222222222");
        assert !WarrantyPolicy.validImei("49015420323751");
        assert !WarrantyPolicy.validImei("4901542032375180");
        assert !WarrantyPolicy.validImei("49015420323751x");
        assert !WarrantyPolicy.validImei(null);
        System.out.println("PASS: warranty origin/path/method boundaries and valid real-IMEI format");
    }
}
