package com.example.demo.domain.publication;

import com.example.demo.domain.comment.ReactionType;
import com.example.demo.domain.user.User;
import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.time.LocalDateTime;

@Entity
@Table(name = "publication_reactions",
        uniqueConstraints = @UniqueConstraint(columnNames = {"publication_id","user_id","reaction_type"}))
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PublicationReaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "publication_id", nullable = false)
    private Publication publication;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(name = "reaction_type", length = 20, nullable = false)
    private PublicationReactionType reactionType;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}