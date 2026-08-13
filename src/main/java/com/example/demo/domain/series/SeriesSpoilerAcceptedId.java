package com.example.demo.domain.series;

import jakarta.persistence.Embeddable;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.io.Serializable;

@Embeddable
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SeriesSpoilerAcceptedId implements Serializable {
    private Long userId;
    private Long seriesId;
}