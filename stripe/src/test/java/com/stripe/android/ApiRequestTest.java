package com.stripe.android;

import android.os.Build;

import androidx.test.core.app.ApplicationProvider;

import com.stripe.android.exception.InvalidRequestException;
import com.stripe.android.model.CardFixtures;

import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.io.UnsupportedEncodingException;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
public class ApiRequestTest {
    @Test
    public void getHeaders_withAllRequestOptions_properlyMapsRequestOptions() {
        final String stripeAccount = "acct_123abc";
        final Map<String, String> headerMap =
                ApiRequest.createGet(StripeApiHandler.getSourcesUrl(),
                        ApiRequest.Options.create(ApiKeyFixtures.FAKE_PUBLISHABLE_KEY,
                                stripeAccount), null)
                        .getHeaders();

        assertNotNull(headerMap);
        assertEquals("Bearer " + ApiKeyFixtures.FAKE_PUBLISHABLE_KEY,
                headerMap.get("Authorization"));
        assertEquals(ApiVersion.getDefault().getCode(), headerMap.get("Stripe-Version"));
        assertEquals(stripeAccount, headerMap.get("Stripe-Account"));
    }

    @Test
    public void getHeaders_withOnlyRequiredOptions_doesNotAddEmptyOptions() {
        final Map<String, String> headerMap =
                ApiRequest.createGet(StripeApiHandler.getSourcesUrl(),
                        ApiRequest.Options.create(ApiKeyFixtures.DEFAULT_PUBLISHABLE_KEY), null)
                        .getHeaders();

        assertTrue(headerMap.containsKey("Stripe-Version"));
        assertFalse(headerMap.containsKey("Stripe-Account"));
        assertTrue(headerMap.containsKey("Authorization"));
    }

    @Test
    public void getHeaders_containsPropertyMapValues() throws JSONException {
        final Map<String, String> headers =
                ApiRequest.createGet(StripeApiHandler.getSourcesUrl(),
                        ApiRequest.Options.create(ApiKeyFixtures.DEFAULT_PUBLISHABLE_KEY), null)
                        .getHeaders();

        final JSONObject userAgentData = new JSONObject(headers.get("X-Stripe-Client-User-Agent"));
        assertEquals(BuildConfig.VERSION_NAME, userAgentData.getString("bindings.version"));
        assertEquals("Java", userAgentData.getString("lang"));
        assertEquals("Stripe", userAgentData.getString("publisher"));
        assertEquals("android", userAgentData.getString("os.name"));
        assertEquals(Build.VERSION.SDK_INT,
                Integer.parseInt(userAgentData.getString("os.version")));
        assertTrue(userAgentData.getString("java.version").startsWith("1.8.0"));
    }

    @Test
    public void getHeaders_correctlyAddsExpectedAdditionalParameters() {
        final Map<String, String> headerMap =
                ApiRequest.createGet(StripeApiHandler.getSourcesUrl(),
                        ApiRequest.Options.create(ApiKeyFixtures.DEFAULT_PUBLISHABLE_KEY), null)
                        .getHeaders();

        final String expectedUserAgent =
                String.format(Locale.ROOT, "Stripe/v1 AndroidBindings/%s",
                        BuildConfig.VERSION_NAME);
        assertEquals(expectedUserAgent, headerMap.get("User-Agent"));
        assertEquals("application/json", headerMap.get("Accept"));
        assertEquals("UTF-8", headerMap.get("Accept-Charset"));
    }

    @Test
    public void createQuery_withCardData_createsProperQueryString()
            throws UnsupportedEncodingException, InvalidRequestException {
        final Map<String, Object> cardMap =
                new StripeNetworkUtils(ApplicationProvider.getApplicationContext())
                        .hashMapFromCard(CardFixtures.MINIMUM_CARD);
        final String expectedValue = "product_usage=&card%5Bnumber%5D=4242424242424242&card%5B" +
                "cvc%5D=123&card%5Bexp_month%5D=1&card%5Bexp_year%5D=2050";
        final String query = ApiRequest.createGet(StripeApiHandler.getSourcesUrl(), cardMap,
                ApiRequest.Options.create(ApiKeyFixtures.DEFAULT_PUBLISHABLE_KEY), null)
                .createQuery();
        assertEquals(expectedValue, query);
    }

