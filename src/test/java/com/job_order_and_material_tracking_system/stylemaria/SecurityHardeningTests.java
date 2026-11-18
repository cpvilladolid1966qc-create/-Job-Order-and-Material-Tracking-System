package com.job_order_and_material_tracking_system.stylemaria;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockFilterChain;

class SecurityHardeningTests {

    @Test
    void crossOriginMutationsAreRejectedAndSameOriginMutationsReachAuthorization() throws Exception {
        DemoRoleInterceptor interceptor = new DemoRoleInterceptor();

        MockHttpServletRequest crossOrigin = request("https://attacker.example");
        crossOrigin.addHeader("Sec-Fetch-Site", "cross-site");
        MockHttpServletResponse rejectedResponse = new MockHttpServletResponse();
        assertFalse(interceptor.preHandle(crossOrigin, rejectedResponse, new Object()));
        assertEquals(403, rejectedResponse.getStatus());

        MockHttpServletRequest sameOrigin = request("https://stylemaria.example");
        sameOrigin.addHeader("Sec-Fetch-Site", "same-origin");
        MockHttpServletResponse sameOriginResponse = new MockHttpServletResponse();
        assertFalse(interceptor.preHandle(sameOrigin, sameOriginResponse, new Object()));
        assertEquals(401, sameOriginResponse.getStatus());
    }

    @Test
    void securityHeadersAreAppliedAndSensitivePagesAreNotCached() throws Exception {
        SecurityHeadersFilter filter = new SecurityHeadersFilter();
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/admin.html");
        request.setSecure(true);
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertEquals("nosniff", response.getHeader("X-Content-Type-Options"));
        assertEquals("DENY", response.getHeader("X-Frame-Options"));
        assertEquals("strict-origin-when-cross-origin", response.getHeader("Referrer-Policy"));
        assertEquals("max-age=31536000", response.getHeader("Strict-Transport-Security"));
        assertEquals("no-store", response.getHeader("Cache-Control"));
        assertTrue(response.getHeader("Content-Security-Policy").contains("frame-ancestors 'none'"));
    }

    @Test
    void productionConfigurationRequiresHttpsStrongAdminAndWorkingMailCredentials() {
        ProductionConfigurationValidator valid = new ProductionConfigurationValidator(
                "https://stylemaria.example",
                "admin@stylemaria.example",
                "a-unique-admin-passphrase-of-20",
                "smtp-user",
                "smtp-password",
                "orders@stylemaria.example");
        assertDoesNotThrow(() -> valid.run(new DefaultApplicationArguments(new String[0])));

        ProductionConfigurationValidator insecureUrl = new ProductionConfigurationValidator(
                "http://stylemaria.example",
                "admin@stylemaria.example",
                "a-unique-admin-passphrase-of-20",
                "smtp-user",
                "smtp-password",
                "orders@stylemaria.example");
        assertThrows(IllegalStateException.class,
                () -> insecureUrl.run(new DefaultApplicationArguments(new String[0])));

        ProductionConfigurationValidator weakPassword = new ProductionConfigurationValidator(
                "https://stylemaria.example",
                "admin@stylemaria.example",
                "too-short",
                "smtp-user",
                "smtp-password",
                "orders@stylemaria.example");
        assertThrows(IllegalStateException.class,
                () -> weakPassword.run(new DefaultApplicationArguments(new String[0])));
    }

    private MockHttpServletRequest request(String origin) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/customer/profile");
        request.setScheme("https");
        request.setServerName("stylemaria.example");
        request.setServerPort(443);
        request.addHeader("Origin", origin);
        return request;
    }

}
