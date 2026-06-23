package com.linkflow.web.controller;

import com.linkflow.web.client.AuthApiClient;
import com.linkflow.web.client.BackendApiException;
import com.linkflow.web.client.UserApiClient;
import com.linkflow.web.dto.auth.LoginForm;
import com.linkflow.web.dto.auth.RegisterForm;
import com.linkflow.web.dto.auth.RegisterResponse;
import com.linkflow.web.dto.auth.TokenResponse;
import com.linkflow.web.dto.user.UserResponse;
import com.linkflow.web.session.SessionManager;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequiredArgsConstructor
public class AuthController {

    private final AuthApiClient authApiClient;
    private final UserApiClient userApiClient;
    private final SessionManager sessionManager;

    @PostMapping("/login")
    public String login(@Valid @ModelAttribute LoginForm form,
                        BindingResult bindingResult,
                        HttpSession session,
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
                    session,
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
            RegisterResponse response = authApiClient.register(
                    form.getEmail(),
                    form.getPassword(),
                    form.getFirstName(),
                    form.getLastName()
            );
            // Redirect to verify-email page with simulation token
            return "redirect:/verify-email?token=" + (response.verificationToken() != null ? response.verificationToken() : "")
                    + "&email=" + form.getEmail();
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

    @PostMapping("/verify-email")
    public String verifyEmail(@RequestParam String token, Model model) {
        try {
            authApiClient.verifyEmail(token);
            model.addAttribute("successMessage", "Email verified successfully! You can now log in.");
        } catch (BackendApiException ex) {
            model.addAttribute("errorMessage", ex.getMessage());
            model.addAttribute("token", token);
        }
        model.addAttribute("pageTitle", "Verify Email");
        return "public/verify-email";
    }

    @PostMapping("/forgot-password")
    public String forgotPassword(@RequestParam String email, Model model) {
        try {
            String resetToken = authApiClient.forgotPassword(email);
            // Simulation: show the token in the UI banner
            model.addAttribute("resetToken", resetToken);
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

    @PostMapping("/verify-email-change")
    public String verifyEmailChange(@RequestParam String token, Model model, HttpSession session) {
        try {
            userApiClient.verifyEmailChange(token);
            model.addAttribute("successMessage", "Email updated successfully! Please log back in.");
            sessionManager.clearSession(session);
        } catch (BackendApiException ex) {
            model.addAttribute("errorMessage", ex.getMessage());
            model.addAttribute("token", token);
        }
        model.addAttribute("pageTitle", "Verify Email Change");
        return "public/verify-email-change";
    }
}

