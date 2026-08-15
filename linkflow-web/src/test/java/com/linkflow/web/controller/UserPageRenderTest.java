package com.linkflow.web.controller;

import com.linkflow.web.client.ActuatorApiClient;
import com.linkflow.web.client.AnalyticsApiClient;
import com.linkflow.web.client.ApiCallHelper;
import com.linkflow.web.client.AuthApiClient;
import com.linkflow.web.client.UrlApiClient;
import com.linkflow.web.client.UserApiClient;
import com.linkflow.web.config.ThymeleafConfig;
import com.linkflow.web.config.WebClientConfig;
import com.linkflow.web.security.ContentSecurityPolicyFilter;
import com.linkflow.web.security.SessionAuthFilter;
import com.linkflow.web.security.WebSecurityConfig;
import com.linkflow.web.session.AuthState;
import com.linkflow.web.session.SessionManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityFilterAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.function.Function;

import static com.linkflow.web.controller.PageRenderFixtures.URL_ID;
import static com.linkflow.web.controller.PageRenderFixtures.USER_AUTH;
import static com.linkflow.web.controller.PageRenderFixtures.analytics;
import static com.linkflow.web.controller.PageRenderFixtures.emptyPage;
import static com.linkflow.web.controller.PageRenderFixtures.url;
import static com.linkflow.web.controller.PageRenderFixtures.user;
import static com.linkflow.web.controller.PageRenderFixtures.userSession;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@WebMvcTest(
        controllers = {
                DashboardController.class,
                UrlController.class,
                ProfileController.class,
                ToolsController.class
        },
        excludeAutoConfiguration = {
                SecurityAutoConfiguration.class,
                SecurityFilterAutoConfiguration.class,
                UserDetailsServiceAutoConfiguration.class
        },
        excludeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = {
                WebSecurityConfig.class,
                SessionAuthFilter.class,
                ContentSecurityPolicyFilter.class
        })
)
@Import(ThymeleafConfig.class)
@AutoConfigureMockMvc(addFilters = false)
class UserPageRenderTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ApiCallHelper apiCallHelper;
    @MockBean
    private UrlApiClient urlApiClient;
    @MockBean
    private AnalyticsApiClient analyticsApiClient;
    @MockBean
    private UserApiClient userApiClient;
    @MockBean
    private AuthApiClient authApiClient;
    @MockBean
    private SessionManager sessionManager;
    @MockBean
    private ActuatorApiClient actuatorApiClient;
    @MockBean
    private WebClientConfig webClientConfig;

    @BeforeEach
    void stubBackend() {
        when(apiCallHelper.withTokenRefresh(any(), any())).thenAnswer(invocation -> {
            Function<AuthState, ?> call = invocation.getArgument(1);
            return call.apply(USER_AUTH);
        });
        when(urlApiClient.listUserUrls(any(), anyInt(), anyInt(), any(), any())).thenReturn(emptyPage());
        when(analyticsApiClient.getTopUrls(any(), anyInt())).thenReturn(List.of());
        when(analyticsApiClient.getRecentClicks(any(), anyInt())).thenReturn(List.of());
        when(urlApiClient.getById(any(), eq(URL_ID))).thenReturn(url());
        when(analyticsApiClient.getUrlAnalytics(any(), eq(URL_ID))).thenReturn(analytics());
        when(analyticsApiClient.getClickTrend(any(), eq(URL_ID), anyInt())).thenReturn(List.of());
        when(userApiClient.getMe(any())).thenReturn(user());
        when(webClientConfig.getRateLimit()).thenReturn(new WebClientConfig.RateLimit());
    }

    @Test
    void dashboardRenders() throws Exception {
        mockMvc.perform(get("/dashboard").session(userSession()))
                .andExpect(status().isOk())
                .andExpect(view().name("user/dashboard"))
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML))
                .andExpect(content().string(containsString("Welcome")));
    }

    @Test
    void urlListRenders() throws Exception {
        mockMvc.perform(get("/urls").session(userSession()))
                .andExpect(status().isOk())
                .andExpect(view().name("user/urls"))
                .andExpect(content().string(containsString("My Links")));
    }

    @Test
    void createUrlFormRenders() throws Exception {
        mockMvc.perform(get("/urls/new").session(userSession()))
                .andExpect(status().isOk())
                .andExpect(view().name("user/url-new"))
                .andExpect(content().string(containsString("Create Short URL")));
    }

    @Test
    void bulkCreateFormRenders() throws Exception {
        mockMvc.perform(get("/urls/bulk").session(userSession()))
                .andExpect(status().isOk())
                .andExpect(view().name("user/url-bulk"))
                .andExpect(content().string(containsString("Bulk Create")));
    }

    @Test
    void urlDetailRenders() throws Exception {
        mockMvc.perform(get("/urls/{id}", URL_ID).session(userSession()))
                .andExpect(status().isOk())
                .andExpect(view().name("user/url-detail"))
                .andExpect(content().string(containsString("abc12")));
    }

    @Test
    void urlEditFormRenders() throws Exception {
        mockMvc.perform(get("/urls/{id}/edit", URL_ID).session(userSession()))
                .andExpect(status().isOk())
                .andExpect(view().name("user/url-edit"))
                .andExpect(content().string(containsString("Edit")));
    }

    @Test
    void urlAnalyticsRenders() throws Exception {
        mockMvc.perform(get("/urls/{id}/analytics", URL_ID).session(userSession()))
                .andExpect(status().isOk())
                .andExpect(view().name("user/url-analytics"))
                .andExpect(content().string(containsString("Analytics")));
    }

    @Test
    void profileRenders() throws Exception {
        mockMvc.perform(get("/profile").session(userSession()))
                .andExpect(status().isOk())
                .andExpect(view().name("user/profile"))
                .andExpect(content().string(containsString("My Profile")));
    }

    @Test
    void rateLimitDemoRendersSlidingWindowCopy() throws Exception {
        mockMvc.perform(get("/tools/rate-limit").session(userSession()))
                .andExpect(status().isOk())
                .andExpect(view().name("tools/rate-limit"))
                .andExpect(content().string(containsString("sliding-window")));
    }
}
