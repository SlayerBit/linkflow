package com.linkflow.web.controller;

import com.linkflow.web.config.ThymeleafConfig;
import com.linkflow.web.security.ContentSecurityPolicyFilter;
import com.linkflow.web.security.SessionAuthFilter;
import com.linkflow.web.security.WebSecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityFilterAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@WebMvcTest(
        controllers = ErrorPageRenderTest.ErrorViews.class,
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
@Import({ThymeleafConfig.class, ErrorPageRenderTest.ErrorViews.class})
@AutoConfigureMockMvc(addFilters = false)
class ErrorPageRenderTest {

    @Autowired
    private MockMvc mockMvc;

    @Controller
    static class ErrorViews {
        @GetMapping("/test-error/404")
        String notFound() {
            return "error/404";
        }

        @GetMapping("/test-error/500")
        String serverError() {
            return "error/500";
        }

        @GetMapping("/test-error/error")
        String fallback(Model model) {
            model.addAttribute("status", 418);
            model.addAttribute("error", "I'm a teapot");
            return "error/error";
        }
    }

    @Test
    void notFoundPageRenders() throws Exception {
        mockMvc.perform(get("/test-error/404"))
                .andExpect(status().isOk())
                .andExpect(view().name("error/404"))
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML))
                .andExpect(content().string(containsString("Page not found")));
    }

    @Test
    void serverErrorPageRenders() throws Exception {
        mockMvc.perform(get("/test-error/500"))
                .andExpect(status().isOk())
                .andExpect(view().name("error/500"))
                .andExpect(content().string(containsString("Something went wrong")));
    }

    @Test
    void fallbackErrorPageRendersStatusAndMessage() throws Exception {
        mockMvc.perform(get("/test-error/error"))
                .andExpect(status().isOk())
                .andExpect(view().name("error/error"))
                .andExpect(content().string(containsString("418")))
                .andExpect(content().string(containsString("teapot")));
    }
}
