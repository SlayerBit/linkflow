package com.linkflow.web.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.linkflow.web.client.ActuatorApiClient;
import com.linkflow.web.client.AnalyticsApiClient;
import com.linkflow.web.client.ApiCallHelper;
import com.linkflow.web.client.UrlApiClient;
import com.linkflow.web.client.UserApiClient;
import com.linkflow.web.config.ThymeleafConfig;
import com.linkflow.web.config.WebClientConfig;
import com.linkflow.web.dto.analytics.SystemStatsResponse;
import com.linkflow.web.security.ContentSecurityPolicyFilter;
import com.linkflow.web.security.SessionAuthFilter;
import com.linkflow.web.security.WebSecurityConfig;
import com.linkflow.web.session.AuthState;
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

import static com.linkflow.web.controller.PageRenderFixtures.ADMIN_AUTH;
import static com.linkflow.web.controller.PageRenderFixtures.URL_ID;
import static com.linkflow.web.controller.PageRenderFixtures.USER_ID;
import static com.linkflow.web.controller.PageRenderFixtures.adminSession;
import static com.linkflow.web.controller.PageRenderFixtures.analytics;
import static com.linkflow.web.controller.PageRenderFixtures.emptyPage;
import static com.linkflow.web.controller.PageRenderFixtures.url;
import static com.linkflow.web.controller.PageRenderFixtures.user;
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
        controllers = AdminController.class,
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
class AdminPageRenderTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ApiCallHelper apiCallHelper;
    @MockBean
    private AnalyticsApiClient analyticsApiClient;
    @MockBean
    private UserApiClient userApiClient;
    @MockBean
    private UrlApiClient urlApiClient;
    @MockBean
    private ActuatorApiClient actuatorApiClient;
    @MockBean
    private WebClientConfig webClientConfig;

    @BeforeEach
    void stubBackend() throws Exception {
        when(apiCallHelper.withTokenRefresh(any(), any())).thenAnswer(invocation -> {
            Function<AuthState, ?> call = invocation.getArgument(1);
            return call.apply(ADMIN_AUTH);
        });
        when(analyticsApiClient.getSystemStats(any()))
                .thenReturn(new SystemStatsResponse(0, 0, 0, 0, 0, 0, 0));
        when(analyticsApiClient.getAdminTopUrls(any(), anyInt())).thenReturn(List.of());
        when(analyticsApiClient.getSystemRecentClicks(any(), anyInt())).thenReturn(List.of());
        when(analyticsApiClient.getSystemClickTrend(any(), anyInt())).thenReturn(List.of());
        when(userApiClient.listAdminUsers(any(), anyInt(), anyInt(), any(), any())).thenReturn(emptyPage());
        when(userApiClient.getAdminUser(any(), eq(USER_ID))).thenReturn(user());
        when(urlApiClient.listAdminUrls(any(), anyInt(), anyInt(), any(), any())).thenReturn(emptyPage());
        when(urlApiClient.getAdminUrlById(any(), eq(URL_ID))).thenReturn(url());
        when(analyticsApiClient.getAdminUrlAnalytics(any(), eq(URL_ID))).thenReturn(analytics());
        when(analyticsApiClient.getAdminClickTrend(any(), eq(URL_ID), anyInt())).thenReturn(List.of());
        when(analyticsApiClient.getAdminRecentClicks(any(), eq(URL_ID), anyInt())).thenReturn(List.of());
        when(webClientConfig.getRateLimit()).thenReturn(new WebClientConfig.RateLimit());
        when(webClientConfig.getGrafanaUrl()).thenReturn("http://localhost:3000");
        when(webClientConfig.getPrometheusUrl()).thenReturn("http://localhost:9090");
        when(webClientConfig.getPublicGatewayUrl()).thenReturn("http://localhost:8080");
        when(actuatorApiClient.getHealth()).thenReturn(new ObjectMapper().readTree(
                "{\"status\":\"UP\",\"components\":{\"db\":{\"status\":\"UP\"},\"redis\":{\"status\":\"UP\"}}}"
        ));
    }

    @Test
    void adminDashboardRenders() throws Exception {
        mockMvc.perform(get("/admin").session(adminSession()))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/dashboard"))
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML))
                .andExpect(content().string(containsString("Admin Dashboard")));
    }

    @Test
    void adminUsersRenders() throws Exception {
        mockMvc.perform(get("/admin/users").session(adminSession()))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/users"))
                .andExpect(content().string(containsString("Users Management")));
    }

    @Test
    void adminUserDetailRenders() throws Exception {
        mockMvc.perform(get("/admin/users/{id}", USER_ID).session(adminSession()))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/user-detail"))
                .andExpect(content().string(containsString("ada@example.com")));
    }

    @Test
    void adminUrlsRenders() throws Exception {
        mockMvc.perform(get("/admin/urls").session(adminSession()))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/urls"))
                .andExpect(content().string(containsString("All URLs")));
    }

    @Test
    void adminUrlDetailRenders() throws Exception {
        mockMvc.perform(get("/admin/urls/{id}", URL_ID).session(adminSession()))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/url-detail"))
                .andExpect(content().string(containsString("abc12")));
    }

    @Test
    void adminAnalyticsRenders() throws Exception {
        mockMvc.perform(get("/admin/analytics").session(adminSession()))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/analytics"))
                .andExpect(content().string(containsString("System Analytics")));
    }

    @Test
    void adminSystemRenders() throws Exception {
        mockMvc.perform(get("/admin/system").session(adminSession()))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/system"))
                .andExpect(content().string(containsString("System Health")));
    }
}
