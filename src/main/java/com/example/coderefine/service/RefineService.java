package com.example.coderefine.service;

import com.example.coderefine.exception.ResourceNotFoundException;
import com.example.coderefine.model.Refinement;
import com.example.coderefine.model.Session;
import com.example.coderefine.model.User;
import com.example.coderefine.repository.RefinementRepository;
import com.example.coderefine.repository.SessionRepository;
import com.example.coderefine.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Service
public class RefineService {
    private final SessionRepository sessionRepository;
    private final RefinementRepository refinementRepository;
    private final UserRepository userRepository;
    private final AiRefineService aiRefineService;

    public RefineService(SessionRepository sessionRepository, RefinementRepository refinementRepository,
                         UserRepository userRepository, AiRefineService aiRefineService) {
        this.sessionRepository = sessionRepository;
        this.refinementRepository = refinementRepository;
        this.userRepository = userRepository;
        this.aiRefineService = aiRefineService;
    }

    public Session createSession(String email, String title, String language) {
        return sessionRepository.save(new Session(title, language, getUser(email)));
    }

    public List<Session> getAllSessions(String email) {
        return sessionRepository.findByUserOrderByUpdatedAtDesc(getUser(email));
    }

    /** Returns the session only if it belongs to this user; otherwise 404. */
    public Session getSession(String email, Long id) {
        return sessionRepository.findByIdAndUser(id, getUser(email))
                .orElseThrow(() -> new ResourceNotFoundException("Session not found with id " + id));
    }

    @Transactional
    public void deleteSession(String email, Long id) {
        sessionRepository.delete(getSession(email, id));
    }

    @Transactional
    public Refinement saveRefinement(Session session, String originalCode, String refinedCode, String explanation, String language) {
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

    public Map<String, Object> refineCode(String code, String language, java.util.function.Consumer<String> progress) {
        return aiRefineService.refineCode(code, language, progress);
    }

    private User getUser(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
    }

    private int calcScore(String code) {
        int score = 100;
        if (code.contains("any")) score -= 10;
        if (code.length() < 20) score -= 20;
        return Math.max(score, 0);
    }
}
