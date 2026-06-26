package com.enterprise.ticketmaster.repository;

import com.enterprise.ticketmaster.model.Comment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CommentRepository extends JpaRepository<Comment, Long> {

    @Query(value = """
        SELECT
            c.ticket_id,
            t.raised_by,
            t.assigned_agent,
            t.created_at AS ticket_created_at,
            MIN(c.created_at) AS first_comment_at
        FROM comments c
        JOIN tickets t ON t.id = c.ticket_id
        WHERE c.author_username != t.raised_by
          AND t.assigned_agent IS NOT NULL
        GROUP BY c.ticket_id, t.raised_by, t.assigned_agent, t.created_at
        """, nativeQuery = true)
    List<Object[]> findFirstResponsePerTicket();
}