package com.linkflow.web.controller;

import com.linkflow.web.client.AnalyticsApiClient;
import com.linkflow.web.client.ApiCallHelper;
import com.linkflow.web.client.UrlApiClient;
import com.linkflow.web.dto.analytics.UrlAnalyticsResponse;
import com.linkflow.web.dto.common.PagedResponse;
import com.linkflow.web.dto.url.CreateUrlForm;
import com.linkflow.web.dto.url.UpdateUrlForm;
import com.linkflow.web.dto.url.UrlResponse;
import com.linkflow.web.dto.url.BulkCreateUrlForm;
import com.linkflow.web.dto.url.BulkCreateUrlResponse;
import com.linkflow.web.client.BackendApiException;
import java.util.ArrayList;
import java.util.Map;
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

    @GetMapping("/bulk")
    public String bulkCreateForm(Model model) {
        model.addAttribute("bulkCreateUrlForm", new BulkCreateUrlForm());
        model.addAttribute("pageTitle", "Bulk Create URLs");
        model.addAttribute("activeNav", "urls-bulk");
        return "user/url-bulk";
    }

    @PostMapping("/bulk")
    public String bulkCreate(@Valid @ModelAttribute("bulkCreateUrlForm") BulkCreateUrlForm form,
                             BindingResult bindingResult,
                             HttpSession session,
                             Model model) {
        if (bindingResult.hasErrors()) {
            model.addAttribute("pageTitle", "Bulk Create URLs");
            model.addAttribute("activeNav", "urls-bulk");
            return "user/url-bulk";
        }

        String urlsText = form.getUrlsText();
        String[] lines = urlsText.split("\\r?\\n");
        List<Map<String, Object>> requestUrls = new ArrayList<>();
        
        for (String line : lines) {
            String trimmed = line.trim();
            if (!trimmed.isEmpty()) {
                requestUrls.add(Map.of("originalUrl", trimmed));
            }
        }

        if (requestUrls.isEmpty()) {
            bindingResult.rejectValue("urlsText", "error.bulkCreateUrlForm", "Please enter at least one valid URL");
            model.addAttribute("pageTitle", "Bulk Create URLs");
            model.addAttribute("activeNav", "urls-bulk");
            return "user/url-bulk";
        }

        if (requestUrls.size() > 100) {
            bindingResult.rejectValue("urlsText", "error.bulkCreateUrlForm", "Maximum 100 URLs per bulk request");
            model.addAttribute("pageTitle", "Bulk Create URLs");
            model.addAttribute("activeNav", "urls-bulk");
            return "user/url-bulk";
        }

        try {
            String idempotencyKey = UUID.randomUUID().toString();
            BulkCreateUrlResponse response = apiCallHelper.withTokenRefresh(session, auth ->
                    urlApiClient.bulkCreate(auth.accessToken(), requestUrls, idempotencyKey)
            );
            model.addAttribute("results", response.urls());
            model.addAttribute("successMessage", "Successfully shortened " + response.count() + " URLs!");
        } catch (BackendApiException ex) {
            model.addAttribute("errorMessage", "Failed to create URLs: " + ex.getMessage());
        }

        model.addAttribute("pageTitle", "Bulk Create URLs");
        model.addAttribute("activeNav", "urls-bulk");
        return "user/url-bulk";
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
