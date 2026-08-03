package com.linkflow.app;

import com.linkflow.app.support.AbstractIntegrationTest;
import com.linkflow.app.support.TestMailbox;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Covers changing the address an account signs in with, end to end against a real SMTP server.
 * <p>
 * This flow is the one place where a mistake locks someone out permanently: the confirmation link
 * goes to the <em>new</em> mailbox, so if the address is wrong or the change applies without proof
 * of control, the account moves somewhere its owner cannot reach. The tests below are written
 * around that — the old address must keep working until the link is redeemed, and the new one must
 * not work before.
 */
class EmailChangeIT extends AbstractIntegrationTest {

    private static final String PASSWORD = "StrongP@ss1";

    @Test
    void confirmingTheLinkMovesTheAccountToTheNewAddress() throws Exception {
        String original = uniqueEmail("change-from");
        String replacement = uniqueEmail("change-to");
        registerUser(original, PASSWORD, "Mover");
        String accessToken = login(original, PASSWORD).accessToken();

        requestEmailChange(accessToken, PASSWORD, replacement).andExpect(status().isOk());

        // The link must arrive at the new address; sending it to the old one would confirm nothing
        // about who controls the mailbox the account is moving to.
        String token = TestMailbox.awaitToken(replacement, "/verify-email-change");

        verifyEmailChange(token).andExpect(status().isOk());

        mockMvc.perform(loginRequest(replacement, PASSWORD)).andExpect(status().isOk());
        mockMvc.perform(loginRequest(original, PASSWORD)).andExpect(status().isUnauthorized());
    }

    @Test
    void theOldAddressKeepsWorkingUntilTheLinkIsConfirmed() throws Exception {
        String original = uniqueEmail("pending-from");
        String replacement = uniqueEmail("pending-to");
        registerUser(original, PASSWORD, "Pending");
        String accessToken = login(original, PASSWORD).accessToken();

        requestEmailChange(accessToken, PASSWORD, replacement).andExpect(status().isOk());

        // Requesting the change must not apply it. Otherwise a typo in the new address would strand
        // the account at a mailbox nobody owns, with no way back.
        mockMvc.perform(loginRequest(original, PASSWORD)).andExpect(status().isOk());
        mockMvc.perform(loginRequest(replacement, PASSWORD)).andExpect(status().isUnauthorized());
    }

    /**
     * The current password is required even though the caller already holds a valid access token.
     * A stolen or borrowed session would otherwise be enough to redirect the account to an
     * attacker's mailbox, which converts temporary access into permanent ownership.
     */
    @Test
    void aWrongCurrentPasswordIsRejectedAndSendsNothing() throws Exception {
        String original = uniqueEmail("badpass-from");
        String replacement = uniqueEmail("badpass-to");
        registerUser(original, PASSWORD, "BadPass");
        String accessToken = login(original, PASSWORD).accessToken();

        requestEmailChange(accessToken, "NotTheP@ssword9", replacement)
                .andExpect(status().isConflict());

        TestMailbox.assertNoMailFor(replacement);
    }

    @Test
    void anAddressAlreadyInUseIsRejected() throws Exception {
        String original = uniqueEmail("taken-from");
        String occupied = uniqueEmail("taken-by");
        registerUser(original, PASSWORD, "Taken");
        registerUser(occupied, PASSWORD, "Occupier");

        String accessToken = login(original, PASSWORD).accessToken();

        // Setting up the second account sent it a verification email of its own; drop it so the
        // assertion below can only see mail produced by the rejected request.
        TestMailbox.clear();

        requestEmailChange(accessToken, PASSWORD, occupied).andExpect(status().isConflict());
        TestMailbox.assertNoMailFor(occupied);
    }

