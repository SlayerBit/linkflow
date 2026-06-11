package com.linkflow.web.controller;

import com.linkflow.web.client.ApiCallHelper;
import com.linkflow.web.client.UserApiClient;
import com.linkflow.web.dto.user.UpdateProfileForm;
import com.linkflow.web.dto.user.UserResponse;
import com.linkflow.web.session.AuthState;
import com.linkflow.web.session.SessionManager;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequiredArgsConstructor
public class ProfileController {

    private final ApiCallHelper apiCallHelper;
    private final UserApiClient userApiClient;
    private final SessionManager sessionManager;

    @GetMapping("/profile")
    public String profile(HttpSession session, Model model) {
        UserResponse user = apiCallHelper.withTokenRefresh(session, auth ->
                userApiClient.getMe(auth.accessToken())
        );
        UpdateProfileForm form = new UpdateProfileForm();
        form.setFirstName(user.firstName());
        form.setLastName(user.lastName());
        model.addAttribute("user", user);
        model.addAttribute("updateProfileForm", form);
        model.addAttribute("pageTitle", "Profile");
        model.addAttribute("activeNav", "profile");
        return "user/profile";
    }

    @PostMapping("/profile")
    public String updateProfile(@Valid @ModelAttribute UpdateProfileForm form,
                                BindingResult bindingResult,
                                HttpSession session,
                                Model model,
                                RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            UserResponse user = apiCallHelper.withTokenRefresh(session, auth ->
                    userApiClient.getMe(auth.accessToken())
            );
            model.addAttribute("user", user);
            model.addAttribute("pageTitle", "Profile");
            model.addAttribute("activeNav", "profile");
            return "user/profile";
        }

        UserResponse updated = apiCallHelper.withTokenRefresh(session, auth ->
                userApiClient.updateMe(auth.accessToken(), form.getFirstName(), form.getLastName())
        );

        AuthState current = sessionManager.getAuthState(session);
        long remainingSeconds = Math.max(0, current.expiresAt() - java.time.Instant.now().getEpochSecond());
        sessionManager.establishSession(
                session,
                current.accessToken(),
                current.refreshToken(),
                remainingSeconds,
                updated.email(),
                updated.firstName(),
                updated.lastName(),
                updated.roles()
        );

        redirectAttributes.addFlashAttribute("successMessage", "Profile updated successfully.");
        return "redirect:/profile";
    }
}
