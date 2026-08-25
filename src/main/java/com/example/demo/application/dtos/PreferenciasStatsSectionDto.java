package com.example.demo.application.dtos;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PreferenciasStatsSectionDto {
    // [{genero, totem, total, porcentaje}] — la distribución de espíritu
    private List<Map<String, Object>> distribucionEspiritu;
    private List<Map<String, Object>> topFavoritas;
    private List<Map<String, Object>> topNoMeCanso;
    private List<Map<String, Object>> topNoLaBanco;
}