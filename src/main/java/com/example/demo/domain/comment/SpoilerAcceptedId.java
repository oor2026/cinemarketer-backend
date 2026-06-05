package com.example.demo.domain.comment;

import jakarta.persistence.Embeddable;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.io.Serializable;

@Embeddable
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SpoilerAcceptedId implements Serializable {
    private Long userId;
    private Long movieId;
}