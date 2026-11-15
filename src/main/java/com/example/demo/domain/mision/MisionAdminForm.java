package com.example.demo.domain.mision;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Lo que el admin completa en el asistente para crear o editar una misión (y su sello). Todos los campos son opcionales a este nivel:
 * es MisionAdminValidador quien decide qué falta, qué sobra y qué combinaciones no tienen sentido.
 */
public record MisionAdminForm(
        // ---- 1. qué se le pide al usuario
        String metrica,                 // VOTOS, COMENTARIOS, RACHA_DIAS, TRIVIAS, PUBLICACIONES, INTERVENCIONES, CANJES, SORTEOS, GUSTOS, RECOMENDACIONES
        String tipoContenido,           // PELICULA | SERIE | null (ambas)
        Long generoId, Long directorId, Long sagaId, Long actorId,      // ids de TMDB (solo para películas)
        Boolean distintas,              // contar títulos / publicaciones / premios DISTINTOS
        // ---- 2. cuánto y cuándo
        Integer meta,
        String vigencia,                // HOY, SEMANA, PERMANENTE, RACHA, EVENTO
        LocalDateTime desde, LocalDateTime hasta,
        List<Integer> escalera,         // metas crecientes (opcional)
        Integer escaleraPaso,           // paso fijo después del último escalón (opcional)
        String escaleraSobre,           // PROGRESO | VECES (si no viene, se deduce de la vigencia)
        // ---- 3. nombre y sello
        String titulo, String descripcion, String descripcionCumplida, String categoria,
        Long selloExistenteId, Integer nivelSello,                      // para reutilizar el sello de otra misión
        String selloNombre, String selloDescripcion, String selloImagenUrl, String selloCategoria,
        // ---- 4. visibilidad
        String visibilidad,             // VISIBLE | MISTERIOSA | OCULTA
        Integer sellosNecesarios,       // solo MISTERIOSA: cuántos sellos hacen falta para revelarla
        Long prerrequisitoId,           // misión que hay que ganar antes (encadenada)
        String audiencia,               // TODOS | PREMIUM | FREE
        String dificultad,              // FACIL | MEDIA | DIFICIL
        Integer orden,
        String accionTexto, String accionDestino) {
}
