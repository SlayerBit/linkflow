package com.linkflow.web.controller;

import com.linkflow.web.client.AuthApiClient;
import com.linkflow.web.client.UserApiClient;
import com.linkflow.web.config.ThymeleafConfig;
import com.linkflow.web.security.ContentSecurityPolicyFilter;
import com.linkflow.web.security.SessionAuthFilter;
import com.linkflow.web.security.WebSecurityConfig;
import com.linkflow.web.session.SessionManager;
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

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@WebMvcTest(
        controllers = {PublicController.class, AuthController.class},
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
class PublicPageRenderTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AuthApiClient authApiClient;
    @MockBean
    private UserApiClient userApiClient;
    @MockBean
    private SessionManager sessionManager;

    @Test
    void homeRenders() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(view().name("public/index"))
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML))
                .andExpect(content().string(containsString("LinkFlow")));
    }

    @Test
    void loginRenders() throws Exception {
        mockMvc.perform(get("/login"))
                .andExpect(status().isOk())
                .andExpect(view().name("public/login"))
                .andExpect(content().string(containsString("Sign in")));
    }

    @Test
    void registerRenders() throws Exception {
        mockMvc.perform(get("/register"))
                .andExpect(status().isOk())
                .andExpect(view().name("public/register"))
                .andExpect(content().string(containsString("Create account")));
    }

    @Test
    void checkEmailRenders() throws Exception {
        mockMvc.perform(get("/check-email").param("email", "ada@example.com"))
                .andExpect(status().isOk())
                .andExpect(view().name("public/check-email"))
                .andExpect(content().string(containsString("Check your email")))
                .andExpect(content().string(containsString("ada@example.com")));
    }

    @Test
    void forgotPasswordRenders() throws Exception {
        mockMvc.perform(get("/forgot-password"))
                .andExpect(status().isOk())
                .andExpect(view().name("public/forgot-password"))
                .andExpect(content().string(containsString("Forgot password")));
    }

    @Test
    void resetPasswordWithoutTokenRendersLinkNeededState() throws Exception {
        mockMvc.perform(get("/reset-password"))
                .andExpect(status().isOk())
                .andExpect(view().name("public/reset-password"))
                .andExpect(content().string(containsString("Link needed")));
    }

    @Test
    void verifyEmailWithoutTokenRendersResendForm() throws Exception {
        mockMvc.perform(get("/verify-email"))
                .andExpect(status().isOk())
                .andExpect(view().name("public/verify-email"))
                .andExpect(content().string(containsString("Verify your email")))
                .andExpect(content().string(containsString("Send a new link")));
    }

    @Test
    void verifyEmailChangeWithoutTokenRendersTheMissingLinkMessage() throws Exception {
        mockMvc.perform(get("/verify-email-change"))
                .andExpect(status().isOk())
                .andExpect(view().name("public/verify-email-change"))
                .andExpect(content().string(containsString("confirmation link")));
    }
}
