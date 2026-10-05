package ir.graph.repo.server;

import ir.graph.repo.core.util.Json;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.file.Path;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Runtime tests for localization before authentication, including the security filter chain. */
@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class LocalizationMvcTest {
    private static final Path HOME = Path.of("target", "test-data", "locale-" + UUID.randomUUID()).toAbsolutePath();
    @Autowired MockMvc mvc;

    @DynamicPropertySource
    static void settings(DynamicPropertyRegistry properties) {
        properties.add("graph.home", HOME::toString);
        properties.add("graph.bootstrap-password", () -> "localization-test-password-only-2026");
        properties.add("graph.public-url", () -> "http://localhost:8081");
        properties.add("graph.login-failure-limit", () -> 0);
    }

    @Test void initialDocumentIsEnglishAndLtr() throws Exception {
        mvc.perform(get("/index.html"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("lang=\"en\" dir=\"ltr\"")));
    }

    @ParameterizedTest
    @CsvSource({"en,en", "de-DE,de", "fa-IR,fa", "fr-FR,en", "de;q=0.2|fa;q=0.9,fa"})
    void errorLocaleIsNegotiatedBeforeLogin(String header, String expected) throws Exception {
        mvc.perform(get("/api/session").header("Accept-Language", header.replace('|', ',')))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("Content-Language", expected))
                .andExpect(header().stringValues("Vary", org.hamcrest.Matchers.hasItem(containsString("Accept-Language"))))
                .andExpect(jsonPath("$.error").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.message").isNotEmpty());
    }

    @Test void missingLanguageDefaultsToEnglish() throws Exception {
        mvc.perform(get("/api/session"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("Content-Language", "en"))
                .andExpect(jsonPath("$.message").value("Sign in or provide a valid access token."));
    }

    @Test void germanAuthenticationErrorsAndPersianOriginErrorsAreTranslated() throws Exception {
        mvc.perform(post("/api/login").header("Origin", "http://localhost:8081")
                        .header("Accept-Language", "de").contentType(MediaType.APPLICATION_JSON)
                        .content(Json.stringify(Json.map("username", "admin", "password", "incorrect"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Melden Sie sich an oder verwenden Sie ein gültiges Zugriffstoken."));
        mvc.perform(post("/api/login").header("Origin", "https://invalid.example")
                        .header("Accept-Language", "fa").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden())
                .andExpect(header().string("Content-Language", "fa"))
                .andExpect(jsonPath("$.error").value("ORIGIN"));
    }

    @ParameterizedTest
    @CsvSource({"en", "de", "fa"})
    void translationCatalogsAreAvailableBeforeAuthentication(String language) throws Exception {
        mvc.perform(get("/locales/" + language + ".json"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dashboard").isNotEmpty())
                .andExpect(jsonPath("$.language").isNotEmpty())
                .andExpect(header().exists("Content-Security-Policy"));
    }

    @Test void unrelatedLocalePathsRemainDenied() throws Exception {
        mvc.perform(get("/locales/secrets.json")).andExpect(status().isNotFound());
    }
}
