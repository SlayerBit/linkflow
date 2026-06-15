package com.linkflow.web.controller;

import com.linkflow.web.client.AnalyticsApiClient;
import com.linkflow.web.client.ApiCallHelper;
import com.linkflow.web.client.UrlApiClient;
import com.linkflow.web.dto.analytics.UrlAnalyticsResponse;
import com.linkflow.web.dto.common.PagedResponse;
import com.linkflow.web.dto.url.CreateUrlForm;
import com.linkflow.web.dto.url.UpdateUrlForm;
import com.linkflow.web.dto.url.UrlResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import java.util.List;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

@Controller
@RequestMapping("/urls")
@RequiredArgsConstructor
public class UrlController {

    private final ApiCallHelper apiCallHelper;
    private final UrlApiClient urlApiClient;
    private final AnalyticsApiClient analyticsApiClient;
    private final ObjectMapper objectMapper;

    @GetMapping
    public String list(@RequestParam(defaultValue = "0") int page,
                       @RequestParam(defaultValue = "20") int size,
                       @RequestParam(defaultValue = "createdAt") String sortBy,
                       @RequestParam(defaultValue = "desc") String direction,
                       HttpSession session,
                       Model model) {
        PagedResponse<UrlResponse> urls = apiCallHelper.withTokenRefresh(session, auth ->
                urlApiClient.listUserUrls(auth.accessToken(), page, size, sortBy, direction)
        );
        model.addAttribute("urls", urls);
        model.addAttribute("sortBy", sortBy);
        model.addAttribute("direction", direction);
        model.addAttribute("pageTitle", "My URLs");
        model.addAttribute("activeNav", "urls");
        return "user/urls";
    }

    @GetMapping("/new")
    public String createForm(Model model) {
        model.addAttribute("createUrlForm", new CreateUrlForm());
        model.addAttribute("pageTitle", "Create URL");
        model.addAttribute("activeNav", "urls");
        return "user/url-new";
    }

    @PostMapping("/new")
    public String create(@Valid @ModelAttribute CreateUrlForm form,
                         BindingResult bindingResult,
                         HttpSession session,
                         Model model,
                         RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            model.addAttribute("pageTitle", "Create URL");
            model.addAttribute("activeNav", "urls");
            return "user/url-new";
        }

        Instant expiresAt = form.getExpiresAt() != null
                ? form.getExpiresAt().toInstant(ZoneOffset.UTC)
                : null;
        String idempotencyKey = UUID.randomUUID().toString();