    @Test
    public void getContentType() {
        final String contentType = ApiRequest.createGet(StripeApiHandler.getSourcesUrl(),
                ApiRequest.Options.create(ApiKeyFixtures.DEFAULT_PUBLISHABLE_KEY), null)
                .getContentType();
        assertEquals("application/x-www-form-urlencoded; charset=UTF-8", contentType);
    }

    @Test
    public void getOutputBytes_withEmptyBody_shouldHaveZeroLength()
            throws UnsupportedEncodingException, InvalidRequestException {
        final byte[] output = ApiRequest.createPost(StripeApiHandler.getPaymentMethodsUrl(),
                ApiRequest.Options.create(ApiKeyFixtures.DEFAULT_PUBLISHABLE_KEY), null)
                .getOutputBytes();
        assertEquals(0, output.length);
    }
    
    @Test
    public void getOutputBytes_withNonEmptyBody_shouldHaveNonZeroLength()
            throws UnsupportedEncodingException, InvalidRequestException {
        final Map<String, String> params = new HashMap<>();
        params.put("customer", "cus_123");

        final byte[] output = ApiRequest.createPost(StripeApiHandler.getPaymentMethodsUrl(),
                params,
                ApiRequest.Options.create(ApiKeyFixtures.DEFAULT_PUBLISHABLE_KEY), null)
                .getOutputBytes();
        assertEquals(16, output.length);
    }

    @Test
    public void testEquals() {
        final Map<String, String> params = new HashMap<>();
        params.put("customer", "cus_123");
        assertEquals(
                ApiRequest.createPost(StripeApiHandler.getPaymentMethodsUrl(),
                        params,
                        ApiRequest.Options.create(ApiKeyFixtures.DEFAULT_PUBLISHABLE_KEY), null),
                ApiRequest.createPost(StripeApiHandler.getPaymentMethodsUrl(),
                        params,
                        ApiRequest.Options.create(ApiKeyFixtures.DEFAULT_PUBLISHABLE_KEY), null)
        );

        assertNotEquals(
                ApiRequest.createPost(StripeApiHandler.getPaymentMethodsUrl(),
                        params,
                        ApiRequest.Options.create(ApiKeyFixtures.DEFAULT_PUBLISHABLE_KEY), null),
                ApiRequest.createPost(StripeApiHandler.getPaymentMethodsUrl(),
                        params,
                        ApiRequest.Options.create(ApiKeyFixtures.DEFAULT_PUBLISHABLE_KEY,
                                "acct"), null)
        );
    }

    @Test
    public void getHeaders_withAppInfo() throws JSONException {
        final ApiRequest apiRequest = new ApiRequest(StripeRequest.Method.GET,
                StripeApiHandler.getPaymentMethodsUrl(), null,
                ApiRequest.Options.create(ApiKeyFixtures.DEFAULT_PUBLISHABLE_KEY),
                AppInfoTest.APP_INFO);
        final Map<String, String> headers = apiRequest.getHeaders();
        assertEquals(
                "Stripe/v1 AndroidBindings/9.2.0 " +
                        "MyAwesomePlugin/1.2.34 (https://myawesomeplugin.info)",
                headers.get("User-Agent"));

        final JSONObject userAgentData = new JSONObject(headers.get("X-Stripe-Client-User-Agent"));
        assertEquals(
                "{\"name\":\"MyAwesomePlugin\"," +
                        "\"partnerId\":\"pp_partner_1234\"," +
                        "\"version\":\"1.2.34\"," +
                        "\"url\":\"https:\\/\\/myawesomeplugin.info\"}",
                userAgentData.getString("application"));
    }
}