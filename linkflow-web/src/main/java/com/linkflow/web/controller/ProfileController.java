package com.linkflow.web.controller;

import com.linkflow.web.client.ApiCallHelper;
import com.linkflow.web.client.UserApiClient;
import com.linkflow.web.client.AuthApiClient;
import com.linkflow.web.client.BackendApiException;
import com.linkflow.web.dto.user.UpdateProfileForm;
import com.linkflow.web.dto.user.ChangePasswordForm;
import com.linkflow.web.dto.user.RequestEmailChangeForm;
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

import com.linkflow.web.client.AnalyticsApiClient;
import com.linkflow.web.client.UrlApiClient;
import com.linkflow.web.dto.analytics.TopUrlResponse;
import com.linkflow.web.dto.common.PagedResponse;
import com.linkflow.web.dto.url.UrlResponse;
import java.util.List;

@Controller
@RequiredArgsConstructor
public class ProfileController {

        private final ApiCallHelper apiCallHelper;
        private final UserApiClient userApiClient;
        private final AuthApiClient authApiClient;
        private final SessionManager sessionManager;
        private final UrlApiClient urlApiClient;
        private final AnalyticsApiClient analyticsApiClient;

        @GetMapping("/profile")
        public String profile(HttpSession session, Model model) {
                UserResponse user = apiCallHelper.withTokenRefresh(session,
                                auth -> userApiClient.getMe(auth.accessToken()));
                UpdateProfileForm form = new UpdateProfileForm();
                form.setFirstName(user.firstName());
                form.setLastName(user.lastName());

                addProfileStats(session, model);

                model.addAttribute("user", user);
                model.addAttribute("updateProfileForm", form);

                if (!model.containsAttribute("changePasswordForm")) {
                        model.addAttribute("changePasswordForm", new ChangePasswordForm());
                }
                if (!model.containsAttribute("requestEmailChangeForm")) {
                        model.addAttribute("requestEmailChangeForm", new RequestEmailChangeForm());
                }

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
                        UserResponse user = apiCallHelper.withTokenRefresh(session,
                                        auth -> userApiClient.getMe(auth.accessToken()));
                        addProfileStats(session, model);
                        model.addAttribute("user", user);
                        model.addAttribute("pageTitle", "Profile");
                        model.addAttribute("activeNav", "profile");
                        return "user/profile";
                }

