package com.linkflow.app;

import com.linkflow.app.support.AbstractIntegrationTest;
import com.linkflow.app.support.TestMailbox;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Covers password recovery and activation resend against a real SMTP server, following the same
 * path a user does: request, read the emailed link, redeem it.
 */
class AccountRecoveryIT extends AbstractIntegrationTest {

    private static final String PASSWORD = "StrongP@ss1";
    private static final String NEW_PASSWORD = "EvenStr0nger@2";

    @Test
    void forgotPasswordEmailsAWorkingResetLink() throws Exception {
        String email = uniqueEmail("reset");
        registerUser(email, PASSWORD, "Reset");

        requestPasswordReset(email);
        String resetToken = TestMailbox.awaitToken(email, "/reset-password");

        resetPassword(resetToken, NEW_PASSWORD).andExpect(status().isOk());

        // The old password must no longer work, and the new one must.
        mockMvc.perform(loginRequest(email, PASSWORD)).andExpect(status().isUnauthorized());
        mockMvc.perform(loginRequest(email, NEW_PASSWORD)).andExpect(status().isOk());
    }

    @Test
    void resetTokenCannotBeRedeemedTwice() throws Exception {
        String email = uniqueEmail("replay");
        registerUser(email, PASSWORD, "Replay");

        requestPasswordReset(email);
        String resetToken = TestMailbox.awaitToken(email, "/reset-password");

        resetPassword(resetToken, NEW_PASSWORD).andExpect(status().isOk());
        resetPassword(resetToken, "YetAnother@3").andExpect(status().isConflict());
    }

    @Test
    void requestingASecondResetInvalidatesTheFirstLink() throws Exception {
        String email = uniqueEmail("supersede");
        registerUser(email, PASSWORD, "Supersede");

        requestPasswordReset(email);
        String firstToken = TestMailbox.awaitToken(email, "/reset-password");

        TestMailbox.clear();
        requestPasswordReset(email);
        String secondToken = TestMailbox.awaitToken(email, "/reset-password");
        assertNotEquals(firstToken, secondToken);

        // An intercepted earlier link must not survive a fresh request.
        resetPassword(firstToken, NEW_PASSWORD).andExpect(status().isConflict());
        resetPassword(secondToken, NEW_PASSWORD).andExpect(status().isOk());
    }

    @Test
    void forgotPasswordForUnknownAddressLooksIdenticalToASuccess() throws Exception {
        mockMvc.perform(post("/api/v1/auth/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\": \"%s\"}".formatted(uniqueEmail("ghost"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.message")
                        .value("If an account exists for that email, a message with next steps has been sent."));
    }

    @Test
    void resendVerificationIssuesAFreshLinkThatActivatesTheAccount() throws Exception {
        String email = uniqueEmail("resend");
        register(email, PASSWORD, "Resend");

        // Discard the registration email so the assertion below can only see the resent one.
        TestMailbox.awaitToken(email, "/verify-email");
        TestMailbox.clear();

        mockMvc.perform(post("/api/v1/auth/resend-verification")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\": \"%s\"}".formatted(email)))
                .andExpect(status().isOk());

        String resentToken = TestMailbox.awaitToken(email, "/verify-email");
        mockMvc.perform(post("/api/v1/auth/verify-email")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\": \"%s\"}".formatted(resentToken)))
                .andExpect(status().isOk());

        mockMvc.perform(loginRequest(email, PASSWORD)).andExpect(status().isOk());
    }

    @Test
    void resendVerificationForUnknownAddressStillReturnsOk() throws Exception {
        mockMvc.perform(post("/api/v1/auth/resend-verification")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\": \"%s\"}".formatted(uniqueEmail("ghost-resend"))))
                .andExpect(status().isOk());
    }

    private void requestPasswordReset(String email) throws Exception {
        mockMvc.perform(post("/api/v1/auth/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\": \"%s\"}".formatted(email)))
                .andExpect(status().isOk());
    }

    private org.springframework.test.web.servlet.ResultActions resetPassword(String token, String newPassword)
            throws Exception {
        return mockMvc.perform(post("/api/v1/auth/reset-password")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"token": "%s", "newPassword": "%s"}
                        """.formatted(token, newPassword)));
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder loginRequest(
            String email, String password) {
        return post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"email": "%s", "password": "%s"}
                        """.formatted(email, password));
    }

    private String uniqueEmail(String prefix) {
        return prefix + "-" + System.nanoTime() + "@example.com";
    }
}
