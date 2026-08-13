package com.example.demo.domain.series;

import com.example.demo.domain.user.User;
import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Entity
@Table(name = "series_spoiler_accepted")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SeriesSpoilerAccepted {

    @EmbeddedId
    private SeriesSpoilerAcceptedId id;

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("userId")
    @JoinColumn(name = "user_id")
    private User user;
}