package com.linkflow.web.security;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

class ContentSecurityPolicyFilterTest {

    private static final Pattern NONCE = Pattern.compile("'nonce-([A-Za-z0-9_-]+)'");

    private final ContentSecurityPolicyFilter filter = new ContentSecurityPolicyFilter();

    @Test
    void publishesNonceForTemplatesAndMatchesTheHeader() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        Object attribute = request.getAttribute(ContentSecurityPolicyFilter.NONCE_ATTRIBUTE);
        assertNotNull(attribute, "Templates need the nonce to mark their inline scripts");
        assertEquals(attribute, nonceFrom(response), "Header nonce must match the one templates use");
    }

    @Test
    void issuesADifferentNoncePerRequest() throws Exception {
        // A reused nonce is guessable and would be no better than 'unsafe-inline'.
        assertNotEquals(nonceFrom(runFilter()), nonceFrom(runFilter()));
    }

    @Test
    void restrictsScriptsToSelfNonceAndTheCdn() throws Exception {
        String policy = runFilter().getHeader("Content-Security-Policy");

        assertTrue(policy.contains("script-src 'self' 'nonce-"), policy);
        assertTrue(policy.contains("https://cdn.jsdelivr.net"), policy);
        assertFalse(policy.contains("script-src 'self' 'unsafe-inline'"),
                "unsafe-inline in script-src would defeat the policy: " + policy);
    }

    @Test
    void deniesFramingObjectsAndOffSiteFormPosts() throws Exception {
        String policy = runFilter().getHeader("Content-Security-Policy");

        assertTrue(policy.contains("frame-ancestors 'none'"), policy);
        assertTrue(policy.contains("object-src 'none'"), policy);
        assertTrue(policy.contains("form-action 'self'"), policy);
        assertTrue(policy.contains("base-uri 'self'"), policy);
    }

    @Test
    void continuesTheFilterChain() throws Exception {
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(new MockHttpServletRequest(), new MockHttpServletResponse(), chain);

        assertNotNull(chain.getRequest(), "Downstream filters must still run");
    }

    private MockHttpServletResponse runFilter() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = new MockFilterChain();
        filter.doFilter(new MockHttpServletRequest(), response, chain);
        return response;
    }

    private static String nonceFrom(MockHttpServletResponse response) {
        String policy = response.getHeader("Content-Security-Policy");
        assertNotNull(policy, "Content-Security-Policy header was not set");

        Matcher matcher = NONCE.matcher(policy);
        assertTrue(matcher.find(), "No nonce in policy: " + policy);
        return matcher.group(1);
    }
}
