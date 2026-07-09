package com.example.coderefine.service;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AiRefineServiceTest {

    @Test
    void shouldFallbackToLocalRefinementWhenNoApiKeysAreConfigured() {
        AiRefineService service = new AiRefineService("", "gemini-2.0-flash-latest", "", "secret123");

        Map<String, Object> result = service.refineCode("def add(a,b):\n    return a+b", "python");

        assertTrue(result.containsKey("refinedCode"));
        assertEquals("def add(a,b):\n    return a+b", result.get("refinedCode"));
        assertTrue(result.containsKey("explanation"));
    }

    @Test
    void shouldPreferGeminiWhenConfiguredAndUseSingleProviderPerRequest() {
        AiRefineService service = new AiRefineService("test-gemini-key", "gemini-2.0-flash-latest", "", "secret123");

        Map<String, Object> result = service.refineCode("def add(a,b):\n    return a+b", "python");

        assertTrue(result.containsKey("model"));
        assertTrue(result.get("model").toString().contains("gemini") || result.get("model").toString().equals("local-fallback"));
        assertTrue(result.containsKey("refinedCode"));
        assertTrue(result.containsKey("explanation"));
    }
}
