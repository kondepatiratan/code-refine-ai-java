package com.example.coderefine.service;

import com.example.coderefine.model.Refinement;
import com.example.coderefine.model.Session;
import com.example.coderefine.repository.RefinementRepository;
import com.example.coderefine.repository.SessionRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Service
public class RefineService {
    private final SessionRepository sessionRepository;
    private final RefinementRepository refinementRepository;
    private final AiRefineService aiRefineService;

    public RefineService(SessionRepository sessionRepository, RefinementRepository refinementRepository, AiRefineService aiRefineService) {
        this.sessionRepository = sessionRepository;
        this.refinementRepository = refinementRepository;
        this.aiRefineService = aiRefineService;
    }

    public Session createSession(String title, String language) {
        Session session = new Session(title, language);
        return sessionRepository.save(session);
    }

    public List<Session> getAllSessions() {
        return sessionRepository.findAll();
    }

    public Session getSession(Long id) {
        return sessionRepository.findById(id).orElseThrow();
    }

    public void deleteSession(Long id) {
        sessionRepository.deleteById(id);
    }

    public Refinement saveRefinement(Long sessionId, String originalCode, String refinedCode, String explanation, String language) {
        Session session = sessionRepository.findById(sessionId).orElseThrow();
        session.setUpdatedAt(LocalDateTime.now());
        sessionRepository.save(session);
        Refinement refinement = new Refinement(session, originalCode, refinedCode, explanation, language, calcScore(refinedCode));
        return refinementRepository.save(refinement);
    }

    public List<Refinement> getRefinements(Session session) {
        return refinementRepository.findBySessionOrderByCreatedAtDesc(session);
    }

    public String detectLanguage(String code) {
        if (code == null || code.trim().length() < 10) {
            return "other";
        }
        String lower = code.toLowerCase();
        
        // Python: check for unique Python keywords first
        if (lower.contains("def ") || lower.contains("import sys") || lower.contains("print(") || lower.contains("elif ")) {
            return "python";
        }
        
        // Java: check for unique Java keywords
        if (lower.contains("package ") || lower.contains("public class") || lower.contains("import java")) {
            return "java";
        }
        
        // JavaScript
        if (lower.contains("function") || lower.contains("const ") || lower.contains("let ")) {
            return "javascript";
        }
        
        // C#
        if (lower.contains("using ") || lower.contains("namespace ")) {
            return "csharp";
        }
        
        return "other";
    }

    public Map<String, Object> refineCode(String code, String language) {
        return aiRefineService.refineCode(code, language);
    }

    private int calcScore(String code) {
        int score = 100;
        if (code.contains("any")) score -= 10;
        if (code.length() < 20) score -= 20;
        return Math.max(score, 0);
    }
}