                UserResponse updated = apiCallHelper.withTokenRefresh(session, auth -> userApiClient
                                .updateMe(auth.accessToken(), form.getFirstName(), form.getLastName()));

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
                                updated.roles());

                redirectAttributes.addFlashAttribute("successMessage", "Profile updated successfully.");
                return "redirect:/profile";
        }

        private void addProfileStats(HttpSession session, Model model) {
                PagedResponse<UrlResponse> userUrlsResponse = apiCallHelper.withTokenRefresh(session,
                                auth -> urlApiClient.listUserUrls(auth.accessToken(), 0, 1000, "createdAt", "desc"));
                List<UrlResponse> userUrls = userUrlsResponse.content();
                long totalUrls = userUrlsResponse.totalElements();

                long activeUrls = userUrls.stream()
                                .filter(u -> u.active() && (u.expiresAt() == null
                                                || u.expiresAt().isAfter(java.time.Instant.now())))
                                .count();

                List<TopUrlResponse> topUrls = apiCallHelper.withTokenRefresh(session,
                                auth -> analyticsApiClient.getTopUrls(auth.accessToken(), 100));
                long totalClicks = topUrls.stream()
                                .mapToLong(TopUrlResponse::totalClicks)
                                .sum();

                model.addAttribute("totalUrls", totalUrls);
                model.addAttribute("activeUrls", activeUrls);
                model.addAttribute("totalClicks", totalClicks);
        }

        @PostMapping("/profile/change-password")
        public String changePassword(@Valid @ModelAttribute("changePasswordForm") ChangePasswordForm form,
                        BindingResult bindingResult,
                        HttpSession session,
                        Model model,
                        RedirectAttributes redirectAttributes) {
                if (bindingResult.hasErrors()) {
                        UserResponse user = apiCallHelper.withTokenRefresh(session,
                                        auth -> userApiClient.getMe(auth.accessToken()));
                        addProfileStats(session, model);

                        UpdateProfileForm profileForm = new UpdateProfileForm();
                        profileForm.setFirstName(user.firstName());
                        profileForm.setLastName(user.lastName());

                        model.addAttribute("user", user);
                        model.addAttribute("updateProfileForm", profileForm);
                        model.addAttribute("requestEmailChangeForm", new RequestEmailChangeForm());
                        model.addAttribute("pageTitle", "Profile");
                        model.addAttribute("activeNav", "profile");
                        return "user/profile";
                }

                if (!form.getNewPassword().equals(form.getConfirmPassword())) {
                        bindingResult.rejectValue("confirmPassword", "error.changePasswordForm",
                                        "Passwords do not match");

                        UserResponse user = apiCallHelper.withTokenRefresh(session,
                                        auth -> userApiClient.getMe(auth.accessToken()));
                        addProfileStats(session, model);

                        UpdateProfileForm profileForm = new UpdateProfileForm();
                        profileForm.setFirstName(user.firstName());
                        profileForm.setLastName(user.lastName());

                        model.addAttribute("user", user);
                        model.addAttribute("updateProfileForm", profileForm);
                        model.addAttribute("requestEmailChangeForm", new RequestEmailChangeForm());
                        model.addAttribute("pageTitle", "Profile");
                        model.addAttribute("activeNav", "profile");
                        return "user/profile";
                }

                try {
                        apiCallHelper.withTokenRefresh(session, auth -> {
                                authApiClient.changePassword(auth.accessToken(), form.getCurrentPassword(),
                                                form.getNewPassword());
                                return null;
                        });
                        sessionManager.clearSession(session);
                        redirectAttributes.addFlashAttribute("successMessage",
                                        "Password changed successfully. Please log in with your new password.");
                        return "redirect:/login";
                } catch (BackendApiException e) {
                        bindingResult.rejectValue("currentPassword", "error.changePasswordForm", e.getMessage());

                        UserResponse user = apiCallHelper.withTokenRefresh(session,
                                        auth -> userApiClient.getMe(auth.accessToken()));
                        addProfileStats(session, model);

                        UpdateProfileForm profileForm = new UpdateProfileForm();
                        profileForm.setFirstName(user.firstName());
                        profileForm.setLastName(user.lastName());

                        model.addAttribute("user", user);
                        model.addAttribute("updateProfileForm", profileForm);
                        model.addAttribute("requestEmailChangeForm", new RequestEmailChangeForm());
                        model.addAttribute("pageTitle", "Profile");
                        model.addAttribute("activeNav", "profile");
                        return "user/profile";
                }
        }

        @PostMapping("/profile/request-email-change")
        public String requestEmailChange(@Valid @ModelAttribute("requestEmailChangeForm") RequestEmailChangeForm form,
                        BindingResult bindingResult,
                        HttpSession session,
                        Model model,
                        RedirectAttributes redirectAttributes) {
                if (bindingResult.hasErrors()) {
                        UserResponse user = apiCallHelper.withTokenRefresh(session,
                                        auth -> userApiClient.getMe(auth.accessToken()));
                        addProfileStats(session, model);

                        UpdateProfileForm profileForm = new UpdateProfileForm();
                        profileForm.setFirstName(user.firstName());
                        profileForm.setLastName(user.lastName());

                        model.addAttribute("user", user);
                        model.addAttribute("updateProfileForm", profileForm);
                        model.addAttribute("changePasswordForm", new ChangePasswordForm());
                        model.addAttribute("pageTitle", "Profile");
                        model.addAttribute("activeNav", "profile");
                        return "user/profile";
                }

                try {
                        String token = apiCallHelper.withTokenRefresh(session,
                                        auth -> userApiClient.requestEmailChange(auth.accessToken(),
                                                        form.getCurrentPassword(), form.getNewEmail()));
                        redirectAttributes.addFlashAttribute("emailChangeToken", token);
                        redirectAttributes.addFlashAttribute("emailChangeNewEmail", form.getNewEmail());
                        redirectAttributes.addFlashAttribute("successMessage",
                                        "Email change request generated. Verify using the token in the banner.");
                } catch (BackendApiException e) {
                        bindingResult.rejectValue("currentPassword", "error.requestEmailChangeForm", e.getMessage());

                        UserResponse user = apiCallHelper.withTokenRefresh(session,
                                        auth -> userApiClient.getMe(auth.accessToken()));
                        addProfileStats(session, model);

                        UpdateProfileForm profileForm = new UpdateProfileForm();
                        profileForm.setFirstName(user.firstName());
                        profileForm.setLastName(user.lastName());

                        model.addAttribute("user", user);
                        model.addAttribute("updateProfileForm", profileForm);
                        model.addAttribute("changePasswordForm", new ChangePasswordForm());
                        model.addAttribute("pageTitle", "Profile");
                        model.addAttribute("activeNav", "profile");
                        return "user/profile";
                }

                return "redirect:/profile";
        }
}
