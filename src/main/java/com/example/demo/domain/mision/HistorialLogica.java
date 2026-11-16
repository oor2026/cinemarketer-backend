package com.example.demo.domain.mision;

import com.example.demo.domain.mision.HistorialDatos.Evento;
import com.example.demo.domain.mision.MisionLogica.Mision;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

/**
 * Las REGLAS de «Reclamar», sin Spring ni base de datos (se prueban solas, en HistorialLogicaTest): qué misiones miran el historial,
 * hasta qué momento se cuenta y cuántas cosas del historial suman en una misión.
 *
 * Qué cuenta como historial: lo que el usuario hizo ANTES de la primera actividad que el sistema contó en vivo para esa misión (si todavía
 * no contó ninguna, todo lo que hizo hasta ahora). Así lo que ya contó el sistema en vivo y el historial nunca se suman dos veces.
 */
public final class HistorialLogica {

    private HistorialLogica() {}

    /** Margen de seguridad: el sistema crea la fila de la misión unos instantes DESPUÉS de que se guarda el voto o el comentario. */
    public static final Duration MARGEN = Duration.ofSeconds(10);

    /** De una película: sus géneros, directores, elenco principal y a cuáles de las sagas candidatas pertenece (los mismos datos que usa el conteo en vivo). */
    public record Atributos(Set<Long> generos, Set<Long> directores, Set<Long> elenco, Set<Long> sagas) {
        public static final Atributos VACIO = new Atributos(Set.of(), Set.of(), Set.of(), Set.of());
    }

    /** total = lo que suma en la misión (si cuenta títulos distintos, cuántos; si no, cuántas veces) · titulos = los ids encontrados. */
    public record Conteo(int total, Set<Long> titulos) {}

    /**
     * ¿Esta misión mira el historial? Solo las permanentes de votos o comentarios, sin fechas: las del día, de la semana, de evento y de racha
     * no (una campaña de «votá una película esta semana» no puede regalarse con votos de hace un año), y las demás métricas todavía no.
     */
    public static boolean elegible(Mision m) {
        return ("VOTOS".equals(m.metrica()) || "COMENTARIOS".equals(m.metrica()))
                && "PERMANENTE".equals(m.vigencia())
                && !m.escaleraSobreVeces()
                && m.desde() == null && m.hasta() == null;
    }

    /** Hasta cuándo se cuenta el historial de una misión (lo anterior a ese momento). */
    public static LocalDateTime corte(LocalDateTime primerAvanceEnVivo, LocalDateTime ahora) {
        return primerAvanceEnVivo == null ? ahora : primerAvanceEnVivo.minus(MARGEN);
    }

    /** La meta que se persigue: la del escalón en que está (en las escaleras) o la de la misión. */
    public static int meta(Mision m, int escalonesLogrados) {
        return m.metaDelNivel(m.esEscalera() ? escalonesLogrados : 0);
    }

    /**
     * Cuántas cosas del historial suman en esta misión. Usa el MISMO criterio que el conteo en vivo (MisionLogica.coincide): mismo tipo de
     * actividad, mismo tipo de contenido y, si la misión pide género, director, saga o elenco, lo que digan los atributos de la película.
     * Los filtros de TMDB solo existen para películas: una serie nunca cumple una misión con filtro, igual que en vivo.
     */
    public static Conteo contar(Mision m, List<Evento> eventos, LocalDateTime corte, Function<Long, Atributos> atributos) {
        String tipo = "VOTOS".equals(m.metrica()) ? "VOTO" : "COMENTARIO";
        Set<Long> ids = new LinkedHashSet<>();
        int total = 0;
        for (Evento e : eventos) {
            if (!tipo.equals(e.tipo())) continue;
            if (!e.cuando().isBefore(corte)) continue;
            if (m.tipoContenido() != null && !m.tipoContenido().equals(e.contenido())) continue;
            if (m.necesitaTmdb()) {
                Atributos a = "PELICULA".equals(e.contenido()) ? atributos.apply(e.targetId()) : Atributos.VACIO;
                if (m.generoId() != null && !a.generos().contains(m.generoId())) continue;
                if (m.directorId() != null && !a.directores().contains(m.directorId())) continue;
                if (m.sagaId() != null && !a.sagas().contains(m.sagaId())) continue;
                if (m.actorId() != null && !a.elenco().contains(m.actorId())) continue;
            }
            total++;
            ids.add(e.targetId());
        }
        return new Conteo(m.cuentaDistintos() ? ids.size() : total, ids);
    }
}
