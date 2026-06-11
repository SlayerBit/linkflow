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
}
