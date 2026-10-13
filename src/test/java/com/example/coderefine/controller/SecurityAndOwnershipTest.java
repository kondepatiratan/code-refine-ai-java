package com.example.coderefine.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "ai.gemini.api-key=",
        "ai.mistral.api-key="
})
class SecurityAndOwnershipTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper mapper;

    private String registerAndLogin(String email) throws Exception {
        String body = "{\"email\":\"" + email + "\",\"password\":\"secret123\"}";
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk());
        String json = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return mapper.readTree(json).get("token").asText();
    }

    @Test
    void wrongPasswordReturns401() throws Exception {
        registerAndLogin("a1@test.com");
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"a1@test.com\",\"password\":\"wrong\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("Invalid email or password"));
    }

    @Test
    void requestWithoutTokenReturns401() throws Exception {
        mvc.perform(get("/api/refine/sessions")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/detect-language").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"public class A {}\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void usersOnlySeeTheirOwnSessions() throws Exception {
        String tokenA = registerAndLogin("owner@test.com");
        String tokenB = registerAndLogin("other@test.com");

        String created = mvc.perform(post("/api/refine/sessions")
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"A's session\",\"language\":\"java\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        long sessionId = mapper.readTree(created).get("id").asLong();

        // Owner sees it
        mvc.perform(get("/api/refine/sessions").header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));

        // Another user sees an empty list and gets 404 for the owner's session
        mvc.perform(get("/api/refine/sessions").header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
        mvc.perform(get("/api/refine/sessions/" + sessionId).header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isNotFound());
        mvc.perform(delete("/api/refine/sessions/" + sessionId).header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isNotFound());
    }

    @Test
    void missingSessionReturns404() throws Exception {
        String token = registerAndLogin("c1@test.com");
        mvc.perform(get("/api/refine/sessions/99999").header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Session not found with id 99999"));
    }
}
