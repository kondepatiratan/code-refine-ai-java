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
        return Map.of("language", refineService.detectLanguage(request.getCode()));
    }

    @GetMapping("/refine/sessions")
    public List<Session> getSessions() {
        return refineService.getAllSessions();
    }

    @PostMapping("/refine/sessions")
    public Session createSession(@RequestBody SessionRequest request) {
        return refineService.createSession(request.getTitle(), request.getLanguage());
    }

    @GetMapping("/refine/sessions/{sid}")
    public Map<String, Object> getSession(@PathVariable Long sid) {
        Session session = refineService.getSession(sid);
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
    public Map<String, Boolean> deleteSession(@PathVariable Long sid) {
        refineService.deleteSession(sid);
        return Map.of("ok", true);
    }

    @PostMapping(value = "/refine/sessions/{sid}/refine", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public ResponseEntity<StreamingResponseBody> refine(@PathVariable Long sid, @RequestBody RefineRequest request) {
        StreamingResponseBody stream = outputStream -> {
            try {
                writeChunk(outputStream, "Refining code...\n");
                Map<String, Object> result = refineService.refineCode(request.getCode(), request.getLanguage());
                String refinedCode = String.valueOf(result.getOrDefault("refinedCode", request.getCode()));
                String explanation = String.valueOf(result.getOrDefault("explanation", "Refinement completed."));

                refineService.saveRefinement(sid, request.getCode(), refinedCode, explanation, request.getLanguage());

                Map<String, Object> payload = new HashMap<>();
                payload.put("done", true);
                payload.put("refinedCode", refinedCode);
                payload.put("explanation", explanation);
                payload.put("errorChanges", result.getOrDefault("errorChanges", List.of()));
                payload.put("score", result.getOrDefault("score", 100));
                payload.put("model", result.getOrDefault("model", "local-fallback"));
                writeData(outputStream, payload);
            } catch (Exception ex) {
                Map<String, Object> payload = new HashMap<>();
                payload.put("done", true);
                payload.put("refinedCode", request.getCode());
                payload.put("explanation", "Refinement failed. " + ex.getMessage());
                payload.put("errorChanges", List.of());
                payload.put("score", 0);
                payload.put("model", "local-fallback");
                writeData(outputStream, payload);
            }
        };
        return ResponseEntity.ok().contentType(MediaType.TEXT_EVENT_STREAM).body(stream);
    }

    private void writeChunk(OutputStream outputStream, String text) throws IOException {
        outputStream.write(("data: " + objectMapper.writeValueAsString(Map.of("chunk", text)) + "\n\n").getBytes(StandardCharsets.UTF_8));
        outputStream.flush();
    }

    private void writeData(OutputStream outputStream, Map<String, Object> data) throws IOException {
        outputStream.write(("data: " + objectMapper.writeValueAsString(data) + "\n\n").getBytes());
        outputStream.flush();
    }
}
