package com.linkflow.web.controller;

import com.linkflow.web.client.AuthApiClient;
import com.linkflow.web.client.BackendApiException;
import com.linkflow.web.client.UserApiClient;
import com.linkflow.web.dto.auth.LoginForm;
import com.linkflow.web.dto.auth.RegisterForm;
import com.linkflow.web.dto.auth.TokenResponse;
import com.linkflow.web.dto.user.UserResponse;
import com.linkflow.web.session.SessionManager;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

@Controller
@RequiredArgsConstructor
public class AuthController {

    private final AuthApiClient authApiClient;
    private final UserApiClient userApiClient;
    private final SessionManager sessionManager;

    @PostMapping("/login")
    public String login(@Valid @ModelAttribute LoginForm form,
                        BindingResult bindingResult,
                        HttpServletRequest request,
                        Model model) {
        if (bindingResult.hasErrors()) {
            model.addAttribute("loginForm", form);
            model.addAttribute("pageTitle", "Login");
            return "public/login";
        }

        try {
            TokenResponse tokens = authApiClient.login(form.getEmail(), form.getPassword());
            UserResponse user = userApiClient.getMe(tokens.accessToken());
            sessionManager.establishSession(
                    request,
                    tokens.accessToken(),
                    tokens.refreshToken(),
                    tokens.expiresIn(),
                    user.email(),
                    user.firstName(),
                    user.lastName(),
                    user.roles()
            );
            return "redirect:/dashboard";
        } catch (BackendApiException ex) {
            if ("EMAIL_NOT_VERIFIED".equals(ex.getErrorCode())) {
                model.addAttribute("errorMessage", "Your email is not verified. Please verify your email before logging in.");
                model.addAttribute("showVerifyLink", true);
                model.addAttribute("loginForm", form);
                model.addAttribute("pageTitle", "Login");
                return "public/login";
            }
            model.addAttribute("errorMessage", ex.getMessage());
            model.addAttribute("loginForm", form);
            model.addAttribute("pageTitle", "Login");
            return "public/login";
        }
    }

    @PostMapping("/register")
    public String register(@Valid @ModelAttribute RegisterForm form,
                           BindingResult bindingResult,
                           Model model,
                           RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            model.addAttribute("registerForm", form);
            model.addAttribute("pageTitle", "Register");
            return "public/register";
        }

