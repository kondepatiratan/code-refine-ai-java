package com.example.coderefine.repository;

import com.example.coderefine.model.Session;
import com.example.coderefine.model.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SessionRepository extends JpaRepository<Session, Long> {
    // Derived queries: Spring builds the SQL from the method names
    List<Session> findByUserOrderByUpdatedAtDesc(User user);

    Optional<Session> findByIdAndUser(Long id, User user);
}
