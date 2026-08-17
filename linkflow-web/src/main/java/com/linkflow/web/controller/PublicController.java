package com.linkflow.web.controller;

import com.linkflow.web.dto.auth.LoginForm;
import com.linkflow.web.dto.auth.RegisterForm;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * Serves the unauthenticated pages that carry no side effects. Flows that call the backend —
 * verification, password reset, email change — live in {@link AuthController}.
 */
@Controller
public class PublicController {

    @GetMapping("/")
    public String index(Model model) {
        model.addAttribute("pageTitle", "URL infrastructure");
        model.addAttribute("fullBleed", true);
        return "public/index";
    }

    @GetMapping("/login")
    public String login(@ModelAttribute("loginForm") LoginForm loginForm, Model model) {
        model.addAttribute("pageTitle", "Login");
        return "public/login";
    }

    @GetMapping("/register")
    public String register(@ModelAttribute("registerForm") RegisterForm registerForm, Model model) {
        model.addAttribute("pageTitle", "Register");
        return "public/register";
    }

    /**
     * Shown straight after registration. The address is echoed so a user who mistyped it notices
     * before waiting on a message that will never arrive.
     */
    @GetMapping("/check-email")
    public String checkEmail(@RequestParam(required = false) String email, Model model) {
        model.addAttribute("email", email != null ? email : "");
        model.addAttribute("pageTitle", "Check your email");
        return "public/check-email";
    }

    @GetMapping("/forgot-password")
    public String forgotPassword(Model model) {
        model.addAttribute("pageTitle", "Forgot Password");
        return "public/forgot-password";
    }

    /**
     * Renders the new-password form. The token is only consumed when the form is submitted, so
     * a mail client prefetching this link cannot spend it.
     */
    @GetMapping("/reset-password")
    public String resetPassword(@RequestParam(required = false) String token, Model model) {
        model.addAttribute("token", token != null ? token : "");
        model.addAttribute("pageTitle", "Reset Password");
        return "public/reset-password";
    }
}
