package com.linkflow.web.controller;

import com.linkflow.web.client.AuthApiClient;
import com.linkflow.web.client.BackendApiException;
import com.linkflow.web.client.UserApiClient;
import com.linkflow.web.dto.auth.LoginForm;
import com.linkflow.web.dto.auth.RegisterForm;
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
            model.addAttribute("errorMessage", ex.getMessage());
            model.addAttribute("loginForm", form);
            model.addAttribute("pageTitle", "Login");
            return "public/login";
        }
    }

    @PostMapping("/register")
    public String register(@Valid @ModelAttribute RegisterForm form,
                           BindingResult bindingResult,
                           Model model) {
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
            model.addAttribute("successMessage", "Registration successful. Please log in.");
            model.addAttribute("loginForm", new LoginForm());
            model.addAttribute("pageTitle", "Login");
            return "public/login";
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
}
