package com.example.demo.domain.comment;

import com.example.demo.domain.user.User;
import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Entity
@Table(name = "spoiler_accepted")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SpoilerAccepted {

    @EmbeddedId
    private SpoilerAcceptedId id;

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("userId")
    @JoinColumn(name = "user_id")
    private User user;
}