        UrlResponse created = apiCallHelper.withTokenRefresh(session, auth ->
                urlApiClient.create(
                        auth.accessToken(),
                        form.getOriginalUrl(),
                        form.getCustomAlias(),
                        expiresAt,
                        idempotencyKey
                )
        );
        redirectAttributes.addFlashAttribute("successMessage", "URL created successfully.");
        return "redirect:/urls/" + created.id();
    }

    @GetMapping("/{id}")
    public String detail(@PathVariable UUID id, HttpSession session, Model model) {
        UrlResponse url = apiCallHelper.withTokenRefresh(session, auth ->
                urlApiClient.getById(auth.accessToken(), id)
        );
        UrlAnalyticsResponse analytics = apiCallHelper.withTokenRefresh(session, auth ->
                analyticsApiClient.getUrlAnalytics(auth.accessToken(), id)
        );
        model.addAttribute("url", url);
        model.addAttribute("analytics", analytics);
        model.addAttribute("pageTitle", "URL Details");
        model.addAttribute("activeNav", "urls");
        return "user/url-detail";
    }

    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable UUID id, HttpSession session, Model model) {
        UrlResponse url = apiCallHelper.withTokenRefresh(session, auth ->
                urlApiClient.getById(auth.accessToken(), id)
        );
        UpdateUrlForm form = new UpdateUrlForm();
        if (url.expiresAt() != null) {
            form.setExpiresAt(url.expiresAt().atZone(ZoneOffset.UTC).toLocalDateTime());
        }
        form.setActive(url.active());
        model.addAttribute("updateUrlForm", form);
        model.addAttribute("url", url);
        model.addAttribute("pageTitle", "Edit URL");
        model.addAttribute("activeNav", "urls");
        return "user/url-edit";
    }

    @PostMapping("/{id}/edit")
    public String edit(@PathVariable UUID id,
                       @ModelAttribute UpdateUrlForm form,
                       HttpSession session,
                       RedirectAttributes redirectAttributes) {
        Instant expiresAt = form.getExpiresAt() != null
                ? form.getExpiresAt().toInstant(ZoneOffset.UTC)
                : null;
        apiCallHelper.withTokenRefresh(session, auth ->
                urlApiClient.update(auth.accessToken(), id, expiresAt, form.isActive())
        );
        redirectAttributes.addFlashAttribute("successMessage", "URL updated successfully.");
        return "redirect:/urls/" + id;
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable UUID id,
                         HttpSession session,
                         RedirectAttributes redirectAttributes) {
        apiCallHelper.withTokenRefresh(session, auth -> {
            urlApiClient.delete(auth.accessToken(), id);
            return null;
        });
        redirectAttributes.addFlashAttribute("successMessage", "URL deleted successfully.");
        return "redirect:/urls";
    }

    @PostMapping("/{id}/reactivate")
    public String reactivate(@PathVariable UUID id,
                             HttpSession session,
                             RedirectAttributes redirectAttributes) {
        apiCallHelper.withTokenRefresh(session, auth ->
                urlApiClient.reactivate(auth.accessToken(), id)
        );
        redirectAttributes.addFlashAttribute("successMessage", "URL reactivated successfully.");
        return "redirect:/urls/" + id;
    }

    @GetMapping("/{id}/analytics")
    public String analytics(@PathVariable UUID id, HttpSession session, Model model) throws JsonProcessingException {
        UrlResponse url = apiCallHelper.withTokenRefresh(session, auth ->
                urlApiClient.getById(auth.accessToken(), id)
        );
        UrlAnalyticsResponse analytics = apiCallHelper.withTokenRefresh(session, auth ->
                analyticsApiClient.getUrlAnalytics(auth.accessToken(), id)
        );
        List<com.linkflow.web.dto.analytics.ClickTrendResponse> trend7d = apiCallHelper.withTokenRefresh(session, auth ->
                analyticsApiClient.getClickTrend(auth.accessToken(), id, 7)
        );
        List<com.linkflow.web.dto.analytics.ClickTrendResponse> trend30d = apiCallHelper.withTokenRefresh(session, auth ->
                analyticsApiClient.getClickTrend(auth.accessToken(), id, 30)
        );
        List<com.linkflow.web.dto.analytics.ClickTrendResponse> trend90d = apiCallHelper.withTokenRefresh(session, auth ->
                analyticsApiClient.getClickTrend(auth.accessToken(), id, 90)
        );

        model.addAttribute("url", url);
        model.addAttribute("analytics", analytics);
        model.addAttribute("trend7dJson", objectMapper.writeValueAsString(trend7d));
        model.addAttribute("trend30dJson", objectMapper.writeValueAsString(trend30d));
        model.addAttribute("trend90dJson", objectMapper.writeValueAsString(trend90d));
        model.addAttribute("pageTitle", "URL Analytics");
        model.addAttribute("activeNav", "urls");
        return "user/url-analytics";
    }

    @GetMapping("/{id}/qr-proxy")
    public ResponseEntity<byte[]> qrProxy(@PathVariable UUID id, HttpSession session) {
        byte[] png = apiCallHelper.withTokenRefresh(session, auth ->
                urlApiClient.getQrCode(auth.accessToken(), id)
        );
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, MediaType.IMAGE_PNG_VALUE)
                .body(png);
    }
}
