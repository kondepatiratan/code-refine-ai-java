package com.example.coderefine.repository;

import com.example.coderefine.model.Refinement;
import com.example.coderefine.model.Session;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface RefinementRepository extends JpaRepository<Refinement, Long> {
    List<Refinement> findBySessionOrderByCreatedAtDesc(Session session);
}
