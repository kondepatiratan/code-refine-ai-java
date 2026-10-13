package com.example.coderefine.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class AiRefineService {
    private final String geminiApiKey;
    private final String geminiModel;
    @Value("${ai.gemini.fallback-models:}")
    private String geminiFallbackModels;
    private final String mistralApiKey;
    private final String jwtSecret;
    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final ObjectMapper objectMapper = new ObjectMapper();

    public AiRefineService(
            @Value("${ai.gemini.api-key:}") String geminiApiKey,
            @Value("${ai.gemini.model:gemini-2.5-flash}") String geminiModel,
            @Value("${ai.mistral.api-key:}") String mistralApiKey,
            @Value("${jwt.secret:secret123}") String jwtSecret) {
        this.geminiApiKey = geminiApiKey;
        this.geminiModel = geminiModel;
        this.mistralApiKey = mistralApiKey;
        this.jwtSecret = jwtSecret;
    }

    public Map<String, Object> refineCode(String code, String language) {
        return refineCode(code, language, msg -> { });
    }

    /**
     * Refines code with a fallback chain: Gemini models -> Mistral -> local fallback.
     * Each step reports a live status message through {@code progress}, which the
     * controller streams to the browser as Server-Sent Events.
     */
    public Map<String, Object> refineCode(String code, String language, Consumer<String> progress) {
        if (code == null || code.isBlank()) {
            return fallback(code, language, "No code provided");
        }

        String preferredProvider = getPreferredProvider();
        if (preferredProvider.equals("gemini") && hasApiKey(geminiApiKey)) {
            Map<String, Object> response = tryGeminiModels(code, language, progress);
            if (response != null) {
                return response;
            }
        }

        if (preferredProvider.equals("mistral") && hasApiKey(mistralApiKey)) {
            try {
                progress.accept("Analyzing with Mistral...");
                Map<String, Object> response = callMistral(code, language);
                if (response != null) {
                    return response;
                }
            } catch (Exception ex) {
                System.err.println("Mistral request failed: " + ex.getMessage());
            }
        }

        if (preferredProvider.equals("gemini") && hasApiKey(mistralApiKey)) {
            try {
                progress.accept("Gemini unavailable, switching to Mistral fallback...");
                Map<String, Object> response = callMistral(code, language);
                if (response != null) {
                    return response;
                }
            } catch (Exception ex) {
                System.err.println("Mistral request failed: " + ex.getMessage());
            }
        }

        if (preferredProvider.equals("mistral") && hasApiKey(geminiApiKey)) {
            progress.accept("Mistral unavailable, switching to Gemini...");
            Map<String, Object> response = tryGeminiModels(code, language, progress);
            if (response != null) {
                return response;
            }
        }

        progress.accept("AI providers unavailable, applying local refinement...");
        return fallback(code, language, "AI provider unavailable. Using local refinement fallback.");
    }

    private Map<String, Object> tryGeminiModels(String code, String language, Consumer<String> progress) {
        for (String model : getGeminiCandidateModels()) {
            try {
                progress.accept("Analyzing with Gemini (" + model + ")...");
                Map<String, Object> response = callGemini(code, language, model);
                if (response != null) {
                    return response;
                }
            } catch (Exception ex) {
                System.err.println("Gemini request failed for model " + model + ": " + ex.getMessage());
            }
        }
        return null;
    }

    private Map<String, Object> callGemini(String code, String language, String model) throws Exception {
        String prompt = buildPrompt(code, language);
        Map<String, Object> body = Map.of(
            "contents", List.of(
                Map.of("parts", List.of(
                    Map.of("text", prompt)
                ))
            )
        );
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://generativelanguage.googleapis.com/v1/models/" + model + ":generateContent?key=" + geminiApiKey))
                .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body), StandardCharsets.UTF_8))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() >= 400) {
            throw new RuntimeException(response.body());
        }

        JsonNode root = objectMapper.readTree(response.body());
        String text = root.path("candidates").path(0).path("content").path("parts").path(0).path("text").asText("");
        if (text.isBlank()) {
            return null;
        }
        Map<String, Object> parsed = parseAiResponse(text, "gemini", code, language);
        parsed.put("model", model);
        return parsed;
    }

    private List<String> getGeminiCandidateModels() {
        List<String> models = new ArrayList<>();
        if (geminiModel != null && !geminiModel.isBlank()) {
            String primary = geminiModel.trim();
            if (isSupportedGeminiModel(primary)) {
                models.add(primary);
            }
        }

        if (geminiFallbackModels != null && !geminiFallbackModels.isBlank()) {
            String[] candidates = geminiFallbackModels.split(",");
            for (String candidate : candidates) {
                String model = candidate == null ? "" : candidate.trim();
                if (!model.isBlank() && isSupportedGeminiModel(model) && !models.contains(model)) {
                    models.add(model);
                }
            }
        }

        if (models.isEmpty()) {
            models.add("gemini-2.5-flash");
        }
        return models;
    }

    private boolean isSupportedGeminiModel(String model) {
        if (model == null || model.isBlank()) {
            return false;
        }
        // Accept standard Gemini models
        return true;
    }

    private Map<String, Object> callMistral(String code, String language) throws Exception {
        String prompt = buildPrompt(code, language);
        Map<String, Object> payload = new HashMap<>();
        payload.put("model", "mistral-small-latest");
        payload.put("messages", List.of(Map.of("role", "user", "content", prompt)));

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://api.mistral.ai/v1/chat/completions"))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + mistralApiKey)
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(payload), StandardCharsets.UTF_8))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() >= 400) {
            throw new RuntimeException(response.body());
        }

        JsonNode root = objectMapper.readTree(response.body());
        String text = root.path("choices").path(0).path("message").path("content").asText("");
        if (text.isBlank()) {
            return null;
        }
        return parseAiResponse(text, "mistral", code, language);
    }

    private Map<String, Object> parseAiResponse(String text, String provider, String originalCode, String language) {
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (start >= 0 && end > start) {
            String jsonText = text.substring(start, end + 1);
            try {
                Map<String, Object> parsed = objectMapper.readValue(jsonText, Map.class);
                Map<String, Object> normalized = new HashMap<>();
                Object refinedCode = parsed.get("refinedCode");
                Object explanation = parsed.get("explanation");
                Object errorChanges = parsed.get("errorChanges");

                String safeRefined = (refinedCode == null || String.valueOf(refinedCode).isBlank())
                        ? originalCode
                        : String.valueOf(refinedCode);
                String safeExplanation = (explanation == null || String.valueOf(explanation).isBlank())
                        ? "Code refined successfully."
                        : String.valueOf(explanation);

                normalized.put("refinedCode", safeRefined);
                normalized.put("explanation", safeExplanation);
                normalized.put("errorChanges", errorChanges instanceof List ? errorChanges : List.of());
                normalized.put("model", provider);
                normalized.put("language", language);
                return normalized;
            } catch (Exception ignored) {
            }
        }
        return fallback(originalCode, language, "AI response was not valid JSON");
    }

    private String getPreferredProvider() {
        if (hasApiKey(geminiApiKey) && !hasApiKey(mistralApiKey)) {
            return "gemini";
        }
        if (hasApiKey(mistralApiKey) && !hasApiKey(geminiApiKey)) {
            return "mistral";
        }
        return "gemini";
    }

    private boolean hasApiKey(String apiKey) {
        return apiKey != null && !apiKey.isBlank();
    }

    private Map<String, Object> fallback(String code, String language, String explanation) {
        String refinedCode = code == null ? "" : code.trim();
        List<String> errorChanges = new ArrayList<>();

        // Local safety net: fix obvious Java out-of-bounds literal accesses in fallback mode.
        if ("java".equalsIgnoreCase(language)) {
            LocalRefineResult localResult = applyLocalJavaHeuristics(refinedCode);
            refinedCode = localResult.refinedCode();
            errorChanges.addAll(localResult.errorChanges());
            if (!localResult.errorChanges().isEmpty()) {
                explanation = "Applied local Java safety fixes (AI unavailable). " + explanation;
            }
        }

        Map<String, Object> response = new HashMap<>();
        response.put("refinedCode", refinedCode);
        response.put("explanation", explanation);
        response.put("errorChanges", errorChanges);
        response.put("model", "local-fallback");
        response.put("language", language);
        return response;
    }

    private String buildPrompt(String code, String language) {
        return "You are an expert " + language + " code fixer. "
                + "Fix syntax/runtime/logic issues while preserving intended behavior. "
                + "Return STRICT JSON only (no markdown, no backticks, no extra text) with this exact schema: "
                + "{\"refinedCode\":\"string\",\"explanation\":\"string\",\"errorChanges\":[\"string\"]}. "
                + "Rules: refinedCode must contain full corrected code; explanation must be short (max 4 lines); "
                + "errorChanges must list concrete fixes. If code is already correct, return original code in refinedCode.\n\n"
                + "Input code:\n" + code;
    }

    public String getJwtSecret() {
        return jwtSecret;
    }

    private LocalRefineResult applyLocalJavaHeuristics(String code) {
        if (code == null || code.isBlank()) {
            return new LocalRefineResult(code == null ? "" : code, List.of());
        }

        // Capture simple array literal declarations: int[] arr = {1,2,3};
        Pattern declarationPattern = Pattern.compile("\\b(?:byte|short|int|long|float|double|char|boolean|String)\\s*\\[\\s*]\\s*(\\w+)\\s*=\\s*\\{([^}]*)}");
        Matcher declarationMatcher = declarationPattern.matcher(code);

        Map<String, Integer> arraySizes = new HashMap<>();
        while (declarationMatcher.find()) {
            String var = declarationMatcher.group(1);
            String values = declarationMatcher.group(2).trim();
            if (values.isBlank()) {
                arraySizes.put(var, 0);
            } else {
                int size = (int) List.of(values.split(",")).stream().map(String::trim).filter(v -> !v.isBlank()).count();
                arraySizes.put(var, size);
            }
        }

        if (arraySizes.isEmpty()) {
            return new LocalRefineResult(code, List.of());
        }

        String refined = code;
        List<String> changes = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : arraySizes.entrySet()) {
            String var = entry.getKey();
            int size = entry.getValue();
            if (size <= 0) {
                continue;
            }

            Pattern accessPattern = Pattern.compile("\\b" + Pattern.quote(var) + "\\s*\\[\\s*(\\d+)\\s*]\\s*");
            Matcher accessMatcher = accessPattern.matcher(refined);
            StringBuffer sb = new StringBuffer();
            boolean changed = false;
            while (accessMatcher.find()) {
                int index = Integer.parseInt(accessMatcher.group(1));
                if (index >= size) {
                    int replacementIndex = size - 1;
                    String replacement = var + "[" + replacementIndex + "]";
                    accessMatcher.appendReplacement(sb, Matcher.quoteReplacement(replacement));
                    changed = true;
                } else {
                    accessMatcher.appendReplacement(sb, Matcher.quoteReplacement(accessMatcher.group(0)));
                }
            }
            accessMatcher.appendTail(sb);
            if (changed) {
                refined = sb.toString();
                changes.add("Adjusted out-of-bounds index on array '" + var + "' to last valid index " + (size - 1));
            }
        }

        return new LocalRefineResult(refined, changes);
    }

    private record LocalRefineResult(String refinedCode, List<String> errorChanges) {}
}
