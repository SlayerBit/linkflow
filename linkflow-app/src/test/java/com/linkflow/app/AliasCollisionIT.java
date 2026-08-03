package com.linkflow.app;

import com.linkflow.app.support.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Custom aliases are unique across all users, so claiming one is inherently a race. What matters is
 * that whoever loses gets a conflict they can act on rather than an opaque server error.
 */
class AliasCollisionIT extends AbstractIntegrationTest {

    private String firstUserToken;
    private String secondUserToken;

    @BeforeEach
    void createTwoUsers() throws Exception {
        firstUserToken = tokenForNewUser();
        secondUserToken = tokenForNewUser();
    }

    @Test
    void aliasAlreadyTakenByAnotherUserIsRejectedAsAConflict() throws Exception {
        String alias = uniqueAlias();

        createWithAlias(firstUserToken, alias).andExpect(status().isCreated());

        createWithAlias(secondUserToken, alias)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("CONFLICT"))
                .andExpect(jsonPath("$.message").value(
                        org.hamcrest.Matchers.containsString(alias)));
    }

    @Test
    void reusingOwnAliasIsAlsoAConflict() throws Exception {
        String alias = uniqueAlias();

        createWithAlias(firstUserToken, alias).andExpect(status().isCreated());

        createWithAlias(firstUserToken, alias)
                .andExpect(status().isConflict());
    }

    @Test
    void aliasesAreCaseInsensitiveSoCasingCannotBeUsedToDuplicateOne() throws Exception {
        String alias = uniqueAlias();

        createWithAlias(firstUserToken, alias.toLowerCase()).andExpect(status().isCreated());

        // Aliases are normalised to lower case, so this is the same link, not a second one.
        createWithAlias(secondUserToken, alias.toUpperCase())
                .andExpect(status().isConflict());
    }

    private org.springframework.test.web.servlet.ResultActions createWithAlias(
            String accessToken, String alias) throws Exception {
        return mockMvc.perform(post("/api/v1/urls")
                .header("Authorization", "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        { "originalUrl": "https://example.com/alias-race", "customAlias": "%s" }
                        """.formatted(alias)));
    }

    private String tokenForNewUser() throws Exception {
        String email = "alias-" + UUID.randomUUID() + "@example.com";
        registerUser(email, "StrongP@ss1", "Alias");
        return login(email, "StrongP@ss1").accessToken();
    }

    private static String uniqueAlias() {
        return "al" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
    }
}
