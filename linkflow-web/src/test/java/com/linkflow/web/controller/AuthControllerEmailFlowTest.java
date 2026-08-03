package com.linkflow.web.controller;

import com.linkflow.web.client.AuthApiClient;
import com.linkflow.web.client.BackendApiException;
import com.linkflow.web.client.UserApiClient;
import com.linkflow.web.session.SessionManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultMatcher;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

/**
 * Covers what the email pages are told to display, which is where these flows go wrong in ways
 * that are invisible from the backend.
 * <p>
 * Asserted against the model rather than the rendered markup, so the checks describe the state the
 * page is in — "the account is verified", "a link was requested" — rather than the styling that
 * currently expresses it.
 * <p>
 * Standalone setup, deliberately: these are routing and model decisions, and loading security,
 * session storage, and view resolution to observe them would test the framework instead.
 */
@ExtendWith(MockitoExtension.class)
class AuthControllerEmailFlowTest {

    @Mock
    private AuthApiClient authApiClient;
    @Mock
    private UserApiClient userApiClient;
    @Mock
    private SessionManager sessionManager;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(new AuthController(authApiClient, userApiClient, sessionManager))
                .build();
    }

    @Test
    void aValidLinkMarksThePageVerified() throws Exception {
        mockMvc.perform(get("/verify-email").param("token", "good-token"))
                .andExpect(status().isOk())
                .andExpect(view().name("public/verify-email"))
                .andExpect(model().attribute("verified", true))
                .andExpect(model().attributeDoesNotExist("errorMessage"));

        verify(authApiClient).verifyEmail("good-token");
    }

    @Test
    void aRejectedLinkSurfacesTheBackendMessageAndDoesNotClaimVerification() throws Exception {
        doThrow(new BackendApiException("This verification link has expired.", "CONFLICT", 409))
                .when(authApiClient).verifyEmail("stale-token");

        mockMvc.perform(get("/verify-email").param("token", "stale-token"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("errorMessage", "This verification link has expired."))
                .andExpect(model().attributeDoesNotExist("verified"));
    }

    @Test
    void reachingThePageWithoutALinkVerifiesNothing() throws Exception {
        mockMvc.perform(get("/verify-email"))
                .andExpect(status().isOk())
                .andExpect(view().name("public/verify-email"))
                .andExpect(model().attributeDoesNotExist("verified"));

        verify(authApiClient, org.mockito.Mockito.never()).verifyEmail(anyString());
    }

    /**
     * The regression this exists for: the resend acknowledgement used to travel as a generic
     * success message, and the page keyed its "Email confirmed" state off any success message at
     * all. Asking for a new link therefore told the user their address was already confirmed, and
     * hid the form they would need if it were not.
     */
    @Test
    void resendingALinkAcknowledgesWithoutClaimingTheAddressIsConfirmed() throws Exception {
        mockMvc.perform(post("/resend-verification").param("email", "user@example.com"))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attributeExists("infoMessage"))
                // No success message, which is what the page used to read as confirmation.
                .andExpect(flashAttributeDoesNotExist("successMessage"));

        verify(authApiClient).resendVerification("user@example.com");
    }

    /**
     * The address is carried back so the resend form arrives pre-filled — and specifically as a
     * flash attribute rather than a redirect query parameter.
     * <p>
     * That distinction is the whole point of the test. An email address contains an {@code @},
     * which is percent-encoded into a redirect URL, while Spring matches a saved flash map against
     * the decoded parameters of the request that follows. Add the address with
     * {@code addAttribute} and the two never compare equal, so the flash map is discarded as
     * belonging to some other request and the acknowledgement vanishes silently. The redirect must
     * therefore carry no query string at all.
     */
    @Test
    void resendingCarriesTheAddressBackWithoutPuttingItInTheUrl() throws Exception {
        mockMvc.perform(post("/resend-verification").param("email", "user@example.com"))
                .andExpect(redirectedUrl("/verify-email"))
                .andExpect(flash().attribute("email", "user@example.com"));
    }

    /**
     * The other half of the round trip: the landing page must not blank the address it was just
     * handed. It sets a default for visitors who arrive with nothing, and that default has to yield
     * to whatever the resend put in the model.
     */
    @Test
    void theLandingPageKeepsAnAddressHandedToItByTheResend() throws Exception {
        mockMvc.perform(get("/verify-email").flashAttr("email", "user@example.com"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("email", "user@example.com"));
    }

    @Test
    void theLandingPageDefaultsTheAddressToBlankWhenGivenNothing() throws Exception {
        mockMvc.perform(get("/verify-email"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("email", ""));
    }

    /**
     * The backend answers identically for unknown and already-verified addresses, so an error
     * reaching this point is a real fault. Claiming success would leave the user waiting on a
     * message that was never sent.
     */
    @Test
    void aFailedResendIsReportedRatherThanAcknowledged() throws Exception {
        doThrow(new BackendApiException("Service temporarily unavailable", "SERVICE_UNAVAILABLE", 503))
                .when(authApiClient).resendVerification("user@example.com");

        mockMvc.perform(post("/resend-verification").param("email", "user@example.com"))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attributeExists("errorMessage"))
                // The acknowledgement must not travel alongside the failure.
                .andExpect(flashAttributeDoesNotExist("infoMessage"));
    }

    @Test
    void forgotPasswordAnswersTheSameWayWhateverTheAddress() throws Exception {
        mockMvc.perform(post("/forgot-password").param("email", "nobody@example.com"))
                .andExpect(status().isOk())
                .andExpect(view().name("public/forgot-password"))
                .andExpect(model().attributeExists("successMessage"));
    }

    @Test
    void mismatchedPasswordsAreCaughtBeforeSpendingTheResetToken() throws Exception {
        mockMvc.perform(post("/reset-password")
                        .param("token", "reset-token")
                        .param("newPassword", "NewP@ssw0rd")
                        .param("confirmPassword", "DifferentP@ss1"))
                .andExpect(status().isOk())
                .andExpect(model().attributeExists("errorMessage"))
                // The token has to survive the round trip, or correcting the typo means fetching a
                // whole new link from the mailbox.
                .andExpect(model().attribute("token", "reset-token"));

        verify(authApiClient, org.mockito.Mockito.never()).resetPassword(anyString(), anyString());
    }

    @Test
    void aSuccessfulResetDropsTheTokenFromTheModel() throws Exception {
        mockMvc.perform(post("/reset-password")
                        .param("token", "reset-token")
                        .param("newPassword", "NewP@ssw0rd")
                        .param("confirmPassword", "NewP@ssw0rd"))
                .andExpect(status().isOk())
                .andExpect(model().attributeExists("successMessage"))
                // Absent rather than blank: the page shows its confirmation state instead of a
                // form still holding a token that has now been spent.
                .andExpect(model().attributeDoesNotExist("token"));

        verify(authApiClient).resetPassword("reset-token", "NewP@ssw0rd");
    }

    @Test
    void confirmingAnEmailChangeClearsTheSessionBecauseTheSignInIdentityMoved() throws Exception {
        mockMvc.perform(get("/verify-email-change").param("token", "change-token"))
                .andExpect(status().isOk())
                .andExpect(view().name("public/verify-email-change"))
                .andExpect(model().attributeExists("successMessage"));

        verify(userApiClient).verifyEmailChange("change-token");
        verify(sessionManager).clearSession(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void aFailedEmailChangeLeavesTheSessionAlone() throws Exception {
        doThrow(new BackendApiException("This confirmation link has expired.", "CONFLICT", 409))
                .when(userApiClient).verifyEmailChange("stale-token");

        mockMvc.perform(get("/verify-email-change").param("token", "stale-token"))
                .andExpect(status().isOk())
                .andExpect(model().attributeExists("errorMessage"));

        verify(sessionManager, org.mockito.Mockito.never()).clearSession(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void openingTheEmailChangePageWithoutALinkExplainsWhatIsMissing() throws Exception {
        mockMvc.perform(get("/verify-email-change"))
                .andExpect(status().isOk())
                .andExpect(model().attributeExists("errorMessage"));

        verify(userApiClient, org.mockito.Mockito.never()).verifyEmailChange(anyString());
    }

    /** {@code flash()} offers no negative assertion, and these tests turn on what is absent. */
    private static ResultMatcher flashAttributeDoesNotExist(String name) {
        return result -> assertFalse(result.getFlashMap().containsKey(name),
                "expected no flash attribute named '" + name + "'");
    }
}
