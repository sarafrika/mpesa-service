package com.sarafrika.apps.mpesaservice.services.daraja.products;

import com.sarafrika.apps.mpesaservice.utils.enums.DarajaFeature;
import com.sarafrika.apps.mpesaservice.utils.enums.Environment;
import java.net.URI;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Where each flagged product is called and where Safaricom posts its results. Sandbox paths are the ones
 * Safaricom's portal documents; production paths for Tax Remittance and B2B Express Checkout are settings
 * (Safaricom only shows the sandbox URL), and Pochi's production path is the documented one. */
@Component
public class DarajaProductEndpoints {

    static final String TAX_REMITTANCE_PATH = "/mpesa/b2b/v1/remittax";
    static final String POCHI_PATH = "/mpesa/b2pochi/v1/paymentrequest";
    static final String B2B_CHECKOUT_PATH = "/v1/ussdpush/get-msisdn";

    private final String sandboxBaseUrl;
    private final String productionBaseUrl;
    private final String callbackBaseUrl;
    private final String taxProductionPath;
    private final String checkoutProductionPath;

    public DarajaProductEndpoints(
            @Value("${mpesa.daraja.sandbox.base-url:https://sandbox.safaricom.co.ke}") String sandboxBaseUrl,
            @Value("${mpesa.daraja.production.base-url:https://api.safaricom.co.ke}") String productionBaseUrl,
            @Value("${mpesa.callback.base-url:}") String callbackBaseUrl,
            @Value("${mpesa.daraja.products.tax-remittance-production-path:" + TAX_REMITTANCE_PATH + "}")
            String taxProductionPath,
            @Value("${mpesa.daraja.products.b2b-express-checkout-production-path:" + B2B_CHECKOUT_PATH + "}")
            String checkoutProductionPath) {
        this.sandboxBaseUrl = trim(sandboxBaseUrl);
        this.productionBaseUrl = trim(productionBaseUrl);
        this.callbackBaseUrl = trim(callbackBaseUrl);
        this.taxProductionPath = taxProductionPath;
        this.checkoutProductionPath = checkoutProductionPath;
    }

    public URI requestUrl(DarajaFeature product, Environment environment) {
        boolean production = environment == Environment.PRODUCTION;
        String path = switch (product) {
            case TAX_REMITTANCE -> production ? taxProductionPath : TAX_REMITTANCE_PATH;
            case BUSINESS_TO_POCHI -> POCHI_PATH;
            case B2B_EXPRESS_CHECKOUT -> production ? checkoutProductionPath : B2B_CHECKOUT_PATH;
            default -> throw new IllegalArgumentException(product + " is not built in this service");
        };
        return URI.create((production ? productionBaseUrl : sandboxBaseUrl) + path);
    }

    /** Our callback URL for a path under /api/v1/callbacks, e.g. "/pochi/result". */
    public String callbackUrl(String path) {
        return callbackBaseUrl + path;
    }

    private static String trim(String url) {
        return url == null ? "" : url.replaceAll("/+$", "");
    }
}
