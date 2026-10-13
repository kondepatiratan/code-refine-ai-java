package com.example.coderefine.controller;

import com.example.coderefine.dto.DetectLanguageRequest;
import com.example.coderefine.dto.RefineRequest;
import com.example.coderefine.dto.SessionRequest;
import com.example.coderefine.model.Refinement;
import com.example.coderefine.model.Session;
import com.example.coderefine.service.RefineService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.IOException;
import java.io.OutputStream;
import java.security.Principal;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class RefineController {
    private final RefineService refineService;
    private final ObjectMapper objectMapper;

    public RefineController(RefineService refineService, ObjectMapper objectMapper) {
        this.refineService = refineService;
        this.objectMapper = objectMapper;
    }

    @PostMapping("/detect-language")
    public Map<String, String> detectLanguage(@RequestBody DetectLanguageRequest request) {
        if (request == null || request.getCode() == null || request.getCode().isBlank()) {
            throw new IllegalArgumentException("Code cannot be empty");
        }
        return Map.of("language", refineService.detectLanguage(request.getCode()));
    }

    @GetMapping("/refine/sessions")
    public List<Session> getSessions(Principal principal) {
        return refineService.getAllSessions(principal.getName());
    }

    @PostMapping("/refine/sessions")
    public Session createSession(@RequestBody SessionRequest request, Principal principal) {
        if (request == null || request.getTitle() == null || request.getTitle().isBlank()) {
            throw new IllegalArgumentException("Session title cannot be empty");
        }
        if (request.getLanguage() == null || request.getLanguage().isBlank()) {
            throw new IllegalArgumentException("Language cannot be empty");
        }
        return refineService.createSession(principal.getName(), request.getTitle(), request.getLanguage());
    }

    @GetMapping("/refine/sessions/{sid}")
    public Map<String, Object> getSession(@PathVariable Long sid, Principal principal) {
        Session session = refineService.getSession(principal.getName(), sid);
        List<Refinement> refinements = refineService.getRefinements(session);
        Map<String, Object> body = new HashMap<>();
        body.put("id", session.getId());
        body.put("title", session.getTitle());
        body.put("language", session.getLanguage());
        body.put("updatedAt", session.getUpdatedAt());
        body.put("refinements", refinements);
        return body;
    }

    @DeleteMapping("/refine/sessions/{sid}")
    public Map<String, Boolean> deleteSession(@PathVariable Long sid, Principal principal) {
        refineService.deleteSession(principal.getName(), sid);
        return Map.of("ok", true);
    }

    @PostMapping(value = "/refine/sessions/{sid}/refine", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public ResponseEntity<StreamingResponseBody> refine(@PathVariable Long sid, @RequestBody RefineRequest request, Principal principal) {
        if (request == null || request.getCode() == null || request.getCode().isBlank()) {
            throw new IllegalArgumentException("Code cannot be empty");
        }
        if (request.getLanguage() == null || request.getLanguage().isBlank()) {
            throw new IllegalArgumentException("Language cannot be empty");
        }
        // Check ownership before streaming starts, so another user's session returns 404
        Session session = refineService.getSession(principal.getName(), sid);

        StreamingResponseBody stream = outputStream -> {
            try {
                writeChunk(outputStream, "Refining code...\n");
                // Stream each fallback step to the browser as it happens
                Map<String, Object> result = refineService.refineCode(request.getCode(), request.getLanguage(), msg -> {
                    try {
                        writeChunk(outputStream, msg + "\n");
                    } catch (IOException ignored) {
                        // client disconnected; refinement still completes and is saved
                    }
                });
                writeChunk(outputStream, "Done. Saving to history...\n");
                String refinedCode = String.valueOf(result.getOrDefault("refinedCode", request.getCode()));
                String explanation = String.valueOf(result.getOrDefault("explanation", "Refinement completed."));

                refineService.saveRefinement(session, request.getCode(), refinedCode, explanation, request.getLanguage());

                Map<String, Object> payload = new HashMap<>();
                payload.put("done", true);
                payload.put("refinedCode", refinedCode);
                payload.put("explanation", explanation);
                payload.put("errorChanges", result.getOrDefault("errorChanges", List.of()));
                payload.put("score", result.getOrDefault("score", 100));
                payload.put("model", result.getOrDefault("model", "local-fallback"));
                writeData(outputStream, payload);
            } catch (Exception ex) {
                System.err.println("Error during refinement: " + ex.getMessage());
                ex.printStackTrace();
                
                Map<String, Object> payload = new HashMap<>();
                payload.put("done", true);
                payload.put("refinedCode", request.getCode());
                payload.put("explanation", "Refinement failed. Please try again.");
                payload.put("errorChanges", List.of());
                payload.put("score", 0);
                payload.put("model", "local-fallback");
                payload.put("error", true);
                try {
                    writeData(outputStream, payload);
                } catch (IOException ioe) {
                    System.err.println("Failed to write error response: " + ioe.getMessage());
                }
            }
        };
        return ResponseEntity.ok().contentType(MediaType.TEXT_EVENT_STREAM).body(stream);
    }

    private void writeChunk(OutputStream outputStream, String text) throws IOException {
        outputStream.write(("data: " + objectMapper.writeValueAsString(Map.of("chunk", text)) + "\n\n").getBytes(StandardCharsets.UTF_8));
        outputStream.flush();
    }

    private void writeData(OutputStream outputStream, Map<String, Object> data) throws IOException {
        outputStream.write(("data: " + objectMapper.writeValueAsString(data) + "\n\n").getBytes(StandardCharsets.UTF_8));
        outputStream.flush();
    }
}
