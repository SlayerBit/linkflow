package com.linkflow.web.controller;

import com.linkflow.web.dto.auth.LoginForm;
import com.linkflow.web.dto.auth.RegisterForm;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;

@Controller
public class PublicController {

    @GetMapping("/")
    public String index(Model model) {
        model.addAttribute("pageTitle", "URL Shortener");
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

    @GetMapping("/verify-email")
    public String verifyEmail(@org.springframework.web.bind.annotation.RequestParam(required = false) String token,
                              @org.springframework.web.bind.annotation.RequestParam(required = false) String email,
                              Model model) {
        model.addAttribute("token", token != null ? token : "");
        model.addAttribute("email", email != null ? email : "");
        model.addAttribute("pageTitle", "Verify Email");
        return "public/verify-email";
    }

    @GetMapping("/forgot-password")
    public String forgotPassword(Model model) {
        model.addAttribute("pageTitle", "Forgot Password");
        return "public/forgot-password";
    }

    @GetMapping("/reset-password")
    public String resetPassword(@org.springframework.web.bind.annotation.RequestParam(required = false) String token,
                                Model model) {
        model.addAttribute("token", token != null ? token : "");
        model.addAttribute("pageTitle", "Reset Password");
        return "public/reset-password";
    }

    @GetMapping("/verify-email-change")
    public String verifyEmailChange(@org.springframework.web.bind.annotation.RequestParam(required = false) String token,
                                    Model model) {
        model.addAttribute("token", token != null ? token : "");
        model.addAttribute("pageTitle", "Verify Email Change");
        return "public/verify-email-change";
    }
}
