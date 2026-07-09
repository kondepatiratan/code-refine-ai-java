package com.example.coderefine.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

@Entity
@Table(name = "refinements")
public class Refinement {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "session_id")
    @JsonIgnore
    private Session session;

    @Column(columnDefinition = "TEXT")
    private String originalCode;

    @Column(columnDefinition = "TEXT")
    private String refinedCode;

    @Column(columnDefinition = "TEXT")
    private String explanation;

    private String language;
    private Integer score;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    public Refinement() {
        this.createdAt = LocalDateTime.now();
    }

    public Refinement(Session session, String originalCode, String refinedCode, String explanation, String language, Integer score) {
        this();
        this.session = session;
        this.originalCode = originalCode;
        this.refinedCode = refinedCode;
        this.explanation = explanation;
        this.language = language;
        this.score = score;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Session getSession() { return session; }
    public void setSession(Session session) { this.session = session; }
    public String getOriginalCode() { return originalCode; }
    public void setOriginalCode(String originalCode) { this.originalCode = originalCode; }
    public String getRefinedCode() { return refinedCode; }
    public void setRefinedCode(String refinedCode) { this.refinedCode = refinedCode; }
    public String getExplanation() { return explanation; }
    public void setExplanation(String explanation) { this.explanation = explanation; }
    public String getLanguage() { return language; }
    public void setLanguage(String language) { this.language = language; }
    public Integer getScore() { return score; }
    public void setScore(Integer score) { this.score = score; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