        try {
            authApiClient.register(
                    form.getEmail(),
                    form.getPassword(),
                    form.getFirstName(),
                    form.getLastName()
            );
            return "redirect:/check-email?email="
                    + URLEncoder.encode(form.getEmail(), StandardCharsets.UTF_8);
        } catch (BackendApiException ex) {
            model.addAttribute("errorMessage", ex.getMessage());
            model.addAttribute("registerForm", form);
            model.addAttribute("pageTitle", "Register");
            return "public/register";
        }
    }

    @PostMapping("/logout")
    public String logout(HttpSession session) {
        var authState = sessionManager.getAuthState(session);
        if (authState != null) {
            try {
                authApiClient.logout(authState.accessToken(), authState.refreshToken());
            } catch (BackendApiException ignored) {
            }
        }
        sessionManager.clearSession(session);
        return "redirect:/";
    }

    /**
     * Lands the emailed activation link. Verification happens on GET because that is what a user
     * clicking a link expects; the alternative — rendering a confirm button — protects against
     * link-prefetching mail clients but costs an extra step. The backend absorbs the prefetch case
     * instead, by treating an already-verified account as success.
     */
    @GetMapping("/verify-email")
    public String verifyEmail(@RequestParam(required = false) String token,
                              @RequestParam(required = false) String email,
                              Model model) {
        model.addAttribute("pageTitle", "Verify Email");

        // A query parameter wins, but must not overwrite an address carried here as a flash
        // attribute by the resend below — flash attributes are already merged into the model at
        // this point, and unconditionally setting the parameter would blank the form.
        if (email != null && !email.isBlank()) {
            model.addAttribute("email", email);
        } else if (!model.containsAttribute("email")) {
            model.addAttribute("email", "");
        }

        // Reached without a link, e.g. straight from the sign-in page prompt, or redirected here
        // after a resend. Either way the page just offers the resend form.
        if (token == null || token.isBlank()) {
            return "public/verify-email";
        }

        try {
            authApiClient.verifyEmail(token);
            // Distinct from a generic success message: the template keys the whole "account
            // activated" state off this flag, so an unrelated notice cannot claim the email is
            // confirmed when it is not.
            model.addAttribute("verified", true);
        } catch (BackendApiException ex) {
            model.addAttribute("errorMessage", ex.getMessage());
        }
        return "public/verify-email";
    }

    /**
     * The address is carried back as a flash attribute rather than a redirect query parameter.
     * <p>
     * Both would repopulate the form, but a query parameter also breaks the message: an email
     * address contains an {@code @}, which is percent-encoded into the redirect URL, while Spring
     * matches a saved flash map against the <em>decoded</em> parameters of the following request.
     * The two never compare equal, the flash map is discarded as belonging to some other request,
     * and the acknowledgement disappears without a trace — the user is left looking at an unchanged
     * page, with no way to tell whether anything was sent.
     * <p>
     * Keeping the address out of the URL is worth having in its own right. It would otherwise
     * persist in browser history and be handed to any third-party asset the page loads via the
     * Referer header.
     */
    @PostMapping("/resend-verification")
    public String resendVerification(@RequestParam String email,
                                     RedirectAttributes redirectAttributes) {
        redirectAttributes.addFlashAttribute("email", email);
        try {
            authApiClient.resendVerification(email);
        } catch (BackendApiException ex) {
            // The backend answers uniformly for unknown addresses, so a failure here is a genuine
            // fault rather than "no such account" — surface it instead of claiming success.
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
            return "redirect:/verify-email";
        }
        // An acknowledgement, not a confirmation. Worded to match the backend, which sends nothing
        // for unknown or already-verified addresses and cannot say so without leaking which.
        redirectAttributes.addFlashAttribute("infoMessage",
                "If that address needs verifying, a new link is on its way. "
                        + "It can take a minute to arrive — check your spam folder too.");
        return "redirect:/verify-email";
    }

    @PostMapping("/forgot-password")
    public String forgotPassword(@RequestParam String email, Model model) {
        try {
            authApiClient.forgotPassword(email);
            // Deliberately identical whether or not the address is registered.
            model.addAttribute("successMessage",
                    "If an account exists for that address, a reset link is on its way. "
                            + "The link expires in 15 minutes.");
        } catch (BackendApiException ex) {
            model.addAttribute("errorMessage", ex.getMessage());
        }
        model.addAttribute("pageTitle", "Forgot Password");
        return "public/forgot-password";
    }

    @PostMapping("/reset-password")
    public String resetPassword(@RequestParam String token,
                                @RequestParam String newPassword,
                                @RequestParam String confirmPassword,
                                Model model) {
        if (!newPassword.equals(confirmPassword)) {
            model.addAttribute("errorMessage", "Passwords do not match.");
            model.addAttribute("token", token);
            model.addAttribute("pageTitle", "Reset Password");
            return "public/reset-password";
        }
        try {
            authApiClient.resetPassword(token, newPassword);
            model.addAttribute("successMessage", "Password reset successfully! You can now log in with your new password.");
        } catch (BackendApiException ex) {
            model.addAttribute("errorMessage", ex.getMessage());
            model.addAttribute("token", token);
        }
        model.addAttribute("pageTitle", "Reset Password");
        return "public/reset-password";
    }

    /**
     * Lands the confirmation link sent to the new address. The session is cleared on success
     * because the account's sign-in identity has changed and every token was revoked server-side.
     */
    @GetMapping("/verify-email-change")
    public String verifyEmailChange(@RequestParam(required = false) String token,
                                    Model model,
                                    HttpSession session) {
        model.addAttribute("pageTitle", "Verify Email Change");

        if (token == null || token.isBlank()) {
            model.addAttribute("errorMessage",
                    "This page needs the confirmation link from your new email address.");
            return "public/verify-email-change";
        }

        try {
            userApiClient.verifyEmailChange(token);
            model.addAttribute("successMessage",
                    "Your email is updated. Please sign in again with the new address.");
            sessionManager.clearSession(session);
        } catch (BackendApiException ex) {
            model.addAttribute("errorMessage", ex.getMessage());
        }
        return "public/verify-email-change";
    }
}