    @Test
    void requestingTheChangeRequiresAuthentication() throws Exception {
        mockMvc.perform(post("/api/v1/users/me/email-change-request")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"currentPassword": "%s", "newEmail": "%s"}
                                """.formatted(PASSWORD, uniqueEmail("anon"))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void anUnknownConfirmationTokenIsNotFound() throws Exception {
        verifyEmailChange("not-a-real-token").andExpect(status().isNotFound());
    }

    /**
     * Mail scanners and link prefetchers open these links before the recipient does, so the token
     * can be spent by a machine. Once the account already carries the new address the request has
     * been satisfied, and a second redemption should confirm that rather than report a conflict at
     * a user who did nothing wrong.
     */
    @Test
    void redeemingTheSameLinkTwiceIsIdempotent() throws Exception {
        String original = uniqueEmail("twice-from");
        String replacement = uniqueEmail("twice-to");
        registerUser(original, PASSWORD, "Twice");
        String accessToken = login(original, PASSWORD).accessToken();

        requestEmailChange(accessToken, PASSWORD, replacement).andExpect(status().isOk());
        String token = TestMailbox.awaitToken(replacement, "/verify-email-change");

        verifyEmailChange(token).andExpect(status().isOk());
        verifyEmailChange(token).andExpect(status().isOk());

        mockMvc.perform(loginRequest(replacement, PASSWORD)).andExpect(status().isOk());
    }

    /**
     * Only the newest link may work. A confirmation sent to an address the user then thought better
     * of must not stay redeemable — otherwise an old message sitting in a mailbox, or intercepted in
     * transit, can still move the account after the user has redirected it somewhere else.
     */
    @Test
    void aSecondRequestInvalidatesTheFirstLink() throws Exception {
        String original = uniqueEmail("supersede-from");
        String firstTarget = uniqueEmail("supersede-first");
        String secondTarget = uniqueEmail("supersede-second");
        registerUser(original, PASSWORD, "Supersede");
        String accessToken = login(original, PASSWORD).accessToken();

        requestEmailChange(accessToken, PASSWORD, firstTarget).andExpect(status().isOk());
        String firstToken = TestMailbox.awaitToken(firstTarget, "/verify-email-change");

        requestEmailChange(accessToken, PASSWORD, secondTarget).andExpect(status().isOk());
        String secondToken = TestMailbox.awaitToken(secondTarget, "/verify-email-change");

        verifyEmailChange(firstToken).andExpect(status().isConflict());
        verifyEmailChange(secondToken).andExpect(status().isOk());

        mockMvc.perform(loginRequest(secondTarget, PASSWORD)).andExpect(status().isOk());
    }

    /**
     * The address is the sign-in identity, so changing it invalidates every credential minted
     * against the old one. Sessions on other devices must not survive.
     */
    @Test
    void confirmingTheChangeRevokesExistingSessions() throws Exception {
        String original = uniqueEmail("revoke-from");
        String replacement = uniqueEmail("revoke-to");
        registerUser(original, PASSWORD, "Revoke");
        TokenPair tokens = login(original, PASSWORD);

        mockMvc.perform(get("/api/v1/users/me").header("Authorization", "Bearer " + tokens.accessToken()))
                .andExpect(status().isOk());

        requestEmailChange(tokens.accessToken(), PASSWORD, replacement).andExpect(status().isOk());
        verifyEmailChange(TestMailbox.awaitToken(replacement, "/verify-email-change"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\": \"%s\"}".formatted(tokens.refreshToken())))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void changingToTheAddressAlreadyOnTheAccountIsRejected() throws Exception {
        String email = uniqueEmail("same");
        registerUser(email, PASSWORD, "Same");
        String accessToken = login(email, PASSWORD).accessToken();

        requestEmailChange(accessToken, PASSWORD, email)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").exists());
    }

    private ResultActions requestEmailChange(String accessToken, String currentPassword, String newEmail)
            throws Exception {
        return mockMvc.perform(post("/api/v1/users/me/email-change-request")
                .header("Authorization", "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"currentPassword": "%s", "newEmail": "%s"}
                        """.formatted(currentPassword, newEmail)));
    }

    private ResultActions verifyEmailChange(String token) throws Exception {
        return mockMvc.perform(post("/api/v1/users/verify-email-change")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\": \"%s\"}".formatted(token)));
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
